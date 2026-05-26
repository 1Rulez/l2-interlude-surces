/*
 *
 * This file is part of L2J DataPack.
 *
 * L2J DataPack is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * L2J DataPack is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jmega.gameserver.handler.admincommandhandlers;

import com.l2jmega.commons.math.MathUtil;

import com.l2jmega.gameserver.handler.IAdminCommandHandler;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.WorldObject;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.item.instance.ItemInstance;
import com.l2jmega.gameserver.network.serverpackets.NpcHtmlMessage;

/**
 * This class handles following admin commands:
 * <ul>
 * <li>show_ivetory</li>
 * <li>delete_item</li>
 * </ul>
 * @author Zealar
 */
public class AdminInventory implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_show_item",
		"admin_delete_item"
	};
	
	@Override
	public boolean useAdminCommand(String command, Player activeChar)
	{
		if (activeChar.getAccessLevel().getLevel() < 7)
			return false;
		
		Player player = null;
		WorldObject target = activeChar.getTarget();
		if (target instanceof Player)
			player = (Player) target;
		else if (command.length() > ADMIN_COMMANDS[0].length() + 1)
		{
			String param = command.substring(ADMIN_COMMANDS[0].length() + 1).trim();
			if (!MathUtil.isDigit(param))
			{
				player = World.getInstance().getPlayer(param);
				if (player == null)
				{
					for (Player p : World.getInstance().getPlayers())
					{
						if (p != null && p.getName().equalsIgnoreCase(param))
						{
							player = p;
							break;
						}
					}
				}
			}
		}
		
		if (player == null)
		{
			activeChar.sendMessage("Select a target or specify player name");
			return false;
		}
		
		activeChar.setTarget(player);
		
		if (command.startsWith(ADMIN_COMMANDS[0]))
		{
			int page = 0;
			if (command.length() > ADMIN_COMMANDS[0].length() + 1)
			{
				String com = command.substring(ADMIN_COMMANDS[0].length() + 1).trim();
				if (MathUtil.isDigit(com))
					page = Integer.parseInt(com);
			}
			showItemsPage(activeChar, player, page);
		}
		else if (command.contains(ADMIN_COMMANDS[1]))
		{
			String val = command.substring(ADMIN_COMMANDS[1].length() + 1).trim();
			player.destroyItem("GM Destroy", Integer.parseInt(val), player.getInventory().getItemByObjectId(Integer.parseInt(val)).getCount(), null, true);
			player.broadcastUserInfo();
			showItemsPage(activeChar, player, 0);
		}
		
		return true;
	}
	
	private static void showItemsPage(Player activeChar, Player target, int page)
	{
		final ItemInstance[] items = target.getInventory().getItemsIcon();
		
		int maxItemsPerPage = 10;
		int maxPages = items.length / maxItemsPerPage;
		if (items.length > (maxItemsPerPage * maxPages))
		{
			maxPages++;
		}
		
		if (page > maxPages)
		{
			page = maxPages;
		}
		
		int itemsStart = maxItemsPerPage * page;
		int itemsEnd = items.length;
		
		if ((itemsEnd - itemsStart) > maxItemsPerPage)
		{
			itemsEnd = itemsStart + maxItemsPerPage;
		}
		
		final NpcHtmlMessage adminReply = new NpcHtmlMessage(0);
		adminReply.setFile("data/html/admin/inventory.htm");
		adminReply.replace("%PLAYER_NAME%", target.getName());
		
		StringBuilder sbItems = new StringBuilder();
		
		for (int i = itemsStart; i < itemsEnd; i++)
		{
			int itemId = items[i].getItem().getItemId();
			
			sbItems.append("<tr><td><img src=\"" + items[i].getItem().getIcon(itemId) + "\" width=32 height=32></td>");
			sbItems.append("<td width=60>" + items[i].getName() + "(" + items[i].getItemId() + ")</td>");
			sbItems.append("<td><button action=\"bypass -h admin_delete_item " + String.valueOf(items[i].getObjectId()) + "\" width=16 height=16 back=\"L2UI_ch3.FrameCloseOnBtn\" fore=\"L2UI_ch3.FrameCloseOnBtn\">" + "</td></tr>");
		}
		
		adminReply.replace("%ITEMS%", sbItems.toString());
		
		StringBuilder sbPages = new StringBuilder();
		for (int x = 0; x < maxPages; x++)
		{
			int pagenr = x + 1;
			sbPages.append("<td><button value=\"" + String.valueOf(pagenr) + "\" action=\"bypass -h admin_show_item " + String.valueOf(x) + "\" width=15 height=15 back=\"l2ui.ComboBox1\" fore=\"l2ui.ComboBox1\"></td>");
		}
		
		adminReply.replace("%PAGES%", sbPages.toString());
		
		activeChar.sendPacket(adminReply);
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
