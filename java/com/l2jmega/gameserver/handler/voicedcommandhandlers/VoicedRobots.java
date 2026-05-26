package com.l2jmega.gameserver.handler.voicedcommandhandlers;

import Base.RandomFightEvent.RandomFight;
import Base.custom.event.AnonymousPvPEvent;
import com.l2jmega.events.CTF;
import com.l2jmega.events.TvT;
import com.l2jmega.gameserver.handler.IVoicedCommandHandler;
import com.l2jmega.gameserver.model.L2Party;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.network.serverpackets.NpcHtmlMessage;
import phantom.FakePlayer;
import phantom.ai.party.PartyFollowerAI;
import phantom.ai.party.PartyMode;

public class VoicedRobots implements IVoicedCommandHandler
{
	private static final String[] VOICED_COMMANDS = {
		"robots",
		"robots_invite",
		"robots_setmode",
		"robots_partymode",
		"robots_kick",
		"robots_kickall"
	};
	
	@Override
	public String[] getVoicedCommandList()
	{
		return VOICED_COMMANDS;
	}
	
	@Override
	public boolean useVoicedCommand(String command, Player activeChar, String params)
	{
		if (isInEvent(activeChar))
		{
			activeChar.sendMessage("Robot commands are not available during events (TvT, CTF, Random Fight, Arena, Olympiad, Anonymous PvP).");
			return true;
		}

		if (command.equals("robots"))
		{
			showPanel(activeChar);
			return true;
		}
		
		if (command.equals("robots_invite"))
		{
			handleInvite(activeChar);
			showPanel(activeChar);
			return true;
		}
		
		if (command.equals("robots_setmode"))
		{
			if (params != null && !params.isEmpty())
				handleSetMode(activeChar, params.trim());
			showPanel(activeChar);
			return true;
		}
		
		if (command.equals("robots_partymode"))
		{
			if (params != null && !params.isEmpty())
				handlePartyMode(activeChar, params.trim().toUpperCase());
			showPanel(activeChar);
			return true;
		}
		
		if (command.equals("robots_kick"))
		{
			handleKick(activeChar);
			showPanel(activeChar);
			return true;
		}
		
		if (command.equals("robots_kickall"))
		{
			handleKickAll(activeChar);
			showPanel(activeChar);
			return true;
		}
		
		return false;
	}
	
	private static boolean isInEvent(Player player)
	{
		if (player == null)
			return false;
		if ((TvT.is_teleport() || TvT.is_started()) && player._inEventTvT)
			return true;
		if ((CTF.is_teleport() || CTF.is_started()) && player._inEventCTF)
			return true;
		if (RandomFight.isEventActive())
			return true;
		if (player.isInArenaEvent() || player.isArenaProtection())
			return true;
		if (player.isInOlympiadMode() || player.isOlympiadProtection())
			return true;
		if (AnonymousPvPEvent.isPlayerInEvent(player))
			return true;
		return false;
	}

	private static void handleInvite(Player activeChar)
	{
		if (!(activeChar.getTarget() instanceof FakePlayer))
		{
			activeChar.sendMessage("Select a bot first.");
			return;
		}
		
		FakePlayer fake = (FakePlayer) activeChar.getTarget();
		
		if (fake.isSitting() || fake.isInStoreMode())
		{
			activeChar.sendMessage(fake.getName() + " is busy (sitting or trading).");
			return;
		}
		
		if (fake.isInParty())
		{
			activeChar.sendMessage(fake.getName() + " is already in a party.");
			return;
		}
		
		if (!activeChar.isInParty())
			activeChar.setParty(new L2Party(activeChar, 0));
		
		L2Party party = activeChar.getParty();
		if (party.getMemberCount() >= 9)
		{
			activeChar.sendMessage("Party is full.");
			return;
		}
		
		if (party.getLeader() != activeChar)
		{
			activeChar.sendMessage("Only party leader can invite bots.");
			return;
		}
		
		fake.joinParty(party);
		fake.setFakeAi(new PartyFollowerAI(fake));
		fake.setPartyMode(PartyMode.FOLLOW);
		activeChar.sendMessage(fake.getName() + " joined your party (FOLLOW).");
	}
	
	private static void handleSetMode(Player activeChar, String params)
	{
		String[] parts = params.split(" ", 2);
		if (parts.length < 2)
			return;
		
		int objectId;
		try { objectId = Integer.parseInt(parts[0]); }
		catch (NumberFormatException e) { return; }
		
		String modeStr = parts[1].toUpperCase();
		PartyMode mode;
		try { mode = PartyMode.valueOf(modeStr); }
		catch (IllegalArgumentException e) { activeChar.sendMessage("Unknown mode: " + modeStr); return; }
		
		if (!activeChar.isInParty())
		{
			activeChar.sendMessage("You are not in a party.");
			return;
		}
		
		for (Player member : activeChar.getParty().getPartyMembers())
		{
			if (member instanceof FakePlayer && member.getObjectId() == objectId)
			{
				FakePlayer fake = (FakePlayer) member;
				fake.setPartyMode(mode);
				if (!(fake.getFakeAi() instanceof PartyFollowerAI))
					fake.setFakeAi(new PartyFollowerAI(fake));
				activeChar.sendMessage(fake.getName() + " -> " + mode.getDescription());
				return;
			}
		}
		activeChar.sendMessage("Bot not found in your party.");
	}
	
	private static void handlePartyMode(Player activeChar, String modeStr)
	{
		if (!activeChar.isInParty())
		{
			activeChar.sendMessage("You are not in a party.");
			return;
		}
		
		PartyMode mode;
		try { mode = PartyMode.valueOf(modeStr); }
		catch (IllegalArgumentException e) { activeChar.sendMessage("Unknown mode: " + modeStr); return; }
		
		int count = 0;
		for (Player member : activeChar.getParty().getPartyMembers())
		{
			if (member instanceof FakePlayer)
			{
				FakePlayer fake = (FakePlayer) member;
				fake.setPartyMode(mode);
				if (!(fake.getFakeAi() instanceof PartyFollowerAI))
					fake.setFakeAi(new PartyFollowerAI(fake));
				count++;
			}
		}
		activeChar.sendMessage(count + " bot(s) set to " + mode.getDescription() + ".");
	}
	
	private static void handleKick(Player activeChar)
	{
		if (!(activeChar.getTarget() instanceof FakePlayer))
		{
			activeChar.sendMessage("Select a bot to kick.");
			return;
		}
		
		FakePlayer fake = (FakePlayer) activeChar.getTarget();
		
		if (!activeChar.isInParty() || !fake.isInParty() || fake.getParty() != activeChar.getParty())
		{
			activeChar.sendMessage("This bot is not in your party.");
			return;
		}
		
		if (activeChar.getParty().getLeader() != activeChar)
		{
			activeChar.sendMessage("Only party leader can kick bots.");
			return;
		}
		
		fake.leaveParty();
		activeChar.sendMessage(fake.getName() + " kicked from party.");
	}
	
	private static void handleKickAll(Player activeChar)
	{
		if (!activeChar.isInParty())
		{
			activeChar.sendMessage("You are not in a party.");
			return;
		}
		
		if (activeChar.getParty().getLeader() != activeChar)
		{
			activeChar.sendMessage("Only party leader can kick bots.");
			return;
		}
		
		java.util.List<FakePlayer> toRemove = new java.util.ArrayList<>();
		for (Player member : activeChar.getParty().getPartyMembers())
		{
			if (member instanceof FakePlayer)
				toRemove.add((FakePlayer) member);
		}
		
		for (FakePlayer fake : toRemove)
			fake.leaveParty();
		
		activeChar.sendMessage(toRemove.size() + " bot(s) kicked from party.");
	}
	
	private static void showPanel(Player activeChar)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("<html><title>Robot Control</title><body>");
		sb.append("<center>");
		sb.append("<table width=270 cellspacing=0 cellpadding=2>");
		sb.append("<tr><td align=center><font color=\"LEVEL\">Robot Party Manager</font></td></tr>");
		sb.append("</table>");
		
		String targetInfo = "No bot selected";
		if (activeChar.getTarget() instanceof FakePlayer)
		{
			FakePlayer target = (FakePlayer) activeChar.getTarget();
			targetInfo = target.getName() + " [Lv." + target.getLevel() + "] " + target.getPartyMode().getDescription();
		}
		sb.append("<table width=270><tr><td align=center><font color=\"aaaaaa\">").append(targetInfo).append("</font></td></tr></table>");
		sb.append("<img src=\"L2UI.SquareGray\" width=270 height=1>");
		
		sb.append("<table width=270 cellspacing=1 cellpadding=1>");
		sb.append("<tr>");
		sb.append("<td align=center><button value=\"Invite\" action=\"bypass -h voiced_robots_invite\" width=85 height=20 back=\"sek.cbui94\" fore=\"sek.cbui92\"></td>");
		sb.append("<td align=center><button value=\"Kick\" action=\"bypass -h voiced_robots_kick\" width=85 height=20 back=\"sek.cbui94\" fore=\"sek.cbui92\"></td>");
		sb.append("<td align=center><button value=\"Kick All\" action=\"bypass -h voiced_robots_kickall\" width=85 height=20 back=\"sek.cbui94\" fore=\"sek.cbui92\"></td>");
		sb.append("</tr>");
		sb.append("</table>");
		sb.append("<img src=\"L2UI.SquareGray\" width=270 height=1>");
		
		sb.append("<table width=270><tr><td align=center><font color=\"LEVEL\">All Bots Mode</font></td></tr></table>");
		sb.append("<table width=270 cellspacing=1 cellpadding=1>");
		sb.append("<tr>");
		sb.append("<td align=center><button value=\"Follow\" action=\"bypass -h voiced_robots_partymode follow\" width=85 height=20 back=\"sek.cbui94\" fore=\"sek.cbui92\"></td>");
		sb.append("<td align=center><button value=\"Assist\" action=\"bypass -h voiced_robots_partymode assist\" width=85 height=20 back=\"sek.cbui94\" fore=\"sek.cbui92\"></td>");
		sb.append("<td align=center><button value=\"Defend\" action=\"bypass -h voiced_robots_partymode defend\" width=85 height=20 back=\"sek.cbui94\" fore=\"sek.cbui92\"></td>");
		sb.append("</tr>");
		sb.append("<tr>");
		sb.append("<td align=center><button value=\"Farm\" action=\"bypass -h voiced_robots_partymode farm\" width=85 height=20 back=\"sek.cbui94\" fore=\"sek.cbui92\"></td>");
		sb.append("<td align=center><button value=\"Stand\" action=\"bypass -h voiced_robots_partymode stand\" width=85 height=20 back=\"sek.cbui94\" fore=\"sek.cbui92\"></td>");
		sb.append("<td width=85></td>");
		sb.append("</tr>");
		sb.append("</table>");
		sb.append("<img src=\"L2UI.SquareGray\" width=270 height=1>");
		
		sb.append("<table width=270><tr><td align=center><font color=\"LEVEL\">Party Bots</font></td></tr></table>");
		
		if (activeChar.isInParty())
		{
			sb.append("<table width=270 cellspacing=0 cellpadding=1 bgcolor=000000>");
			boolean hasFakes = false;
			for (Player member : activeChar.getParty().getPartyMembers())
			{
				if (member instanceof FakePlayer)
				{
					hasFakes = true;
					FakePlayer fake = (FakePlayer) member;
					int objId = fake.getObjectId();
					String modeName = fake.getPartyMode().name();
					int hpPercent = (int) (fake.getCurrentHp() / fake.getMaxHp() * 100);
					
					sb.append("<tr>");
					sb.append("<td width=110><font color=\"00FF00\">").append(fake.getName()).append("</font></td>");
					sb.append("<td width=50 align=center><font color=\"aaaaaa\">").append(hpPercent).append("%</font></td>");
					sb.append("<td width=110 align=right><font color=\"LEVEL\">").append(modeName).append("</font></td>");
					sb.append("</tr>");
					sb.append("<tr><td colspan=3>");
					sb.append("<a action=\"bypass -h voiced_robots_setmode ").append(objId).append(" FOLLOW\">Fol</a> ");
					sb.append("<a action=\"bypass -h voiced_robots_setmode ").append(objId).append(" ASSIST\">Ast</a> ");
					sb.append("<a action=\"bypass -h voiced_robots_setmode ").append(objId).append(" DEFEND\">Def</a> ");
					sb.append("<a action=\"bypass -h voiced_robots_setmode ").append(objId).append(" FARM\">Frm</a> ");
					sb.append("<a action=\"bypass -h voiced_robots_setmode ").append(objId).append(" STAND\">Stn</a>");
					sb.append("</td></tr>");
					sb.append("<tr><td colspan=3><img src=\"L2UI.SquareGray\" width=260 height=1></td></tr>");
				}
			}
			if (!hasFakes)
				sb.append("<tr><td colspan=3 align=center><font color=\"999999\">No bots in party</font></td></tr>");
			sb.append("</table>");
		}
		else
		{
			sb.append("<table width=270><tr><td align=center><font color=\"999999\">No party</font></td></tr></table>");
		}
		
		sb.append("</center>");
		sb.append("</body></html>");
		
		NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setHtml(sb.toString());
		activeChar.sendPacket(html);
	}
}
