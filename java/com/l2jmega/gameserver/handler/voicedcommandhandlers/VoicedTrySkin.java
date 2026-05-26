package com.l2jmega.gameserver.handler.voicedcommandhandlers;

import java.util.StringTokenizer;

import com.l2jmega.commons.concurrent.ThreadPool;

import com.l2jmega.Config;
import com.l2jmega.gameserver.data.xml.DressMeData;
import com.l2jmega.gameserver.handler.IVoicedCommandHandler;
import com.l2jmega.gameserver.model.DressMe;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.zone.ZoneId;
import com.l2jmega.gameserver.network.serverpackets.MagicSkillUse;
import com.l2jmega.gameserver.network.serverpackets.NpcHtmlMessage;
import com.l2jmega.gameserver.network.serverpackets.SetupGauge;
import com.l2jmega.gameserver.network.serverpackets.SetupGauge.GaugeColor;

public class VoicedTrySkin implements IVoicedCommandHandler {
	private static final String[] VOICED_COMMANDS = new String[] { "skin", "trySkin", "voiced_trySkin" };
	
	
	@Override
	public boolean useVoicedCommand(String command, Player activeChar, String target) {
		if (command.equals("skin") && Config.CMD_SKIN)
			showTrySkinHtml(activeChar); 
		
		if (command.startsWith("voiced_trySkin")) {
			if (!activeChar.isInsideZone(ZoneId.TOWN)) {
				activeChar.sendMessage("This command can only be used within a city.");
				return false;
			}
			
			final Integer skinId = resolveSkinId(command, target);
			if (skinId == null) {
				activeChar.sendMessage("Invalid skin id.");
				return false;
			}
			
			final DressMe dress = DressMeData.getInstance().getItemId(skinId);
			final DressMe dress2 = DressMeData.getInstance().getItemId(0);
			
			// Check if same skin is already active (toggle functionality)
			if (activeChar.getDress() != null && activeChar.getDress().getItemId() == skinId) {
				// Remove skin if same one is used
				activeChar.setDress(dress2);
				activeChar.broadcastUserInfo();
				activeChar.sendMessage("Skin removed!");
				return true;
			}
			
			if (dress != null) {
				if (Config.SKIN_ENABLE_ANIMATIONS)
				{
					activeChar.broadcastPacket(new MagicSkillUse(activeChar, activeChar, Config.SKIN_SKILL_ID, 1, 3000, 0));
					activeChar.sendPacket(new SetupGauge(GaugeColor.BLUE, 3000));
					activeChar.setIsParalyzed(true);
					
					ThreadPool.schedule(() -> {
						activeChar.setIsParalyzed(false);
					}, 3000L);
				}

				activeChar.setDress(dress);
				ThreadPool.schedule(() -> {
					activeChar.setDress(dress2);
					activeChar.broadcastUserInfo();
				}, 3000L);
			} else {
				activeChar.sendMessage("Invalid skin.");
				return false;
			} 
		}
		
		if (command.startsWith("trySkin")) {
			if (!activeChar.isInsideZone(ZoneId.TOWN)) {
				activeChar.sendMessage("This command can only be used within a city.");
				return false;
			}
			
			final Integer skinId = resolveSkinId(command, target);
			if (skinId == null) {
				activeChar.sendMessage("Invalid skin id.");
				return false;
			}
			
			final DressMe dress = DressMeData.getInstance().getItemId(skinId);
			final DressMe dress2 = DressMeData.getInstance().getItemId(0);
			
			// Check if same skin is already active (toggle functionality)
			if (activeChar.getDress() != null && activeChar.getDress().getItemId() == skinId) {
				// Remove skin if same one is used
				activeChar.setDress(dress2);
				activeChar.broadcastUserInfo();
				activeChar.sendMessage("Skin removed!");
				return true;
			}
			
			if (dress != null) {
				if (Config.SKIN_ENABLE_ANIMATIONS)
				{
					activeChar.broadcastPacket(new MagicSkillUse(activeChar, activeChar, Config.SKIN_SKILL_ID, 1, 3000, 0));
					activeChar.sendPacket(new SetupGauge(GaugeColor.BLUE, 3000));
					activeChar.setIsParalyzed(true);
					
					ThreadPool.schedule(() -> {
						activeChar.setIsParalyzed(false);
					}, 3000L);
				}

				activeChar.setDress(dress);
				ThreadPool.schedule(() -> {
					activeChar.setDress(dress2);
					activeChar.broadcastUserInfo();
				}, 3000L);
			} else {
				activeChar.sendMessage("Invalid skin.");
				return false;
			} 
		}
		
		return true;
	}
	
	private static void showTrySkinHtml(Player activeChar) {
		NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile("data/html/mods/menu/trySkin.htm");
		activeChar.sendPacket(html);
	}
	
	private static Integer resolveSkinId(String command, String target) {
		final Integer value = parseSkinId(command, target);
		if (value == null)
			return null;
		
		if (DressMeData.getInstance().getItemId(value) != null)
			return value;
		
		if (value >= 1 && value <= 27)
			return value;
		
		return null;
	}
	
	private static Integer parseSkinId(String command, String target) {
		if (target != null && !target.isBlank()) {
			try {
				return Integer.parseInt(target.trim());
			} catch (NumberFormatException e) {
				return null;
			}
		}
		
		StringTokenizer st = new StringTokenizer(command);
		st.nextToken();
		if (!st.hasMoreTokens())
			return null;
		
		try {
			return Integer.parseInt(st.nextToken());
		} catch (NumberFormatException e) {
			return null;
		}
	}
	
	@Override
	public String[] getVoicedCommandList() {
		return VOICED_COMMANDS;
	}
}
