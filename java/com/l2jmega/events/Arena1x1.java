package com.l2jmega.events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.commons.random.Rnd;

import com.l2jmega.Config;
import com.l2jmega.gameserver.model.actor.Summon;
import com.l2jmega.gameserver.model.actor.instance.Pet;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.network.serverpackets.ExShowScreenMessage;
import com.l2jmega.gameserver.model.olympiad.OlympiadManager;
import com.l2jmega.gameserver.util.SiegeParticipationUtil;

import Base.RandomFightEvent.RandomFight;
import phantom.FakePlayer;
import phantom.ai.event.TournamentAI;

public class Arena1x1 implements Runnable
{
	protected static final Logger _log = Logger.getLogger(Arena1x1.class.getName());
	
	// list of participants
	public static List<Pair> registered;
	// number of Arenas
	int free = Config.ARENA_EVENT_COUNT_1X1;
	// Arenas
	Arena[] arenas = new Arena[Config.ARENA_EVENT_COUNT_1X1];
	// list of fights going on
	Map<Integer, String> fights = new HashMap<>(Config.ARENA_EVENT_COUNT_1X1);
	
	public Arena1x1()
	{
		registered = new ArrayList<>();
		int[] coord;
		for (int i = 0; i < Config.ARENA_EVENT_COUNT_1X1; i++)
		{
			coord = Config.ARENA_EVENT_LOCS_1X1[i];
			arenas[i] = new Arena(i, coord[0], coord[1], coord[2]);
		}
	}
	
	public static Arena1x1 getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	public boolean register(Player player)
	{
		if (SiegeParticipationUtil.isPlayerOrClanInActiveSiege(player))
		{
			player.sendMessage("Tournament: You cannot register while your clan is participating in an active siege.");
			return false;
		}

		if (OlympiadManager.getInstance().isParallelEventBlockedFor(player))
		{
			player.sendMessage("Tournament: You cannot register while registered for or fighting in Olympiad.");
			return false;
		}

		if (RandomFight.isCommittedToRandomFight(player))
		{
			player.sendMessage("Tournament: You cannot register while registered or fighting in Random Fight.");
			return false;
		}

		if (Arena2x2.getInstance().isRegistered(player) || Arena5x5.getInstance().isRegistered(player))
		{
			player.sendMessage("Tournament: You are already registered in another mode.");
			return false;
		}
		
		for (Pair p : registered)
		{
			if (p.getLeader() == player)
			{
				player.sendMessage("Tournament: You already registered!");
				return false;
			}

		}
		return registered.add(new Pair(player));
	}
	
	public boolean isRegistered(Player player)
	{
		for (Pair p : registered)
		{
			if (p.getLeader() == player)
			{
				return true;
			}
		}
		return false;
	}
	
	public Map<Integer, String> getFights()
	{
		return fights;
	}
	
	public boolean remove(Player player)
	{
		for (int i = 0; i < registered.size(); i++)
		{
			Pair p = registered.get(i);
			if (p.getLeader() == player)
			{
				p.removeMessage();
				registered.remove(i);
				return true;
			}
		}
		return false;
	}
	
	@Override
	public synchronized void run()
	{
		boolean load = true;
		
		// while server is running
		while (load)
		{
			if (!ArenaTask.is_started())
				load = false;
			
			// if no have participants or arenas are busy wait 1 minute
			if (registered.size() < 2 || free == 0)
			{
				try
				{
					Thread.sleep(Config.ARENA_CALL_INTERVAL_1X1 * 1000);
				}
				catch (InterruptedException e)
				{
				}
				continue;
			}
			List<Pair> opponents = selectOpponents();
			if (opponents != null && opponents.size() == 2)
			{
				Thread T = new Thread(new EvtArenaTask(opponents));
				T.setDaemon(true);
				T.start();
			}
			// wait 1 minute for not stress server
			try
			{
				Thread.sleep(Config.ARENA_CALL_INTERVAL_1X1 * 1000);
			}
			catch (InterruptedException e)
			{
			}
		}
	}
	
	@SuppressWarnings("null")
	private List<Pair> selectOpponents()
	{
		List<Pair> opponents = new ArrayList<>();
		Pair pairOne = null, pairTwo = null;
		int tries = 3;
		do
		{
			int first = 0, second = 0;
			if (getRegisteredCount() < 2)
				return opponents;
			
			if (pairOne == null)
			{
				first = Rnd.get(getRegisteredCount());
				pairOne = registered.get(first);
				if (pairOne.check())
				{
					opponents.add(0, pairOne);
					registered.remove(first);
				}
				else
				{
					pairOne = null;
					registered.remove(first);
					return null;
				}
				
			}
			if (pairTwo == null)
			{
				second = Rnd.get(getRegisteredCount());
				pairTwo = registered.get(second);
				if (pairTwo.check())
				{
					opponents.add(1, pairTwo);
					registered.remove(second);
				}
				else
				{
					pairTwo = null;
					registered.remove(second);
					return null;
				}
				
			}
		}
		while ((pairOne == null || pairTwo == null) && --tries > 0);
		return opponents;
	}
	
	public void clear()
	{
		registered.clear();
	}
	
	public int getRegisteredCount()
	{
		return registered.size();
	}

	private void restoreOpponents(List<Pair> opponents)
	{
		for (Pair p : opponents)
		{
			if (p == null)
				continue;

			if (!isRegistered(p.getLeader()))
				registered.add(p);
		}
	}

	public boolean isFightActive(int arenaId)
	{
		return fights.containsKey(arenaId);
	}
	
	private class Pair extends AbstractArenaPair
	{
		Player leader;
		
		public Pair(Player leader)
		{
			super(leader);
			this.leader = leader;
		}
		
		@Override
		protected void setModeFlag(Player player, boolean value)
		{
			player.setArena1x1(value);
		}
		
		public Player getLeader()
		{
			return leader;
		}
		
		public boolean check()
		{
			if (leader == null)
				return false;
			
			if (!leader.isOnline())
			{
				leader.sendMessage("Tournament: You participation in Event was Canceled.");
				return false;
			}
			return true;
		}
		
		public boolean isDead()
		{
			if (allMembersUnavailable())
				return false;
			
			return !allMembersDeadOrNull();
		}
		
		public boolean isAlive()
		{			
			if (allMembersUnavailable())
				return false;
			
			return !allMembersDeadOrNull();
		}
		
		public void teleportTo(int x, int y, int z)
		{
			if (leader != null && leader.isOnline())
			{
				leader.getAppearance().setInvisible();
				leader.setCurrentCp(leader.getMaxCp());
				leader.setCurrentHp(leader.getMaxHp());
				leader.setCurrentMp(leader.getMaxMp());
				
				if (leader.isInObserverMode())
				{
					leader.setLastCords(x, y, z);
					leader.leaveOlympiadObserverMode();
				}
				else if (!leader.isInJail())
					leader.teleToLocation(x, y, z, 0);
				
				leader.broadcastUserInfo();
				
			}
		}
		
		public void EventTitle(String title, String color)
		{
			if (leader != null && leader.isOnline())
			{
				leader.setTitle(title);
				leader.getAppearance().setTitleColor(Integer.decode("0x" + color));
				leader.broadcastUserInfo();
				leader.broadcastTitleInfo();
			}

		}

		@Override
		public void saveTitle()
		{
			super.saveTitle();
		}

		@Override
		public void backTitle()
		{
			super.backTitle();
		}

		public void rewards()
		{
			if (leader != null && leader.isOnline())
			{
				if (leader.isVip())
					leader.addItem("Arena_Event", Config.ARENA_REWARD_ID_1X1, Config.ARENA_WIN_REWARD_COUNT_1X1 * Config.RATE_DROP_VIP, leader, true);
				else
					leader.addItem("Arena_Event", Config.ARENA_REWARD_ID_1X1, Config.ARENA_WIN_REWARD_COUNT_1X1, leader, true);
				
			       if (Config.ACTIVE_MISSION) {
			           if (!this.leader.check_obj_mission(this.leader.getObjectId()))
			             this.leader.updateMission(); 
			           if (!this.leader.is1x1Completed() && this.leader.getTournament1x1Cont() < Config.MISSION_1X1_CONT)
			             this.leader.setTournament1x1Cont(this.leader.getTournament1x1Cont() + 1); 
			         }
			}
			
			sendPacket("Winner =)", 5);
			super.broadcastVictoryEffect();

		}

		public void rewardsLost()
		{
			if (leader != null && leader.isOnline())
				leader.addItem("Arena_Event", Config.ARENA_REWARD_ID_1X1, Config.ARENA_LOST_REWARD_COUNT_1X1, leader, true);
			
			sendPacket("Loser =(", 5);
		}

		@Override
		public void sendPacket(String message, int duration)
		{
			super.sendPacket(message, duration);
		}

		@Override
		public void setInTournamentEvent(boolean val)
		{
			super.setInTournamentEvent(val);
		}

		@Override
		public void removeMessage()
		{
			super.removeMessage();
		}

		public void setArenaProtection(boolean val)
		{
			super.setArenaProtectionAndMode(val);
		}

		public void revive()
		{
			super.reviveAll();
		}

		@Override
		public void setImobilised(boolean val)
		{
			if (leader != null && leader.isOnline())
			{
				leader.setIsInvul(val);
				leader.setStopArena(val);
			}
		}

		@Override
		public void setArenaAttack(boolean val)
		{
			if (leader != null && leader.isOnline())
			{
				leader.setArenaAttack(val);
				leader.broadcastUserInfo();
			}

		}
		
		public void removePet()
		{
			if (leader != null && leader.isOnline())
			{
				// Remove Summon's buffs
				if (leader.getPet() != null)
				{
					Summon summon = leader.getPet();
					if (summon != null)
						summon.unSummon(summon.getOwner());
					
					if (summon instanceof Pet)
						summon.unSummon(leader);
					
				}
				
				if (leader.getMountType() == 1 || leader.getMountType() == 2)
					leader.dismount();
				
			}
			
		}

		public void inicarContagem(int duration)
		{
			if (leader != null && leader.isOnline())
				ThreadPool.schedule(new countdown(leader, duration), 0);

		}
		
		public void sendPacketinit(String message, int duration)
		{
			if (leader != null && leader.isOnline())
				leader.sendPacket(new ExShowScreenMessage(message, duration * 1000, ExShowScreenMessage.SMPOS.MIDDLE_LEFT, false));			
		}

		public void TourAura(int color)
		{
			super.setTourAura(color);
		}
		
		
	}
	
	private class EvtArenaTask implements Runnable
	{
		private final Pair pairOne;
		private final Pair pairTwo;
		private final int pOneX, pOneY, pOneZ, pTwoX, pTwoY, pTwoZ;
		
		private Arena arena;
		
		public EvtArenaTask(List<Pair> opponents)
		{
			pairOne = opponents.get(0);
			pairTwo = opponents.get(1);
			Player leader = pairOne.getLeader();
			pOneX = leader.getX();
			pOneY = leader.getY();
			pOneZ = leader.getZ();
			leader = pairTwo.getLeader();
			pTwoX = leader.getX();
			pTwoY = leader.getY();
			pTwoZ = leader.getZ();
		}
		
		@Override
		public void run()
		{
			free--;
			pairOne.saveTitle();
			pairTwo.saveTitle();
			pairOne.TourAura(Config.TOUR_AURA_TEAM1);
			pairTwo.TourAura(Config.TOUR_AURA_TEAM2);
			if (!portPairsToArena())
			{
				restoreOpponents(List.of(pairOne, pairTwo));
				free++;
				return;
			}
			pairOne.inicarContagem(Config.ARENA_WAIT_INTERVAL_1X1);
			pairTwo.inicarContagem(Config.ARENA_WAIT_INTERVAL_1X1);
			try
			{
				Thread.sleep(Config.ARENA_WAIT_INTERVAL_1X1 * 1000);
			}
			catch (InterruptedException e1)
			{
			}
			pairOne.sendPacketinit("Started. Good Fight!", 3);
			pairTwo.sendPacketinit("Started. Good Fight!", 3);
			pairOne.EventTitle(Config.MSG_TEAM1, Config.TITLE_COLOR_TEAM1);
			pairTwo.EventTitle(Config.MSG_TEAM2, Config.TITLE_COLOR_TEAM2);
			pairOne.setImobilised(false);
			pairTwo.setImobilised(false);
			pairOne.setArenaAttack(true);
			pairTwo.setArenaAttack(true);
			
			while (check())
			{
				// check players status each seconds
				try
				{
					Thread.sleep(Config.ARENA_CHECK_INTERVAL_1X1);
				}
				catch (InterruptedException e)
				{
					break;
				}
			}
			finishDuel();
			free++;
		}
		
		private void finishDuel()
		{
			fights.remove(arena.id);
			rewardWinner();
			try
			{
				Thread.sleep(5000);
			}
			catch (InterruptedException e)
			{
			}
			pairOne.revive();
			pairTwo.revive();
			pairOne.teleportTo(pOneX, pOneY, pOneZ);
			pairTwo.teleportTo(pTwoX, pTwoY, pTwoZ);
			pairOne.TourAura(0);
			pairTwo.TourAura(0);
			pairOne.backTitle();
			pairTwo.backTitle();
			pairOne.setInTournamentEvent(false);
			pairTwo.setInTournamentEvent(false);
			pairOne.setArenaProtection(false);
			pairTwo.setArenaProtection(false);
			pairOne.setArenaAttack(false);
			pairTwo.setArenaAttack(false);
			arena.setFree(true);
			Player l1 = pairOne.getLeader();
			Player l2 = pairTwo.getLeader();
			if (ArenaTask.is_started() && l1 instanceof FakePlayer)
				TournamentAI.scheduleFakeReRegister1x1((FakePlayer) l1);
			if (ArenaTask.is_started() && l2 instanceof FakePlayer)
				TournamentAI.scheduleFakeReRegister1x1((FakePlayer) l2);
		}
		
		private void rewardWinner()
		{
			if (pairOne.isAlive() && !pairTwo.isAlive())
			{
				pairOne.rewards();
				pairTwo.rewardsLost();
			}
			else if (pairTwo.isAlive() && !pairOne.isAlive())
			{
				pairTwo.rewards();
				pairOne.rewardsLost();
			}
		}
		
		private boolean check()
		{
			return (pairOne.isDead() && pairTwo.isDead());
		}
		
		private boolean portPairsToArena()
		{
			if (!pairOne.check() || !pairTwo.check())
				return false;

			for (Arena arena : arenas)
			{
				if (arena.isFree)
				{
					this.arena = arena;
					arena.setFree(false);
					if (pairOne.getLeader() != null && pairOne.getLeader().isOnline() && pairOne.getLeader().isInParty())
						pairOne.getLeader().leaveParty();
					if (pairTwo.getLeader() != null && pairTwo.getLeader().isOnline() && pairTwo.getLeader().isInParty())
						pairTwo.getLeader().leaveParty();
					pairOne.removePet();
					pairTwo.removePet();
					pairOne.teleportTo(arena.x - 850, arena.y, arena.z);
					pairTwo.teleportTo(arena.x + 850, arena.y, arena.z);
					pairOne.setImobilised(true);
					pairTwo.setImobilised(true);
					pairOne.setInTournamentEvent(true);
					pairTwo.setInTournamentEvent(true);
					fights.put(this.arena.id, pairOne.getLeader().getName() + " vs " + pairTwo.getLeader().getName());
					return true;
				}
			}
			return false;
		}
	}
	
	private class Arena
	{
		protected int x, y, z;
		protected boolean isFree = true;
		int id;
		
		public Arena(int id, int x, int y, int z)
		{
			this.id = id;
			this.x = x;
			this.y = y;
			this.z = z;
		}
		
		public void setFree(boolean val)
		{
			isFree = val;
		}
	}
	
	protected class countdown implements Runnable
	{
		private final Player _player;
		private int _time;
		
		public countdown(Player player, int time)
		{
			_time = time;
			_player = player;
		}
		
		@Override
		public void run()
		{
			if (_player.isOnline())
			{
				
				switch (_time)
				{
					case 300:
					case 240:
					case 180:
					case 120:
					case 60:
						if (_player.isOnline())
						{
							_player.sendPacket(new ExShowScreenMessage("The battle starts in " + _time + " second(s)..", 4000));
							_player.sendMessage(_time + " second(s) to start the battle.");
						}
						break;
					case 45:
						if (_player.isOnline())
						{
							_player.sendPacket(new ExShowScreenMessage("" + _time + " ..", 3000));
							_player.sendMessage(_time + " second(s) to start the battle!");
						}
						break;
					case 27:
						if (_player.isOnline())
						{
							_player.sendPacket(new ExShowScreenMessage("The battle starts in 30 second(s)..", 4000));
							_player.sendMessage("30 second(s) to start the battle!");
						}
						break;
					case 20:
						if (_player.isOnline())
						{
							_player.sendPacket(new ExShowScreenMessage("" + _time + " ..", 3000));
							_player.sendMessage(_time + " second(s) to start the battle!");
						}
						break;
					case 15:
						if (_player.isOnline())
						{
							_player.sendPacket(new ExShowScreenMessage("" + _time + " ..", 3000));
							_player.sendMessage(_time + " second(s) to start the battle!");
						}
						break;
					case 10:
						if (_player.isOnline())
							_player.sendMessage(_time + " second(s) to start the battle!");
						break;
					case 5:
						if (_player.isOnline())
							_player.sendMessage(_time + " second(s) to start the battle!");
						break;
					case 4:
						if (_player.isOnline())
							_player.sendMessage(_time + " second(s) to start the battle!");
						break;
					case 3:
						if (_player.isOnline())
							_player.sendMessage(_time + " second(s) to start the battle!");
						break;
					case 2:
						if (_player.isOnline())
							_player.sendMessage(_time + " second(s) to start the battle!");
						break;
					case 1:
						if (_player.isOnline())
							_player.sendMessage(_time + " second(s) to start the battle!");
						break;
				}
				if (_time > 1)
				{
					ThreadPool.schedule(new countdown(_player, _time - 1), 1000);
				}
			}
		}
	}
	
	private static class SingletonHolder
	{
		protected static final Arena1x1 INSTANCE = new Arena1x1();
	}
	
}
