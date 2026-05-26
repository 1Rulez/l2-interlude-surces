package com.l2jmega.gameserver.communitybbs;

import com.l2jmega.gameserver.communitybbs.Manager.BaseBBSManager;
import com.l2jmega.gameserver.data.cache.HtmCache;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.actor.instance.Player;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.StringTokenizer;

import com.l2jmega.Config;
import com.l2jmega.gameserver.util.LogFileNameUtil;


public class ProblemReportBBSManager extends BaseBBSManager
{
	static String _type = "";
	static String _majority = "";
	static String _title = "";	
	
	public static ProblemReportBBSManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	@Override
	public void parseCmd(String command, Player activeChar)
	{
		if (command.equals("_bbsProblemReport"))
		{
			showReportWindow(activeChar);
		}
		else if (command.startsWith("_bbsProblemReport"))
		{
			StringTokenizer st = new StringTokenizer(command, ";");
		    st.nextToken();
		    String secondCommand = st.nextToken();
		    if (secondCommand.startsWith("toDescription"))
	        {			
		    	StringTokenizer st1 = new StringTokenizer(secondCommand);
		    	st1.nextToken();
		    	
		    	String text = "";
		    	
		    	setMajority(st1.nextToken());
		    	
		    	setType(st1.nextToken());
		    	while (st1.hasMoreTokens()) 
		    	{
		    		text = text + st1.nextToken() + " ";
		    	}
		    	if (text == "")
		        {
		          activeChar.sendMessage("Please insert title first.");
		          return;
		        }
		    	setTitle(text);
		    	
		    	showDescriptionWindow(activeChar);
	        }
		    else if (secondCommand.startsWith("submit"))
		    {
		    	String description = secondCommand.substring(9);
		    	if (description == "")
		        {
		    		activeChar.sendMessage("Please insert description first.");
		            return;
		        }
		    	if (description.length() >= 150)
		    	{
		    		activeChar.sendMessage("The current description lenght is " + description.length() + ". Maximum lenght is 800!");
		            return;
		    	}
		    	try
		    	{
		    		String fname = "data/reports/" + getMajority() + "_" + getType() + "_report_" + LogFileNameUtil.sanitize(activeChar.getName()) + ".txt";
		    		File file = new File(fname);
		    		boolean exist = file.createNewFile();
		    		if (!exist)
		            {
		    			activeChar.sendMessage("You have already submit a report, staff member must confirm it first.");
		                return;
		            }
		    		FileWriter fstream = new FileWriter(fname);
		            BufferedWriter out = new BufferedWriter(fstream);
		            out.write("Problem Report");
		            out.newLine();
		            out.write("- - - - - - - - - - - - - - - - - - - -");
		            out.newLine();
		            out.write("Player Details:");
		            out.newLine();
		            out.write("Account: " + activeChar.getAccountName());
		            out.newLine();
		            out.write("Name: " + activeChar.getName());
		            out.newLine();
		            out.write("IP: " + activeChar.getClient().getConnection().getInetAddress().getHostAddress());
		            out.newLine();
		            out.write("- - - - - - - - - - - - - - - - - - - -");
		            out.newLine();
		            out.write("Type of report: " + getType());
		            out.newLine();
		            out.newLine();
		            out.write("Majority of report: " + getMajority());
		            out.newLine();
		            out.newLine();
		            out.write("Title: " + getTitle());
		            out.newLine();
		            out.newLine();
		            out.write("Description: " + description);
		            out.close();
		            
		            separateAndSend(HtmCache.getInstance().getHtm("data/html/CommunityBoard/reporting/completed.htm"), activeChar);	
		            
		            World.getInstance();
		            for (Player gms : World.getAllGMs()) 
		            {
		            	gms.sendMessage("ATTENTION: " + activeChar.getName() + " just submited a report! Please take care of his report by browsing in /data/reports folder.");
		            }
		    	}
		    	catch (Exception e)
		        {
		    		activeChar.sendMessage("Failed to submit report. Try again or contact with staff member. This error should not occur.");
		            e.printStackTrace();
		            return;
		        }
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
		return "reporting/";
	}
	
	private static void showReportWindow(Player activeChar)
	{
		if (Config.ENABLE_BBS_REPORT)
		{
			String content = HtmCache.getInstance().getHtm("data/html/CommunityBoard/reporting/main.htm");
			
		    content = content.replaceAll("%charName%", activeChar.getName());
		    separateAndSend(content, activeChar);			
		}
		else
	    {
			separateAndSend(HtmCache.getInstance().getHtm("data/html/CommunityBoard/top/functionDisabled.htm"), activeChar);
	    }
	}
	
	static void setType(String val)
	{
		_type = val;
	}
	
	static void setMajority(String val)
	{
	    _majority = val;
	}
	  
	static void setTitle(String val)
	{
	    _title = val;
	}
	  
	static String getType()
	{
	    return _type;
	}
	  
    static String getMajority()
	{
	    return _majority;
	}
	  
	static String getTitle()
	{
	    return _title;
	}	
	
	private static void showDescriptionWindow(Player activeChar)
	{
		String content = HtmCache.getInstance().getHtm("data/html/CommunityBoard/reporting/description.htm");
		
	    content = content.replaceAll("%charName%", activeChar.getName());
	    content = content.replaceAll("%type%", getType());
	    content = content.replaceAll("%majority%", getMajority());
	    content = content.replaceAll("%title%", getTitle());
	    separateAndSend(content, activeChar);		
	}
	
	private static class SingletonHolder
	{
		protected static final ProblemReportBBSManager _instance = new ProblemReportBBSManager();
	}
}
