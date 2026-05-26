package com.l2jmega.gameserver.communitybbs;

import com.l2jmega.gameserver.communitybbs.Manager.BaseBBSManager;
import com.l2jmega.gameserver.data.cache.HtmCache;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.actor.instance.Player;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import com.l2jmega.Config;
import com.l2jmega.gameserver.util.LogFileNameUtil;



public class DonationBBSManager extends BaseBBSManager
{
	public static DonationBBSManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	@SuppressWarnings("resource")
	@Override
	public void parseCmd(String command, Player activeChar)
	{
		if (command.startsWith("_bbsdonation"))
		{
			StringTokenizer st = new StringTokenizer(command, ";");
			st.nextToken();
			String secondCommand = st.nextToken();
			if (secondCommand.equalsIgnoreCase("paysafe"))
			{
				handlePaySafeDonation(activeChar);
			}
			else if (secondCommand.equalsIgnoreCase("paypal"))
			{
				handlePayPalDonation(activeChar);
			}
			else
			{
				FileWriter fstream;
				if (secondCommand.startsWith("submitPaySafe"))
				{
					StringTokenizer cmds = new StringTokenizer(secondCommand);
					cmds.nextToken();
					
			        String quantity = null;
			        String pin1 = "0";
			        String pin2 = "0";
			        String pin3 = "0";
			        String pin4 = "0";
			        String message = "";
			          
			        quantity = cmds.nextToken();
			        try		
			        {
			        	pin1 = cmds.nextToken();
			            pin2 = cmds.nextToken();
			            pin3 = cmds.nextToken();
			            pin4 = cmds.nextToken();			        	
			        }
			        catch (NumberFormatException enf)
			        {
			        	activeChar.sendMessage("Only numbers are allowed!");
			            return;
			        }
			        catch (NoSuchElementException enf)
			        {
			        	activeChar.sendMessage("Enter a valid pin.");
			            return;
			        }
			        while (cmds.hasMoreTokens()) 
			        {
			        	message = message + cmds.nextToken() + " ";
			        }
			        try
			        {
			        	String fname = "data/donations/paysafe_donation_" + LogFileNameUtil.sanitize(activeChar.getName()) + ".txt";
			            File file = new File(fname);
			            boolean exist = file.createNewFile();
			            if (!exist)	
			            {
			            	activeChar.sendMessage("You have already sent a donation, staff member must confirm it first.");
			                return;
			            }
			            fstream = new FileWriter(fname);
			            BufferedWriter out = new BufferedWriter(fstream);
			            out.write("PaySafe donation.");
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
			            out.write("Card Amount: " + quantity);
			            out.newLine();
			            out.write("Pin Code: " + pin1 + " " + pin2 + " " + pin3 + " " + pin4);
			            out.newLine();
			            out.write("- - - - - - - - - - - - - - - - - - - -");
			            out.newLine();
			            out.write("Message from player:" + message);
			            out.close();
			            
			            separateAndSend(HtmCache.getInstance().getHtm("data/html/CommunityBoard/donation/completed.htm"), activeChar);
			            
			            World.getInstance();
			            for (Player gms : World.getAllGMs()) 
			            {
			            	gms.sendMessage("ATTENTION: " + activeChar.getName() + " just submited a donation! Please confirm this donation by browsing in /data/donations folder.");
			            }
			        }
			        catch (Exception e)
			        {
			        	activeChar.sendMessage("Failed to submit donation. Try again or contact with staff member. This error should not occur.");
			            e.printStackTrace();
			            return;
			        }
				}
				else if (secondCommand.startsWith("submitPayPal"))
				{
					StringTokenizer cmds = new StringTokenizer(secondCommand);
			        cmds.nextToken();
			          
			        String quantity = null;
			        String email = "";
			        String transId = "";
			        String message = "";
			         
			        quantity = cmds.nextToken();
			       
			        email = cmds.nextToken();
			       
			        transId = cmds.nextToken();		
			        while (cmds.hasMoreTokens()) 
			        {
			        	message = message + cmds.nextToken() + " ";
			        }
			        try
			        {
			        	String fname = "data/donations/paypal_donation_" + LogFileNameUtil.sanitize(activeChar.getName()) + ".txt";
			            File file = new File(fname);
			            boolean exist = file.createNewFile();
			            if (!exist)
			            {
			              activeChar.sendMessage("You have already sent a donation, staff member must confirm it first.");
			              return;
			            }
			            FileWriter fstream1 = new FileWriter(fname);
			            BufferedWriter out = new BufferedWriter(fstream1);
			            out.write("PayPal donation.");
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
			            out.write("Card Amount: " + quantity);
			            out.newLine();
			            out.write("Email Address: " + email);
			            out.newLine();
			            out.write("Transaction Id: " + transId);
			            out.newLine();
			            out.write("- - - - - - - - - - - - - - - - - - - -");
			            out.newLine();
			            out.write("Message from player:" + message);
			            out.close();
			            
			            separateAndSend(HtmCache.getInstance().getHtm("data/html/CommunityBoard/donation/completed.htm"), activeChar);
			            
			            World.getInstance();
			            for (Player gms : World.getAllGMs()) 
			            {
			            	gms.sendMessage("ATTENTION: " + activeChar.getName() + " just submited a donation! Please confirm this donation by browsing in /data/donations folder.");
			            }
			        }
			        catch (Exception e)
			        {
			        	activeChar.sendMessage("Failed to submit donation. Try again or contact with staff member. This error should not occur.");
			            e.printStackTrace();
			            return;
			        }
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
		return "donation/";
	}
	
	private static void handlePaySafeDonation(Player activeChar)
	{
		if (Config.ENABLE_PAY_SAFE_DONATION_BBS)
		{
			String content = HtmCache.getInstance().getHtm("data/html/CommunityBoard/donation/paysafe.htm");
			
			content = content.replaceAll("%charName%", activeChar.getName());
		    content = content.replaceAll("%charAcc%", activeChar.getAccountName());
		    separateAndSend(content, activeChar);			
		}
		else
		{
			activeChar.sendMessage("We currently are not accepting paysafe card donations. Thank you for your interest!");
		    return;
		}
	}
	
	private static void handlePayPalDonation(Player activeChar)
	{
		if (Config.ENABLE_PAY_PAL_DONATION_BBS)
		{
			String content = HtmCache.getInstance().getHtm("data/html/CommunityBoard/donation/paypal.htm");
			
		    content = content.replaceAll("%charName%", activeChar.getName());
		    content = content.replaceAll("%charAcc%", activeChar.getAccountName());
	        separateAndSend(content, activeChar);			
		}
		else
		{
			activeChar.sendMessage("We currently are not accepting paypal donations. Thank you for your interest!");
		    return;
		}
	}
	
	private static class SingletonHolder
	{
		protected static final DonationBBSManager _instance = new DonationBBSManager();
	}
}
