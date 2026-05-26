package com.l2jmega.gameserver.handler.usercommandhandlers;

import com.l2jmega.Config;
import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.events.CTF;
import com.l2jmega.events.TvT;
import com.l2jmega.gameserver.data.SkillTable;
import com.l2jmega.gameserver.data.MapRegionTable;
import com.l2jmega.gameserver.handler.IUserCommandHandler;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.zone.ZoneId;
import com.l2jmega.gameserver.network.SystemMessageId;
import com.l2jmega.gameserver.network.serverpackets.MagicSkillUse;
import com.l2jmega.gameserver.network.serverpackets.PlaySound;
import com.l2jmega.gameserver.network.serverpackets.SetupGauge;
import com.l2jmega.gameserver.network.serverpackets.SetupGauge.GaugeColor;

public class Escape implements IUserCommandHandler
{
	private static final int[] COMMAND_IDS =
	{
		52
	};
	private static final int NORMAL_ESCAPE_SKILL_ID = 2099;
	private static final int GM_ESCAPE_SKILL_ID = 2100;
	
	@Override
	public boolean useUserCommand(int id, Player activeChar)
	{
		if (activeChar.isCastingNow() || activeChar.isSitting() || activeChar.isMovementDisabled() || activeChar.isOutOfControl() || activeChar.isInOlympiadMode() || activeChar.isInObserverMode() || activeChar.isFestivalParticipant() || activeChar.isInJail())
		{
			activeChar.sendPacket(SystemMessageId.NO_UNSTUCK_PLEASE_SEND_PETITION);
			return false;
		}
		
		if ((TvT.is_started() && activeChar._inEventTvT) || (CTF.is_started() && activeChar._inEventCTF))
		{
			activeChar.sendMessage("You may not use an escape skill in event.");
			return false;
		}
		
		if (activeChar.isArenaProtection() || activeChar.isInsideZone(ZoneId.TOURNAMENT))
		{
			activeChar.sendMessage("You cannot use this skill in Tournament Event/Zone.");
			return false;
		}
		
		activeChar.stopMove(null);
		
		if (activeChar.isGM())
		{
			activeChar.doCast(SkillTable.getInstance().getInfo(GM_ESCAPE_SKILL_ID, 1));
		}
		else
		{
			activeChar.sendPacket(new PlaySound("systemmsg_e.809"));
			final int unstuckSeconds = Math.max(0, Config.UNSTUCK_SECONDS);
			if (unstuckSeconds == 30)
				activeChar.doCast(SkillTable.getInstance().getInfo(NORMAL_ESCAPE_SKILL_ID, 1));
			else if (unstuckSeconds == 1)
				activeChar.doCast(SkillTable.getInstance().getInfo(GM_ESCAPE_SKILL_ID, 1));
			else
				startConfiguredUnstuck(activeChar, unstuckSeconds);
			
			activeChar.sendMessage("You use Escape: " + unstuckSeconds + " seconds");
		}
		
		return true;
	}

	private static void startConfiguredUnstuck(Player activeChar, int seconds)
	{
		if (activeChar == null)
			return;

		final int delayMs = Math.max(0, seconds) * 1000;
		activeChar.broadcastPacket(new MagicSkillUse(activeChar, activeChar, NORMAL_ESCAPE_SKILL_ID, 1, delayMs, 0));
		activeChar.sendPacket(new SetupGauge(GaugeColor.BLUE, delayMs));
		
		ThreadPool.schedule(() ->
		{
			if (!activeChar.isOnline() || activeChar.isDead() || activeChar.isInOlympiadMode() || activeChar.isInObserverMode()
				|| activeChar.isFestivalParticipant() || activeChar.isInJail())
				return;

			activeChar.teleToLocation(MapRegionTable.TeleportType.TOWN);
		}, delayMs);
	}
	
	@Override
	public int[] getUserCommandList()
	{
		return COMMAND_IDS;
	}
}
