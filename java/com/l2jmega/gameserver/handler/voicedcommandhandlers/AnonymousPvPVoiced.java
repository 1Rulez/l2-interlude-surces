package com.l2jmega.gameserver.handler.voicedcommandhandlers;

import com.l2jmega.gameserver.handler.IVoicedCommandHandler;
import com.l2jmega.gameserver.model.actor.instance.Player;
import Base.custom.event.AnonymousPvPEvent;

public class AnonymousPvPVoiced implements IVoicedCommandHandler 
{
    private static final String[] VOICED_COMMANDS = { "join", "leave" };

    @Override
    public boolean useVoicedCommand(String command, Player activeChar, String target)
    {
        /* ===== CURSED WEAPON PROTECTION ===== */
        if (activeChar.isCursedWeaponEquipped())
        {
            activeChar.sendMessage("You cannot join this event while holding a cursed weapon!");
            return true;
        }

        if (command.equalsIgnoreCase("join"))
        {
            AnonymousPvPEvent.register(activeChar);
            return true;
        }

        if (command.equalsIgnoreCase("leave"))
        {
            AnonymousPvPEvent.unregister(activeChar);
            return true;
        }

        return false;
    }

    @Override
    public String[] getVoicedCommandList()
    {
        return VOICED_COMMANDS;
    }
}