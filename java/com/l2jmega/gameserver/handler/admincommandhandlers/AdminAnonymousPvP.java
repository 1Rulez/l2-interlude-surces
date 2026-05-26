package com.l2jmega.gameserver.handler.admincommandhandlers;

import com.l2jmega.gameserver.handler.IAdminCommandHandler;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.network.serverpackets.NpcHtmlMessage;

import Base.custom.event.AnonymousPvPEvent;

public class AdminAnonymousPvP implements IAdminCommandHandler
{
    private static final String[] ADMIN_COMMANDS =
    {
        "admin_anonymous",
        "admin_anonymous_start",
        "admin_anonymous_stop",
        "admin_anonymous_status"
    };

    @Override
    public boolean useAdminCommand(String command, Player player)
    {
        if (command.equals("admin_anonymous"))
        {
            showMenu(player);
            return true;
        }

        switch (command)
        {
            case "admin_anonymous_start":
                if (AnonymousPvPEvent.isEventActive())
                {
                    player.sendMessage("Anonymous PvP event is already running.");
                }
                else
                {
                    AnonymousPvPEvent.startEvent();
                    player.sendMessage("Anonymous PvP event started manually.");
                }
                showMenu(player);
                break;

            case "admin_anonymous_stop":
                if (!AnonymousPvPEvent.isEventActive())
                {
                    player.sendMessage("Anonymous PvP event is not running.");
                }
                else
                {
                    AnonymousPvPEvent.endEvent();
                    player.sendMessage("Anonymous PvP event stopped.");
                }
                showMenu(player);
                break;

            case "admin_anonymous_status":
                player.sendMessage("Anonymous PvP: " + (AnonymousPvPEvent.isEventActive() ? "ACTIVE" : "inactive") +
                    ", Remaining: " + AnonymousPvPEvent.getRemainingTimeSeconds() + "s");
                showMenu(player);
                break;
        }
        return true;
    }

    private static void showMenu(Player player)
    {
        String status = AnonymousPvPEvent.isEventActive() ? "ACTIVE" : "inactive";
        int remaining = AnonymousPvPEvent.getRemainingTimeSeconds();

        NpcHtmlMessage html = new NpcHtmlMessage(0);
        html.setFile("data/html/admin/anonymous.htm");
        html.replace("%status%", status);
        html.replace("%remaining%", String.valueOf(remaining));
        player.sendPacket(html);
    }

    @Override
    public String[] getAdminCommandList()
    {
        return ADMIN_COMMANDS;
    }
}
