package com.l2jmega.gameserver.handler.voicedcommandhandlers;

import com.l2jmega.events.CTF;
import com.l2jmega.events.TvT;
import com.l2jmega.gameserver.handler.IVoicedCommandHandler;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.olympiad.OlympiadManager;

public class VoicedEvent implements IVoicedCommandHandler
{
    private static final String[] VOICED_COMMANDS =
    {
        "tvtjoin",
        "tvtleave",
        "ctfjoin",
        "ctfleave",
        "register",
        "unregister"
    };

    @Override
    public boolean useVoicedCommand(String command, Player activeChar, String target)
    {
        /* ===== GLOBAL CURSED WEAPON PROTECTION ===== */
        if (activeChar.isCursedWeaponEquipped())
        {
            activeChar.sendMessage("You cannot participate in events while holding a cursed weapon.");
            return true;
        }

        if (command.startsWith("register") || command.startsWith("tvtjoin"))
        {
            if (TvT.is_joining() || TvT.is_teleport() || TvT.is_started())
            {
                JoinTvT(activeChar);
            }
            else
            {
                activeChar.sendMessage("There are no events currently available");
            }
        }
        else if (command.startsWith("unregister") || command.startsWith("tvtleave"))
        {
            if (TvT.is_joining() || TvT.is_teleport() || TvT.is_started())
            {
                LeaveTvT(activeChar);
            }
            else
            {
                activeChar.sendMessage("There are no events currently available");
            }
        }
        else if (command.startsWith("ctfjoin"))
        {
            if (CTF.is_joining() || CTF.is_teleport() || CTF.is_started())
                JoinCTF(activeChar);
            else
                activeChar.sendMessage("There are no CTF events currently available!");
        }
        else if (command.startsWith("ctfleave"))
        {
            if (CTF.is_joining() || CTF.is_teleport() || CTF.is_started())
                LeaveCTF(activeChar);
            else
                activeChar.sendMessage("There are no CTF events currently available!");
        }

        return true;
    }

    @Override
    public String[] getVoicedCommandList()
    {
        return VOICED_COMMANDS;
    }

    /* =======================================================
       TVT JOIN
       ======================================================= */

    public static boolean JoinTvT(Player activeChar)
    {
        if (activeChar == null)
            return false;

        if (!TvT.is_joining())
        {
            activeChar.sendMessage("There are no events currently available.");
            return false;
        }

        if (activeChar._inEventTvT)
        {
            activeChar.sendMessage("You are already registered.");
            return false;
        }

        if (activeChar.isCursedWeaponEquipped())
        {
            activeChar.sendMessage("You cannot participate while holding a cursed weapon.");
            return false;
        }

        if (OlympiadManager.getInstance().isParallelEventBlockedFor(activeChar))
        {
            activeChar.sendMessage("You cannot participate in events while registered for or fighting in Olympiad.");
            return false;
        }

        if (activeChar.getLevel() < TvT.get_minlvl())
        {
            activeChar.sendMessage("Your level is too low.");
            return false;
        }

        if (activeChar.getLevel() > TvT.get_maxlvl())
        {
            activeChar.sendMessage("Your level is too high.");
            return false;
        }

        if (activeChar.getKarma() > 0)
        {
            activeChar.sendMessage("You cannot participate because you have Karma.");
            return false;
        }

        if (TvT.is_teleport() || TvT.is_started())
        {
            activeChar.sendMessage("TvT registration period is over.");
            return false;
        }

        TvT.addPlayer(activeChar, "");
        return true;
    }

    public boolean LeaveTvT(Player activeChar)
    {
        if (activeChar == null)
            return false;

        if (!TvT.is_joining())
        {
            activeChar.sendMessage("There are no events currently available.");
            return false;
        }

        if ((TvT.is_teleport() || TvT.is_started()) && activeChar._inEventTvT)
        {
            activeChar.sendMessage("You cannot leave after the event started.");
            return false;
        }

        if (!activeChar._inEventTvT)
        {
            activeChar.sendMessage("You are not registered.");
            return false;
        }

        TvT.removePlayer(activeChar);
        return true;
    }

    /* =======================================================
       CTF JOIN
       ======================================================= */

    public static boolean JoinCTF(Player activeChar)
    {
        if (activeChar == null)
            return false;

        if (!CTF.is_joining())
        {
            activeChar.sendMessage("There are no events currently available.");
            return false;
        }

        if (activeChar._inEventCTF)
        {
            activeChar.sendMessage("You are already registered.");
            return false;
        }

        if (activeChar.isCursedWeaponEquipped())
        {
            activeChar.sendMessage("You cannot participate while holding a cursed weapon.");
            return false;
        }

        if (OlympiadManager.getInstance().isParallelEventBlockedFor(activeChar))
        {
            activeChar.sendMessage("You cannot participate in events while registered for or fighting in Olympiad.");
            return false;
        }

        if (activeChar.getLevel() < CTF.get_minlvl())
        {
            activeChar.sendMessage("Your level is too low.");
            return false;
        }

        if (activeChar.getLevel() > CTF.get_maxlvl())
        {
            activeChar.sendMessage("Your level is too high.");
            return false;
        }

        if (activeChar.getKarma() > 0)
        {
            activeChar.sendMessage("You cannot participate because you have Karma.");
            return false;
        }

        if (CTF.is_teleport() || CTF.is_started())
        {
            activeChar.sendMessage("CTF registration period is over.");
            return false;
        }

        CTF.addPlayer(activeChar, "");
        return true;
    }

    public boolean LeaveCTF(Player activeChar)
    {
        if (activeChar == null)
            return false;

        if (!CTF.is_joining())
        {
            activeChar.sendMessage("There are no events currently available.");
            return false;
        }

        if ((CTF.is_teleport() || CTF.is_started()) && activeChar._inEventCTF)
        {
            activeChar.sendMessage("You cannot leave after CTF started.");
            return false;
        }

        if (!activeChar._inEventCTF)
        {
            activeChar.sendMessage("You are not registered.");
            return false;
        }

        CTF.removePlayer(activeChar);
        return true;
    }
}