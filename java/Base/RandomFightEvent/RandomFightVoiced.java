package Base.RandomFightEvent;

import com.l2jmega.gameserver.handler.IVoicedCommandHandler;
import com.l2jmega.gameserver.model.actor.instance.Player;

public class RandomFightVoiced implements IVoicedCommandHandler
{
    private static final String[] VOICED_COMMANDS = { "regrandom", "unrandom" };

    @Override
    public boolean useVoicedCommand(String command, Player player, String target)
    {
        if (player == null)
            return true;

        /* ===== CURSED WEAPON PROTECTION ===== */
        if (player.isCursedWeaponEquipped())
        {
            player.sendMessage("You cannot participate while holding a cursed weapon!");
            return true;
        }

        if (command.equalsIgnoreCase("regrandom"))
        {
            RandomFight.register(player);
            player.sendMessage("You registered to the event!");
            return true;
        }

        if (command.equalsIgnoreCase("unrandom"))
        {
            RandomFight.unregister(player);
            player.sendMessage("You unregistered from the event!");
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