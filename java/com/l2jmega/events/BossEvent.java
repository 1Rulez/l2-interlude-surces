package com.l2jmega.events;

import Base.RandomFightEvent.RandomFight;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Logger;

import com.l2jmega.Config;
import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.commons.random.Rnd;
import com.l2jmega.gameserver.data.NpcTable;
import com.l2jmega.gameserver.data.SpawnTable;
import com.l2jmega.gameserver.handler.VoicedCommandHandler;
import com.l2jmega.gameserver.handler.voicedcommandhandlers.BossEventCMD;
import com.l2jmega.gameserver.instancemanager.NextBossEvent;
import com.l2jmega.gameserver.model.L2Spawn;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.actor.template.NpcTemplate;
import com.l2jmega.gameserver.model.location.Location;
import com.l2jmega.gameserver.network.clientpackets.Say2;
import com.l2jmega.gameserver.network.serverpackets.CreatureSay;
import com.l2jmega.gameserver.network.serverpackets.ExShowScreenMessage;
import com.l2jmega.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS;
import com.l2jmega.gameserver.network.serverpackets.MagicSkillUse;
import phantom.FakePlayerConfig;
import phantom.ai.event.KillTheBossAI;

public class BossEvent
{
    public L2Spawn bossSpawn;
    public L2Spawn eventNpc;
    public List<Location> locList = new ArrayList<>();
    public Location loc;
    public List<Integer> bossList = new ArrayList<>();
    public int bossId;
    public int objectId;
    public List<Player> eventPlayers = new ArrayList<>();
    protected static final Logger _log = Logger.getLogger(BossEvent.class.getName());
    private EventState state = EventState.INACTIVE;
    public boolean started = false;
    public boolean aborted = false;
    private Player lastAttacker = null;
    private Map<Integer, Integer> generalRewards = new HashMap<>();
    @SuppressWarnings("unused")
    private Map<Integer, Integer> lastAttackerRewards = new HashMap<>();
    private Map<Integer, Integer> mainDamageDealerRewards = new HashMap<>();
    public ScheduledFuture<?> despawnBoss = null;
    public ScheduledFuture<?> countDownTask = null;
    private String bossName = "";
    public boolean bossKilled = false;
    public long startTime;

    
    public static boolean isEventEnabled()
    {
        return Config.BOSS_EVENT_ENABLED;
    }

    public boolean isEventActive()
    {
        return state != null &&
                state != EventState.INACTIVE &&
                state != EventState.FINISHING;
    }

    public int getRemainingTimeSeconds()
    {
        if(startTime <= 0)
            return 0;

        int time = (int)((startTime - System.currentTimeMillis()) / 1000);
        return Math.max(time, 0);
    }
    
    public String getMenuTime()
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
    
    public enum EventState
    {
        REGISTRATION, TELEPORTING, WAITING, FIGHTING, FINISHING, INACTIVE
    }

    public BossEvent()
    {
        VoicedCommandHandler.getInstance().registerHandler(new BossEventCMD());

        _log.info("[Hero Boss Event] Loaded.");
        _log.info("[Hero Boss Event] Scheduler initializing...");

        NextBossEvent.getInstance().startCalculationOfNextEventTime();
        _log.info("[Hero Boss Event] Scheduler started.");
    }

    public boolean addPlayer(Player player)
    {
        if (player != null && RandomFight.isCommittedToRandomFight(player))
        {
            player.sendMessage("You cannot register for Boss Event while registered or fighting in Random Fight.");
            return false;
        }
        return eventPlayers.add(player);
    }
    public boolean removePlayer(Player player) { return eventPlayers.remove(player); }
    public boolean isRegistered(Player player) { return eventPlayers.contains(player); }

    class Registration implements Runnable
    {
        @Override
        public void run()
        {
            startRegistration();
        }
    }

    public void teleToTown()
    {
        final List<Player> playersToTeleport = new ArrayList<>(eventPlayers);
        for (Player p : playersToTeleport)
        {
            if (p == null)
            {
                eventPlayers.remove(p);
                continue;
            }

            if (!p.isOnline())
            {
                eventPlayers.remove(p);
                continue;
            }

            if (p.isDead())
                p.doRevive();

            p.teleToLocation(new Location(83374, 148081, -3407), 300);
        }
        setState(EventState.INACTIVE);
    }

    public void delay(int delay)
    {
        try { Thread.sleep(delay); }
        catch (InterruptedException e) { e.printStackTrace(); }
    }

    class Teleporting implements Runnable
    {
        Location teleTo;
        List<Player> toTeleport = new ArrayList<>();

        public Teleporting(List<Player> toTeleport, Location teleTo)
        {
            this.teleTo = teleTo;
            this.toTeleport = toTeleport;
        }

        @Override
        public void run()
        {
            if (eventPlayers.size() >= Config.BOSS_EVENT_MIN_PLAYERS)
            {
                /* ===== NPC REGISTER SAFETY FIX ===== */
                if (eventNpc != null)
                {
                    despawnNpc(eventNpc);
                    eventNpc = null;
                }

                setState(EventState.TELEPORTING);

                announce("Event Started!", false);
                startCountDown(Config.BOSS_EVENT_TIME_TO_TELEPORT_PLAYERS, true);

                for (Player p : toTeleport)
                {
                    ThreadPool.schedule(() -> {
                        if (p != null && p.isOnline())
                            p.teleToLocation(teleTo, 300);
                    }, Config.BOSS_EVENT_TIME_TO_TELEPORT_PLAYERS * 1000L);
                }

                /* Replace dangerous delay() with scheduler but keep logic structure */
                ThreadPool.schedule(() ->
                {
                    setState(EventState.WAITING);
                    
                    startCountDown(Config.BOSS_EVENT_TIME_TO_WAIT, true);
                    
                    ThreadPool.schedule(new Fighting(bossId, teleTo), Config.BOSS_EVENT_TIME_TO_WAIT * 1000L);

                }, Config.BOSS_EVENT_TIME_TO_TELEPORT_PLAYERS * 1000L);
            }
            else
            {
                announce("Event was cancelled due to lack of participation!", false);

                if (eventNpc != null)
                {
                    despawnNpc(eventNpc);
                    eventNpc = null;
                }

                if (bossSpawn != null)
                {
                    despawnNpc(bossSpawn);
                    bossSpawn = null;
                }

                eventPlayers.clear();
                objectId = 0;
                bossKilled = false;

                setState(EventState.INACTIVE);
            }
        }
    }

    public void reward(Player p, Map<Integer, Integer> rewardType)
    {
        for (Map.Entry<Integer, Integer> entry : rewardType.entrySet())
        {
            p.addItem("BossEventReward", entry.getKey(), entry.getValue(), null, true);
        }
    }

    public void rewardPlayers()
    {
        for (Player p : eventPlayers)
        {
            if (p.getBossEventDamage() > Config.BOSS_EVENT_MIN_DAMAGE_TO_OBTAIN_REWARD)
            {
                reward(p, generalRewards);
            }
            else
            {
                p.sendPacket(new ExShowScreenMessage("You didn't caused min damage to receive rewards!", 5000));
                p.sendMessage("You didn't caused min damage to receive rewards! Min. Damage: "
                        + Config.BOSS_EVENT_MIN_DAMAGE_TO_OBTAIN_REWARD + ". Your Damage: " + p.getBossEventDamage());
            }
        }

        if (Config.BOSS_EVENT_REWARD_MAIN_DAMAGE_DEALER)
        {
            if (getMainDamageDealer() != null)
            {
                reward(getMainDamageDealer(), mainDamageDealerRewards);
                getMainDamageDealer().sendChatMessage(0, Say2.CRITICAL_ANNOUNCE, "[Hero Boss Event]",
                        "Congratulations, you was the damage dealer! So you will receive wonderful rewards.");
            }
        }
    }

    public void finishEvent()
    {
        started = false;

        KillTheBossAI.unspawnPhantoms();

        _log.info("[Hero Boss Event] Event finished.");

        rewardPlayers();

        if (bossKilled)
            announce(bossName + " has been defeated!", false);

        if (Config.BOSS_EVENT_REWARD_LAST_ATTACKER && lastAttacker != null)
        {
            announce("LastAttacker: " + lastAttacker.getName(), false);
        }

        if (Config.BOSS_EVENT_REWARD_MAIN_DAMAGE_DEALER && getMainDamageDealer() != null)
        {
            announce("Main Damage Dealer: " + getMainDamageDealer().getName() + ". Total Damage = "
                    + getMainDamageDealer().getBossEventDamage(), false);
        }

        ThreadPool.schedule(() -> {
            teleToTown();
            eventPlayers.clear();
            despawnNpc(eventNpc);
            despawnNpc(bossSpawn);
            eventNpc = null;
            bossSpawn = null;
            objectId = 0;
            setState(EventState.INACTIVE);
        }, Config.BOSS_EVENT_TIME_TO_TELEPORT_PLAYERS * 1000L);

        if (despawnBoss != null)
        {
            despawnBoss.cancel(true);
            despawnBoss = null;
        }

        setState(EventState.FINISHING);
        startCountDown(Config.BOSS_EVENT_TIME_TO_TELEPORT_PLAYERS, true);
    }

    public void forceStop()
    {
        started = false;
        aborted = true;
        bossKilled = false;

        KillTheBossAI.unspawnPhantoms();

        if (countDownTask != null)
        {
            countDownTask.cancel(true);
            countDownTask = null;
        }

        if (despawnBoss != null)
        {
            despawnBoss.cancel(true);
            despawnBoss = null;
        }

        for (Player p : eventPlayers)
        {
            if (p != null)
            {
                p.sendPacket(new ExShowScreenMessage("Boss Event has been stopped by an administrator.", 5000));
                if (p.isDead())
                    p.doRevive();
                p.teleToLocation(new Location(83374, 148081, -3407), 300);
                p.setBossEventDamage(0);
            }
        }

        eventPlayers.clear();
        despawnNpc(eventNpc);
        despawnNpc(bossSpawn);
        eventNpc = null;
        bossSpawn = null;
        objectId = 0;
        bossName = "";
        startTime = 0;
        setLastAttacker(null);
        setState(EventState.INACTIVE);
    }

    class Fighting implements Runnable
    {
        int bossId;
        Location spawnLoc;

        public Fighting(int bossId, Location spawnLoc)
        {
            this.bossId = bossId;
            this.spawnLoc = spawnLoc;
        }

        @Override
        public void run()
        {
            if (spawnNpc(bossId, loc.getX(), loc.getY(), loc.getZ()))
            {
                setState(EventState.FIGHTING);
                if (Config.BOSS_EVENT_TIME_ON_SCREEN)
                {
                    startCountDown(Config.BOSS_EVENT_TIME_TO_DESPAWN_BOSS, true);
                }
                despawnBoss = ThreadPool.schedule(new DespawnBossTask(bossSpawn),
                        Config.BOSS_EVENT_TIME_TO_DESPAWN_BOSS * 1000L);
                objectId = bossSpawn.getNpc().getObjectId();
                for (Player p : eventPlayers)
                {
                    p.sendPacket(new ExShowScreenMessage("Hero Boss " + bossSpawn.getNpc().getName()
                            + " has been spawned. Go and Defeat him!", 5000));
                }
            }
        }
    }

    public void despawnNpc(L2Spawn spawn)
    {
        if (spawn == null)
            return;

        if (spawn.getNpc() != null)
        {
            spawn.getNpc().deleteMe();
        }

        spawn.setRespawnState(false);
        SpawnTable.getInstance().deleteSpawn(spawn, true);
    }

    class DespawnBossTask implements Runnable
    {
        L2Spawn spawn;

        public DespawnBossTask(L2Spawn spawn)
        {
            this.spawn = spawn;
        }

        @Override
        public void run()
        {
            if (spawn != null)
            {
                announceScreen("Your time is over " + spawn.getNpc().getName() + " returned to his home!", true);
                announce("Your time is over " + spawn.getNpc().getName() + " returned to his home!", true);
                announce("You will be teleported to town.", true);
                despawnNpc(spawn);
                ThreadPool.schedule(() -> {
                    teleToTown();
                    eventPlayers.clear();
                    setState(EventState.INACTIVE);
                    objectId = 0;
                }, 10000L);
            }
        }
    }

    public void startRegistration()
    {
        try
        {
            resetPlayersDamage();
            bossKilled = false;
            bossList = Config.BOSS_EVENT_ID;
            bossId = bossList.get(Rnd.get(bossList.size()));
            locList = Config.BOSS_EVENT_LOCATION;
            loc = locList.get(Rnd.get(locList.size()));
            if (NpcTable.getInstance().getTemplate(bossId) != null)
            {
                startTime = System.currentTimeMillis() + Config.BOSS_EVENT_REGISTRATION_TIME * 1000;
                eventNpc = spawnEventNpc(Config.BOSS_EVENT_NPC_REGISTER_LOC._x, Config.BOSS_EVENT_NPC_REGISTER_LOC._y, Config.BOSS_EVENT_NPC_REGISTER_LOC._z);
                generalRewards = Config.BOSS_EVENT_GENERAL_REWARDS;
                lastAttackerRewards = Config.BOSS_EVENT_LAST_ATTACKER_REWARDS;
                mainDamageDealerRewards = Config.BOSS_EVENT_MAIN_DAMAGE_DEALER_REWARDS;
                started = true;
                aborted = false;
                bossName = NpcTable.getInstance().getTemplate(bossId).getName();
                setState(EventState.REGISTRATION);

                _log.info("[Hero Boss Event] Registration started. Boss: " + bossName);

                announce("Registration started!", false);
                announce("Joinable in giran or use command \".bossevent\" to register to event", false);
                startCountDown(Config.BOSS_EVENT_REGISTRATION_TIME, false);

                if (FakePlayerConfig.ALLOW_FAKE_PLAYER_KTB && FakePlayerConfig.isAutomatedFakePopulationEnabled())
                {
                    KillTheBossAI.spawnPhantoms();
                }

                ThreadPool.schedule(new Teleporting(eventPlayers, loc), Config.BOSS_EVENT_REGISTRATION_TIME * 1000);
            }
            else
            {
                _log.warning(getClass().getName() + ": cannot be started. Invalid BossId: " + bossList);
                return;
            }
        }
        catch (Exception e)
        {
            _log.warning("[Boss Event]: Couldn't be started");
            e.printStackTrace();
        }
    }

    public int timeInMillisToStart()
    {
        return (int) (startTime - System.currentTimeMillis()) / 1000;
    }

    public void startCountDownEnterWorld(Player player)
    {
        if (getState() == EventState.REGISTRATION)
        {
            ThreadPool.schedule(new Countdown(player, timeInMillisToStart(), getState()), 0);
        }
    }

    public boolean spawnNpc(int npcId, int x, int y, int z)
    {
        NpcTemplate tmpl = NpcTable.getInstance().getTemplate(npcId);
        try
        {
            bossSpawn = new L2Spawn(tmpl);

            bossSpawn.setLoc(x, y, z, Rnd.get(65535));
            bossSpawn.setRespawnDelay(1);

            SpawnTable.getInstance().addNewSpawn(bossSpawn, false);

            bossSpawn.setRespawnState(false);
            bossSpawn.doSpawn(false);
            bossSpawn.getNpc().isAggressive();
            bossSpawn.getNpc().decayMe();
            bossSpawn.getNpc().spawnMe(bossSpawn.getNpc().getX(), bossSpawn.getNpc().getY(), bossSpawn.getNpc().getZ());
            bossSpawn.getNpc().broadcastPacket(new MagicSkillUse(bossSpawn.getNpc(), bossSpawn.getNpc(), 1034, 1, 1, 1));
            return true;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return false;
        }
    }

    public void resetPlayersDamage()
    {
        for (Player p : World.getInstance().getPlayers())
        {
            p.setBossEventDamage(0);
        }
    }

    public L2Spawn spawnEventNpc(int x, int y, int z)
    {
        L2Spawn spawn = null;
        NpcTemplate tmpl = NpcTable.getInstance().getTemplate(Config.BOSS_EVENT_REGISTRATION_NPC_ID);
        try
        {
            spawn = new L2Spawn(tmpl);

            spawn.setLoc(x, y, z, Rnd.get(65535));
            spawn.setRespawnDelay(1);

            SpawnTable.getInstance().addNewSpawn(spawn, false);

            spawn.setRespawnState(false);
            spawn.doSpawn(false);
            spawn.getNpc().isAggressive();
            spawn.getNpc().decayMe();
            spawn.getNpc().spawnMe(spawn.getNpc().getX(), spawn.getNpc().getY(), spawn.getNpc().getZ());
            spawn.getNpc().broadcastPacket(new MagicSkillUse(spawn.getNpc(), spawn.getNpc(), 1034, 1, 1, 1));
            return spawn;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return spawn;
        }
    }

    public final Player getMainDamageDealer()
    {
        int dmg = 0;
        Player mainDamageDealer = null;
        for (Player p : eventPlayers)
        {
            if (p.getBossEventDamage() > dmg)
            {
                dmg = p.getBossEventDamage();
                mainDamageDealer = p;
            }
        }
        return mainDamageDealer;
    }

    public static BossEvent getInstance()
    {
        return SingleTonHolder._instance;
    }

    private static class SingleTonHolder
    {
        protected static final BossEvent _instance = new BossEvent();
    }

    public void startCountDown(int time, boolean eventOnly)
    {
        Collection<Player> players = new ArrayList<>();
        players = eventOnly ? eventPlayers : World.getInstance().getPlayers();
        for (Player player : players)
        {
            ThreadPool.schedule(new Countdown(player, time, getState()), 0L);
        }
    }

    public void announce(String text, boolean eventOnly)
    {
        Collection<Player> players = new ArrayList<>();
        players = eventOnly ? eventPlayers : World.getInstance().getPlayers();
        for (Player player : players)
        {
            player.sendPacket(new CreatureSay(0, Say2.CRITICAL_ANNOUNCE, "[Hero Boss Event]", text));
        }
    }

    public void announceScreen(String text, boolean eventOnly)
    {
        Collection<Player> players = new ArrayList<>();
        players = eventOnly ? eventPlayers : World.getInstance().getPlayers();
        for (Player player : players)
        {
            player.sendPacket(new ExShowScreenMessage(text, 4000));
        }
    }

    public EventState getState()
    {
        return state;
    }

    public void setState(EventState state)
    {
        this.state = state;
    }

    public Player getLastAttacker()
    {
        return lastAttacker;
    }

    public void setLastAttacker(Player lastAttacker)
    {
        this.lastAttacker = lastAttacker;
    }

    protected class Countdown implements Runnable
    {
        private final Player _player;
        private final int _time;
        private String text = "";
        EventState evtState;

        public Countdown(Player player, int time, EventState evtState)
        {
            _time = time;
            _player = player;
            switch (evtState)
            {
            case REGISTRATION:
                text = "Hero Boss Event registration ends in: ";
                break;
            case TELEPORTING:
                text = "You will be teleported to Hero Boss Event in: ";
                break;
            case WAITING:
                text = "Hero Boss will spawn in: ";
                break;
            case FINISHING:
                text = "You will be teleported to City in: ";
                break;
            }
            this.evtState = evtState;
        }

        @Override
        public void run()
        {
            if (getState() == EventState.INACTIVE)
            {
                return;
            }
            if (_player.isOnline())
            {
                switch (evtState)
                {
                case REGISTRATION:
                case TELEPORTING:
                case WAITING:
                case FINISHING:
                    switch (_time)
                    {

                    case 60:
                    case 120:
                    case 180:
                    case 240:
                    case 300:
                        _player.sendPacket(new CreatureSay(0, Say2.CRITICAL_ANNOUNCE, "[Hero Boss Event]", text + _time / 60 + " minute(s)"));
                        break;
                    case 45:
                    case 30:
                    case 15:
                    case 10:
                    case 5:
                    case 4:
                    case 3:
                    case 2:
                    case 1:
                        _player.sendPacket(new CreatureSay(0, Say2.CRITICAL_ANNOUNCE, "[Hero Boss Event]", text + _time + " second(s)"));
                        break;
                    }
                    if (_time > 1)
                    {
                        ThreadPool.schedule(new Countdown(_player, _time - 1, evtState), 1000L);
                    }
                    break;
                case FIGHTING:
                    int minutes = _time / 60;
                    int second = _time % 60;
                    String timing = ((minutes < 10) ? ("0" + minutes) : minutes) + ":" + ((second < 10) ? ("0" + second) : second);

                    _player.sendPacket(new ExShowScreenMessage("Time Left: " + timing, 1100, SMPOS.BOTTOM_RIGHT, true));
                    if (_time > 1)
                    {
                        ThreadPool.schedule(new Countdown(_player, _time - 1, evtState), 1000L);
                    }
                    break;
                }
            }
        }
    }
}
