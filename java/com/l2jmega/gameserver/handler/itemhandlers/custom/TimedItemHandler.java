package com.l2jmega.gameserver.handler.itemhandlers.custom;

import com.l2jmega.Config;
import com.l2jmega.gameserver.handler.IItemHandler;
import com.l2jmega.gameserver.model.actor.Playable;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.item.instance.ItemInstance;

import Base.LimitedTimeItems.TimedItemManager;

public class TimedItemHandler implements IItemHandler
{
    @Override
    public void useItem(Playable playable, ItemInstance item, boolean forceUse)
    {
        if (!(playable instanceof Player))
            return;

        Player player = (Player) playable;

        if (!Config.ENABLE_TIMED_ITEMS) return;
        if (!Config.LIST_TIMED_ITEMS.contains(item.getItemId())) return;

        TimedItemManager manager = TimedItemManager.getInstance();

        // Register the item if not active
        if (!manager.isActive(item))
            manager.registerItem(item);

        // Show remaining time to player
        long remaining = manager.getRemainingTime(item);
        long hours = remaining / 3600_000;
        long minutes = (remaining / 60_000) % 60;
        long seconds = (remaining / 1000) % 60;

        player.sendMessage(String.format("Timed item '%s' remaining: %02d:%02d:%02d (HH:MM:SS)",
                item.getItem().getName(), hours, minutes, seconds));
    }
}
