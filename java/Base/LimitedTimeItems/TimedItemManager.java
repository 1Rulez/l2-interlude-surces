package Base.LimitedTimeItems;

import com.l2jmega.Config;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.item.instance.ItemInstance;
import com.l2jmega.L2DatabaseFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TimedItemManager
{
    private static final Logger _log = Logger.getLogger(TimedItemManager.class.getName());

    private static TimedItemManager _instance;
    private final Map<Integer, TimedInfo> _timedItems = new ConcurrentHashMap<>();

    public static TimedItemManager getInstance()
    {
        if (_instance == null)
            _instance = new TimedItemManager();
        return _instance;
    }

    public static class TimedInfo
    {
        public int charId;
        public int itemObjId;
        public long endTime;

        public TimedInfo(int charId, int itemObjId, long endTime)
        {
            this.charId = charId;
            this.itemObjId = itemObjId;
            this.endTime = endTime;
        }
    }

    public Map<Integer, TimedInfo> getAllRegisteredItems()
    {
        return _timedItems;
    }

    public boolean isActive(ItemInstance item)
    {
        return _timedItems.containsKey(item.getObjectId());
    }

    public long getRemainingTime(ItemInstance item)
    {
        TimedInfo info = _timedItems.get(item.getObjectId());
        if (info == null)
            return 0;

        return Math.max(0, info.endTime - System.currentTimeMillis());
    }

    public String getFormattedRemainingTime(ItemInstance item)
    {
        long remaining = getRemainingTime(item);

        long hours = remaining / 3600000;
        long minutes = (remaining / 60000) % 60;
        long seconds = (remaining / 1000) % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    
 // ------------------- IMPORTANT FIX -------------------
    // This method is required by your PcInventory
    public void setTimed(ItemInstance item)
    {
        // Register and save to DB
        registerItem(item);
    }
    // -----------------------------------------------------
    
    
    public void registerItem(ItemInstance item)
    {
        long durationMs = getItemDuration(item.getItemId());

        long endTime = System.currentTimeMillis() + durationMs;

        TimedInfo info = new TimedInfo(item.getOwnerId(), item.getObjectId(), endTime);

        _timedItems.put(item.getObjectId(), info);
        saveTimedItem(item, endTime);
    }

    public void registerItem(ItemInstance item, long customDurationMs)
    {
        long endTime = System.currentTimeMillis() + customDurationMs;

        TimedInfo info = new TimedInfo(item.getOwnerId(), item.getObjectId(), endTime);

        _timedItems.put(item.getObjectId(), info);
        saveTimedItem(item, endTime);
    }

    public void unregisterItemByObjectId(int objectId)
    {
        _timedItems.remove(objectId);

        try (Connection con = L2DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM character_timed_items WHERE itemObjId=?"))
        {
            ps.setInt(1, objectId);
            ps.executeUpdate();
        }
        catch (Exception e)
        {
            _log.warning("TimedItemManager: Failed to delete timed item from DB: " + e.getMessage());
        }
    }
    public void registerExistingInventory(Player player)
    {
        if (!Config.ENABLE_TIMED_ITEMS)
            return;

        for (ItemInstance item : player.getInventory().getItems())
        {
            if (!Config.LIST_TIMED_ITEMS.contains(item.getItemId()))
                continue;

            if (_timedItems.containsKey(item.getObjectId()))
                continue;

            // If item exists but not timed → register it
            registerItem(item);
        }
    }

    private static long getItemDuration(int itemId)
    {
        if (Config.TIMED_ITEM_CUSTOM_TIME.containsKey(itemId))
            return Config.TIMED_ITEM_CUSTOM_TIME.get(itemId) * 3600_000L;

        return Config.TIMED_ITEM_TIME * 3600_000L;
    }

    private static void saveTimedItem(ItemInstance item, long endTime)
    {
        try (Connection con = L2DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "REPLACE INTO character_timed_items (charId, itemObjId, expireTime) VALUES (?, ?, ?)"))
        {
            ps.setInt(1, item.getOwnerId());
            ps.setInt(2, item.getObjectId());
            ps.setLong(3, endTime);
            ps.executeUpdate();
        }
        catch (Exception e)
        {
            _log.log(Level.WARNING, "TimedItemManager: Failed to save timed item", e);
        }
    }

    public void loadTimedItemsForPlayer(Player player)
    {
        if (!Config.ENABLE_TIMED_ITEMS)
            return;

        try (Connection con = L2DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT itemObjId, expireTime FROM character_timed_items WHERE charId=?"))
        {
            ps.setInt(1, player.getObjectId());
            try (ResultSet rs = ps.executeQuery())
            {
                while (rs.next())
                {
                    int itemObjId = rs.getInt("itemObjId");
                    long endTime = rs.getLong("expireTime");

                    _timedItems.put(itemObjId, new TimedInfo(player.getObjectId(), itemObjId, endTime));
                }
            }
        }
        catch (Exception e)
        {
            _log.warning("TimedItemManager: Failed to load timed items for player: " + e.getMessage());
        }
    }

    public void sendRemainingTimeMessage(ItemInstance item)
    {
        Player player = item.getActingPlayer();
        if (player == null)
            return;

        long remaining = getRemainingTime(item);
        long hours = remaining / 3600000;
        long minutes = (remaining / 60000) % 60;
        long seconds = (remaining / 1000) % 60;

        player.sendMessage("Timed item '" + item.getItemName() + "' remaining: "
                + String.format("%02d:%02d:%02d", hours, minutes, seconds));
    }
}
