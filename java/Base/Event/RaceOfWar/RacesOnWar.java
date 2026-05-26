package Base.Event.RaceOfWar;

import com.l2jmega.Config;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.network.serverpackets.CreatureSay;
import com.l2jmega.gameserver.network.serverpackets.ExShowScreenMessage;

/**
 * Race Of War Event - Critical Announce Version.
 */
public class RacesOnWar
{
    private static StateRacesOnWar stateRaceOnWar = StateRacesOnWar.INACTIVE;

    // Event time tracking
    private static long eventStartTime = 0;
    private static final int EVENT_DURATION_MIN = Config.RACE_OF_WAR_DURATION_MIN;

    private static int darkElfPoints = 0;
    private static int elfPoints = 0;
    private static int humanPoints = 0;
    private static int orcPoints = 0;
    private static int dwarfPoints = 0;
    private static final int ANNOUNCE_CHANNEL = 18;

    /* ========================= */
    /* Event Start               */
    /* ========================= */
    public static void startRacesOnWar()
    {
        if (stateRaceOnWar == StateRacesOnWar.ACTIVE)
            return;

        resetPoints();

        eventStartTime = System.currentTimeMillis();

        setStateRaceOnWar(StateRacesOnWar.ACTIVE);

        notifyAllPlayer(true);
        announceEvent("Event has STARTED! Fight for your race!");
        System.out.println("[RaceOfWar]: Event started.");
    }

    /* ========================= */
    /* Event Stop                */
    /* ========================= */
    public static void endRacesOnWar()
    {
        if (stateRaceOnWar == StateRacesOnWar.INACTIVE)
            return;

        announceEvent("Event FINISHED! Final scores:");
        announceEvent("Dark Elves kills -> " + darkElfPoints);
        announceEvent("Elves kills -> " + elfPoints);
        announceEvent("Humans kills -> " + humanPoints);
        announceEvent("Orcs kills -> " + orcPoints);
        announceEvent("Dwarves kills -> " + dwarfPoints);

        resetPoints();

        setStateRaceOnWar(StateRacesOnWar.INACTIVE);

        notifyAllPlayer(false);
        System.out.println("[RaceOfWar]: Event ended.");
    }

    /* ========================= */
    /* Event Status              */
    /* ========================= */

    public static boolean isEventActive()
    {
        return stateRaceOnWar == StateRacesOnWar.ACTIVE;
    }

    public static int getRemainingTimeSeconds()
    {
        if (!isEventActive() || eventStartTime == 0)
            return 0;

        long elapsed = (System.currentTimeMillis() - eventStartTime) / 1000;

        int total = EVENT_DURATION_MIN * 60;

        int remaining = (int)(total - elapsed);

        return Math.max(remaining, 0);
    }

    /* ========================= */
    /* Player Death Handler      */
    /* ========================= */
    public static void onDie(Player victim, Player killer)
    {
        if (stateRaceOnWar != StateRacesOnWar.ACTIVE)
            return;

        if (victim == null || killer == null)
            return;

        if (victim == killer)
            return;

        // Same IP protection
        if (victim.getClient() != null && killer.getClient() != null)
        {
            try
            {
                String victimIp = victim.getClient().getConnection().getInetAddress().getHostAddress();
                String killerIp = killer.getClient().getConnection().getInetAddress().getHostAddress();

                if (victimIp.equalsIgnoreCase(killerIp))
                {
                    killer.sendMessage("[Races On War]: Same IP detected. Kill not counted.");
                    return;
                }
            }
            catch (Exception ignored){}
        }

        if (victim.getRace() == killer.getRace())
            return;

        addPointToRace(killer);

        killer.addItem(
            "RaceOfWar",
            Config.RACE_OF_WAR_REWARD_ID,
            Config.RACE_OF_WAR_REWARD_COUNT,
            killer,
            true
        );

        announceKillEvent(victim, killer);
    }

    /* ========================= */
    /* Points Logic              */
    /* ========================= */

    private static void addPointToRace(Player player)
    {
        switch (player.getRace())
        {
            case DARK_ELF:
                darkElfPoints++;
                break;

            case ELF:
                elfPoints++;
                break;

            case HUMAN:
                humanPoints++;
                break;

            case ORC:
                orcPoints++;
                break;

            case DWARF:
                dwarfPoints++;
                break;
        }
    }

    private static void resetPoints()
    {
        darkElfPoints = 0;
        elfPoints = 0;
        humanPoints = 0;
        orcPoints = 0;
        dwarfPoints = 0;
    }

    /* ========================= */
    /* Messaging                 */
    /* ========================= */

    public static void notifyAllPlayer(boolean start)
    {
        for (Player player : World.getInstance().getPlayers())
        {
            if (player == null || !player.isOnline())
                continue;

            player.sendPacket(
                new ExShowScreenMessage(
                    start ?
                    "[Race Of War]: Event STARTED! Fight for your race!" :
                    "[Race Of War]: Event FINISHED!",
                    8000
                )
            );
        }
    }

    private static void announceKillEvent(Player victim, Player killer)
    {
        String message = "Race Of War: " + victim.getName() + " (" + victim.getRace() + ") was killed by " + killer.getName() + " (" + killer.getRace() + ")";

        if (victim.isOnline())
            victim.sendPacket(new CreatureSay(0, ANNOUNCE_CHANNEL, "Race Of War", message));

        if (killer.isOnline())
            killer.sendPacket(new CreatureSay(0, ANNOUNCE_CHANNEL, "Race Of War", message));
    }

    private static void announceEvent(String message)
    {
        String fullMessage = "Race Of War: " + message;

        for (Player player : World.getInstance().getPlayers())
        {
            if (player != null && player.isOnline())
                player.sendPacket(new CreatureSay(0, ANNOUNCE_CHANNEL, "Race Of War", fullMessage));
        }
    }

    /* ========================= */
    /* State                     */
    /* ========================= */

    public static StateRacesOnWar getStateRaceOnWar()
    {
        return stateRaceOnWar;
    }

    public static void setStateRaceOnWar(StateRacesOnWar state)
    {
        stateRaceOnWar = state;
    }
    public static String getMenuTime()
    {
        if (!isEventActive())
            return "00:00";

        int remaining = getRemainingTimeSeconds();

        if (remaining <= 0)
            return "00:00";

        int minutes = remaining / 60;
        int seconds = remaining % 60;

        return String.format("%02d:%02d", minutes, seconds);
    }
}
