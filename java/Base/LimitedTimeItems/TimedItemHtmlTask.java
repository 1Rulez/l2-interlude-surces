package Base.LimitedTimeItems;

import com.l2jmega.Config;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.item.instance.ItemInstance;
import com.l2jmega.gameserver.network.serverpackets.NpcHtmlMessage;
import com.l2jmega.commons.concurrent.ThreadPool;

import java.util.concurrent.ScheduledFuture;
import java.util.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class TimedItemHtmlTask implements Runnable
{
    private static final Logger _log = Logger.getLogger(TimedItemHtmlTask.class.getName());
    private static ScheduledFuture<?> _task;
    private static final long INTERVAL = Config.TIMED_ITEM_HTML_INTERVAL_MINUTES * 60 * 1000L;

    public static void startTask()
    {
        if (!Config.ENABLE_TIMED_ITEMS)
        {
            _log.info("TimedItemHtmlTask: Disabled");
            return;
        }

        if (_task != null && !_task.isCancelled())
            return;

        _task = ThreadPool.scheduleAtFixedRate(new TimedItemHtmlTask(), INTERVAL, INTERVAL);
        _log.info("TimedItemHtmlTask: Started (every " + Config.TIMED_ITEM_HTML_INTERVAL_MINUTES + " minutes)");
    }

    private static String loadHtmlTemplate()
    {
        try
        {
            return new String(Files.readAllBytes(Paths.get("data/html/mods/timedItem/timeditem.htm")));
        }
        catch (Exception e)
        {
            _log.warning("TimedItemHtmlTask: Failed to load HTML template.");
            return "";
        }
    }

    @Override
    public void run()
    {
        try
        {
            TimedItemManager manager = TimedItemManager.getInstance();
            String template = loadHtmlTemplate();

            // 🔥 Improvement #1: Stop if template fails
            if (template.isEmpty())
            {
                _log.warning("TimedItemHtmlTask: HTML template empty. Task stopped.");
                return;
            }

            int playersWithTimedItems = 0;

            for (Player player : World.getInstance().getPlayers())
            {
                List<String> lines = new ArrayList<>();

                for (ItemInstance item : player.getInventory().getItems())
                {
                    if (Config.LIST_TIMED_ITEMS.contains(item.getItemId()) && manager.isActive(item))
                    {
                        long remaining = manager.getRemainingTime(item);
                        long hours = remaining / 3600_000;
                        long minutes = (remaining / 60_000) % 60;
                        long seconds = (remaining / 1000) % 60;

                        lines.add(
                            "<br>Item: <b>" + item.getItemName() + "</b><br>" +
                            "Remaining: <b>" +
                            String.format("%02d:%02d:%02d", hours, minutes, seconds) +
                            "</b><br>"
                        );
                    }
                }

                // If player has no active timed items, skip sending the HTML
                if (lines.isEmpty())
                    continue;

                // Count players with timed items
                playersWithTimedItems++;

                // Log only if player has timed items
                _log.info("TimedItemHtmlTask: Player " + player.getName() + " has active timed items.");

                String itemListHtml = String.join("", lines);

                String html = template
                        .replace("%itemName%", "Multiple Items")
                        .replace("%playerName%", player.getName())
                        .replace("%hours%", "00")
                        .replace("%minutes%", "00")
                        .replace("%seconds%", "00")
                        .replace("%itemList%", itemListHtml);

                NpcHtmlMessage msg = new NpcHtmlMessage(player.getObjectId());
                msg.setHtml(html);
                player.sendPacket(msg);
            }

            // 🔥 Improvement #2: Summary log once per run
            if (playersWithTimedItems > 0)
            {
                _log.info("TimedItemHtmlTask: Active players with timed items = " + playersWithTimedItems);
            }
        }
        catch (Exception e)
        {
            _log.warning("TimedItemHtmlTask: Error while sending timed item HTML.");
            e.printStackTrace();
        }
    }
}
