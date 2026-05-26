package Base.LimitedTimeItems;

import com.l2jmega.Config;
import com.l2jmega.gameserver.model.World;
import phantom.FakePlayerConfig;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.item.instance.ItemInstance;
import com.l2jmega.gameserver.network.serverpackets.ItemList;
import com.l2jmega.L2DatabaseFactory;
import com.l2jmega.commons.concurrent.ThreadPool;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Logger;

/**
 * Periodic task to check and remove expired timed items
 * and send remaining time messages to online players.
 */
public class TimedItemTask implements Runnable
{
    private static final Logger _log = Logger.getLogger(TimedItemTask.class.getName());
    private static ScheduledFuture<?> _task;

    public static void startTask()
    {
        if (!Config.ENABLE_TIMED_ITEMS)
        {
            _log.info("TimedItemTask: Disabled");
            return;
        }

        if (_task != null && !_task.isCancelled())
            return;

        // ⚡ Run every 60 seconds instead of 1 second
        _task = ThreadPool.scheduleAtFixedRate(new TimedItemTask(), 60000, 60000);
        _log.info("TimedItemTask: Started (interval 60s)");
    }

    @Override
    public void run()
    {
        try
        {
            TimedItemManager manager = TimedItemManager.getInstance();
            long now = System.currentTimeMillis();

            // Avoid ConcurrentModificationException by copying entry set
            Map<Integer, TimedItemManager.TimedInfo> itemsCopy = Map.copyOf(manager.getAllRegisteredItems());

            int activePlayers = 0;
            int activeItems = itemsCopy.size();

            // Count players with active timed items
            for (TimedItemManager.TimedInfo info : itemsCopy.values())
            {
                Player player = World.getInstance().getPlayer(info.charId);
                if (player != null)
                    activePlayers++;
            }

         // Log summary only when there are active items
            if (activePlayers > 0 && activeItems > 0)
            {
                _log.info("TimedItemTask: Active players with timed items = " + activePlayers +
                          " | Active timed items total = " + activeItems);
            }

            for (TimedItemManager.TimedInfo info : itemsCopy.values())
            {
                long remaining = info.endTime - now;
                Player player = World.getInstance().getPlayer(info.charId);
                ItemInstance item = null;

                if (player != null)
                    item = player.getInventory().getItemByObjectId(info.itemObjId);

                if (remaining <= 0)
                {
                    // Expired
                    if (player != null && item != null)
                    {
                        if (item.isEquipped())
                            player.getInventory().unEquipItemInSlot(item.getLocationSlot());

                        player.getInventory().destroyItem("TimedItemExpired", item, player, null);
                        player.getInventory().refreshWeight();
                        player.broadcastUserInfo();
                        player.sendPacket(new ItemList(player, false));
                        player.sendMessage("Your timed item '" + item.getItem().getName() + "' has expired and was removed.");
                    }
                    else
                    {
                        // Offline: remove from DB
                        try (Connection con = L2DatabaseFactory.getInstance().getConnection();
                             PreparedStatement ps = con.prepareStatement(
                                     "DELETE FROM character_timed_items WHERE itemObjId=?"))
                        {
                            ps.setInt(1, info.itemObjId);
                            ps.executeUpdate();
                        }
                        catch (Exception e)
                        {
                            _log.warning("TimedItemTask: Failed to remove offline timed item objectId=" + info.itemObjId);
                        }
                    }

                    manager.unregisterItemByObjectId(info.itemObjId);
                }
                else
                {
                    // Online: send remaining time message every minute
                    if (player != null && item != null)
                    {
                        manager.sendRemainingTimeMessage(item);

                        // Detailed log once every minute (skip for fake players if configured)
                        if (!(player.isPhantom() && FakePlayerConfig.HIDE_TIMED_ITEM_TASK_LOG_FOR_FAKES))
                        {
                            long hrs = remaining / 3600000;
                            long mins = (remaining / 60000) % 60;
                            long secs = (remaining / 1000) % 60;

                            _log.info("TimedItemTask: Player=" + player.getName() +
                                      " | Item=" + item.getItemName() +
                                      " | Remaining=" + String.format("%02d:%02d:%02d", hrs, mins, secs));
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            _log.warning("TimedItemTask: Error while updating timed items.");
            e.printStackTrace();
        }
    }
}
