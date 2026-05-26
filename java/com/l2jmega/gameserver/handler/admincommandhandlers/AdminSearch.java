package com.l2jmega.gameserver.handler.admincommandhandlers;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import com.l2jmega.commons.lang.StringUtil;
import com.l2jmega.commons.math.MathUtil;

import com.l2jmega.gameserver.data.ItemTable;
import com.l2jmega.gameserver.data.xml.DressMeData;
import com.l2jmega.gameserver.handler.IAdminCommandHandler;
import com.l2jmega.gameserver.model.DressMe;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.item.kind.Item;
import com.l2jmega.gameserver.network.serverpackets.NpcHtmlMessage;

public class AdminSearch implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_pesquisa",
		"admin_search",
	
	};
	private static final int PAGE_LIMIT = 15;
	
	@Override
	public boolean useAdminCommand(String command, Player activeChar)
	{	
		if (command.startsWith("admin_pesquisa") || command.startsWith("admin_search"))
		{
			StringTokenizer st = new StringTokenizer(command, " ");
			st.nextToken();
			
			if (!st.hasMoreTokens())
			{
				final NpcHtmlMessage html = new NpcHtmlMessage(0);
				html.setFile("data/html/admin/search.htm");
				html.replace("%items%", "");
				html.replace("%pages%", "");
				activeChar.sendPacket(html);
				
			}
			else
			{
				final String item = st.nextToken();
				int page = 1;
				if (st.hasMoreTokens())
				{
					
					try
					{
						page = Integer.parseInt(st.nextToken());
					}
					catch (NumberFormatException e)
					{
						page = 1;
					}
				}
				results(activeChar, item, page);
			}
		}
		return true;
	}
	
	private static void results(Player activeChar, String item, int page)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile("data/html/admin/search.htm");
		
		final List<String> rows = new ArrayList<>();
		
		for (Item itemName : ItemTable.getInstance().getAllItems())
			if (itemName != null)
				if (itemName.getName().toLowerCase().contains(item.toLowerCase()))
				{
					final String actualName = getFontedWord(item, itemName.getName());
					rows.add("<tr><td><a action=\"bypass -h admin_create_item " + itemName.getItemId() + "\"> " + actualName + "</a> (" + itemName.getItemId() + ")</td></tr>");
				}
		
		for (DressMe dress : DressMeData.getInstance().getEntries(0))
		{
			if (matchesSkin(item, dress))
			{
				rows.add("<tr><td><a action=\"bypass -h admin_create_item " + dress.getItemId() + "\">Skin ID " + dress.getItemId() + "</a> (chest " + dress.getChestId() + ", hair " + dress.getHairId() + ")</td></tr>");
			}
		}
		
		if (rows.isEmpty())
		{
			html.replace("%items%", "<tr><td>No items or skins found with value " + item + ".</td></tr>");
			html.replace("%pages%", "");
			activeChar.sendPacket(html);
			return;
		}
		
		final int max = Math.min(100, MathUtil.countPagesNumber(rows.size(), PAGE_LIMIT));
		page = Math.max(1, Math.min(page, max));
		
		final StringBuilder sb = new StringBuilder();
		for (String row : rows.subList((page - 1) * PAGE_LIMIT, Math.min(page * PAGE_LIMIT, rows.size())))
			sb.append(row);
		
		html.replace("%items%", sb.toString());
		sb.setLength(0);
		
		for (int i = 0; i < max; i++)
		{
			final int pagenr = i + 1;
			if (page == pagenr)
				StringUtil.append(sb, pagenr, "&nbsp;");
			else
				StringUtil.append(sb, "<a action=\"bypass -h admin_search ", item, " ", pagenr, "\">", pagenr, "</a>&nbsp;");
			
		}
		
		html.replace("%pages%", sb.toString());
		activeChar.sendPacket(html);
	}

	private static boolean matchesSkin(String value, DressMe dress)
	{
		final String search = value.toLowerCase();
		return String.valueOf(dress.getItemId()).contains(search) || String.valueOf(dress.getChestId()).contains(search) || String.valueOf(dress.getHairId()).contains(search);
	}
	
	private static String getFontedWord(String word, String tt)
	{
		
		int position = tt.toLowerCase().indexOf(word.toLowerCase());
		StringBuilder str = new StringBuilder(tt);
		
		String font = "<FONT COLOR=\"LEVEL\">";
		str.insert(position, font);
		str.insert(position + (font.length() + word.length()), "</FONT>");
		
		return str.toString();
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
