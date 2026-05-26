package com.l2jmega.gameserver.communitybbs;

import com.l2jmega.gameserver.communitybbs.Manager.BaseBBSManager;
import com.l2jmega.gameserver.data.cache.HtmCache;
import com.l2jmega.gameserver.model.actor.instance.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.l2jmega.L2DatabaseFactory;




public class AnnouncementsBBSManager extends BaseBBSManager
{
	private static final Logger _log = Logger.getLogger(AnnouncementsBBSManager.class.getName());
	
	static String _title;
	
	public static AnnouncementsBBSManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	@Override
	public void parseCmd(String command, Player activeChar)
	{
		if (command.startsWith("_bbsannouncements"))
		{
			StringTokenizer st = new StringTokenizer(command, ";");
			st.nextToken();
			String secondCommand = st.nextToken();
			
			if (secondCommand.equalsIgnoreCase("show"))
			{
				showList(activeChar);
			}		
			else if (secondCommand.equalsIgnoreCase("write"))
			{
				separateAndSend(HtmCache.getInstance().getHtm("data/html/CommunityBoard/announcements/adminWrite.htm"), activeChar);
			}
			else if (secondCommand.startsWith("postTitle"))
			{
				String title = secondCommand.substring(9);
				if (title == "")
				{
					activeChar.sendMessage("Please insert title first.");
					return;
				}
				if (title.length() >= 150)
				{
					activeChar.sendMessage("The current title lenght is " + title.length() + ". Maximum lenght is 150!");
					return;
				}
				setTitle(title);
				
				String content = HtmCache.getInstance().getHtm("data/html/CommunityBoard/announcements/adminWrite2.htm");
				content = content.replaceAll("%title%", getTitle());
				separateAndSend(content, activeChar);
			}
			else if (secondCommand.startsWith("postText"))
			{
				String content = secondCommand.substring(9);
				if (content == "")
				{
					activeChar.sendMessage("Please insert your post content. This message cannot be empty.");
					return;
				}
				if (content.length() >= 1000)
				{
					activeChar.sendMessage("The current content lenght is " + content.length() + ". Maximum lenght is 1000!");
					return;
				}
				insertAnnouncement(getTitle(), content, activeChar);
			}
			else if (secondCommand.startsWith("read"))
			{
				StringTokenizer st2 = new StringTokenizer(secondCommand);
				st2.nextToken();
				
				String command_Id = st2.nextToken();
				int announcement_id = Integer.valueOf(command_Id).intValue();
				showAnnouncement(activeChar, announcement_id);
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
		return "announcements/";
	}
	
	static String getTitle()
	{
		return _title;
	}
	
	static void setTitle(String text)
	{
		_title = text;
	}
	
	private static void insertAnnouncement(String title, String content, Player activeChar)
	{
		DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
		Date date = new Date();
		try
		{
			Connection con = L2DatabaseFactory.getInstance().getConnection();Throwable localThrowable3 = null;
			try
			{
				PreparedStatement statement = con.prepareStatement("INSERT INTO bbs_announcements (announce_id,announce_title,announce_text,announce_date,author) VALUES (?,?,?,?,?)");
				
				statement.setInt(1, getAnnouncementId());
		        statement.setString(2, title);
		        statement.setString(3, content);
		        statement.setString(4, dateFormat.format(date));
		        statement.setString(5, activeChar.getName());
		        statement.execute();
		        statement.close();				
			}
			catch (Throwable localThrowable1)
			{
				localThrowable3 = localThrowable1;throw localThrowable1;
			}
			finally
			{
				if (con != null) 
				{
					if (localThrowable3 != null) 
					{
						try
						{
							con.close();
						}
						catch (Throwable localThrowable2)
						{
							localThrowable3.addSuppressed(localThrowable2);
						}
					}
					else
					{
						con.close();
					}
				}
			}
		}
		catch (SQLException e)
		{
			_log.log(Level.WARNING, "Failed to insert new announcement in database " + e.getMessage(), e);
		}
		AnnouncementsUpdateManager.updateList();
		showList(activeChar);
	}
	
	private static int getAnnouncementId()
	{
		int id = 0;
		try
		{
			Connection con = L2DatabaseFactory.getInstance().getConnection();Throwable localThrowable3 = null;
			try
			{
				PreparedStatement statement = con.prepareStatement("SELECT announce_id FROM bbs_announcements ORDER BY announce_id DESC LIMIT 1");
				ResultSet rset = statement.executeQuery();
				while (rset.next()) 
				{
					id = rset.getInt("announce_id");					
				}
				rset.close();
				statement.close();
			}
			catch (Throwable localThrowable1)
			{
				localThrowable3 = localThrowable1;throw localThrowable1;
			}
			finally
			{
				if (con != null) 
				{
					if (localThrowable3 != null) 
					{
						try
						{
							con.close();
						}
						catch (Throwable localThrowable2)
						{
							localThrowable3.addSuppressed(localThrowable2);
						}
					}
					else
					{
						con.close();
					}
				}
			}
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Failed to load announcementId " + e.getMessage(), e);
		}
		return id + 1;
	}
	
	private static void showList(Player activeChar)
	{
		String content = HtmCache.getInstance().getHtm("data/html/CommunityBoard/announcements/main.htm");
		if (activeChar.isGM()) 
		{
			content = content.replaceAll("%adminMenu%", showAdminMenu());
		}
		else
		{
			content = content.replaceAll("%adminMenu%", "");
		}
		content = content.replaceAll("%showList%", AnnouncementsUpdateManager.showList());
		separateAndSend(content, activeChar);
	}
	
	private static void showAnnouncement(Player activeChar, int announcement_id)
	{
		String content = HtmCache.getInstance().getHtm("data/html/CommunityBoard/announcements/postTemplate.htm");
			String title = "";String text = "";String date = "";String author = "";
			try
			{
				Connection con = L2DatabaseFactory.getInstance().getConnection();Throwable localThrowable3 = null;
				try
				{
					PreparedStatement st = con.prepareStatement("SELECT announce_title,announce_text,announce_date,author FROM bbs_announcements WHERE announce_id=?;");
					st.setInt(1, announcement_id);
					ResultSet rset = st.executeQuery();
					while (rset.next())
					{
						title = rset.getString("announce_title");
				        text = rset.getString("announce_text");
				        date = rset.getString("announce_date");
				        author = rset.getString("author");
					}
					rset.close();
					st.close();
				}
				catch (Throwable localThrowable1)
				{
					localThrowable3 = localThrowable1;throw localThrowable1;
				}
				finally
				{
					if (con != null) 
					{
						if (localThrowable3 != null) 
						{
							try
							{
								con.close();
							}
							catch (Throwable localThrowable2)
							{
								localThrowable3.addSuppressed(localThrowable2);
							}
						}
						else 
						{
							con.close();
						}
					}
				}
			}			
			catch (SQLException e)
			{
				_log.log(Level.WARNING, "Failed to load post with id " + announcement_id + " and error trace: " + e.getMessage(), e);
			}
			content = content.replaceAll("%title%", title);
		    content = content.replaceAll("%text%", text);
		    content = content.replaceAll("%date%", date);
		    content = content.replaceAll("%author%", author);
		    separateAndSend(content, activeChar);			
	}
	
	static String showAdminMenu()
	{
		StringBuilder tb = new StringBuilder();
		
		tb.append("<img src=\"l2ui.squaregray\" width=\"610\" height=\"1\"/><table width=610><tr>");
	    tb.append("<td width=110 height=18 align=center><font color=FF0000>Admin Only:</font></td>");
	    tb.append("<td width=400 height=18 align=left>The list will show only latest 5 announcements.</td>");
	    tb.append("<td width=100 height=18 align=center><button value=\"Write\" action=\"bypass _bbsannouncements;write\" width=75 height=21 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"></td>");
	    tb.append("</tr></table><img src=\"l2ui.squaregray\" width=\"610\" height=\"1\"><br><br>");
	    
		return tb.toString();
	}
	
	private static class SingletonHolder
	{
		protected static final AnnouncementsBBSManager _instance = new AnnouncementsBBSManager();
	}
}