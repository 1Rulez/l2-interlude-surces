package com.l2jmega.gameserver.communitybbs;

import com.l2jmega.gameserver.communitybbs.Manager.BaseBBSManager;
import com.l2jmega.gameserver.data.NpcTable;
import com.l2jmega.gameserver.data.cache.HtmCache;
import com.l2jmega.gameserver.instancemanager.GrandBossManager;
import com.l2jmega.gameserver.model.actor.instance.Player;

import java.util.logging.Level;
import java.util.logging.Logger;






public class BossBBSManager extends BaseBBSManager
{
	private static final Logger _log = Logger.getLogger(BossBBSManager.class.getName());
	
	private static final int[] BOSSES = { 25512, 29001, 29006, 29014, 29019, 29020, 29022, 29028, 29065 };
	
	public static BossBBSManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	@Override
	public void parseCmd(String command, Player activeChar)
	{
		if (command.equals("_bbsGrandBoss")) 
		{
			showGrandBossStatus(activeChar);
		}
		else 
		{
			super.parseCmd(command, activeChar);
		}
	}
	
	@Override
	protected String getFolder()
	{
		return "stats/boss";
	}
	
	private static void showGrandBossStatus(Player activeChar)
	{
		String content = HtmCache.getInstance().getHtm("data/html/CommunityBoard/stats/boss/grandBoss.htm");
		StringBuilder tb = new StringBuilder();
		try
		{
			for (int boss : BOSSES)
			{
				String name = NpcTable.getInstance().getTemplate(boss).getName();
		        long delay = GrandBossManager.getInstance().getStatsSet(boss).getLong("respawn_time");
		        if (delay <= System.currentTimeMillis())		
		        {
		        	tb.append("<table border=0 cellspacing=0 cellpadding=2 height=24 width=400><tr>");
		            tb.append("<td FIXWIDTH=200 align=center>" + name + "</td>");
		            tb.append("<td FIXWIDTH=200 align=center><font color=99FF00>Alive</font></td>");
		            tb.append("</tr></table>");
		            tb.append("<img src=\"L2UI.Squaregray\" width=\"400\" height=\"1\">");		        	
		        }
		        else
		        {
		        	int hours = (int)((delay - System.currentTimeMillis()) / 1000L / 60L / 60L);
		        	int mins = (int)((delay - hours * 60 * 60 * 1000 - System.currentTimeMillis()) / 1000L / 60L);
		        	
		            tb.append("<table border=0 cellspacing=0 cellpadding=2 height=24 width=400><tr>");
		            tb.append("<td FIXWIDTH=200 align=center>" + name + "</td>");
		            tb.append("<td FIXWIDTH=200 align=center><font color=CC0000>" + hours + " hours and " + mins + " minutes to respawn</font></td>");
		            tb.append("</tr></table>");
		            tb.append("<img src=\"L2UI.Squaregray\" width=\"400\" height=\"1\">");		        	
		        }
			}
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Failed to load grand boss list.");
		}
		content = content.replaceAll("%showList%", tb.toString());
		separateAndSend(content, activeChar);
	}
	
	private static class SingletonHolder
	{
		protected static final BossBBSManager _instance = new BossBBSManager();
	}
}