package com.l2jmega.gameserver.handler.voicedcommandhandlers;

import com.l2jmega.gameserver.handler.IUserCommandHandler;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.network.serverpackets.NpcHtmlMessage;

public class Pin implements IUserCommandHandler
{
	private static final int[] COMMAND_IDS =
	{
	115
	};

	@Override
	public boolean useUserCommand(int id, Player activeChar)
	{
		if(activeChar.getPincheck())
		{
			      StringBuilder sb = new StringBuilder();
			      NpcHtmlMessage html = new NpcHtmlMessage(1);
			      
			      sb.append("<html><head><title>Character Sequrity Pin Panel</title></head>");
			      sb.append("<body>");      
			      sb.append("<center>");
			      sb.append("<table width=\"250\" cellpadding=\"5\" bgcolor=\"000000\">");
			      sb.append("<tr>");
			      sb.append("<td width=\"45\" valign=\"top\" align=\"center\"><img src=\"L2ui_ch3.menubutton4\" width=\"38\" height=\"38\"></td>");
			      sb.append("<td valign=\"top\"><font color=\"FF6600\">PIN Panel</font>");
			      sb.append("<br1><font color=\"00FF00\">"+activeChar.getName()+"</font>, use this interface to enable PIN security.</td></tr></table></center>");
			      sb.append("<center>");
			      sb.append("<img src=\"l2ui_ch3.herotower_deco\" width=256 height=32 align=center><br>");
			      sb.append("</center>");
			      sb.append("<table width=\"350\" cellpadding=\"5\" bgcolor=\"000000\">");
			      sb.append("<tr>");
			      sb.append("<td width=\"45\" valign=\"top\" align=\"center\"><img src=\"Icon.etc_old_key_i02\" width=\"32\" height=\"32\"></td>");
			      sb.append("<td valign=\"top\">Please enter your PIN:<edit var=\"pin\" width=80 height=15>");
			      sb.append("<br>Must contain 4 digits</td>");
			      sb.append("</tr>");
			      sb.append("</table>");
			      sb.append("<br>");
			      sb.append("<center>");
			      sb.append("<button value=\"Submit\" action=\"bypass -h submitpin $pin\" width=75 height=21 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\">");
			      sb.append("</center>");
			      sb.append("<center>");
			      sb.append("<img src=\"l2ui_ch3.herotower_deco\" width=256 height=32 align=center>");
			      sb.append("<font color=\"FF6600\">Elysian Sequrity</font>"); 
			      sb.append("</center>");
			      sb.append("</body></html>");
			               
			      html.setHtml(sb.toString());
			      activeChar.sendPacket(html);
		}
		else
			activeChar.sendMessage("You have already submitted a Pin code");
		
		return true;
	}

	@Override
	public int[] getUserCommandList()
	{
		return COMMAND_IDS;
	}
}