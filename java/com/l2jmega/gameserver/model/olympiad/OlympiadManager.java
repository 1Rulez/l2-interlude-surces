package com.l2jmega.gameserver.model.olympiad;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import Base.RandomFightEvent.RandomFight;
import Base.custom.event.AnonymousPvPEvent;
import com.l2jmega.Config;
import com.l2jmega.L2DatabaseFactory;
import com.l2jmega.events.Arena1x1;
import com.l2jmega.events.Arena2x2;
import com.l2jmega.events.Arena5x5;
import com.l2jmega.events.BossEvent;
import com.l2jmega.events.CTF;
import com.l2jmega.events.TvT;
import com.l2jmega.gameserver.instancemanager.AioManager;
import com.l2jmega.gameserver.model.L2Party;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.L2Party.MessageType;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.item.instance.ItemInstance;
import com.l2jmega.gameserver.model.item.type.CrystalType;
import com.l2jmega.gameserver.network.SystemMessageId;
import com.l2jmega.gameserver.network.serverpackets.ExShowScreenMessage;
import com.l2jmega.gameserver.network.serverpackets.NpcHtmlMessage;
import com.l2jmega.gameserver.network.serverpackets.SystemMessage;
import com.l2jmega.gameserver.templates.StatsSet;
import com.l2jmega.gameserver.util.SiegeParticipationUtil;
import com.l2jmega.util.CloseUtil;

import phantom.FakePlayer;

/**
 * @author DS
 */
public class OlympiadManager
{
	private final List<Integer> _nonClassBasedRegisters;
	private final Map<Integer, List<Integer>> _classBasedRegisters;
	
	protected OlympiadManager()
	{
		_nonClassBasedRegisters = new CopyOnWriteArrayList<>();
		_classBasedRegisters = new ConcurrentHashMap<>();
	}
	
	public static final OlympiadManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	public final List<Integer> getRegisteredNonClassBased()
	{
		return _nonClassBasedRegisters;
	}
	
	public final Map<Integer, List<Integer>> getRegisteredClassBased()
	{
		return _classBasedRegisters;
	}
	
	protected final List<List<Integer>> hasEnoughRegisteredClassed()
	{
		List<List<Integer>> result = null;
		for (Map.Entry<Integer, List<Integer>> classList : _classBasedRegisters.entrySet())
		{
			if (classList.getValue() != null && classList.getValue().size() >= Config.ALT_OLY_CLASSED)
			{
				if (result == null)
					result = new ArrayList<>();
				
				result.add(classList.getValue());
			}
		}
		return result;
	}
	
	protected final boolean hasEnoughRegisteredNonClassed()
	{
		return _nonClassBasedRegisters.size() >= Config.ALT_OLY_NONCLASSED;
	}
	
	protected final void clearRegistered()
	{
		_nonClassBasedRegisters.clear();
		_classBasedRegisters.clear();
	}
	
	public final boolean isRegistered(Player noble)
	{
		return isRegistered(noble, false);
	}
	
	private final boolean isRegistered(Player player, boolean showMessage)
	{
		final Integer objId = Integer.valueOf(player.getObjectId());
		
		if (_nonClassBasedRegisters.contains(objId))
		{
			if (showMessage)
				player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_ARE_ALREADY_ON_THE_WAITING_LIST_FOR_ALL_CLASSES_WAITING_TO_PARTICIPATE_IN_THE_GAME));
			
			return true;
		}
		
		final List<Integer> classed = _classBasedRegisters.get(player.getBaseClass());
		if (classed != null && classed.contains(objId))
		{
			if (showMessage)
				player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_ARE_ALREADY_ON_THE_WAITING_LIST_TO_PARTICIPATE_IN_THE_GAME_FOR_YOUR_CLASS));
			
			return true;
		}
		
		return false;
	}
	
	public final boolean isRegisteredInComp(Player noble)
	{
		return isRegistered(noble, false) || isInCompetition(noble, false);
	}
	
	/**
	 * Real players cannot join parallel server events (TvT, CTF, Boss Event, Tournament arenas, etc.)
	 * while registered for Olympiad, inside an Olympiad fight, or under Olympiad protection.
	 */
	public final boolean isParallelEventBlockedFor(Player player)
	{
		if (player == null || player instanceof FakePlayer)
			return false;
		
		return isRegisteredInComp(player)
			|| player.isInOlympiadMode()
			|| player.getOlympiadGameId() != -1
			|| player.isOlympiadProtection();
	}
	
	private static final boolean isInCompetition(Player player, boolean showMessage)
	{
		if (!Olympiad._inCompPeriod)
			return false;
		
		for (int i = OlympiadGameManager.getInstance().getNumberOfStadiums(); --i >= 0;)
		{
			AbstractOlympiadGame game = OlympiadGameManager.getInstance().getOlympiadTask(i).getGame();
			if (game == null)
				continue;
			
			if (game.containsParticipant(player.getObjectId()))
			{
				if (showMessage)
					player.sendPacket(SystemMessageId.YOU_HAVE_ALREADY_BEEN_REGISTERED_IN_A_WAITING_LIST_OF_AN_EVENT);
				
				return true;
			}
		}
		return false;
	}
	
	public final boolean registerNoble(Player player, CompetitionType type)
	{
		if (SiegeParticipationUtil.isPlayerOrClanInActiveSiege(player))
		{
			player.sendMessage("You cannot register for Olympiad while your clan is participating in an active siege.");
			return false;
		}

		if (!Config.OLY_FIGHT)
		{
			player.sendMessage("The Olympiad Game Is disabled...");
			return false;
		}
		
		if (!Olympiad._inCompPeriod)
		{
			player.sendPacket(SystemMessageId.THE_OLYMPIAD_GAME_IS_NOT_CURRENTLY_IN_PROGRESS);
			return false;
		}
		
		if (Olympiad.getInstance().getMillisToCompEnd() < 600000)
		{
			player.sendPacket(SystemMessageId.GAME_REQUEST_CANNOT_BE_MADE);
			return false;
		}
		
	    if (player.isAio() || AioManager.getInstance().hasAioPrivileges(player.getObjectId())) {
	        player.sendMessage("SYS: : AIOX cannot register for events.");
	        return false;
	      } 
		
		// Olympiad dualbox protection
		if (player._active_boxes > 1 && !Config.ALLOW_DUALBOX_OLY)
		{
			final List<String> players_in_boxes = player.active_boxes_characters;
			
			if (players_in_boxes != null && players_in_boxes.size() > 1)
				for (final String character_name : players_in_boxes)
				{
					final Player ppl = World.getInstance().getPlayer(character_name);
					
					if (ppl != null && ppl.isOlympiadProtection())
					{
						player.sendMessage("You are already participating in Olympiad with another char!");
						return false;
					}
				}
		}
		
		if (!Config.OLLY_GRADE_A)
		{
			ItemInstance item;
			for (int i = 1; i < 15; i++)
			{
				item = player.getInventory().getPaperdollItem(i);
				if (item == null)
					continue;
				
				if (item.getItem().getCrystalType() == CrystalType.S)
				{
					player.sendMessage("[Olympiad]: Olympiad (Grade-A) no custom!");
					player.sendPacket(new ExShowScreenMessage("Olympiad (Grade-A) no custom!", 5000));
					return false;
				}
				
			     if (item.getEnchantLevel() > Config.ALT_OLY_ENCHANT_LIMIT)
			      {
			         player.sendMessage("You can not register olympiad when item enchant level is above +" + Config.ALT_OLY_ENCHANT_LIMIT + ".");
				     player.sendPacket(new ExShowScreenMessage("You can not register olympiad when item enchant level is above +" + Config.ALT_OLY_ENCHANT_LIMIT + ".", 5000));
			         return false;
			      }
			}
		}
		
		switch (type)
		{
			case CLASSED:
			{
				if (!Config.OLY_CLASSED_FIGHT)
				{
					player.sendMessage("Function temporarily disabled!");
					return false;
				}
				
				StatsSet playerStat = Olympiad.getNobleStats(player.getObjectId());
				if (playerStat == null)
				{
					playerStat = new StatsSet();
					playerStat.set(Olympiad.CLASS_ID, player.getBaseClass());
					playerStat.set(Olympiad.CHAR_NAME, player.getName());
					playerStat.set(Olympiad.POINTS, Olympiad.DEFAULT_POINTS);
					playerStat.set(Olympiad.COMP_DONE, 0);
					playerStat.set(Olympiad.COMP_WON, 0);
					playerStat.set(Olympiad.COMP_LOST, 0);
					playerStat.set(Olympiad.COMP_DRAWN, 0);
					playerStat.set("to_save", false);
					
					saveOlympiadStatus(player);
					Olympiad.addNobleStats(player.getObjectId(), playerStat);
					
				}
				
				if (!checkNoble(player))
					return false;
				
				List<Integer> classed = _classBasedRegisters.get(player.getBaseClass());
				if (classed != null)
					classed.add(player.getObjectId());
				else
				{
					classed = new CopyOnWriteArrayList<>();
					classed.add(player.getObjectId());
					_classBasedRegisters.put(player.getBaseClass(), classed);
				}
				
				player.setOlympiadProtection(true);
				player.sendPacket(SystemMessageId.YOU_HAVE_BEEN_REGISTERED_IN_A_WAITING_LIST_OF_CLASSIFIED_GAMES);
				break;
			}
			
			case NON_CLASSED:
			{
				
				StatsSet playerStat = Olympiad.getNobleStats(player.getObjectId());
				if (playerStat == null)
				{
					playerStat = new StatsSet();
					playerStat.set(Olympiad.CLASS_ID, player.getBaseClass());
					playerStat.set(Olympiad.CHAR_NAME, player.getName());
					playerStat.set(Olympiad.POINTS, Olympiad.DEFAULT_POINTS);
					playerStat.set(Olympiad.COMP_DONE, 0);
					playerStat.set(Olympiad.COMP_WON, 0);
					playerStat.set(Olympiad.COMP_LOST, 0);
					playerStat.set(Olympiad.COMP_DRAWN, 0);
					playerStat.set("to_save", false);
					
					saveOlympiadStatus(player);
					Olympiad.addNobleStats(player.getObjectId(), playerStat);
				}
				
				if (!checkNoble(player))
					return false;
				
				_nonClassBasedRegisters.add(player.getObjectId());
				player.setOlympiadProtection(true);
				player.sendPacket(SystemMessageId.YOU_HAVE_BEEN_REGISTERED_IN_A_WAITING_LIST_OF_NO_CLASS_GAMES);
				break;
			}
		}
		
		// Remove player from his party
		player.stopAllEffectsExceptThoseThatLastThroughDeath();
		
		final L2Party party = player.getParty();
		if (party != null)
			party.removePartyMember(player, MessageType.Expelled);
		
		return true;
	}
	
	public final boolean unRegisterNoble(Player noble)
	{
		if (!Olympiad._inCompPeriod)
		{
			noble.sendPacket(SystemMessageId.THE_OLYMPIAD_GAME_IS_NOT_CURRENTLY_IN_PROGRESS);
			return false;
		}
		
		if (!noble.isNoble())
		{
			noble.sendPacket(SystemMessageId.NOBLESSE_ONLY);
			return false;
		}
		
		if (!isRegistered(noble, false))
		{
			noble.sendPacket(SystemMessageId.YOU_HAVE_NOT_BEEN_REGISTERED_IN_A_WAITING_LIST_OF_A_GAME);
			return false;
		}
		
		if (isInCompetition(noble, false))
			return false;
		
		if (!noble.isInObserverMode())
			noble.setOlympiadProtection(false);
		
		Integer objId = Integer.valueOf(noble.getObjectId());
		if (_nonClassBasedRegisters.remove(objId))
		{
			noble.sendPacket(SystemMessageId.YOU_HAVE_BEEN_DELETED_FROM_THE_WAITING_LIST_OF_A_GAME);
			return true;
		}
		
		final List<Integer> classed = _classBasedRegisters.get(noble.getBaseClass());
		if (classed != null && classed.remove(objId))
		{
			_classBasedRegisters.remove(noble.getBaseClass());
			_classBasedRegisters.put(noble.getBaseClass(), classed);
			
			noble.sendPacket(SystemMessageId.YOU_HAVE_BEEN_DELETED_FROM_THE_WAITING_LIST_OF_A_GAME);
			return true;
		}
		
		return false;
	}
	
	public final void removeDisconnectedCompetitor(Player player)
	{
		final OlympiadGameTask task = OlympiadGameManager.getInstance().getOlympiadTask(player.getOlympiadGameId());
		if (task != null && task.isGameStarted())
			task.getGame().handleDisconnect(player);
		
		final Integer objId = Integer.valueOf(player.getObjectId());
		if (_nonClassBasedRegisters.remove(objId))
			return;
		
		final List<Integer> classed = _classBasedRegisters.get(player.getBaseClass());
		if (classed != null && classed.remove(objId))
			return;
	}
	
	/**
	 * @param player - messages will be sent to this Player
	 * @return true if all requirements are met
	 */
	private final boolean checkNoble(Player player)
	{
		if (!player.isNoble())
		{
			player.sendPacket(SystemMessageId.ONLY_NOBLESS_CAN_PARTICIPATE_IN_THE_OLYMPIAD);
			return false;
		}
		
		if (player.isSubClassActive())
		{
			player.sendPacket(SystemMessageId.YOU_CANT_JOIN_THE_OLYMPIAD_WITH_A_SUB_JOB_CHARACTER);
			return false;
		}
		
		if (player.isCursedWeaponEquipped())
		{
			player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CANNOT_JOIN_OLYMPIAD_POSSESSING_S1).addItemName(player.getCursedWeaponEquippedId()));
			return false;
		}
		
		if (player.getInventoryLimit() * 0.8 <= player.getInventory().getSize())
		{
			player.sendPacket(SystemMessageId.SINCE_80_PERCENT_OR_MORE_OF_YOUR_INVENTORY_SLOTS_ARE_FULL_YOU_CANNOT_PARTICIPATE_IN_THE_OLYMPIAD);
			return false;
		}

		if (isRegisteredInAnotherEvent(player, true))
			return false;
		
		if (isRegistered(player, true))
			return false;
		
		if (isInCompetition(player, true))
			return false;
		
		if (Config.ALT_OLY_UNLIMITED_TEST_MODE)
			return true;
		
		int points = 0;
		
		Connection con = null;
		try
		{
			
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(OLYMPIAD_POINTS_WIN);
			statement.setInt(1, player.getObjectId());
			
			ResultSet rset = statement.executeQuery();
			
			while (rset.next())
			{
				points = rset.getInt("olympiad_points");
			}
			
			rset.close();
			statement.close();
			statement = null;
			rset = null;
			
		}
		catch (Exception e)
		{
			if (Config.DEBUG)
				e.printStackTrace();
		}
		finally
		{
			CloseUtil.close(con);
		}
		
		if (points <= 0)
		{
			final NpcHtmlMessage message = new NpcHtmlMessage(0);
			message.setFile("data/html/olympiad/noble_nopoints1.htm");
			message.replace("%objectId%", player.getTargetId());
			player.sendPacket(message);
			return false;
		}
		
		return true;
	}

	private static boolean isRegisteredInAnotherEvent(Player player, boolean showMessage)
	{
		if (player == null)
			return false;

		if (isRegisteredInTvT(player) || isRegisteredInCTF(player) || isRegisteredInTournament(player) || RandomFight.isRegistered(player) || RandomFight.isFighting(player) || player.isInArenaEvent() || player.isArenaAttack() || player.isArenaProtection() || player.isArena1x1() || player.isArena2x2() || player.isArena5x5() || player.isArena9x9() || player.isTournamentTeleport() || BossEvent.getInstance().isRegistered(player) || AnonymousPvPEvent.isRegistered(player))
		{
			if (showMessage)
				player.sendMessage("You cannot register in the Grand Olympiad while registered in another event.");
			return true;
		}

		if (player instanceof FakePlayer fake && (fake.isFakePvp() || fake.isFakeEvent() || fake.isFakeKTBEvent() || fake.isTour()))
			return true;

		return false;
	}

	private static boolean isRegisteredInTvT(Player player)
	{
		return TvT._players.contains(player) || TvT._playersShuffle.contains(player) || TvT._savePlayers.contains(player.getName());
	}

	private static boolean isRegisteredInCTF(Player player)
	{
		return CTF._players.contains(player) || CTF._playersShuffle.contains(player) || CTF._savePlayers.contains(player.getName());
	}

	private static boolean isRegisteredInTournament(Player player)
	{
		return Arena1x1.getInstance().isRegistered(player) || Arena2x2.getInstance().isRegistered(player) || Arena5x5.getInstance().isRegistered(player);
	}
	
	private static final String OLYMPIAD_SAVE_DATA = "INSERT INTO olympiad_nobles (char_Id, class_id, olympiad_points, competitions_done, competitions_won, competitions_lost, competitions_drawn) VALUES (?,?,?,?,?,?,?)";
	private static final String OLYMPIAD_POINTS_WIN = "SELECT olympiad_points FROM olympiad_nobles WHERE char_Id=?";
	
	public void saveOlympiadStatus(Player player)
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			final PreparedStatement statement = con.prepareStatement(OLYMPIAD_SAVE_DATA);
			
			statement.setInt(1, player.getObjectId());
			statement.setInt(2, player.getClassId().getId());
			statement.setLong(3, Config.ALT_OLY_START_POINTS);
			statement.setLong(4, 0);
			statement.setLong(5, 0);
			statement.setInt(6, 0);
			statement.setInt(7, 0);
			
			statement.execute();
			statement.close();
		}
		catch (SQLException e)
		{
		}
	}
	
	private static class SingletonHolder
	{
		protected static final OlympiadManager _instance = new OlympiadManager();
	}
}
