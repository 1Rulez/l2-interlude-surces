package com.l2jmega.gameserver.handler.chathandlers;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.l2jmega.Config;
import com.l2jmega.commons.random.Rnd;
import com.l2jmega.gameserver.data.MapRegionTable;
import com.l2jmega.gameserver.data.sql.ClanTable;
import com.l2jmega.gameserver.handler.IChatHandler;
import com.l2jmega.gameserver.instancemanager.ChatGlobalManager;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.pledge.Clan;
import com.l2jmega.gameserver.model.zone.ZoneId;
import com.l2jmega.gameserver.network.FloodProtectors;
import com.l2jmega.gameserver.network.FloodProtectors.Action;
import com.l2jmega.gameserver.network.SystemMessageId;
import com.l2jmega.gameserver.network.clientpackets.Say2;
import com.l2jmega.gameserver.network.clientpackets.RequestJoinPledge;
import com.l2jmega.gameserver.network.serverpackets.AskJoinPledge;
import com.l2jmega.gameserver.network.serverpackets.CreatureSay;
import com.l2jmega.gameserver.network.serverpackets.SystemMessage;

import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.FakePlayerManager;

public class ChatShout implements IChatHandler
{
	private static final Map<Integer, Long> CLAN_LF_LAST_INVITE = new ConcurrentHashMap<>();
	private static final int[] COMMAND_IDS =
	{
		1
	};
	
	@Override
	public void handleChat(int type, Player activeChar, String target, String text)
	{
		if (Config.DISABLE_CHAT && !activeChar.isGM())
		{
			activeChar.sendMessage("The chat is Temporarily unavailable.");
			return;
		}
		
		if (Config.ENABLE_CHAT_LEVEL && activeChar.getBaseClass() == activeChar.getActiveClass() && activeChar.getLevel() < Config.CHAT_LEVEL)
		{
			activeChar.sendMessage("You cannot chat with players until you reach level "+Config.CHAT_LEVEL+".");
			return;
		}
		
		if (Say2.isChatDisabled("all") && !activeChar.isGM())				
			return;
		
		if (text.equals("Thanks for using my stuff - Elfocrash"))
			return;

		tryTriggerFakeClanInvite(activeChar, text);
		
		
		if (activeChar.ChatProtection(activeChar.getHWID()) && activeChar.isChatBlocked() && ((activeChar.getChatBanTimer()-1500) > System.currentTimeMillis()))
		{
			if (((activeChar.getChatBanTimer() - System.currentTimeMillis()) / 1000) >= 60)
				activeChar.sendChatMessage(0, Say2.TELL, "SYS", "Your chat was suspended for " + (activeChar.getChatBanTimer() - System.currentTimeMillis()) / (1000*60) + " minute(s).");
			else
				activeChar.sendChatMessage(0, Say2.TELL, "SYS", "Your chat was suspended for " + (activeChar.getChatBanTimer() - System.currentTimeMillis()) / 1000 + " second(s).");
			
			activeChar.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return;
		}
		
		if ((activeChar.getChatGlobalTimer()-1500) > System.currentTimeMillis())
		{
			activeChar.sendMessage("You must wait " + (activeChar.getChatGlobalTimer() - System.currentTimeMillis()) / 1000 + " seconds to use global chat.");
			return;
		}
		
		if (!FloodProtectors.performAction(activeChar.getClient(), Action.GLOBAL_CHAT))
			return;
		
		final CreatureSay cs = new CreatureSay(activeChar.getObjectId(), type,  activeChar.getName(), text);
		
		String convert = text.toLowerCase();
		final CreatureSay disable = new CreatureSay(activeChar.getObjectId(), type, activeChar.getName(), convert);
		
		final int region = MapRegionTable.getInstance().getMapRegion(activeChar.getX(), activeChar.getY());
		
		if (Config.DEFAULT_GLOBAL_CHAT.equalsIgnoreCase("ON"))
		{
			for (Player player : World.getInstance().getPlayers())
			{
				if (region == MapRegionTable.getInstance().getMapRegion(player.getX(), player.getY()))
					
					if (Config.DISABLE_CAPSLOCK && !activeChar.isGM())
						player.sendPacket(disable);
					else
						player.sendPacket(cs);
				
				if (!activeChar.isGM() && Config.CUSTOM_GLOBAL_CHAT_TIME > 0)
				{
					activeChar.setChatGlobalTimer(System.currentTimeMillis() + Config.CUSTOM_GLOBAL_CHAT_TIME * 1000);
					if (!ChatGlobalManager.getInstance().hasChatPrivileges(activeChar.getObjectId()))
						ChatGlobalManager.getInstance().addChatTime(activeChar.getObjectId(), System.currentTimeMillis() + Config.CUSTOM_GLOBAL_CHAT_TIME * 1000);
				}
			}
		}
		else if (Config.DEFAULT_GLOBAL_CHAT.equalsIgnoreCase("GLOBAL"))
		{
			if (Config.GLOBAL_CHAT_WITH_PVP)
			{
				if ((activeChar.getPvpKills() < Config.GLOBAL_PVP_AMOUNT) && !activeChar.isGM())
				{
					activeChar.sendMessage("You must have at least " + Config.GLOBAL_PVP_AMOUNT + " pvp kills in order to speak in global chat");
					return;
				}
				
				for (Player player : World.getInstance().getPlayers())
				{
					if (Config.DISABLE_CAPSLOCK && !activeChar.isGM())
						player.sendPacket(disable);
					else
						player.sendPacket(cs);
					
					if (!activeChar.isGM() && Config.CUSTOM_GLOBAL_CHAT_TIME > 0)
					{
						activeChar.setChatGlobalTimer(System.currentTimeMillis() + Config.CUSTOM_GLOBAL_CHAT_TIME * 1000);
						if (!ChatGlobalManager.getInstance().hasChatPrivileges(activeChar.getObjectId()))
							ChatGlobalManager.getInstance().addChatTime(activeChar.getObjectId(), System.currentTimeMillis() + Config.CUSTOM_GLOBAL_CHAT_TIME * 1000);
					}
				}
				
			}
			else
			{
				for (Player player : World.getInstance().getPlayers())
				{
					if (Config.DISABLE_CAPSLOCK && !activeChar.isGM())
						player.sendPacket(disable);
					else
						player.sendPacket(cs);
					
					if (!activeChar.isGM() && Config.CUSTOM_GLOBAL_CHAT_TIME > 0)
					{
						activeChar.setChatGlobalTimer(System.currentTimeMillis() + Config.CUSTOM_GLOBAL_CHAT_TIME * 1000);
						if (!ChatGlobalManager.getInstance().hasChatPrivileges(activeChar.getObjectId()))
							ChatGlobalManager.getInstance().addChatTime(activeChar.getObjectId(), System.currentTimeMillis() + Config.CUSTOM_GLOBAL_CHAT_TIME * 1000);
					}
				}
				
			}
		}
	}
	
	@Override
	public int[] getChatTypeList()
	{
		return COMMAND_IDS;
	}

	private static void tryTriggerFakeClanInvite(Player activeChar, String text)
	{
		if (activeChar == null || text == null || activeChar.isGM())
			return;
		if (!FakePlayerConfig.FAKE_CLAN_LF_INVITE_ENABLED)
			return;
		if (activeChar.getClan() != null)
			return;
		if (!canUseFakeClanInvite(activeChar))
			return;

		final String lower = text.toLowerCase();
		if (!isLookingForClan(lower))
			return;
		final boolean exactLfClan = lower.contains("lf clan");

		final long now = System.currentTimeMillis();
		final long cooldownMs = Math.max(10, FakePlayerConfig.FAKE_CLAN_LF_INVITE_COOLDOWN_SECONDS) * 1000L;
		final long last = CLAN_LF_LAST_INVITE.getOrDefault(activeChar.getObjectId(), 0L);
		if (now - last < cooldownMs)
			return;

		if (!exactLfClan && Rnd.get(1, 100) > Math.max(0, FakePlayerConfig.FAKE_CLAN_LF_INVITE_CHANCE))
			return;

		final List<FakePlayer> candidates = collectEligibleClanRecruiters(activeChar);

		if (candidates.isEmpty())
		{
			// Fallback for exact "lf clan": temporarily enable invite privilege on existing clan fakes.
			if (exactLfClan)
			{
				for (FakePlayer fake : FakePlayerManager.getFakePlayers())
				{
					if (fake == null || fake.isDead() || !fake.isOnline() || fake.getClan() == null)
						continue;
					fake.setClanPrivileges(fake.getClanPrivileges() | Clan.CP_CL_JOIN_CLAN);
					if (isViableClanRecruiter(fake, activeChar))
						candidates.add(fake);
				}
			}
		}

		boolean invited = trySendFakeClanInvite(activeChar, candidates, false);
		if (!invited && exactLfClan)
		{
			final FakePlayer recruiter = spawnEmergencyClanRecruiter(activeChar);
			if (recruiter != null)
				invited = trySendFakeClanInvite(activeChar, Collections.singletonList(recruiter), true);
		}

		if (activeChar.getRequest().isProcessingRequest())
			CLAN_LF_LAST_INVITE.put(activeChar.getObjectId(), now);
	}

	private static List<FakePlayer> collectEligibleClanRecruiters(Player activeChar)
	{
		final List<FakePlayer> candidates = new ArrayList<>();
		if (activeChar == null)
			return candidates;

		for (FakePlayer fake : FakePlayerManager.getFakePlayers())
		{
			if (!isViableClanRecruiter(fake, activeChar))
				continue;

			candidates.add(fake);
		}
		return candidates;
	}

	private static boolean isViableClanRecruiter(FakePlayer fake, Player target)
	{
		if (fake == null || target == null || fake.isDead() || !fake.isOnline())
			return false;
		if (fake.getRequest().isProcessingRequest() || target.getRequest().isProcessingRequest())
			return false;
		if (fake.isInStoreMode() || fake.isCastingNow() || fake.isInCombat())
			return false;

		final Clan clan = fake.getClan();
		if (clan == null)
			return false;

		final boolean canInviteByPrivilege = (fake.getClanPrivileges() & Clan.CP_CL_JOIN_CLAN) == Clan.CP_CL_JOIN_CLAN;
		final boolean isLeader = fake.isClanLeader();
		if (!canInviteByPrivilege && !isLeader)
			return false;

		return clan.checkClanJoinCondition(fake, target, 0);
	}

	private static boolean trySendFakeClanInvite(Player activeChar, List<FakePlayer> candidates, boolean forceEmergencyInvite)
	{
		if (activeChar == null || candidates == null || candidates.isEmpty())
			return false;

		final List<FakePlayer> shuffled = new ArrayList<>(candidates);
		Collections.shuffle(shuffled);
		for (FakePlayer inviter : shuffled)
		{
			if (!sendFakeClanInvite(inviter, activeChar, forceEmergencyInvite))
				continue;

			return true;
		}

		return false;
	}

	private static boolean sendFakeClanInvite(FakePlayer inviter, Player activeChar, boolean forceEmergencyInvite)
	{
		if (inviter == null || activeChar == null || inviter.isDead() || !inviter.isOnline())
			return false;

		try
		{
			final Clan clan = inviter.getClan();
			if (clan == null)
				return false;

			if (!forceEmergencyInvite && !isViableClanRecruiter(inviter, activeChar))
				return false;

			if ((inviter.getClanPrivileges() & Clan.CP_CL_JOIN_CLAN) != Clan.CP_CL_JOIN_CLAN)
			{
				if (inviter.isClanLeader() || forceEmergencyInvite)
					inviter.setClanPrivileges(Clan.CP_ALL);
				else
					inviter.setClanPrivileges(inviter.getClanPrivileges() | Clan.CP_CL_JOIN_CLAN);
			}

			if ((inviter.getClanPrivileges() & Clan.CP_CL_JOIN_CLAN) != Clan.CP_CL_JOIN_CLAN)
				return false;

			if (!canTargetJoinClan(clan, activeChar))
				return false;

			final RequestJoinPledge requestPacket = new RequestJoinPledge();
			final Field pledgeType = RequestJoinPledge.class.getDeclaredField("_pledgeType");
			pledgeType.setAccessible(true);
			pledgeType.setInt(requestPacket, 0);

			if (!inviter.getRequest().setRequest(activeChar, requestPacket))
				return false;

			activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_HAS_INVITED_YOU_TO_JOIN_THE_CLAN_S2).addCharName(inviter).addString(clan.getName()));
			activeChar.sendPacket(new AskJoinPledge(inviter.getObjectId(), clan.getName()));
			return true;
		}
		catch (Exception e)
		{
			return false;
		}
	}

	private static boolean canTargetJoinClan(Clan clan, Player target)
	{
		if (clan == null || target == null)
			return false;
		if (target.getClan() != null)
			return false;
		if ((target.getClanJoinExpiryTime() - 100 * 1000) > System.currentTimeMillis())
			return false;
		return clan.getSubPledgeMembersCount(0) < clan.getMaxNrOfMembers(0);
	}

	private static FakePlayer spawnEmergencyClanRecruiter(Player activeChar)
	{
		if (activeChar == null || !canUseFakeClanInvite(activeChar))
			return null;

		final Clan preferredClan = findJoinableClan(activeChar);
		if (preferredClan == null)
			return null;

		for (int attempt = 0; attempt < 8; attempt++)
		{
			try
			{
				final int spawnX = activeChar.getX() + Rnd.get(-150, 150);
				final int spawnY = activeChar.getY() + Rnd.get(-150, 150);
				final FakePlayer recruiter = FakePlayerManager.spawnClanPlayer(spawnX, spawnY, activeChar.getZ(), preferredClan);
				if (recruiter == null || recruiter.getClan() == null)
					continue;

				if (recruiter.isClanLeader())
					recruiter.setClanPrivileges(Clan.CP_ALL);
				else
					recruiter.setClanPrivileges(recruiter.getClanPrivileges() | Clan.CP_CL_JOIN_CLAN);

				if (isViableClanRecruiter(recruiter, activeChar))
					return recruiter;

				recruiter.despawnPlayer();
			}
			catch (Exception e)
			{
				// Keep chat flow safe even if emergency recruiter spawn fails.
			}
		}

		return null;
	}

	private static Clan findJoinableClan(Player activeChar)
	{
		if (activeChar == null)
			return null;

		final List<Integer> configuredClanIds = new ArrayList<>(FakePlayerConfig.LIST_CLAN_ID);
		while (!configuredClanIds.isEmpty())
		{
			final int index = Rnd.get(configuredClanIds.size());
			final Clan clan = ClanTable.getInstance().getClan(configuredClanIds.remove(index));
			if (isJoinableClan(clan))
				return clan;
		}

		final Collection<Clan> allClans = ClanTable.getInstance().getClans();
		if (allClans == null || allClans.isEmpty())
			return null;

		final List<Clan> shuffledClans = new ArrayList<>(allClans);
		Collections.shuffle(shuffledClans);
		for (Clan clan : shuffledClans)
		{
			if (isJoinableClan(clan))
				return clan;
		}

		return null;
	}

	private static boolean isJoinableClan(Clan clan)
	{
		if (clan == null)
			return false;
		if (clan.getCharPenaltyExpiryTime() > System.currentTimeMillis())
			return false;
		return clan.getSubPledgeMembersCount(0) < clan.getMaxNrOfMembers(0);
	}

	private static boolean isLookingForClan(String lower)
	{
		if (lower == null)
			return false;
		if (lower.contains("lf clan") || (lower.contains("lf") && lower.contains("clan")))
			return true;

		final String raw = FakePlayerConfig.FAKE_CLAN_LF_INVITE_TRIGGERS;
		if (raw == null || raw.trim().isEmpty())
			return false;

		for (String trigger : raw.toLowerCase().split(";"))
		{
			final String t = trigger.trim();
			if (!t.isEmpty() && lower.contains(t))
				return true;
		}
		return false;
	}

	private static boolean canUseFakeClanInvite(Player activeChar)
	{
		if (activeChar == null)
			return false;
		
		if (activeChar instanceof FakePlayer || activeChar.isPhantom() || activeChar.isGM() || activeChar.getClan() != null)
			return false;

		if (activeChar.isInOlympiadMode() || activeChar.getOlympiadGameId() != -1 || activeChar.isOlympiadProtection())
			return false;

		if (activeChar.isInArenaEvent() || activeChar.isArenaAttack() || activeChar.isArenaProtection() || activeChar.isArena1x1() || activeChar.isArena2x2() || activeChar.isArena5x5() || activeChar.isArena9x9() || activeChar.isTournamentTeleport())
			return false;

		if (activeChar._inEventTvT || activeChar._inEventCTF || activeChar.isArenaObserv() || activeChar.isInObserverMode() || activeChar.isEventObserver() || activeChar.isZoneObserver())
			return false;

		return activeChar.isInsideZone(ZoneId.TOWN) || activeChar.isInsideZone(ZoneId.PEACE);
	}
}
