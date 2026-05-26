package com.l2jmega.gameserver.handler.admincommandhandlers;

import com.l2jmega.Config;
import com.l2jmega.gameserver.handler.IAdminCommandHandler;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.item.instance.ItemInstance;
import com.l2jmega.gameserver.network.serverpackets.NpcHtmlMessage;

import java.util.logging.Logger;

import Base.LimitedTimeItems.TimedItemManager;

public class AdminTimedItem implements IAdminCommandHandler
{
    private static final Logger _log = Logger.getLogger(AdminTimedItem.class.getName());

    private static final String[] ADMIN_COMMANDS =
    {
        "admin_timeditem_give",
        "admin_timeditem_remove",
        "admin_timeditem_check"
    };

    @Override
    public String[] getAdminCommandList()
    {
        return ADMIN_COMMANDS;
    }

    @Override
    public boolean useAdminCommand(String command, Player activeChar)
    {
        try
        {
            if (command.startsWith("admin_timeditem_give"))
            {
                String[] parts = command.split(" ");
                if (parts.length < 4)
                {
                    activeChar.sendMessage("Usage: //admin_timeditem_give <player> <itemId> <hours>");
                    return false;
                }

                String playerName = parts[1];
                int itemId = Integer.parseInt(parts[2]);
                int hours = Integer.parseInt(parts[3]);

                Player target = World.getInstance().getPlayer(playerName);
                if (target == null)
                {
                    activeChar.sendMessage("Player not found: " + playerName);
                    return false;
                }

                ItemInstance item = target.getInventory().addItem("AdminGive", itemId, 1, target, null);

                // Register timed item with custom time (hours)
                TimedItemManager.getInstance().registerItem(item, hours * 3600_000L);

                activeChar.sendMessage("Timed item " + item.getItemName() + " given to " + target.getName() + " for " + hours + " hours.");
                target.sendMessage("You have received a timed item: " + item.getItemName() + " for " + hours + " hours.");

                return true;
            }
            else if (command.startsWith("admin_timeditem_remove"))
            {
                String[] parts = command.split(" ");
                if (parts.length < 3)
                {
                    activeChar.sendMessage("Usage: //admin_timeditem_remove <player> <itemObjId>");
                    return false;
                }

                String playerName = parts[1];
                int itemObjId = Integer.parseInt(parts[2]);

                Player target = World.getInstance().getPlayer(playerName);
                if (target == null)
                {
                    activeChar.sendMessage("Player not found: " + playerName);
                    return false;
                }

                ItemInstance item = target.getInventory().getItemByObjectId(itemObjId);
                if (item != null)
                {
                    // Remove from manager + DB
                    TimedItemManager.getInstance().unregisterItemByObjectId(itemObjId);

                    // Destroy item from player inventory
                    target.destroyItem("AdminRemoveTimedItem", item, null, true);

                    activeChar.sendMessage("Timed item removed from " + target.getName());
                    target.sendMessage("Your timed item " + item.getItemName() + " was removed by an admin.");
                }
                else
                {
                    activeChar.sendMessage("Item not found for player.");
                }
                return true;
            }
            else if (command.startsWith("admin_timeditem_check"))
            {
                String[] parts = command.split(" ");
                if (parts.length < 2)
                {
                    activeChar.sendMessage("Usage: //admin_timeditem_check <player>");
                    return false;
                }

                String playerName = parts[1];
                Player target = World.getInstance().getPlayer(playerName);
                if (target == null)
                {
                    activeChar.sendMessage("Player not found: " + playerName);
                    return false;
                }

                StringBuilder sb = new StringBuilder();
                sb.append("<html><body><center>");
                sb.append("<br><font color=\"00FF00\">Timed Items for ").append(target.getName()).append("</font><br><br>");

                boolean hasTimed = false;

                for (ItemInstance item : target.getInventory().getItems())
                {
                    if (Config.LIST_TIMED_ITEMS.contains(item.getItemId()))
                    {
                        String remaining = TimedItemManager.getInstance().getFormattedRemainingTime(item);
                        sb.append("Item: ").append(item.getItemName())
                          .append(" - Remaining: ").append(remaining).append("<br>");
                        hasTimed = true;
                    }
                }

                if (!hasTimed)
                    sb.append("No timed items found.");

                sb.append("</center></body></html>");

                NpcHtmlMessage msg = new NpcHtmlMessage(0);
                msg.setHtml(sb.toString());
                activeChar.sendPacket(msg);

                return true;
            }
        }
        catch (Exception e)
        {
            _log.warning("AdminTimedItem: Error executing command: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
}
