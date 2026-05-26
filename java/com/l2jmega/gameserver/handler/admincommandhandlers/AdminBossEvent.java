package com.l2jmega.gameserver.handler.admincommandhandlers;

import com.l2jmega.events.BossEvent;
import com.l2jmega.gameserver.handler.IAdminCommandHandler;
import com.l2jmega.gameserver.model.actor.instance.Player;

public class AdminBossEvent implements IAdminCommandHandler
{
    private static final String[] ADMIN_COMMANDS =
    {
        "admin_boss_start",
        "admin_boss_stop",
        "admin_boss_status"
    };

    @Override
    public boolean useAdminCommand(String command, Player player)
    {
        final BossEvent bossEvent = BossEvent.getInstance();

        switch (command)
        {
            case "admin_boss_start":
                if (bossEvent.isEventActive())
                {
                    player.sendMessage("Boss Event is already active: " + bossEvent.getState());
                    break;
                }

                bossEvent.startRegistration();
                player.sendMessage("Boss Event registration forced start.");
                break;

            case "admin_boss_stop":
                if (!bossEvent.isEventActive())
                {
                    player.sendMessage("Boss Event is not active.");
                    break;
                }

                bossEvent.forceStop();
                player.sendMessage("Boss Event stopped.");
                break;

            case "admin_boss_status":
                player.sendMessage("Boss Event state: " + bossEvent.getState());
                player.sendMessage("Registered: " + bossEvent.eventPlayers.size());
                player.sendMessage("Boss objectId: " + bossEvent.objectId);
                player.sendMessage("Started: " + bossEvent.started + ", Aborted: " + bossEvent.aborted);
                break;
        }

        return true;
    }

    @Override
    public String[] getAdminCommandList()
    {
        return ADMIN_COMMANDS;
    }
}
