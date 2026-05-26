package com.l2jmega.gameserver.handler.voicedcommandhandlers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.l2jmega.Config;
import com.l2jmega.L2DatabaseFactory;
import com.l2jmega.events.CTF;
import com.l2jmega.events.TvT;
import com.l2jmega.gameserver.data.ItemTable;
import com.l2jmega.gameserver.handler.IVoicedCommandHandler;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.network.serverpackets.NpcHtmlMessage;

public class VoicedColor implements IVoicedCommandHandler
{
    private static final Logger _log = Logger.getLogger(VoicedColor.class.getName());

    private static final String[] _voicedCommands =
    {
        "colormanager",
        "namecolor",
        "titlecolor"
    };

    private static final String NAME_UPDATE =
        "UPDATE characters SET color_name=? WHERE obj_Id=?";

    private static final String TITLE_UPDATE =
        "UPDATE characters SET color_title=? WHERE obj_Id=?";

    @Override
    public boolean useVoicedCommand(String command, Player activeChar, String target)
    {
        if ((activeChar._inEventTvT && (TvT.is_started() || TvT.is_teleport())) ||
            (activeChar._inEventCTF && (CTF.is_started() || CTF.is_teleport())))
        {
            activeChar.sendMessage("You cannot use this during events.");
            return false;
        }

        if (command.equalsIgnoreCase("colormanager"))
        {
            color_html(activeChar);
            return true;
        }

        if (command.startsWith("namecolor"))
        {
            handleNameColor(command, target, activeChar);
            color_html(activeChar);
            return true;
        }

        if (command.startsWith("titlecolor"))
        {
            handleTitleColor(command, target, activeChar);
            color_html(activeChar);
            return true;
        }

        return false;
    }

    private static void handleNameColor(String command, String target, Player player)
    {
        final boolean isVip = player.isVip();
        final boolean vipRequired = Config.COLOR_REQUIRES_VIP_FOR_COLOR;
        final boolean withItem = Config.COLOR_WITH_ITEM;

        // Non-VIP
        if (!isVip)
        {
            if (vipRequired)
            {
                player.sendMessage("You need VIP to use this command.");
                return;
            }

            if (!withItem)
            {
                setNameColor(command, target, player);
                return;
            }

            // Item payment mode: non-VIP must consume item.
            if (!player.destroyItemByItemId(
                    "NameColor",
                    Config.COLOR_ITEM_ID,
                    Config.COLOR_NAME_ITEM_AMOUNT,
                    null,
                    true))
            {
                player.sendMessage("You don't have enough items.");
                return;
            }

            setNameColor(command, target, player);
            return;
        }

        // VIP
        if (!withItem)
        {
            setNameColor(command, target, player);
            return;
        }

        // If VIP + item are both enabled, VIP still must consume the item.
        if (vipRequired)
        {
            if (!player.destroyItemByItemId(
                    "NameColor",
                    Config.COLOR_ITEM_ID,
                    Config.COLOR_NAME_ITEM_AMOUNT,
                    null,
                    true))
            {
                player.sendMessage("You don't have enough items.");
                return;
            }
        }

        setNameColor(command, target, player);
    }

    private static void handleTitleColor(String command, String target, Player player)
    {
        final boolean isVip = player.isVip();
        final boolean vipRequired = Config.COLOR_REQUIRES_VIP_FOR_COLOR;
        final boolean withItem = Config.COLOR_WITH_ITEM;

        // Non-VIP
        if (!isVip)
        {
            if (vipRequired)
            {
                player.sendMessage("You need VIP to use this command.");
                return;
            }

            if (!withItem)
            {
                setTitleColor(command, target, player);
                return;
            }

            // Item payment mode: non-VIP must consume item.
            if (!player.destroyItemByItemId(
                    "TitleColor",
                    Config.COLOR_ITEM_ID,
                    Config.COLOR_TITLE_ITEM_AMOUNT,
                    null,
                    true))
            {
                player.sendMessage("You don't have enough items.");
                return;
            }

            setTitleColor(command, target, player);
            return;
        }

        // VIP
        if (!withItem)
        {
            setTitleColor(command, target, player);
            return;
        }

        // If VIP + item are both enabled, VIP still must consume the item.
        if (vipRequired)
        {
            if (!player.destroyItemByItemId(
                    "TitleColor",
                    Config.COLOR_ITEM_ID,
                    Config.COLOR_TITLE_ITEM_AMOUNT,
                    null,
                    true))
            {
                player.sendMessage("You don't have enough items.");
                return;
            }
        }

        setTitleColor(command, target, player);
    }

    // ================= NAME COLOR =================
    private static void setNameColor(String command, String target, Player player)
    {
        try
        {
            int color = parseColorValue(command, target, "namecolor");

            player.getAppearance().setNameColor(color);
            player.broadcastUserInfo();

            try (Connection con = L2DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(NAME_UPDATE))
            {
                ps.setInt(1, color);
                ps.setInt(2, player.getObjectId());
                ps.executeUpdate();
            }

            player.sendMessage("Your name color has been changed.");
        }
        catch (Exception e)
        {
            _log.log(Level.WARNING, "Invalid name color", e);
            player.sendMessage("Invalid color value.");
        }
    }

    // ================= TITLE COLOR =================
    private static void setTitleColor(String command, String target, Player player)
    {
        try
        {
            int color = parseColorValue(command, target, "titlecolor");

            player.getAppearance().setTitleColor(color);
            player.broadcastUserInfo();

            try (Connection con = L2DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(TITLE_UPDATE))
            {
                ps.setInt(1, color);
                ps.setInt(2, player.getObjectId());
                ps.executeUpdate();
            }

            player.sendMessage("Your title color has been changed.");
        }
        catch (Exception e)
        {
            _log.log(Level.WARNING, "Invalid title color", e);
            player.sendMessage("Invalid color value.");
        }
    }

    private static int parseColorValue(String command, String target, String prefix)
    {
        String value = null;

        if (target != null && !target.trim().isEmpty())
            value = target.trim();
        else if (command != null && command.length() > prefix.length())
            value = command.substring(prefix.length()).trim();

        if (value == null || value.isEmpty())
            throw new IllegalArgumentException("Missing color value.");

        return Integer.parseInt(value);
    }

    private static void color_html(Player activeChar)
    {
        String itemName = ItemTable.getInstance()
                .getTemplate(Config.COLOR_ITEM_ID)
                .getName();

        final boolean isVip = activeChar.isVip();
        final boolean vipRequired = Config.COLOR_REQUIRES_VIP_FOR_COLOR;
        final boolean withItem = Config.COLOR_WITH_ITEM;

        NpcHtmlMessage msg = new NpcHtmlMessage(5);
        msg.setFile("data/html/mods/menu/ColorManager.htm");
        msg.replace("%player%", activeChar.getName());

        final boolean isDenied = vipRequired && !isVip;
        final boolean isFreeForPlayer = !withItem || (isVip && !vipRequired);

        msg.replace("%name%",
            isDenied
                ? "VIP"
                : (isFreeForPlayer ? "Free" : String.valueOf(Config.COLOR_NAME_ITEM_AMOUNT)));

        msg.replace("%item%",
            isDenied
                ? "only"
                : (isFreeForPlayer ? "Free" : itemName));

        msg.replace("%title%",
            isDenied
                ? "VIP"
                : (isFreeForPlayer ? "Free" : String.valueOf(Config.COLOR_TITLE_ITEM_AMOUNT)));

        activeChar.sendPacket(msg);
    }

    @Override
    public String[] getVoicedCommandList()
    {
        return _voicedCommands;
    }
}
