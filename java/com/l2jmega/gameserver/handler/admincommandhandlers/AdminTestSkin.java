package com.l2jmega.gameserver.handler.admincommandhandlers;

import com.l2jmega.gameserver.data.xml.DressMeData;
import com.l2jmega.gameserver.handler.IAdminCommandHandler;
import com.l2jmega.gameserver.model.DressMe;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.network.serverpackets.NpcHtmlMessage;

public class AdminTestSkin implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = { "admin_testskin" };
	
	@Override
	public boolean useAdminCommand(String command, Player activeChar)
	{
		if (command.equals("admin_testskin"))
		{
			// Test skin system
			final DressMe dress = DressMeData.getInstance().getItemId(15000);
			if (dress != null)
			{
				activeChar.sendMessage("Skin system is working! Found skin for ID 15000");
				activeChar.setDress(dress);
				activeChar.broadcastUserInfo();
			}
			else
			{
				activeChar.sendMessage("Skin system error! No skin found for ID 15000");
			}
			
			// Show debug info
			final NpcHtmlMessage html = new NpcHtmlMessage(0);
			html.setFile("data/html/admin/skin_test.htm");
			html.replace("%skinCount%", String.valueOf(DressMeData.getInstance().getEntries(0).size()));
			html.replace("%skinId%", "15000");
			html.replace("%skinFound%", dress != null ? "Yes" : "No");
			activeChar.sendPacket(html);
		}
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
