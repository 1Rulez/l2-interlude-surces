package com.l2jmega.gameserver.handler.voicedcommandhandlers;

import com.l2jmega.events.BossEvent;
import com.l2jmega.events.BossEvent.EventState;
import com.l2jmega.gameserver.handler.IVoicedCommandHandler;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.olympiad.OlympiadManager;

public class BossEventCMD implements IVoicedCommandHandler
{

    @Override
    public boolean useVoicedCommand(String command, Player activeChar, String params)
    {
        if (command.startsWith("bossevent"))
        {
            /* ===== CURSED WEAPON PROTECTION ===== */
            if (activeChar.isCursedWeaponEquipped())
            {
                activeChar.sendMessage("You cannot join Boss Event while holding a cursed weapon!");
                return true;
            }

            if (BossEvent.getInstance().getState() != EventState.REGISTRATION)
            {
                activeChar.sendMessage("Boss Event is not running!");
                return true;
            }

            if (OlympiadManager.getInstance().isParallelEventBlockedFor(activeChar))
            {
                activeChar.sendMessage("You cannot participate in events while registered for or fighting in Olympiad.");
                return true;
            }

            if (!BossEvent.getInstance().isRegistered(activeChar))
            {
                if (BossEvent.getInstance().addPlayer(activeChar))
                {
                    activeChar.sendMessage("You have been successfully registered in Boss Event!");
                }
            }
            else
            {
                if (BossEvent.getInstance().removePlayer(activeChar))
                {
                    activeChar.sendMessage("You have been successfully removed from Boss Event!");
                }
            }
        }
        return true;
    }

    @Override
    public String[] getVoicedCommandList()
    {
        return new String[]
        {
            "bossevent"
        };
    }
}