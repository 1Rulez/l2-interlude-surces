package com.l2jmega.gameserver.communitybbs;

import com.l2jmega.gameserver.communitybbs.Manager.BaseBBSManager;
import com.l2jmega.gameserver.data.cache.HtmCache;
import com.l2jmega.gameserver.model.actor.instance.Player;

import java.util.Locale;
import java.util.StringTokenizer;



public class StatsBBSManager extends BaseBBSManager
{
	@Override
	public void parseCmd(String command, Player activeChar)
	{
		if (command.startsWith("_bbsstats"))
		{
			final StringTokenizer st = new StringTokenizer(command, ";");
			st.nextToken();
			final String secondCommand = normalizeStatsCommand(st.hasMoreTokens() ? st.nextToken() : "pvp");
			if (secondCommand.equals("pvp"))
			{
				showTopPvpList(activeChar);
			}
			else if (secondCommand.equals("pk"))
			{
		        showTopPkList(activeChar);
			}
			else if (secondCommand.equals("raid"))
			{
		        showTopRaidList(activeChar);
			}
		    else if (secondCommand.equals("adena"))
		    {
		        showTopAdenaList(activeChar);
		    }
		    else if (secondCommand.equals("online"))
		    {
		        showTopOnlineList(activeChar);		
		    }
		    else
		    {
		    	showTopPvpList(activeChar);
		    }
		}
		else
		{
			super.parseCmd(command, activeChar);
		}
	}
		
	@Override
	protected String getFolder()
	{
		return "stats/";
	}

	private static String normalizeStatsCommand(String rawCommand)
	{
		final String command = rawCommand == null ? "" : rawCommand.trim().toLowerCase(Locale.ROOT);

		if (command.isEmpty() || command.equals("pvp") || command.equals("toppvp") || command.equals("top_pvp") || command.equals("top-pvp"))
		{
			return "pvp";
		}

		if (command.equals("pk") || command.equals("pks") || command.equals("toppk") || command.equals("top_pk") || command.equals("top-pk"))
		{
			return "pk";
		}

		if (command.equals("raid") || command.equals("rb") || command.equals("toprb") || command.equals("raidpoints") || command.equals("topraid"))
		{
			return "raid";
		}

		if (command.equals("adena") || command.equals("rich") || command.equals("money") || command.equals("topadena"))
		{
			return "adena";
		}

		if (command.equals("online") || command.equals("active") || command.equals("activity") || command.equals("toponline"))
		{
			return "online";
		}

		return "pvp";
	}
		
	public static StatsBBSManager getInstance()
	{
		return SingletonHolder._instance;
	}
		
	private static void showTopPvpList(Player activeChar)
	{
		String content = HtmCache.getInstance().getHtm("data/html/CommunityBoard/stats/topPvp.htm");		
		
	    content = content.replace("%toppvp%", PlayerStatsUpdateTask.pvpList());
	    content = content.replace("%lastUpdate%", PlayerStatsUpdateTask.getLastUpdate());
	    separateAndSend(content, activeChar);		
	}
	
	private static void showTopPkList(Player activeChar)
	{
	    String content = HtmCache.getInstance().getHtm("data/html/CommunityBoard/stats/topPk.htm");
	    
	    content = content.replace("%toppk%", PlayerStatsUpdateTask.pkList());
	    content = content.replace("%lastUpdate%", PlayerStatsUpdateTask.getLastUpdate());
	    separateAndSend(content, activeChar);		
	}
	
	private static void showTopRaidList(Player activeChar)
	{
	    String content = HtmCache.getInstance().getHtm("data/html/CommunityBoard/stats/topRaid.htm");
	    
	    content = content.replace("%topraid%", PlayerStatsUpdateTask.raidList());
	    content = content.replace("%lastUpdate%", PlayerStatsUpdateTask.getLastUpdate());
	    separateAndSend(content, activeChar);		
	}
	
	private static void showTopAdenaList(Player activeChar)
	{
	    String content = HtmCache.getInstance().getHtm("data/html/CommunityBoard/stats/topAdena.htm");
	    
	    content = content.replace("%topadena%", PlayerStatsUpdateTask.adenaList());
	    content = content.replace("%lastUpdate%", PlayerStatsUpdateTask.getLastUpdate());
	    separateAndSend(content, activeChar);		
	}
	
	private static void showTopOnlineList(Player activeChar)
	{
	    String content = HtmCache.getInstance().getHtm("data/html/CommunityBoard/stats/topOnline.htm");
	    
	    content = content.replace("%toponline%", PlayerStatsUpdateTask.onlineList());
	    content = content.replace("%lastUpdate%", PlayerStatsUpdateTask.getLastUpdate());
	    separateAndSend(content, activeChar);		
	}
	
	private static class SingletonHolder
	{
		protected static final StatsBBSManager _instance = new StatsBBSManager();
	}
}
