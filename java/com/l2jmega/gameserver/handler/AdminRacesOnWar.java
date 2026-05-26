package com.l2jmega.gameserver.handler;

import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.util.Broadcast;
import Base.Event.RaceOfWar.RacesOnWar;
import Base.Event.RaceOfWar.StateRacesOnWar;

public class AdminRacesOnWar implements IAdminCommandHandler
{
    private static final String[] commands =
    {
        "admin_startrw",
        "admin_stoprw"
    };

    @Override
    public boolean useAdminCommand(String command, Player activeChar)
    {
        if (command.equals("admin_startrw"))
        {
            if (RacesOnWar.getStateRaceOnWar() == StateRacesOnWar.ACTIVE)
            {
                activeChar.sendMessage("[Races On War]: The Event is already active!");
                return false;
            }

            RacesOnWar.startRacesOnWar();
            Broadcast.announceToOnlinePlayers("[Races On War]: Event has started!");
            return true;
        }

        if (command.equals("admin_stoprw"))
        {
            if (RacesOnWar.getStateRaceOnWar() == StateRacesOnWar.INACTIVE)
            {
                activeChar.sendMessage("[Races On War]: Event has already ended!");
                return false;
            }

            RacesOnWar.endRacesOnWar();
            Broadcast.announceToOnlinePlayers("[Races On War]: Event has ended!");
            return true;
        }

        return false;
    }

    @Override
    public String[] getAdminCommandList()
    {
        return commands;
    }
}
