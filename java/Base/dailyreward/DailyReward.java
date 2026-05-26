package Base.dailyreward;

import com.l2jmega.gameserver.communitybbs.Manager.BaseBBSManager;
import com.l2jmega.gameserver.data.IconTable;
import com.l2jmega.gameserver.data.cache.HtmCache;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.Config;

import java.util.Map;

public class DailyReward extends BaseBBSManager
{
    private static final DailyReward _instance = new DailyReward();
    public static DailyReward getInstance() { return _instance; }

    @Override
    public void parseCmd(String command, Player player)
    {
        showPage(command, player);
    }

    private static void showPage(String command, Player player)
    {
        // Block offline + store mode
        if (player.isInStoreMode())
        {
            separateAndSend("<font color=FF0000>Daily rewards are not available in offline or store mode.</font>", player);
            return;
        }

        if (player.isPhantom())
            return;

        DailyRewardManager mgr = DailyRewardManager.getInstance();

        int currentDay = mgr.getCurrentDay(player);
        int totalDays = mgr.getTotalDays();

        long onlineSeconds = mgr.getOnlineSeconds(player);
        double onlineHours = onlineSeconds / 3600.0;
        double requiredHours = Config.DailyRewardOnlineMinutes / 60.0;

        String html = HtmCache.getInstance().getHtm("data/html/CommunityBoard/DailyReward/index.htm");
        if (html == null)
        {
            separateAndSend("Missing HTML", player);
            return;
        }

        // ================= DAYS VIEW =================
        StringBuilder rewards = new StringBuilder();
        rewards.append("<table width=600 align=center>");

        int col = 0;
        for (Map.Entry<Integer, Map<Integer, Integer>> dayEntry : mgr.getAllRewards().entrySet())
        {
            if (col == 0)
                rewards.append("<tr>");

            int day = dayEntry.getKey();
            Map<Integer, Integer> items = dayEntry.getValue();

            String stateColor =
                day < currentDay ? "888888" :
                day == currentDay ? "00FF00" :
                "FF9900";

            String stateText =
                day < currentDay ? "CLAIMED" :
                day == currentDay ? "TODAY" :
                "LOCKED";

            rewards.append("<td width=200 align=center>");
            rewards.append("<table width=190 bgcolor=000000>");
            rewards.append("<tr><td align=center><font color=")
                   .append(stateColor)
                   .append(">Day ").append(day)
                   .append(" (").append(stateText).append(")</font></td></tr>");

            for (Map.Entry<Integer, Integer> e : items.entrySet())
            {
                String icon = IconTable.getIcon(e.getKey());
                rewards.append("<tr><td align=center>")
                       .append("<img src=\"").append(icon).append("\" width=32 height=32><br>")
                       .append("x").append(e.getValue())
                       .append("</td></tr>");
            }

            rewards.append("</table></td>");

            col++;
            if (col == 3)
            {
                rewards.append("</tr>");
                col = 0;
            }
        }

        if (col != 0)
            rewards.append("</tr>");
        rewards.append("</table>");

        // ================= ONLINE INFO =================
        String onlineInfo;
        if (mgr.hasEnoughOnline(player))
        {
            onlineInfo = "<font color=00FF00>You can claim today's reward.</font>";
        }
        else
        {
            onlineInfo = "<font color=888888>You must stay online " +
                    String.format("%.1f", requiredHours) +
                    " hours to unlock today's reward.</font>";
        }

        // ================= CLAIM LOGIC (FIXED ORDER) =================
        String claim;

        // MULTI WINDOW FIRST (HARD BLOCK)
        if (Config.DAILY_REWARD_MULTIWINDOW_PROTECT && mgr.isAnotherCharOnlineAndClaimed(player))
        {
            claim = "<font color=FF0000>Another character of this account already claimed today.</font>";
        }
        // HWID
        else if (Config.DAILY_REWARD_HWID_PROTECT && mgr.isHwidBlocked(player))
        {
            claim = "<font color=FF0000>Only one reward per computer is allowed.</font>";
        }
        // IP
        else if (Config.DAILY_REWARD_IP_PROTECT && mgr.isIpBlocked(player))
        {
            claim = "<font color=FF0000>Only one reward per IP is allowed.</font>";
        }
        // CLAIM
        else if (command.startsWith("_bbsdailyreward_claim"))
        {
            claim = mgr.claim(player)
                ? "<font color=00FF00>Reward claimed!</font>"
                : "<font color=FF0000>You cannot claim yet.</font>";
        }
        // SHOW BUTTON
        else if (mgr.canClaim(player) && mgr.hasEnoughOnline(player))
        {
            claim = "<button value=\"Claim Reward\" action=\"bypass -h _bbsdailyreward_claim\" width=140 height=30>";
        }
        // ONLINE TIME
        else
        {
            claim = "<font color=FF9900>Online time: " +
                    String.format("%.2f", onlineHours) +
                    " / " + String.format("%.2f", requiredHours) + " hours</font>";
        }

        // ================= HTML REPLACE =================
        html = html.replace("%currentDay%", String.valueOf(currentDay));
        html = html.replace("%totalDays%", String.valueOf(totalDays));
        html = html.replace("%onlineTime%", String.format("%.2f", onlineHours));
        html = html.replace("%onlineInfo%", onlineInfo);
        html = html.replace("%rewards%", rewards.toString());
        html = html.replace("%claimButton%", claim);

        separateAndSend(html, player);
    }
}
