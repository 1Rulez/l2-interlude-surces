package com.l2jmega.gameserver.model.zone.type;

import com.l2jmega.gameserver.data.SkillTable;
import com.l2jmega.gameserver.model.L2Skill;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.actor.Creature;
import com.l2jmega.gameserver.model.actor.Summon;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.item.instance.ItemInstance;
import com.l2jmega.gameserver.model.itemcontainer.Inventory;
import com.l2jmega.gameserver.model.itemcontainer.PcInventory;
import com.l2jmega.gameserver.model.zone.L2ZoneType;
import com.l2jmega.gameserver.model.zone.ZoneId;
import com.l2jmega.gameserver.network.serverpackets.CreatureSay;
import com.l2jmega.gameserver.network.serverpackets.ExShowScreenMessage;
import com.l2jmega.gameserver.network.serverpackets.SpecialCamera;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.commons.random.Rnd;
import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.FakePlayerManager;

public class L2ElysianUltimateZone extends L2ZoneType
{
	// ================= EVENT SYSTEM =================

	public enum EventState
	{
	    WAITING,
	    COUNTDOWN,
	    ACTIVE,
	    ENDING
	}

	public static long eventEndTime = 0;
	
	public static boolean isEventActive()
	{
	    return EVENT_ACTIVE;
	}

	public static int getRemainingTimeSeconds()
	{
	    if (nextEventStartTime <= 0)
	        return 0;

	    return (int)((nextEventStartTime - System.currentTimeMillis()) / 1000);
	}

	public static long scheduleNextEventTime()
	{
	    return nextEventStartTime;
	}

	public static volatile EventState EVENT_STATE = EventState.WAITING;

	private static final int COUNTDOWN_TIME = 300;
	private static final long SAFE_ENTRY_WINDOW = 10000;
	
    private static final Logger _log = Logger.getLogger(L2ElysianUltimateZone.class.getName());

    public static boolean pvp_enabled, restart_zone, store_zone, logout_zone;
    public static boolean revive_akamanah, revive_noblesse, revive_superhaste, revive_Zariche, revive_heal;
    public static boolean revive, remove_buffs, remove_pets;
    public static boolean give_noblesse, give_Zariche, give_akamanah, give_hero, give_superhaste;

    public static boolean CAMERA_ENABLED;
    public static int CAMERA_DISTANCE, CAMERA_POV, CAMERA_ANGLE, CAMERA_SPEED, CAMERA_DELAY;

    public static boolean START_VIP1;

    public static int radius;
    static int enchant;
    static int revive_delay;
    public static int[][] spawn_loc;

    private static final List<String> items = new ArrayList<>();
    public static final List<String> classes = new ArrayList<>();
    private static final List<String> grades = new ArrayList<>();
    public static final List<int[]> rewards = new ArrayList<>();
    public static final List<int[]> topKillerRewards = new ArrayList<>();
    private static final Map<Integer, Integer> killStreakMap = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> lastKillTimeMap = new ConcurrentHashMap<>();
    private static final Map<Integer, String> playerIpCache = new ConcurrentHashMap<>();
    private static final Map<Integer, String> playerAccountCache = new ConcurrentHashMap<>();
    
    private static final long FARM_PROTECTION_TIME = 8000; // 8 seconds
    private static final String[] gradeNames = { "", "D", "C", "B", "A", "S" };

    private final L2Skill noblesse = SkillTable.getInstance().getInfo(1323, 1);
    private final L2Skill superhaste = SkillTable.getInstance().getInfo(7029, 4);
    private final L2Skill demonzariche = SkillTable.getInstance().getInfo(3603, 10);
    private final L2Skill akamanah = SkillTable.getInstance().getInfo(3629, 10);

    public static boolean EVENT_ACTIVE = false;
    public static long nextEventStartTime = 0;

    private static int EVENT_DURATION = 15 * 60;
    private static int EVENT_INTERVAL = 30 * 60;
    
    // Kill tracking map
    private static final Map<Integer, Integer> killCounts = new ConcurrentHashMap<>();

    // Pre-event entry cooldown map
    private static final Map<Integer, Long> preEventCooldown = new ConcurrentHashMap<>();
    private static final long PRE_EVENT_COOLDOWN_MS = 5000; // 5 seconds cooldown

    public L2ElysianUltimateZone(int id)
    {
        super(id);
        _log.info("[UltimateZone]: Initializing zone id=" + id);
        loadConfigs();
        scheduleEventCycle();
        if (EVENT_ACTIVE)
        {
            long now = System.currentTimeMillis();

            if (nextEventStartTime <= now)
                nextEventStartTime = now + (EVENT_INTERVAL * 1000L);
        }
    }

    
    public static String getServerDateTime()
    {
        java.text.SimpleDateFormat sdf =
                new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

        return sdf.format(new java.util.Date());
    }
    
    private static void announceGlobal(String message)
    {
        try
        {
            for (Player player : World.getInstance().getPlayers())
            {
                if (player != null && player.isOnline())
                {
                    player.sendPacket(
                            new CreatureSay(
                                    0,
                                    18,
                                    "Ultimate Zone",
                                    message
                            )
                    );
                }
            }

            _log.info("[UltimateZone Global]: " + message);
        }
        catch (Exception e)
        {
            _log.warning("[UltimateZone] Global announce failed");
        }
    }
    
    private static void startCountdownPhase()
    {
        EVENT_STATE = EventState.COUNTDOWN;

        nextEventStartTime = System.currentTimeMillis() + (COUNTDOWN_TIME * 1000L);

        announceGlobal("Ultimate Zone event will start in 5 minutes!");

        for (int i = 5; i >= 1; i--)
        {
            final int minute = i;

            ThreadPool.schedule(() ->
                    announceGlobal("Ultimate Zone starts in " + minute + " minute(s)!"),
                    (5 - minute) * 60 * 1000L);
        }

        ThreadPool.schedule(L2ElysianUltimateZone::startEventPhase,COUNTDOWN_TIME * 1000L);
    }
    
    private static void scheduleEventCycle()
    {
        ThreadPool.schedule(() ->
        {
            if (EVENT_STATE != EventState.WAITING)
                return;

            long now = System.currentTimeMillis();

            if (now >= nextEventStartTime - SAFE_ENTRY_WINDOW)
            {
                startCountdownPhase();
            }

            scheduleEventCycle();

        }, 1000L);
    }

    private static void startEventPhase()
    {
        if (EVENT_STATE != EventState.COUNTDOWN)
            return;

        EVENT_STATE = EventState.ACTIVE;
        EVENT_ACTIVE = true;

        killCounts.clear();
        lastKillTimeMap.clear();

        announceGlobal("Ultimate Zone event has started!");
        autoJoinPhantomsOnEventStart();

        eventEndTime = System.currentTimeMillis() + (EVENT_DURATION * 1000L);

        ThreadPool.schedule(L2ElysianUltimateZone::endEventPhase, EVENT_DURATION * 1000L);
    }

    private static void autoJoinPhantomsOnEventStart()
    {
        if (!FakePlayerConfig.ALLOW_FAKE_PLAYER_ULTIMATE_ZONE || !FakePlayerConfig.isAutomatedFakePopulationEnabled())
            return;

        int min = Math.max(0, FakePlayerConfig.ULTIMATE_ZONE_FAKE_PLAYER_COUNT_MIN);
        int max = Math.max(min, FakePlayerConfig.ULTIMATE_ZONE_FAKE_PLAYER_COUNT_MAX);
        int joinChance = Math.max(0, Math.min(100, FakePlayerConfig.ULTIMATE_ZONE_FAKE_JOIN_CHANCE));

        if (spawn_loc == null || spawn_loc.length == 0)
            return;

        int targetCount = min;
        if (max > min)
            targetCount = min + Rnd.get(max - min + 1);

        int joined = 0;
        for (FakePlayer fake : FakePlayerManager.getFakePlayers())
        {
            if (joined >= targetCount)
                break;

            if (fake == null || !fake.isOnline() || fake.isDead() || fake.isInObserverMode())
                continue;

            if (fake.isInOlympiadMode() || fake.isOlympiadProtection() || fake.getOlympiadGameId() != -1)
                continue;

            if (fake.isInsideZone(ZoneId.ELYSIAN_ULTIMATE))
                continue;

            if (joinChance < 100 && Rnd.get(100) >= joinChance)
                continue;

            int[] baseLoc = spawn_loc[Rnd.get(spawn_loc.length)];
            int x = baseLoc[0] + Rnd.get(-radius, radius);
            int y = baseLoc[1] + Rnd.get(-radius, radius);
            int z = baseLoc[2];
            fake.teleToLocation(x, y, z, 0);
            joined++;
        }

        _log.info("[UltimateZone]: Auto-joined phantoms: " + joined + ".");
    }
    
    public static String getMenuTime()
    {
        long now = System.currentTimeMillis();
        long diff = 0;

        switch (EVENT_STATE)
        {
            case WAITING:
                diff = nextEventStartTime - now;
                break;

            case COUNTDOWN:
                diff = nextEventStartTime - now;
                break;

            case ACTIVE:
                diff = eventEndTime - now;
                break;

            default:
                return "00:00";
        }

        if (diff <= 0)
            return "00:00";

        int totalSeconds = (int)(diff / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;

        return String.format("%02d:%02d", minutes, seconds);
    }

    private static void endEventPhase()
    {
        EVENT_STATE = EventState.ENDING;

        for (int i = 5; i >= 1; i--)
        {
            final int sec = i;

            ThreadPool.schedule(() ->
                    announceGlobal("Event ends in " + sec + "!"),
                    (5 - sec) * 1000L);
        }

        ThreadPool.schedule(() ->
        {
            EVENT_ACTIVE = false;

            announceMostKills();

            for (Player player : World.getInstance().getPlayers())
            {
                if (player.isInsideZone(ZoneId.ELYSIAN_ULTIMATE))
                {
                    player.teleToLocation(83597, 147888, -3405, 0);
                    player.stopAllEffects();
                    playEndingCinematic(player);
                }
            }

            EVENT_STATE = EventState.WAITING;

            ThreadPool.schedule(L2ElysianUltimateZone::scheduleEventCycle,
                    EVENT_INTERVAL * 1000L);

        }, 5000L);
    }

    private static void trackKill(Player killer)
    {
        if (!EVENT_ACTIVE || killer == null)
            return;

        long now = System.currentTimeMillis();
        Long lastKillTime = lastKillTimeMap.get(killer.getObjectId());

        if (lastKillTime != null && now - lastKillTime < FARM_PROTECTION_TIME)
            return;

        lastKillTimeMap.put(killer.getObjectId(), now);

        int streak = killStreakMap.getOrDefault(killer.getObjectId(), 0) + 1;
        killStreakMap.put(killer.getObjectId(), streak);

        int count = killCounts.getOrDefault(killer.getObjectId(), 0) + 1;
        killCounts.put(killer.getObjectId(), count);

        if (streak % 5 == 0)
        {
            killer.sendPacket(new ExShowScreenMessage(
                    "Kill Streak! " + streak + " kills!",
                    3000,
                    ExShowScreenMessage.SMPOS.TOP_CENTER,
                    true));
        }
    }
        private static boolean isExploitAccount(Player player)
        {
            String ip = player.getClient().getConnection().getInetAddress().getHostAddress();
            String account = player.getAccountName();

            if (playerIpCache.containsValue(ip))
                return true;

            if (playerAccountCache.containsValue(account))
                return true;

            playerIpCache.put(player.getObjectId(), ip);
            playerAccountCache.put(player.getObjectId(), account);

            return false;
        }
    

    private static void announceMostKills()
    {
        if (killCounts.isEmpty())
        {
            announceGlobal("No kills recorded.");
            return;
        }

        int topId = 0;
        int max = 0;

        for (Map.Entry<Integer, Integer> entry : killCounts.entrySet())
        {
            if (entry.getValue() > max)
            {
                max = entry.getValue();
                topId = entry.getKey();
            }
        }

        Player topPlayer = World.getInstance().getPlayer(topId);
        
        if (topPlayer == null || isExploitAccount(topPlayer))
        {
            announceGlobal("Top killer reward was invalidated due to exploit detection.");
            return;
        }
        
        announceGlobal("Top killer: " + topPlayer.getName() + " with " + max + " kills!");

        PcInventory inv = topPlayer.getInventory();

        for (int[] reward : topKillerRewards)
        {
            inv.addItem("UltimateZoneTopReward",
                    reward[0],
                    reward[1],
                    topPlayer,
                    topPlayer);
        }

        topPlayer.sendPacket(new ExShowScreenMessage(
                "You received extra reward for being Top Killer!",
                5000,
                ExShowScreenMessage.SMPOS.TOP_CENTER,
                true
        ));
    }

    @Override
    protected void onEnter(Creature character)
    {
        if (!(character instanceof Player))
            return;

        Player player = (Player) character;

        long now = System.currentTimeMillis();

        if (player.isCursedWeaponEquipped())
        {
            removePlayer(player, "You cannot enter with cursed weapons.");
            return;
        }

        if (player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_RHAND) != null)
        {
            int weaponId = player.getInventory()
                    .getPaperdollItem(Inventory.PAPERDOLL_RHAND)
                    .getItemId();

            // Zariche & Akamanah block
            if (weaponId == 8190 || weaponId == 8689)
            {
                removePlayer(player, "You cannot enter with cursed weapons.");
                return;
            }
        }
        
        // ================================
        // EVENT STATE CHECK
        // ================================
        if (EVENT_STATE != EventState.ACTIVE && EVENT_STATE != EventState.COUNTDOWN)
        {
            removePlayer(player, "The Ultimate Zone event is not active.");
            return;
        }

        // ================================
        // SAFE ENTRY WINDOW CHECK
        // ================================
        if (now < nextEventStartTime - SAFE_ENTRY_WINDOW)
        {
        	player.sendMessage(
                "Event is preparing. Please wait.\n" +
                "Server Time: " + getServerDateTime()
        );
            teleportOut(player);
            return;
        }

        // ================================
        // PREVENT ENTRY SPAM
        // ================================
        Long lastAttempt = preEventCooldown.get(player.getObjectId());
        if (lastAttempt != null && now - lastAttempt < PRE_EVENT_COOLDOWN_MS)
            return;

        preEventCooldown.put(player.getObjectId(), now);

        // ================================
        // VIP CHECK
        // ================================
        if (START_VIP1 && !player.isVip())
        {
            removePlayer(player, "Only VIP members can enter the Ultimate Zone.");
            return;
        }

        // ================================
        // CLASS RESTRICTION
        // ================================
        if (classes.contains(String.valueOf(player.getClassId().getId())))
        {
            removePlayer(player, "Your class is not allowed in this zone.");
            return;
        }

        // ================================
        // MARK INSIDE ZONE
        // ================================
        character.setInsideZone(ZoneId.NO_SUMMON_FRIEND, true);
        character.setInsideZone(ZoneId.ELYSIAN_ULTIMATE, true);

        // ================================
        // REMOVE ILLEGAL ITEMS
        // ================================
        for (ItemInstance item : player.getInventory().getItems())
        {
            if (item.isEquipable() && item.isEquipped() && !checkItem(item))
            {
                int slot = player.getInventory().getSlotFromItem(item);
                player.getInventory().unEquipItemInBodySlotAndRecord(slot);

                player.sendPacket(new ExShowScreenMessage(
                        item.getItemName() + " unequipped (not allowed in this zone).",
                        5000,
                        ExShowScreenMessage.SMPOS.TOP_CENTER,
                        true));
            }
        }

        // ================================
        // GIVE BUFFS
        // ================================
        if (give_noblesse)
            noblesse.getEffects(player, player);

        if (give_hero)
        {
            player.setHero(true);
            player.giveHeroSkills();
        }

        if (give_superhaste)
            player.addSkill(superhaste, false);

        if (give_Zariche)
            player.addSkill(demonzariche, false);

        if (give_akamanah)
            player.addSkill(akamanah, false);

        if (pvp_enabled)
            player.updatePvPFlag(1);

        heal(player);
        player.broadcastUserInfo();

        player.sendPacket(new ExShowScreenMessage(
                "You entered Ultimate zone.",
                5000,
                ExShowScreenMessage.SMPOS.TOP_CENTER,
                true));

        clear(player);
    }
    
    private static void teleportOut(Player player)
    {
        player.teleToLocation(83597, 147888, -3405, 0);
    }

    private static void removePlayer(Player player, String message)
    {
        teleportOut(player);

        player.setInsideZone(ZoneId.NO_SUMMON_FRIEND, false);
        player.setInsideZone(ZoneId.ELYSIAN_ULTIMATE, false);

        if (pvp_enabled)
            player.updatePvPFlag(0);

        player.stopAllEffects();

        player.sendPacket(new ExShowScreenMessage(
                message,
                5000,
                ExShowScreenMessage.SMPOS.TOP_CENTER,
                true));
    }

    @Override
    protected void onExit(Creature character)
    {
        character.setInsideZone(ZoneId.NO_SUMMON_FRIEND, false);
        character.setInsideZone(ZoneId.ELYSIAN_ULTIMATE, false);

        if (!(character instanceof Player))
            return;

        Player player = (Player) character;

        if (give_hero)
        {
            player.setHero(false);
            player.removeHeroSkills();
        }

        if (give_superhaste)
            player.removeSkill(superhaste, false);

        if (give_Zariche)
            player.removeSkill(demonzariche, false);

        if (give_akamanah)
            player.removeSkill(akamanah, false);

        if (pvp_enabled)
            player.updatePvPFlag(0);

        player.broadcastUserInfo();
        player.sendPacket(new ExShowScreenMessage(
            "You left Ultimate zone.",
            5000,
            ExShowScreenMessage.SMPOS.TOP_CENTER,
            true
        ));
    }

    @Override
    public void onDieInside(final Creature character)
    {
        if (!(character instanceof Player) || !revive)
            return;

        final Player player = (Player) character;

        // Trigger death camera if enabled
        if (CAMERA_ENABLED && !player.isPhoenixBlessed())
        {
            ThreadPool.schedule(() ->
                player.sendPacket(new SpecialCamera(
                    player.getObjectId(),
                    CAMERA_DISTANCE,
                    CAMERA_POV,
                    CAMERA_ANGLE,
                    CAMERA_SPEED,
                    CAMERA_DELAY
                )), 200);
        }

        ThreadPool.schedule(() ->
        {
            player.doRevive();
            heal(player);

            if (spawn_loc == null || spawn_loc.length == 0)
                return;

            int[] loc = spawn_loc[Rnd.get(spawn_loc.length)];
            player.teleToLocation(
                loc[0] + Rnd.get(-radius, radius),
                loc[1] + Rnd.get(-radius, radius),
                loc[2],
                0
            );
        }, revive_delay * 1000);
    }

    @Override
    public void onReviveInside(Creature character)
    {
        if (!(character instanceof Player))
            return;

        Player player = (Player) character;

        if (revive_noblesse)
            noblesse.getEffects(player, player);

        if (revive_superhaste)
            player.addSkill(superhaste, false);

        if (revive_Zariche)
            player.addSkill(demonzariche, false);

        if (revive_akamanah)
            player.addSkill(akamanah, false);

        if (revive_heal)
            heal(player);
    }

    private static void heal(Player player)
    {
        player.setCurrentHp(player.getMaxHp());
        player.setCurrentCp(player.getMaxCp());
        player.setCurrentMp(player.getMaxMp());
    }

    private static void clear(Player player)
    {
        if (remove_buffs)
            player.stopAllEffects();

        if (remove_pets)
        {
            Summon pet = player.getPet();
            if (pet != null)
                pet.unSummon(player);
        }
    }

    public static void givereward(Player player)
    {
        if (!player.isInsideZone(ZoneId.ELYSIAN_ULTIMATE))
            return;

        for (int[] reward : rewards)
        {
            PcInventory inv = player.getInventory();
            inv.addItem("UltimateZoneReward", reward[0], reward[1], player, player);
        }
    }

    public static boolean checkItem(ItemInstance item)
    {
        if (enchant > 0 && item.getEnchantLevel() > enchant)
            return false;

        int grade = item.getItem().getCrystalType().ordinal();
        if (grades.contains(gradeNames[grade]))
            return false;

        return !items.contains(String.valueOf(item.getItemId()));
    }

    private static void loadConfigs()
    {
        items.clear();
        grades.clear();
        classes.clear();
        rewards.clear();
        topKillerRewards.clear();

        File file = new File("./config/custom/ElysianUltimateZone.properties");

        if (!file.exists())
        {
            _log.warning("[UltimateZone]: Config file not found! Using defaults.");
            return;
        }

        Properties prop = new Properties();

        try (FileInputStream fis = new FileInputStream(file))
        {
            prop.load(fis);

            pvp_enabled = Boolean.parseBoolean(prop.getProperty("EnablePvP", "False"));
            START_VIP1 = Boolean.parseBoolean(prop.getProperty("Enter_vip", "false"));
            spawn_loc = parseItemsList(prop.getProperty("SpawnLoc", "150111,144740,-12248"));
            revive_delay = Integer.parseInt(prop.getProperty("ReviveDelay", "10"));
            revive = revive_delay > 0;

            give_noblesse = Boolean.parseBoolean(prop.getProperty("GiveNoblesse", "False"));
            give_superhaste = Boolean.parseBoolean(prop.getProperty("GiveSuperhaste", "False"));
            give_akamanah = Boolean.parseBoolean(prop.getProperty("GiveAkamanah", "False"));
            give_Zariche = Boolean.parseBoolean(prop.getProperty("GiveDemonZariche", "False"));
            give_hero = Boolean.parseBoolean(prop.getProperty("GiveHero", "False"));

            for (String s : prop.getProperty("Items", "").split(","))
                if (!s.isEmpty())
                    items.add(s.trim());

            for (String s : prop.getProperty("Grades", "").split(","))
                if (!s.isEmpty())
                    grades.add(s.trim());

            for (String s : prop.getProperty("Classes", "").split(","))
                if (!s.isEmpty())
                    classes.add(s.trim());

            radius = Integer.parseInt(prop.getProperty("RespawnRadius", "500"));
            enchant = Integer.parseInt(prop.getProperty("Enchant", "0"));

            revive_noblesse = Boolean.parseBoolean(prop.getProperty("ReviveNoblesse", "False"));
            revive_superhaste = Boolean.parseBoolean(prop.getProperty("ReviveSuperhaste", "False"));
            revive_akamanah = Boolean.parseBoolean(prop.getProperty("ReviveAkamanah", "False"));
            revive_Zariche = Boolean.parseBoolean(prop.getProperty("ReviveDemonicZariche", "False"));
            revive_heal = Boolean.parseBoolean(prop.getProperty("ReviveHeal", "False"));

            EVENT_ACTIVE = Boolean.parseBoolean(prop.getProperty("EventActive", "false"));
            nextEventStartTime = Long.parseLong(prop.getProperty("NextEventStartTime", "0").trim());
            int durationMinutes = Integer.parseInt(prop.getProperty("EventDuration", "15"));
            int intervalMinutes = Integer.parseInt(prop.getProperty("EventInterval", "30"));
            EVENT_DURATION = durationMinutes * 60;
            EVENT_INTERVAL = intervalMinutes * 60;
            
            // Camera config
            CAMERA_ENABLED = Boolean.parseBoolean(prop.getProperty("CameraEnabled", "False"));
            CAMERA_DISTANCE = Integer.parseInt(prop.getProperty("CameraDistance", "10"));
            CAMERA_POV = Integer.parseInt(prop.getProperty("CameraPOV", "0"));
            CAMERA_ANGLE = Integer.parseInt(prop.getProperty("CameraAngle", "5"));
            CAMERA_SPEED = Integer.parseInt(prop.getProperty("CameraSpeed", "3000"));
            CAMERA_DELAY = Integer.parseInt(prop.getProperty("CameraDuration", "3000"));

            for (String reward : prop.getProperty("Rewards", "57,100000").split(";"))
            {
                String[] r = reward.split(",");
                if (r.length == 2)
                    rewards.add(new int[]
                    {
                        Integer.parseInt(r[0].trim()),
                        Integer.parseInt(r[1].trim())
                    });
            }

            for (String reward : prop.getProperty("TopKillerRewards", "").split(";"))
            {
                if (reward.isEmpty())
                    continue;

                String[] r = reward.split(",");
                if (r.length == 2)
                    topKillerRewards.add(new int[]
                    {
                        Integer.parseInt(r[0].trim()),
                        Integer.parseInt(r[1].trim())
                    });
            }

            _log.info("[UltimateZone]: Config loaded successfully.");
        }
        catch (Exception e)
        {
            _log.severe("[UltimateZone]: Failed to load config!");
            e.printStackTrace();
        }
    }

    private static int[][] parseItemsList(String line)
    {
        String[] split = line.split(";");
        int[][] result = new int[split.length][3];

        for (int i = 0; i < split.length; i++)
        {
            String[] val = split[i].split(",");
            result[i][0] = Integer.parseInt(val[0]);
            result[i][1] = Integer.parseInt(val[1]);
            result[i][2] = Integer.parseInt(val[2]);
        }
        return result;
    }

    public void onKill(Creature killer, Creature victim)
    {
        if (!(killer instanceof Player))
            return;

        Player player = (Player) killer;
        trackKill(player);
    }
    
    private static void playEndingCinematic(Player player)
    {
        if (player == null)
            return;

        try
        {
            ThreadPool.schedule(() ->
            {
                player.sendPacket(new SpecialCamera(
                        player.getObjectId(),
                        1000,
                        180,
                        30,
                        3000,
                        2000
                ));
            }, 500);

            ThreadPool.schedule(() ->
            {
                player.sendPacket(new SpecialCamera(
                        player.getObjectId(),
                        1500,
                        90,
                        20,
                        3000,
                        2000
                ));
            }, 3000);

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
