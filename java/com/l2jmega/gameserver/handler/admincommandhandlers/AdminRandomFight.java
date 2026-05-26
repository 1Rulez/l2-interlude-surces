package com.l2jmega.gameserver.handler.admincommandhandlers;

import com.l2jmega.gameserver.handler.IAdminCommandHandler;
import com.l2jmega.gameserver.model.actor.instance.Player;

import Base.RandomFightEvent.RandomFight;

public class AdminRandomFight implements IAdminCommandHandler
{
    private static final String[] ADMIN_COMMANDS =
    {
        "admin_rf_start",
        "admin_rf_stop",
        "admin_rf_status"
    };

    @Override
    public boolean useAdminCommand(String command, Player player)
    {
        switch (command)
        {
            case "admin_rf_start":
                RandomFight.startRegister();
                player.sendMessage("Random Fight forced start.");
                break;

            case "admin_rf_stop":
                RandomFight.forceStop();
                player.sendMessage("Random Fight stopped.");
                break;

            case "admin_rf_status":
                player.sendMessage("RandomFight State: " + RandomFight.state);
                player.sendMessage("Registered: " + RandomFight.getRegisteredCount());
                player.sendMessage("Fighting: " + RandomFight.getPlayersCount());
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
