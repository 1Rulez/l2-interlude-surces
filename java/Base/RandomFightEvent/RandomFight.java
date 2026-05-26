package Base.RandomFightEvent;

import Base.custom.event.AnonymousPvPEvent;
import com.l2jmega.Config;
import com.l2jmega.events.Arena1x1;
import com.l2jmega.events.Arena2x2;
import com.l2jmega.events.Arena5x5;
import com.l2jmega.events.BossEvent;
import com.l2jmega.events.CTF;
import com.l2jmega.events.TvT;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.olympiad.OlympiadManager;
import com.l2jmega.gameserver.model.zone.ZoneId;
import com.l2jmega.gameserver.model.zone.type.L2ElysianUltimateZone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.commons.random.Rnd;
import phantom.FakePlayer;
import phantom.ai.FakePlayerAI;

public class RandomFight
{
    private static final Logger _log = Logger.getLogger(RandomFight.class.getName());
    private static final Object LOCK = new Object();

    public enum State
    {
        INACTIVE,
        REGISTER,
        FIGHT
    }
    
 // ========== TIMER ==========
    private static long registerEndTime = 0;
    private static long fightEndTime = 0;
    public static volatile State state = State.INACTIVE;

    public static boolean isEventActive()
    {
        return state == State.REGISTER || state == State.FIGHT;
    }

    public static int getRemainingTimeSeconds()
    {
        long now = System.currentTimeMillis();

        if (state == State.FIGHT)
        {
            long diff = fightEndTime - now;
            return diff > 0 ? (int)(diff / 1000) : 0;
        }

        if (state == State.REGISTER)
        {
            long diff = registerEndTime - now;
            return diff > 0 ? (int)(diff / 1000) : 0;
        }

        return 0;
    }
    
    // ObjectId based
    private static final Set<Integer> registered = ConcurrentHashMap.newKeySet();
    private static final Set<Integer> players = ConcurrentHashMap.newKeySet();
    private static final Map<Integer, int[]> RETURN_LOC = new ConcurrentHashMap<>();
    private static final Map<Integer, String[]> RETURN_NAME_TITLE = new ConcurrentHashMap<>();
    private static final Map<Integer, FakePlayerAI> RETURN_FAKE_AI = new ConcurrentHashMap<>();

    private static final String[] FAKE_NAMES =
    {
        "Ghost", "Phantom", "WarriorX", "Blade", "NightHunter", "Vortex", "Reaper", "Specter", "Omega"
    };
    private static final String[] FAKE_TITLES =
    {
        "Killer", "Destroyer", "Slayer", "Hunter", "Predator", "Assassin"
    };

    // Anti dualbox identity locks
    private static final Set<String> USED_HWIDS = ConcurrentHashMap.newKeySet();
    private static final Set<String> USED_IPS = ConcurrentHashMap.newKeySet();
    private static final Set<String> USED_ACCOUNTS = ConcurrentHashMap.newKeySet();

    // ================= START =================

    private static void announceGlobal(String message)
    {
        try
        {
            for (Player player : World.getInstance().getPlayers())
            {
                if (player != null && player.isOnline())
                {
                    player.sendPacket(
                            new com.l2jmega.gameserver.network.serverpackets.CreatureSay(
                                    0,
                                    18,
                                    "Random Fight",
                                    message
                            )
                    );
                }
            }

            _log.info("[RandomFight Broadcast]: " + message);
        }
        catch (Exception e)
        {
            _log.warning("[RandomFight] Broadcast failed.");
        }
    }
    
    public static void startRegister()
    {
        synchronized (LOCK)
        {
            if (!Config.ALLOW_RANDOM_FIGHT || state != State.INACTIVE)
                return;

            state = State.REGISTER;
            registerEndTime = System.currentTimeMillis() + (Config.RANDOM_FIGHT_REGISTER_TIME * 1000L);
            registered.clear();
            players.clear();
            RETURN_LOC.clear();
            RETURN_NAME_TITLE.clear();

            USED_HWIDS.clear();
            USED_IPS.clear();
            USED_ACCOUNTS.clear();
        }

        announceGlobal("Random Fight Event started!");
        announceGlobal("Type .regrandom to participate!");

        // Auto-register fake players with configured chance
        if (Config.RANDOM_FIGHT_ALLOW_FAKES && Config.RANDOM_FIGHT_FAKES_JOIN_CHANCE > 0)
        {
            int chance = Math.max(0, Math.min(100, Config.RANDOM_FIGHT_FAKES_JOIN_CHANCE));
            for (Player player : World.getInstance().getPlayers())
            {
                if (!(player instanceof phantom.FakePlayer))
                    continue;
                if (player.isInOlympiadMode() || player.isDead())
                    continue;
                if (chance < 100 && com.l2jmega.commons.random.Rnd.get(100) >= chance)
                    continue;
                register(player);
            }
        }

        _log.info("RandomFight: Registration started.");

        ThreadPool.schedule(
            RandomFight::startFight,
            Config.RANDOM_FIGHT_REGISTER_TIME * 1000L
        );
    }

    // ================= REGISTER =================

    public static boolean isRegistering()
    {
        return state == State.REGISTER;
    }

    public static boolean isRegistered(Player p)
    {
        return registered.contains(p.getObjectId());
    }

    public static boolean isFighting(Player p)
    {
        return players.contains(p.getObjectId());
    }

    public static boolean isPlayerInFight(Player p)
    {
        return p != null && players.contains(p.getObjectId());
    }

    public static boolean isPlayerRegistered(Player p)
    {
        return p != null && registered.contains(p.getObjectId());
    }

    /**
     * Player is in Random Fight registration queue or an active duel — must not join TvT, CTF, etc.
     */
    public static boolean isCommittedToRandomFight(Player player)
    {
        return player != null && (isRegistered(player) || isFighting(player));
    }

    /**
     * TvT, CTF, tournaments, Olympiad, Anonymous PvP, Boss Event, Ultimate Zone, or fake scripted modes.
     *
     * @param checkAnonymousPvP if {@code false}, Anonymous PvP registration is ignored (used when joining that event).
     */
    public static boolean isBusyWithOtherExclusiveEvents(Player player, boolean checkAnonymousPvP)
    {
        if (player == null)
            return true;

        if (isCommittedToRandomFight(player))
            return true;

        if (player._inEventTvT || TvT._players.contains(player) || TvT._playersShuffle.contains(player))
            return true;
        final String name = player.getName();
        if (name != null && TvT._savePlayers.contains(name))
            return true;

        if (player._inEventCTF || CTF._players.contains(player) || CTF._playersShuffle.contains(player))
            return true;
        if (name != null && CTF._savePlayers.contains(name))
            return true;

        if (Arena1x1.getInstance().isRegistered(player) || Arena2x2.getInstance().isRegistered(player) || Arena5x5.getInstance().isRegistered(player)
                || player.isInArenaEvent() || player.isArenaAttack() || player.isArenaProtection()
                || player.isArena1x1() || player.isArena2x2() || player.isArena5x5() || player.isArena9x9()
                || player.isTournamentTeleport())
            return true;

        final OlympiadManager om = OlympiadManager.getInstance();
        if (om.isRegisteredInComp(player) || player.isInOlympiadMode() || player.getOlympiadGameId() != -1
                || player.isOlympiadProtection() || om.isRegistered(player))
            return true;

        if (checkAnonymousPvP && (AnonymousPvPEvent.isRegistered(player) || AnonymousPvPEvent.isPlayerInEvent(player)))
            return true;

        if (BossEvent.getInstance().isRegistered(player))
            return true;

        if (player.isInsideZone(ZoneId.ELYSIAN_ULTIMATE) && L2ElysianUltimateZone.EVENT_ACTIVE)
            return true;

        if (player instanceof FakePlayer fake)
        {
            if (fake.isFakePvp() || fake.isFakeKTBEvent() || fake.isTour())
                return true;
        }

        return false;
    }

    /** Same as {@link #isBusyWithOtherExclusiveEvents(Player, boolean)} with Anonymous PvP checked. */
    public static boolean isBusyWithOtherExclusiveEvents(Player player)
    {
        return isBusyWithOtherExclusiveEvents(player, true);
    }

    public static void register(Player p)
    {
        synchronized (LOCK)
        {
            if (state != State.REGISTER)
                return;

            if (p == null || p.isDead() || p.isInOlympiadMode()
                    || OlympiadManager.getInstance().isRegistered(p))
                return;

            if (players.contains(p.getObjectId()))
                return;

            if (isBusyWithOtherExclusiveEvents(p, true))
            {
                p.sendMessage("You cannot register for Random Fight while another event or Olympiad is active.");
                return;
            }

            String hwid = (p.getClient() != null) ? p.getClient().getHWID() : null;
            if (hwid != null && hwid.isEmpty())
                hwid = null;
            String ip = (p.getClient() != null && p.getClient().getConnection() != null)
                    ? p.getClient().getConnection().getInetAddress().getHostAddress()
                    : null;
            if (ip != null && ip.isEmpty())
                ip = null;
            String acc = p.getAccountName();

            if ((hwid != null && USED_HWIDS.contains(hwid))
                    || (ip != null && USED_IPS.contains(ip))
                    || (acc != null && USED_ACCOUNTS.contains(acc)))
            {
                p.sendMessage("Only one character per computer is allowed.");
                return;
            }

            if (hwid != null)
                USED_HWIDS.add(hwid);
            if (ip != null)
                USED_IPS.add(ip);
            if (acc != null)
                USED_ACCOUNTS.add(acc);

            registered.add(p.getObjectId());
            p.sendMessage("You are registered for Random Fight.");
        }
    }

    public static void unregister(Player p)
    {
        synchronized (LOCK)
        {
            if (state != State.REGISTER)
                return;

            registered.remove(p.getObjectId());

            if (p.getClient() != null)
            {
                USED_HWIDS.remove(p.getClient().getHWID());
                if (p.getClient().getConnection() != null)
                    USED_IPS.remove(p.getClient().getConnection().getInetAddress().getHostAddress());
            }
            String acc = p.getAccountName();
            if (acc != null)
                USED_ACCOUNTS.remove(acc);
        }
    }

    // ================= FIGHT =================

    private static void startFight()
    {
        synchronized (LOCK)
        {
            if (state != State.REGISTER || registered.size() < 2)
            {
                announceGlobal("Random Fight cancelled (not enough players).");
                clean();
                return;
            }

            List<Integer> list = new ArrayList<>(registered);
            Collections.shuffle(list);

            Player p1 = World.getInstance().getPlayer(list.get(0));
            Player p2 = World.getInstance().getPlayer(list.get(1));

            if (!isValid(p1) || !isValid(p2))
            {
                _log.warning("RandomFight: Invalid players, event cancelled.");
                clean();
                return;
            }

            players.add(p1.getObjectId());
            players.add(p2.getObjectId());

            kickPhantomsAndLeaveParty(p1);
            kickPhantomsAndLeaveParty(p2);

            teleport(p1);
            teleport(p2);

            applyRandomFightIdentity(p1);
            applyRandomFightIdentity(p2);

            prepareFakeForFight(p1, p2);
            prepareFakeForFight(p2, p1);

            state = State.FIGHT;
            fightEndTime = System.currentTimeMillis() + (Config.RANDOM_FIGHT_FIGHT_TIME * 1000L);
            // Broadcast relation so both players see each other as attackable (HP bars + PvP cursor)
            p1.setTarget(p2);
            p2.setTarget(p1);
            p1.broadcastUserInfo();
            p2.broadcastUserInfo();

            announceGlobal("Random Fight started: " + p1.getName() + " vs " + p2.getName());
            _log.info("RandomFight: Fight started between " + p1.getName() + " and " + p2.getName());

            ThreadPool.schedule(
                RandomFight::timeExpired,
                Config.RANDOM_FIGHT_FIGHT_TIME * 1000L
            );
        }
    }

    private static boolean isValid(Player p)
    {
        return p != null && p.isOnline() && !p.isDead()
                && !p.isInOlympiadMode()
                && !OlympiadManager.getInstance().isRegistered(p);
    }

    // ================= END =================

    public static void onKill(Player winner, Player loser)
    {
        synchronized (LOCK)
        {
            if (state != State.FIGHT)
                return;

            if (!players.contains(winner.getObjectId()) ||
                !players.contains(loser.getObjectId()))
                return;

            state = State.INACTIVE;

            reward(winner);
            announceGlobal("Random Fight Winner: " + winner.getName());
            _log.info("RandomFight: Winner - " + winner.getName());

            revert();
            clean();
        }
    }

    private static void timeExpired()
    {
        synchronized (LOCK)
        {
            if (state != State.FIGHT)
                return;

            state = State.INACTIVE;

            announceGlobal("Random Fight ended (time expired).");
            revert();
            clean();
        }
    }

    // ================= DISCONNECT =================

    public static void handleDisconnect(Player p)
    {
        synchronized (LOCK)
        {
            if (state == State.FIGHT && players.contains(p.getObjectId()))
            {
                Integer winnerId = players.stream()
                        .filter(id -> id != p.getObjectId())
                        .findFirst()
                        .orElse(null);

                if (winnerId != null)
                {
                    Player winner = World.getInstance().getPlayer(winnerId);
                    if (winner != null)
                    {
                        reward(winner);
                        announceGlobal("Random Fight Winner (disconnect): " + winner.getName());
                    }
                }

                revert();
                clean();
            }
        }
    }

    // ================= UTILS =================

    private static void teleport(Player p)
    {
        saveLocation(p);
        p.teleToLocation(
            Config.RANDOM_FIGHT_X,
            Config.RANDOM_FIGHT_Y,
            Config.RANDOM_FIGHT_Z,
            0
        );
    }

    private static void kickPhantomsAndLeaveParty(Player p)
    {
        if (p == null || !p.isInParty())
            return;

        com.l2jmega.gameserver.model.L2Party party = p.getParty();
        java.util.List<Player> phantomsToKick = new java.util.ArrayList<>();
        for (Player member : party.getPartyMembers())
        {
            if (member != p && member instanceof phantom.FakePlayer)
                phantomsToKick.add(member);
        }
        for (Player fake : phantomsToKick)
            fake.leaveParty();
        if (p.isInParty())
            p.leaveParty();
    }

    private static void saveLocation(Player p)
    {
        RETURN_LOC.put(p.getObjectId(), new int[]
        {
            p.getX(),
            p.getY(),
            p.getZ()
        });
    }

    private static void applyRandomFightIdentity(Player p)
    {
        if (p == null || !players.contains(p.getObjectId()))
            return;

        RETURN_NAME_TITLE.put(p.getObjectId(), new String[] { p.getName(), p.getTitle() });

        String fakeName = FAKE_NAMES[Rnd.get(FAKE_NAMES.length)] + Rnd.get(100, 999);
        String fakeTitle = FAKE_TITLES[Rnd.get(FAKE_TITLES.length)] + " [0]";

        p.setTitle(fakeTitle);
        p.setName(fakeName);
        p.broadcastUserInfo();
    }

    private static void restoreIdentity(Player p)
    {
        if (p == null)
            return;

        String[] orig = RETURN_NAME_TITLE.remove(p.getObjectId());
        if (orig != null && orig.length >= 2)
        {
            p.setName(orig[0]);
            p.setTitle(orig[1]);
        }
        else
        {
            try (java.sql.Connection con = com.l2jmega.L2DatabaseFactory.getInstance().getConnection();
                 java.sql.PreparedStatement ps = con.prepareStatement("SELECT char_name, title FROM characters WHERE obj_Id=?"))
            {
                ps.setInt(1, p.getObjectId());
                try (java.sql.ResultSet rs = ps.executeQuery())
                {
                    if (rs.next())
                    {
                        p.setName(rs.getString("char_name"));
                        p.setTitle(rs.getString("title"));
                    }
                }
            }
            catch (Exception e)
            {
                _log.warning("[RandomFight] DB restore fallback error: " + e.getMessage());
            }
        }
        p.broadcastUserInfo();
    }

    private static void reward(Player p)
    {
        for (int i = 0; i < Config.RANDOM_FIGHT_REWARD_IDS.length; i++)
        {
            p.addItem(
                "RandomFight",
                Config.RANDOM_FIGHT_REWARD_IDS[i],
                Config.RANDOM_FIGHT_REWARD_COUNTS[i],
                p,
                true
            );
        }
    }

    private static void revert()
    {
        for (Integer objId : players)
        {
            Player p = World.getInstance().getPlayer(objId);
            if (p != null)
            {
                if (p instanceof FakePlayer fake)
                {
                    fake.setFakeEvent(false);
                    final FakePlayerAI originalAi = RETURN_FAKE_AI.remove(fake.getObjectId());
                    if (originalAi != null)
                        fake.setFakeAi(originalAi);
                }
                restoreIdentity(p);
                int[] loc = RETURN_LOC.remove(objId);
                if (loc != null)
                    p.teleToLocation(loc[0], loc[1], loc[2], 0);
            }
            else
            {
                RETURN_LOC.remove(objId);
                RETURN_NAME_TITLE.remove(objId);
            }
        }
    }

    private static void clean()
    {
        registered.clear();
        players.clear();
        RETURN_LOC.clear();
        RETURN_NAME_TITLE.clear();
        RETURN_FAKE_AI.clear();

        USED_HWIDS.clear();
        USED_IPS.clear();
        USED_ACCOUNTS.clear();

        state = State.INACTIVE;
    }

    // ================= GM =================

    public static void forceStop()
    {
        synchronized (LOCK)
        {
            announceGlobal("Random Fight Event force stopped by GM.");
            revert();
            clean();
        }
    }

    private static void prepareFakeForFight(Player participant, Player opponent)
    {
        if (!(participant instanceof FakePlayer fake))
            return;

        RETURN_FAKE_AI.put(fake.getObjectId(), fake.getFakeAi());
        fake.assignDefaultAI();
        fake.setFakeEvent(true);
        fake.setIsRunning(true);
        fake.heal();
        fake.setTarget(opponent);
        fake.broadcastUserInfo();
    }

    // ================= ADMIN INFO (FIX) =================

    public static int getRegisteredCount()
    {
        return registered.size();
    }

    public static int getPlayersCount()
    {
        return players.size();
    }
    
    public static String getMenuTime()
    {
        if (state == State.INACTIVE)
            return "00:00";

        int remaining = getRemainingTimeSeconds();
        if (remaining <= 0)
            return "00:00";

        int minutes = remaining / 60;
        int seconds = remaining % 60;

        String time = String.format("%02d:%02d", minutes, seconds);

        if (state == State.REGISTER)
            return "Reg: " + time;

        if (state == State.FIGHT)
            return "Fight: " + time;

        return "00:00";
    }
}
