package com.l2jmega.gameserver.communitybbs.Manager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.l2jmega.L2DatabaseFactory;
import com.l2jmega.gameserver.data.cache.HtmCache;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.memo.PlayerMemo;

public class FavoritesBBSManager extends BaseBBSManager
{
    private static final Logger LOG = Logger.getLogger(FavoritesBBSManager.class.getName());

    private static final String SQL_SELECT = "SELECT id, title, bypass, created_at FROM bbs_favorites WHERE char_id = ? ORDER BY created_at DESC";
    private static final String SQL_INSERT_IGNORE = "INSERT IGNORE INTO bbs_favorites (char_id, title, bypass) VALUES (?, ?, ?)";
    private static final String SQL_DELETE = "DELETE FROM bbs_favorites WHERE id = ? AND char_id = ?";

    protected FavoritesBBSManager()
    {
    }

    public static FavoritesBBSManager getInstance()
    {
        return SingletonHolder._instance;
    }

    @Override
    public void parseCmd(String command, Player activeChar)
    {
        if (command.startsWith("_bbsgetfav"))
        {
            String content = HtmCache.getInstance().getHtm(CB_PATH + "top/favorites.htm");
            if (content == null)
            {
                StringBuilder sb = new StringBuilder();
                sb.append("<html><body>");
                sb.append("<center><font color=B09B79>Favorites</font></center>");
                sb.append("<img src=\"L2UI.SquareGray\" width=\"610\" height=\"1\">");
                sb.append("</body></html>");
                content = sb.toString();
            }
            String favList = buildFavoritesHtml(activeChar);
            String finalContent;
            String marker = "<!--FAV_LIST-->";
            int idx = content.indexOf(marker);
            if (idx != -1)
            {
                finalContent = content.substring(0, idx) + favList + content.substring(idx + marker.length());
            }
            else
            {
                finalContent = content + favList;
            }
            send1001(finalContent, activeChar);
            send1002(activeChar);
        }
        else if (command.startsWith("bbs_add_fav"))
        {
            String last = PlayerMemo.getVar(activeChar, "cb_last_bypass");
            if (last == null || last.isEmpty())
            {
                String notice = "<html><body><br><br><center>No last page to add.<br><br>Open a CB page first, then press Add Favorite.</center></body></html>";
                send1001(notice, activeChar);
                send1002(activeChar);
                return;
            }

            String title = titleForBypass(last);
            try (Connection con = L2DatabaseFactory.getInstance().getConnection(); PreparedStatement ps = con.prepareStatement(SQL_INSERT_IGNORE))
            {
                ps.setInt(1, activeChar.getObjectId());
                ps.setString(2, title);
                ps.setString(3, last);
                ps.executeUpdate();
            }
            catch (SQLException e)
            {
                LOG.log(Level.WARNING, "Failed to insert favorite for player " + activeChar.getObjectId(), e);
            }

            // Refresh list view
            parseCmd("_bbsgetfav", activeChar);
        }
        else if (command.startsWith("bbs_del_fav"))
        {
            int id = -1;
            try
            {
                StringTokenizer st = new StringTokenizer(command, " ");
                st.nextToken(); // skip command
                if (st.hasMoreTokens())
                    id = Integer.parseInt(st.nextToken());
            }
            catch (Exception e)
            {
                id = -1;
            }

            if (id != -1)
            {
                try (Connection con = L2DatabaseFactory.getInstance().getConnection(); PreparedStatement ps = con.prepareStatement(SQL_DELETE))
                {
                    ps.setInt(1, id);
                    ps.setInt(2, activeChar.getObjectId());
                    ps.executeUpdate();
                }
                catch (SQLException e)
                {
                    LOG.log(Level.WARNING, "Failed to delete favorite id=" + id + " for player " + activeChar.getObjectId(), e);
                }
            }
            // Refresh list view
            parseCmd("_bbsgetfav", activeChar);
        }
        else
        {
            super.parseCmd(command, activeChar);
        }
    }

    @Override
    protected String getFolder()
    {
        return "top/";
    }

    // Build favorites list HTML using DB data.
    private static String buildFavoritesHtml(Player activeChar)
    {
        StringBuilder sb = new StringBuilder(512);
        try (Connection con = L2DatabaseFactory.getInstance().getConnection(); PreparedStatement ps = con.prepareStatement(SQL_SELECT))
        {
            ps.setInt(1, activeChar.getObjectId());
            try (ResultSet rs = ps.executeQuery())
            {
                boolean hasAny = false;
                sb.append("<br>");
                sb.append("<center>");
                sb.append("<table width=610 bgcolor=000000>");

                while (rs.next())
                {
                    hasAny = true;
                    int id = rs.getInt("id");
                    String title = rs.getString("title");
                    String bypass = rs.getString("bypass");

                    sb.append("<tr>");
                    sb.append("<td width=350><font color=B09B79>" + safe(title) + "</font></td>");
                    sb.append("<td width=130><button value=\"Open\" action=\"bypass " + bypass + "\" width=70 height=19 back=\"anim70.anim_over\" fore=\"anim70.Anim\"></td>");
                    sb.append("<td width=130><button value=\"Remove\" action=\"bypass bbs_del_fav " + id + "\" width=70 height=19 back=\"anim70.anim_over\" fore=\"anim70.Anim\"></td>");
                    sb.append("</tr>");
                }

                if (!hasAny)
                {
                    sb.append("<tr><td align=center><font color=B09B79>No favorites saved.<br1>Open a page and press Add Favorite.</font></td></tr>");
                }

                sb.append("</table>");
                sb.append("</center>");
            }
        }
        catch (SQLException e)
        {
            LOG.log(Level.WARNING, "Failed to load favorites for player " + activeChar.getObjectId(), e);
            sb.setLength(0);
            sb.append("<br><center><font color=B09B79>Error loading favorites.</font></center>");
        }
        return sb.toString();
    }

    private static String safe(String s)
    {
        if (s == null)
            return "";
        return s.replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String titleForBypass(String bypass)
    {
        if (bypass == null || bypass.isEmpty())
            return "Favorite";
        if (bypass.startsWith("_bbshome")) return "Home";
        if (bypass.startsWith("_bbsloc")) return "Region";
        if (bypass.startsWith("_bbsmemo")) return "Memo";
        if (bypass.startsWith("_bbsmail") || bypass.equals("_maillist_0_1_0_")) return "Mail";
        if (bypass.startsWith("_friend")) return "Friends";
        if (bypass.startsWith("_bbsclan")) return "Clan";
        if (bypass.startsWith("_bbstopics")) return "Topics";
        if (bypass.startsWith("_bbsposts")) return "Posts";
        if (bypass.startsWith("_bbsstats")) return "Stats";
        if (bypass.startsWith("_bbsannouncements")) return "Announcements";
        if (bypass.startsWith("_bbsdonation")) return "Donation";
        if (bypass.startsWith("_bbsProblemReport")) return "Problem Report";
        if (bypass.equals("_bbsGrandBoss")) return "Grand Boss";
        if (bypass.startsWith("_bbsRepair")) return "Repair";
        if (bypass.startsWith("_bbsInfo")) return "Info";
        if (bypass.startsWith("_bbsshop")) return "Shop";
        return bypass;
    }

    private static class SingletonHolder
    {
        protected static final FavoritesBBSManager _instance = new FavoritesBBSManager();
    }
}