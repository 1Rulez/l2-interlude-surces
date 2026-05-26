package com.l2jmega.gameserver.handler.voicedcommandhandlers;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.l2jmega.gameserver.handler.IVoicedCommandHandler;
import com.l2jmega.gameserver.instancemanager.CastleManager;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.entity.Castle;
import com.l2jmega.gameserver.model.entity.Siege;
import com.l2jmega.gameserver.network.SystemMessageId;
import com.l2jmega.gameserver.network.serverpackets.NpcHtmlMessage;
import com.l2jmega.gameserver.network.serverpackets.SiegeInfo;

public class VoicedCastles implements IVoicedCommandHandler
{
	private static final String[] VOICED_COMMANDS =
	{
		"castlemanager",
		"siege_gludio",
		"siege_dion",
		"siege_giran",
		"siege_oren",
		"siege_aden",
		"siege_innadril",
		"siege_goddard",
		"siege_rune",
		"siege_schuttgart"
	};
	
	@Override
	public boolean useVoicedCommand(String command, Player activeChar, String target)
	{
		if (command.startsWith("castlemanager"))
			sendHtml(activeChar);
		if (command.startsWith("siege_"))
		{
			if (activeChar.getClan() != null && !activeChar.isClanLeader())
			{
				activeChar.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
				return false;
			}
			
			int castleId = 0;
			if (command.startsWith("siege_gludio"))
				castleId = 1;
			else if (command.startsWith("siege_dion"))
				castleId = 2;
			else if (command.startsWith("siege_giran"))
				castleId = 3;
			else if (command.startsWith("siege_oren"))
				castleId = 4;
			else if (command.startsWith("siege_aden"))
				castleId = 5;
			else if (command.startsWith("siege_innadril"))
				castleId = 6;
			else if (command.startsWith("siege_goddard"))
				castleId = 7;
			else if (command.startsWith("siege_rune"))
				castleId = 8;
			else if (command.startsWith("siege_schuttgart"))
				castleId = 9;
			
			Castle castle = CastleManager.getInstance().getCastleById(castleId);
			if ((castle != null) && (castleId != 0))
			{
				activeChar.sendPacket(new SiegeInfo(castle));
				sendSiegeStatus(activeChar, castle);
			}
		}
		return true;
	}
	
	private static void sendSiegeStatus(Player activeChar, Castle castle)
	{
		final Siege siege = castle.getSiege();
		if (siege == null)
			return;
		
		final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
		final String siegeDate = format.format(new Date(castle.getSiegeDate().getTimeInMillis()));
		activeChar.sendMessage("Siege castle: " + castle.getName());
		activeChar.sendMessage("Next siege: " + siegeDate);
		
		if (!siege.isCastleSiegeEnabled())
		{
			activeChar.sendMessage("Registration status: DISABLED");
			return;
		}
		
		if (siege.isInProgress())
		{
			activeChar.sendMessage("Registration status: IN PROGRESS");
			return;
		}
		
		if (siege.isRegistrationOver())
		{
			activeChar.sendMessage("Registration status: CLOSED");
			activeChar.sendMessage("Registration closed at: " + format.format(new Date(siege.getSiegeRegistrationEndDate())));
			return;
		}
		
		activeChar.sendMessage("Registration status: OPEN");
		activeChar.sendMessage("Registration closes: " + format.format(new Date(siege.getSiegeRegistrationEndDate())));
	}
	
	private static void sendHtml(Player activeChar)
	{
		String htmFile = "data/html/mods/menu/CastleManager.htm";
		NpcHtmlMessage msg = new NpcHtmlMessage(5);
		msg.setFile(htmFile);
		activeChar.sendPacket(msg);
	}
	
	@Override
	public String[] getVoicedCommandList()
	{
		return VOICED_COMMANDS;
	}
}
