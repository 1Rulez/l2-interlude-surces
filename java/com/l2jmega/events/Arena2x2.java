package com.l2jmega.events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.commons.random.Rnd;

import com.l2jmega.Config;
import com.l2jmega.gameserver.model.L2Effect;
import com.l2jmega.gameserver.model.actor.Summon;
import com.l2jmega.gameserver.model.actor.instance.Pet;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.zone.ZoneId;
import com.l2jmega.gameserver.network.SystemMessageId;
import com.l2jmega.gameserver.network.serverpackets.ExShowScreenMessage;
import com.l2jmega.gameserver.model.olympiad.OlympiadManager;
import com.l2jmega.gameserver.util.SiegeParticipationUtil;

import Base.RandomFightEvent.RandomFight;
import phantom.FakePlayer;
import phantom.ai.event.TournamentAI;

public class Arena2x2 implements Runnable
{
	protected static final Logger _log = Logger.getLogger(Arena2x2.class.getName());
	
	// list of participants
	public static List<Pair> registered;
	private final Object registrationLock = new Object();
	// number of Arenas
	int free = Config.ARENA_EVENT_COUNT;
	// Arenas
	Arena[] arenas = new Arena[Config.ARENA_EVENT_COUNT];
	// list of fights going on
	Map<Integer, String> fights = new HashMap<>(Config.ARENA_EVENT_COUNT);
	
	public Arena2x2()
	{
		registered = new ArrayList<>();
		int[] coord;
		for (int i = 0; i < Config.ARENA_EVENT_COUNT; i++)
		{
			coord = Config.ARENA_EVENT_LOCS[i];
			arenas[i] = new Arena(i, coord[0], coord[1], coord[2]);
		}
	}
	
	public static Arena2x2 getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	public boolean register(Player player, Player assist)
	{
		if (SiegeParticipationUtil.isPlayerOrClanInActiveSiege(player) || SiegeParticipationUtil.isPlayerOrClanInActiveSiege(assist))
		{
			if (player != null && player.isOnline())
				player.sendMessage("Tournament: You cannot register while your clan is participating in an active siege.");
			if (assist != null && assist.isOnline())
				assist.sendMessage("Tournament: You cannot register while your clan is participating in an active siege.");
			return false;
		}

		if (player != null && OlympiadManager.getInstance().isParallelEventBlockedFor(player))
		{
			player.sendMessage("Tournament: You cannot register while registered for or fighting in Olympiad.");
			return false;
		}
		if (assist != null && OlympiadManager.getInstance().isParallelEventBlockedFor(assist))
		{
			if (player != null && player.isOnline())
				player.sendMessage("Tournament: Your partner cannot register while registered for or fighting in Olympiad.");
			assist.sendMessage("Tournament: You cannot register while registered for or fighting in Olympiad.");
			return false;
		}

		if (player != null && RandomFight.isCommittedToRandomFight(player))
		{
			player.sendMessage("Tournament: You cannot register while registered or fighting in Random Fight.");
			return false;
		}
		if (assist != null && RandomFight.isCommittedToRandomFight(assist))
		{
			if (player != null && player.isOnline())
				player.sendMessage("Tournament: Your partner is registered or fighting in Random Fight.");
			assist.sendMessage("Tournament: Leave Random Fight before joining the tournament.");
			return false;
		}

		if (AbstractArenaPair.isForbiddenSupportClass(player) || AbstractArenaPair.isForbiddenSupportClass(assist))
		{
			if (player != null && player.isOnline())
				player.sendMessage("Tournament: This class cannot register in 2x2.");
			if (assist != null && assist.isOnline())
				assist.sendMessage("Tournament: This class cannot register in 2x2.");
			return false;
		}
		
		if (Arena1x1.getInstance().isRegistered(player) || Arena1x1.getInstance().isRegistered(assist) || Arena5x5.getInstance().isRegistered(player) || Arena5x5.getInstance().isRegistered(assist))
		{
			if (player != null && player.isOnline())
				player.sendMessage("Tournament: You or your partner is already registered in another mode.");
			return false;
		}
		
		synchronized (registrationLock)
		{
			for (Pair p : registered)
			{
				if (p.getLeader() == player || p.getAssist() == player)
				{
					player.sendMessage("Tournament: You already registered!");
					return false;
				}
				else if (p.getLeader() == assist || p.getAssist() == assist)
				{
					player.sendMessage("Tournament: Your partner already registered!");
					return false;
				}
			}
			return registered.add(new Pair(player, assist));
		}
	}
	
	public boolean isRegistered(Player player)
	{
		synchronized (registrationLock)
		{
			for (Pair p : registered)
			{
				if (p.getLeader() == player || p.getAssist() == player)
				{
					return true;
				}
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
		synchronized (registrationLock)
		{
			for (int i = 0; i < registered.size(); i++)
			{
				Pair p = registered.get(i);
				if (p.getLeader() == player || p.getAssist() == player)
				{
					p.removeMessage();
					registered.remove(i);
					return true;
				}
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
			
			final int regCount = getRegisteredCount();
			final int freeArenas;
			synchronized (this)
			{
				freeArenas = free;
			}
			if (regCount < 2 || freeArenas == 0)
			{
				try
				{
					Thread.sleep(Config.ARENA_CALL_INTERVAL * 1000);
				}
				catch (InterruptedException e)
				{
				}
				continue;
			}
			List<Pair> opponents = selectOpponents();
			if (opponents != null && opponents.size() == 2)
			{
				synchronized (this)
				{
					if (free == 0)
					{
						restoreOpponents(opponents);
						continue;
					}
					free--;
				}
				Thread T = new Thread(new EvtArenaTask(opponents));
				T.setDaemon(true);
				T.start();
			}
			// wait 1 minute for not stress server
			try
			{
				Thread.sleep(Config.ARENA_CALL_INTERVAL * 1000);
			}
			catch (InterruptedException e)
			{
			}
		}
	}
	
	@SuppressWarnings("null")
	private List<Pair> selectOpponents()
	{
		synchronized (registrationLock)
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
	}
	
	public void clear()
	{
		synchronized (registrationLock)
		{
			registered.clear();
		}
	}
	
	public int getRegisteredCount()
	{
		synchronized (registrationLock)
		{
			return registered.size();
		}
	}

	public boolean isFightActive(int arenaId)
	{
		return fights.containsKey(arenaId);
	}
	
	private void restoreOpponents(List<Pair> opponents)
	{
		synchronized (registrationLock)
		{
			for (Pair p : opponents)
			{
				if (p == null)
					continue;
				
				boolean exists = false;
				for (Pair registeredPair : registered)
				{
					if (registeredPair.getLeader() == p.getLeader() || registeredPair.getAssist() == p.getLeader() || registeredPair.getLeader() == p.getAssist() || registeredPair.getAssist() == p.getAssist())
					{
						exists = true;
						break;
					}
				}
				
				if (!exists)
					registered.add(p);
			}
		}
	}
	
	private class Pair extends AbstractArenaPair
	{
		Player leader;
		Player assist;
		
		public Pair(Player leader, Player assist)
		{
			super(leader, assist);
			this.leader = leader;
			this.assist = assist;
		}
		
		@Override
		protected void setModeFlag(Player player, boolean value)
		{
			player.setArena2x2(value);
		}
		
		public Player getAssist()
		{
			return assist;
		}
		
		public Player getLeader()
		{
			return leader;
		}
		
		public boolean check()
		{
			if ((leader == null || !leader.isOnline()) && (assist != null && assist.isOnline()))
			{
				assist.sendMessage("Tournament: You participation in Event was Canceled.");
				return false;
			}
			else if ((assist == null || !assist.isOnline()) && (leader != null && leader.isOnline()))
			{
				leader.sendMessage("Tournament: You participation in Event was Canceled.");
				return false;
			}
			return true;
		}
		
		public boolean isDead()
		{
			if (Config.ARENA_PROTECT)
			{
				if ((leader != null && leader.isOnline() && leader.isArenaAttack() && !leader.isDead() && !leader.isInsideZone(ZoneId.ARENA_EVENT)))
					leader.logout();
				if ((assist != null && assist.isOnline() && assist.isArenaAttack() && !assist.isDead() && !assist.isInsideZone(ZoneId.ARENA_EVENT)))
					assist.logout();
			}
			
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
			
			if (assist != null && assist.isOnline())
			{
				assist.getAppearance().setInvisible();
				assist.setCurrentCp(assist.getMaxCp());
				assist.setCurrentHp(assist.getMaxHp());
				assist.setCurrentMp(assist.getMaxMp());
				
				if (assist.isInObserverMode())
				{
					assist.setLastCords(x, y + 50, z);
					assist.leaveOlympiadObserverMode();
				}
				else if (!assist.isInJail())
					assist.teleToLocation(x, y + 50, z, 0);
				
				assist.broadcastUserInfo();
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
			
			if (assist != null && assist.isOnline())
			{
				assist.setTitle(title);
				assist.getAppearance().setTitleColor(Integer.decode("0x" + color));
				assist.broadcastUserInfo();
				assist.broadcastTitleInfo();
			}
		}

		@Override
		public void saveTitle()
		{
			if (leader != null && leader.isOnline())
			{
				leader._originalTitleColorTournament = leader.getAppearance().getTitleColor();
				leader._originalTitleTournament = leader.getTitle();
			}
			
			if (assist != null && assist.isOnline())
			{
				assist._originalTitleColorTournament = assist.getAppearance().getTitleColor();
				assist._originalTitleTournament = assist.getTitle();
			}
		}

		@Override
		public void backTitle()
		{
			if (leader != null && leader.isOnline())
			{
				leader.setTitle(leader._originalTitleTournament);
				leader.getAppearance().setTitleColor(leader._originalTitleColorTournament);
				leader.broadcastUserInfo();
				leader.broadcastTitleInfo();
			}
			
			if (assist != null && assist.isOnline())
			{
				assist.setTitle(assist._originalTitleTournament);
				assist.getAppearance().setTitleColor(assist._originalTitleColorTournament);
				assist.broadcastUserInfo();
				assist.broadcastTitleInfo();
			}
		}
		
		public void rewards()
		{
			if (leader != null && leader.isOnline())
			{
				if (leader.isVip())
					leader.addItem("Arena_Event", Config.ARENA_REWARD_ID, Config.ARENA_WIN_REWARD_COUNT * Config.RATE_DROP_VIP, leader, true);
				else
					leader.addItem("Arena_Event", Config.ARENA_REWARD_ID, Config.ARENA_WIN_REWARD_COUNT, leader, true);
				
		        if (Config.ACTIVE_MISSION) {
		            if (!this.leader.check_obj_mission(this.leader.getObjectId()))
		              this.leader.updateMission(); 
		            if (!this.leader.is2x2Completed() && this.leader.getTournament2x2Cont() < Config.MISSION_2X2_CONT)
		              this.leader.setTournament2x2Cont(this.leader.getTournament2x2Cont() + 1); 
		          } 
			}
			
			if (assist != null && assist.isOnline())
			{
				if (assist.isVip())
					assist.addItem("Arena_Event", Config.ARENA_REWARD_ID, Config.ARENA_WIN_REWARD_COUNT * Config.RATE_DROP_VIP, assist, true);
				else
					assist.addItem("Arena_Event", Config.ARENA_REWARD_ID, Config.ARENA_WIN_REWARD_COUNT, assist, true);
				
		        if (Config.ACTIVE_MISSION) {
		            if (!this.assist.check_obj_mission(this.assist.getObjectId()))
		              this.assist.updateMission(); 
		            if (!this.assist.is2x2Completed() && this.assist.getTournament2x2Cont() < Config.MISSION_2X2_CONT)
		              this.assist.setTournament2x2Cont(this.assist.getTournament2x2Cont() + 1); 
		          } 
			}
			
			sendPacket("Winner =)", 5);
			super.broadcastVictoryEffect();

		}
		
		public void rewardsLost()
		{
			if (leader != null && leader.isOnline())
				leader.addItem("Arena_Event", Config.ARENA_REWARD_ID, Config.ARENA_LOST_REWARD_COUNT, leader, true);
			
			if (assist != null && assist.isOnline())
				assist.addItem("Arena_Event", Config.ARENA_REWARD_ID, Config.ARENA_LOST_REWARD_COUNT, assist, true);
			
			sendPacket("Loser =(", 5);
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

			if (assist != null && assist.isOnline())
			{
				assist.setIsInvul(val);
				assist.setStopArena(val);
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

			if (assist != null && assist.isOnline())
			{
				assist.setArenaAttack(val);
				assist.broadcastUserInfo();
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
			
			if (assist != null && assist.isOnline())
			{
				// Remove Summon's buffs
				if (assist.getPet() != null)
				{
					Summon summon = assist.getPet();
					if (summon != null)
						summon.unSummon(summon.getOwner());
					
					if (summon instanceof Pet)
						summon.unSummon(assist);
					
				}
				
				if (assist.getMountType() == 1 || assist.getMountType() == 2)
					assist.dismount();
				
			}
		}
		
		public void removeSkills()
		{
			if (leader != null && leader.isOnline())
			{
				for (L2Effect effect : leader.getAllEffects())
				{
					if (effect.getSkill().getId() == 406 || effect.getSkill().getId() == 139 || effect.getSkill().getId() == 176 || effect.getSkill().getId() == 420)
					{
						leader.stopSkillEffects(effect.getSkill().getId());
						leader.enableSkill(effect.getSkill());
					}
				}
			}
			
			if (assist != null && assist.isOnline())
			{
				for (L2Effect effect : assist.getAllEffects())
				{
					if (effect.getSkill().getId() == 406 || effect.getSkill().getId() == 139 || effect.getSkill().getId() == 176 || effect.getSkill().getId() == 420)
					{
						assist.stopSkillEffects(effect.getSkill().getId());
						assist.enableSkill(effect.getSkill());
					}
				}
			}
			
			if (Config.ARENA_SKILL_PROTECT)
			{
				if (leader != null && leader.isOnline())
				{
					for (L2Effect effect : leader.getAllEffects())
					{
						if (Config.ARENA_STOP_SKILL_LIST.contains(effect.getSkill().getId()))
							leader.stopSkillEffects(effect.getSkill().getId());
					}
					
					if (leader.getMountType() == 2)
					{
						leader.sendPacket(SystemMessageId.AREA_CANNOT_BE_ENTERED_WHILE_MOUNTED_WYVERN);
						leader.enteredNoLanding(5);
					}
					
				}
			}
			
			if (Config.ARENA_SKILL_PROTECT)
			{
				if (assist != null && assist.isOnline())
				{
					for (L2Effect effect : assist.getAllEffects())
					{
						if (Config.ARENA_STOP_SKILL_LIST.contains(effect.getSkill().getId()))
							assist.stopSkillEffects(effect.getSkill().getId());
						
					}
					if (assist.getMountType() == 2)
					{
						assist.sendPacket(SystemMessageId.AREA_CANNOT_BE_ENTERED_WHILE_MOUNTED_WYVERN);
						assist.enteredNoLanding(5);
					}
					
				}
			}
			
		}

		@Override
		public void sendPacket(String message, int duration)
		{
			super.sendPacket(message, duration);
		}

		public void inicarContagem(int duration)
		{
			if (leader != null && leader.isOnline())
				ThreadPool.schedule(new countdown(leader, duration), 0);
			
			if (assist != null && assist.isOnline())
				ThreadPool.schedule(new countdown(assist, duration), 0);
		}
		
		public void sendPacketinit(String message, int duration)
		{
			if (leader != null && leader.isOnline())
				leader.sendPacket(new ExShowScreenMessage(message, duration * 1000, ExShowScreenMessage.SMPOS.MIDDLE_LEFT, false));
			
			if (assist != null && assist.isOnline())
				assist.sendPacket(new ExShowScreenMessage(message, duration * 1000, ExShowScreenMessage.SMPOS.MIDDLE_LEFT, false));
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
			try
			{
				pairOne.saveTitle();
				pairTwo.saveTitle();
				pairOne.TourAura(Config.TOUR_AURA_TEAM1);
				pairTwo.TourAura(Config.TOUR_AURA_TEAM2);
				if (!portPairsToArena())
				{
					restoreOpponents(List.of(pairOne, pairTwo));
					return;
				}
				pairOne.inicarContagem(Config.ARENA_WAIT_INTERVAL);
				pairTwo.inicarContagem(Config.ARENA_WAIT_INTERVAL);
				try
				{
					Thread.sleep(Config.ARENA_WAIT_INTERVAL * 1000);
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
					try
					{
						Thread.sleep(Config.ARENA_CHECK_INTERVAL);
					}
					catch (InterruptedException e)
					{
						break;
					}
				}
				finishDuel();
			}
			catch (Exception e)
			{
				_log.warning("Arena2x2 match failed: " + e.getMessage());
			}
			finally
			{
				synchronized (Arena2x2.this)
				{
					free++;
				}
			}
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
			Player a1 = pairOne.getLeader(), b1 = pairOne.getAssist();
			if (ArenaTask.is_started() && a1 instanceof FakePlayer && b1 instanceof FakePlayer)
				TournamentAI.scheduleFakeReRegister2x2((FakePlayer) a1, (FakePlayer) b1);
			Player a2 = pairTwo.getLeader(), b2 = pairTwo.getAssist();
			if (ArenaTask.is_started() && a2 instanceof FakePlayer && b2 instanceof FakePlayer)
				TournamentAI.scheduleFakeReRegister2x2((FakePlayer) a2, (FakePlayer) b2);
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
					if (pairOne.getAssist() != null && pairOne.getAssist().isOnline() && pairOne.getAssist().isInParty())
						pairOne.getAssist().leaveParty();
					if (pairTwo.getLeader() != null && pairTwo.getLeader().isOnline() && pairTwo.getLeader().isInParty())
						pairTwo.getLeader().leaveParty();
					if (pairTwo.getAssist() != null && pairTwo.getAssist().isOnline() && pairTwo.getAssist().isInParty())
						pairTwo.getAssist().leaveParty();
					pairOne.removePet();
					pairTwo.removePet();
					pairOne.teleportTo(arena.x - 850, arena.y, arena.z);
					pairTwo.teleportTo(arena.x + 850, arena.y, arena.z);
					pairOne.setImobilised(true);
					pairTwo.setImobilised(true);
					pairOne.setInTournamentEvent(true);
					pairTwo.setInTournamentEvent(true);
					pairOne.removeSkills();
					pairTwo.removeSkills();
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
		protected static final Arena2x2 INSTANCE = new Arena2x2();
	}
	
}
