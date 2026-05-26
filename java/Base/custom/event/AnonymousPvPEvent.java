package Base.custom.event;

import com.l2jmega.Config;
import com.l2jmega.L2DatabaseFactory;
import com.l2jmega.gameserver.data.SpawnTable;
import com.l2jmega.gameserver.data.SkillTable;
import com.l2jmega.gameserver.data.sql.ClanTable;
import com.l2jmega.gameserver.data.xml.DressMeData;
import com.l2jmega.gameserver.handler.IVoicedCommandHandler;
import com.l2jmega.gameserver.model.DressMe;
import com.l2jmega.gameserver.model.L2Skill;
import com.l2jmega.gameserver.model.L2Spawn;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.WorldObject;
import com.l2jmega.gameserver.model.WorldRegion;
import com.l2jmega.gameserver.model.actor.Npc;
import com.l2jmega.gameserver.model.actor.instance.GrandBoss;
import com.l2jmega.gameserver.model.actor.instance.Monster;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.actor.instance.RaidBoss;
import com.l2jmega.gameserver.model.item.instance.ItemInstance;
import com.l2jmega.gameserver.model.itemcontainer.Inventory;
import com.l2jmega.gameserver.model.location.Location;
import com.l2jmega.gameserver.model.pledge.Clan;
import com.l2jmega.gameserver.model.pledge.SubPledge;
import com.l2jmega.gameserver.network.SystemMessageId;
import com.l2jmega.gameserver.network.clientpackets.Say2;
import com.l2jmega.gameserver.network.serverpackets.CharInfo;
import com.l2jmega.gameserver.network.serverpackets.ConfirmDlg;
import com.l2jmega.gameserver.network.serverpackets.CreatureSay;
import com.l2jmega.gameserver.network.serverpackets.ExShowScreenMessage;
import com.l2jmega.gameserver.network.serverpackets.MagicSkillUse;
import com.l2jmega.gameserver.network.serverpackets.PledgeShowInfoUpdate;
import com.l2jmega.gameserver.network.serverpackets.PledgeShowMemberListAll;
import com.l2jmega.gameserver.network.serverpackets.PledgeStatusChanged;
import com.l2jmega.gameserver.network.serverpackets.SocialAction;
import com.l2jmega.gameserver.network.serverpackets.UserInfo;

import Base.RandomFightEvent.RandomFight;
import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.FakePlayerManager;
import phantom.ai.FakePlayerAI;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class AnonymousPvPEvent implements IVoicedCommandHandler 

{
    private static final Object EVENT_LOCK = new Object();
    private static boolean notifiedMobClean = false;
    private static long _eventStartTime = 0;
    public static final int ANONYMOUS_PVP_CONFIRM_DLG_ID = 100_052;
    private static final String[] ANONYMOUS_SKINS =(Config.EVENT_ANONYMOUS_SKINS != null && !Config.EVENT_ANONYMOUS_SKINS.isEmpty())? Config.EVENT_ANONYMOUS_SKINS.split(","): new String[0];
    private static final Map<Integer, Integer> _anonymousSkinId = new ConcurrentHashMap<>(); // ObjectId -> skinId
    private static final Map<Integer, Integer> _anonymousRaceId = new ConcurrentHashMap<>(); // ObjectId -> raceId (1=Elf, 3=Orc)
    private static final Map<Integer, FakeIdentity> _fakeIdentity = new ConcurrentHashMap<>();
    private static final List<Player> EVENT_PARTICIPANTS = new CopyOnWriteArrayList<>();
    private static final long IDENTITY_BACKUP_INTERVAL = 60000; // 1 minute
    private static final List<Player> _registered = new CopyOnWriteArrayList<>();
    private static volatile boolean _eventRunning = false;
    private static final Map<Integer, Integer> _playerKills = new ConcurrentHashMap<>();
    private static Timer _flagTimer = new Timer(true);
    private static Timer _mobCleanTimer = new Timer(true);
    private static final Map<Integer, Integer> _killStreak = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> _identityBackupTime = new ConcurrentHashMap<>();
    private static final Map<L2Spawn, DisabledSpawnState> _disabledZoneSpawns = new ConcurrentHashMap<>();
    /** Pre-event AI for fakes (Citizen/store/etc.); restored on leave / event end — same idea as RandomFight. */
    private static final Map<Integer, FakePlayerAI> ANONYMOUS_RETURN_FAKE_AI = new ConcurrentHashMap<>();
      
    public static FakeIdentity getFakeIdentity(Player player)
    {
        if (player == null)
            return null;

        return _fakeIdentity.get(player.getObjectId());
    }
    
    public static class FakeIdentity
    {
        public String fakeName;
        private String fakeTitle;

        public FakeIdentity(String name, String title)
        {
            this.fakeName = name;
            this.fakeTitle = title;
        }

        public String getFakeName()
        {
            return fakeName;
        }

        public String getFakeTitle()
        {
            return fakeTitle;
        }
    }
    
    private static final String[] FAKE_NAMES =
    {
        "Shadow",
        "Ghost",
        "Phantom",
        "WarriorX",
        "Blade",
        "NightHunter",
        "Vortex",
        "Reaper",
        "Specter",
        "Omega"
    };
    

    private static final String[] FAKE_TITLES =
    {
        "Killer",
        "Destroyer",
        "Slayer",
        "Hunter",
        "Predator",
        "Assassin"
    };
    
    private static void applyAnonymousIdentity(Player player)
    {
        if (player == null || !isPlayerInEvent(player))
            return;

        int kills = getPlayerKillCount(player);

        String fakeName = FAKE_NAMES[_rand.nextInt(FAKE_NAMES.length)] + _rand.nextInt(999);

        String fakeTitle = FAKE_TITLES[_rand.nextInt(FAKE_TITLES.length)] + " [" + kills + "]";

        _fakeIdentity.put(player.getObjectId(), new FakeIdentity(fakeName, fakeTitle));

        player.setTitle(fakeTitle);
        player.setName(fakeName);

        player.broadcastUserInfo();
    }

    private static void clearPlayerScreenMessages(Player player)
    {
        if (player != null && player.isOnline()) 
        {
            player.sendPacket(new ExShowScreenMessage("", 1, ExShowScreenMessage.SMPOS.TOP_LEFT, false));
        }
    }
    
    public static boolean canEnterEventZone(Player player)
    {
        return _eventRunning && _registered.contains(player);
    }
    public static boolean isEventActive() 
    {
        return _eventRunning;
    }

    public static boolean isEventZone(Location loc) 
    {
        return isEventZone(loc.getX(), loc.getY(), loc.getZ());
    }

    public static boolean isEventZone(int x, int y, int z) 
    {
        return x >= 136000 && x <= 149000 && y >= -179000 && y <= -165000;
    }

    public static boolean isRegistered(Player p) 
    {
        return _registered.contains(p);
    }

    /**
     * Both players are in the current Anonymous PvP run and may fight each other
     * (excludes same party).
     * @param a 
     * @param b 
     * @return 
     */
    public static boolean areRegisteredOpponents(Player a, Player b)
    {
        if (a == null || b == null || a == b)
            return false;
        if (!_eventRunning)
            return false;
        if (!_registered.contains(a) || !_registered.contains(b))
            return false;
        if (a.getParty() != null && a.getParty() == b.getParty())
            return false;
        return true;
    }

    public static boolean canTeleportToEventZone(Player player, int x, int y, int z) 
    {
        if (!_eventRunning) return true; 
        if (isEventZone(x, y, z) && !isRegistered(player)) 
        {
            player.sendMessage("You cannot teleport to the Anonymous PvP event area without being registered. Use .pvp to participate.");
            return false;
        }
        return true;
    }
    public static boolean isPlayerInEvent(Player p) 
    {
        return _eventRunning && _registered.contains(p);
    }
    
    public static boolean isAnonymousIdentity(Player player)
    {
        return isPlayerInEvent(player);
    }
    
    public static int getRemainingTimeSeconds() 
    {
        if (!_eventRunning || _eventStartTime == 0)
            return 0;
        
        long elapsedMs = System.currentTimeMillis() - _eventStartTime;
        int elapsedSeconds = (int)(elapsedMs / 1000);
        int totalSeconds = EVENT_DURATION_MINUTES * 60;
        int remaining = totalSeconds - elapsedSeconds;
        
        return remaining > 0 ? remaining : 0;
    }
    
    public static int getPlayerKillCount(Player player) 
    {
        if (player == null)
            return 0;
        
        return _playerKills.getOrDefault(player.getObjectId(), 0);
    }
    
    public static void onPlayerRespawn(Player player) 
    {
        if (isPlayerInEvent(player)) 
        {
            int[] loc = getZoneSpawnLocation();
            player.teleToLocation(loc[0], loc[1], loc[2], 0);
            player.sendPacket(new UserInfo(player));

            final Clan clan = player.getClan();
            if (clan != null)
            {
                player.sendPacket(new PledgeShowInfoUpdate(clan));
                player.sendPacket(new PledgeShowMemberListAll(clan, 0));

                for (SubPledge sp : clan.getAllSubPledges())
                    player.sendPacket(new PledgeShowMemberListAll(clan, sp.getId()));

                player.sendPacket(new PledgeStatusChanged(clan));
            }

            player.sendMessage("You have been returned to the Anonymous PvP event zone!");
            applyNoblesseBlessing(player);
        }
    }
    
    public static void onLogout(Player player) 
    {
        if (player == null)
            return;

        synchronized (EVENT_LOCK)
        {
            if (_registered.remove(player)) 
            {
                EVENT_PARTICIPANTS.remove(player);

                AnonymousPvPEventExitHandler.hideExitButton(player);
                clearPlayerScreenMessages(player);

                // Remove event visuals FIRST
                _anonymousSkinId.remove(player.getObjectId());
                _anonymousRaceId.remove(player.getObjectId());
                _killStreak.remove(player.getObjectId());
                _identityBackupTime.remove(player.getObjectId());

                player.setDress(null);

                // Reset PvP flag
                player.updatePvPFlag(0);

                // TELEPORT FIRST (Very important for client cache sync)
                int[] giran = {83400, 148600, -3400};
                player.teleToLocation(giran[0], giran[1], giran[2], 0);

                restoreFakePlayerAfterAnonymous(player);
                // THEN RESTORE ORIGINAL DATA
                restoreOriginalPlayerData(player);

                player.setTitle(player.getTitle());
                player.broadcastUserInfo();

                player.sendPacket(new CharInfo(player));
                player.sendPacket(new UserInfo(player));

                player.sendMessage("You have left the Anonymous PvP event.");
            } 
            else if (isAnonymized(player))
            {
                clearPlayerScreenMessages(player);
                restoreOriginalPlayerData(player);

                player.setTitle(player.getTitle());
                player.broadcastUserInfo();

                player.sendPacket(new CharInfo(player));
                player.sendPacket(new UserInfo(player));
            }
        }
    }
    
    
    private static final Logger LOGGER = Logger.getLogger(AnonymousPvPEvent.class.getName());
    private static final Timer _timer = new Timer(true);
    private static final List<TimerTask> _activeTimerTasks = new CopyOnWriteArrayList<>();
    

    private static final int[][] EVENT_LOCATIONS = 
    {
        {141907, -170472, -1776},
        {144612, -173818, -1520},
        {143377, -166304, -1392},
        {137488, -169497, -1616}
    };
    private static final Random _rand = new Random();
    private static final int CARDINAL_CLASS_ID = 97;
    private static final int EVENT_DURATION_MINUTES = 20;

    private static final class DisabledSpawnState
    {
        private final int respawnDelay;
        private final int respawnRandom;

        private DisabledSpawnState(int respawnDelay, int respawnRandom)
        {
            this.respawnDelay = respawnDelay;
            this.respawnRandom = respawnRandom;
        }
    }
    

    private static int getRewardTokenId()
    { 
    	return Config.ANONYMOUS_PVP_REWARD_TOKEN_ID; 
    }
    private static int getFirstPlaceReward()
    {
    	return Config.ANONYMOUS_PVP_REWARD_FIRST; 
    }
    private static int getSecondPlaceReward()
    { 
    	return Config.ANONYMOUS_PVP_REWARD_SECOND; 
    }
    private static int getThirdPlaceReward()
    { 
    	return Config.ANONYMOUS_PVP_REWARD_THIRD;
    }

    private static void prepareFakePlayerForAnonymousCombat(FakePlayer fake)
    {
        if (fake == null)
            return;
        ANONYMOUS_RETURN_FAKE_AI.putIfAbsent(fake.getObjectId(), fake.getFakeAi());
        fake.assignDefaultAI();
        fake.setFakeEvent(true);
        fake.setIsRunning(true);
    }

    private static void restoreFakePlayerAfterAnonymous(Player player)
    {
        if (!(player instanceof FakePlayer fake))
            return;
        fake.setFakeEvent(false);
        final FakePlayerAI former = ANONYMOUS_RETURN_FAKE_AI.remove(fake.getObjectId());
        if (former != null)
            fake.setFakeAi(former);
    }

    
    private static void registerPhantomInZone(Player phantom)
    {
        synchronized (EVENT_LOCK)
        {
            if (phantom == null || !_eventRunning || !phantom.isOnline() || !phantom.isPhantom())
                return;
            if (phantom instanceof FakePlayer && !FakePlayerConfig.isAutomatedFakePopulationEnabled())
                return;
            if (RandomFight.isBusyWithOtherExclusiveEvents(phantom, false))
                return;
            if (_registered.contains(phantom))
                return;
            if (phantom.getClassId().getId() == CARDINAL_CLASS_ID)
                return;

            try
            {
                _registered.add(phantom);
                EVENT_PARTICIPANTS.add(phantom);
                saveOriginalPlayerData(phantom);
                applyAnonymousIdentity(phantom);

                if (Config.EVENT_ANONYMOUS_RACES != null && Config.EVENT_ANONYMOUS_RACES.length > 0)
                {
                    try
                    {
                        int race = Integer.parseInt(
                                Config.EVENT_ANONYMOUS_RACES[_rand.nextInt(Config.EVENT_ANONYMOUS_RACES.length)].trim());
                        _anonymousRaceId.put(phantom.getObjectId(), race);
                    }
                    catch (Exception e)
                    {
                        _anonymousRaceId.put(phantom.getObjectId(), 1);
                    }
                }

                if (ANONYMOUS_SKINS.length > 0)
                {
                    try
                    {
                        int itemId = Integer.parseInt(ANONYMOUS_SKINS[_rand.nextInt(ANONYMOUS_SKINS.length)].trim());
                        DressMe dress = DressMeData.getInstance().getItemId(itemId);
                        if (dress != null)
                        {
                            phantom.setDress(dress);
                            _anonymousSkinId.put(phantom.getObjectId(), itemId);
                        }
                    }
                    catch (Exception e) { /* ignore */ }
                }

                phantom.updatePvPFlag(1);
                applyNoblesseBlessing(phantom);
                if (phantom instanceof FakePlayer fakePhantom)
                    prepareFakePlayerForAnonymousCombat(fakePhantom);
                phantom.broadcastUserInfo();
                phantom.sendPacket(new CharInfo(phantom));
                phantom.sendPacket(new UserInfo(phantom));
            }
            catch (Exception e)
            {
                LOGGER.warning("[AnonymousPvP] Phantom register error: " + e.getMessage());
            }
        }
    }

    private static void autoRegisterPhantomsOnEventStart()
    {
        if (!FakePlayerConfig.ALLOW_FAKE_PLAYER_ANONYMOUS_PVP || !FakePlayerConfig.isAutomatedFakePopulationEnabled())
            return;

        int min = Math.max(0, FakePlayerConfig.ANONYMOUS_PVP_FAKE_PLAYER_COUNT_MIN);
        int max = Math.max(min, FakePlayerConfig.ANONYMOUS_PVP_FAKE_PLAYER_COUNT_MAX);
        int joinChance = Math.max(0, Math.min(100, FakePlayerConfig.ANONYMOUS_PVP_FAKE_JOIN_CHANCE));

        int targetCount = min;
        if (max > min)
            targetCount = min + _rand.nextInt((max - min) + 1);

        int joined = 0;
        for (FakePlayer fake : FakePlayerManager.getFakePlayers())
        {
            if (joined >= targetCount)
                break;

            if (fake == null || !fake.isOnline() || fake.isDead())
                continue;

            if (fake.isInOlympiadMode() || fake.isOlympiadProtection() || fake.getOlympiadGameId() != -1)
                continue;

            if (RandomFight.isBusyWithOtherExclusiveEvents(fake, false))
                continue;

            if (_registered.contains(fake))
                continue;

            if (joinChance < 100 && _rand.nextInt(100) >= joinChance)
                continue;

            int[] loc = getZoneSpawnLocation();
            fake.teleToLocation(loc[0], loc[1], loc[2], 0);
            registerPhantomInZone(fake);
            joined++;
        }

        LOGGER.info("[AnonymousPvP] Auto-registered " + joined + " phantoms.");
    }

    private static void startFlagTask() 
    {
        _flagTimer.scheduleAtFixedRate(new TimerTask()
        {
            @Override
            public void run()
            {
                if (!_eventRunning)
                    return;

                // Auto-register standalone phantoms that enter the event zone
                for (Player p : World.getInstance().getPlayers())
                {
                    if (p == null || !p.isOnline() || !p.isPhantom())
                        continue;
                    if (_registered.contains(p))
                        continue;
                    if (isEventZone(p.getX(), p.getY(), p.getZ()))
                        registerPhantomInZone(p);
                }

                for (Player p : new ArrayList<>(EVENT_PARTICIPANTS))
                {
                    if (p == null || !p.isOnline())
                        continue;

                    // Must be registered AND in participants list
                    if (!_registered.contains(p))
                    {
                        EVENT_PARTICIPANTS.remove(p);
                        continue;
                    }

                    // Zone protection
                    if (isEventZone(p.getX(), p.getY(), p.getZ()))
                    {
                        p.updatePvPFlag(1);

                        if (!p.isNoblesseBlessed())
                            applyNoblesseBlessing(p);

                        // Phantom party members: apply anonymous disguise when they enter zone with leader
                        if (p.isInParty())
                        {
                            for (Player member : p.getParty().getPartyMembers())
                            {
                                if (member == null || member == p || !member.isPhantom())
                                    continue;
                                if (!isEventZone(member.getX(), member.getY(), member.getZ()))
                                    continue;
                                registerPhantomInZone(member);
                            }
                        }

                        // Individual identity backup timer (BEST PRACTICE)
                        long now = System.currentTimeMillis();
                        long lastBackup = _identityBackupTime.getOrDefault(p.getObjectId(), 0L);

                        if (now - lastBackup >= IDENTITY_BACKUP_INTERVAL)
                        {
                            saveIdentityMemo(p);
                            _identityBackupTime.put(p.getObjectId(), now);
                        }
                    }
                    else
                    {
                        // Player escaped zone = force cleanup
                        unregister(p);
                    }
                }
            }
        }, 0, 5000);

        // Mob cleanup task
        _mobCleanTimer.scheduleAtFixedRate(new TimerTask()
        {
            @Override
            public void run()
            {
                if (_eventRunning)
                    clearMobsInZone();
            }
        }, 0, 60000);
    }

    private static void stopFlagTask()
    {
        _flagTimer.cancel();
        _flagTimer = new Timer(true);
        _mobCleanTimer.cancel();
        _mobCleanTimer = new Timer(true);
    }

    public static boolean isEventEnemy(Player attacker, WorldObject target)
    {
        if (!_eventRunning) return false;
        if (!(target instanceof Player)) return false;
        Player t = (Player) target;
        return _registered.contains(attacker) && _registered.contains(t);
    }
    
    public static void onEventKill(Player killer, Player victim)
    {
        if (killer == null || victim == null)
            return;

        if (!_eventRunning)
            return;

        if (!_registered.contains(killer) || !_registered.contains(victim))
            return;

        if (!killer.isOnline() || !victim.isOnline())
            return;

        _playerKills.merge(killer.getObjectId(), 1, Integer::sum);

        applyAnonymousIdentity(killer);

        // ===== Kill Streak System =====
        int streak = _killStreak.getOrDefault(killer.getObjectId(), 0) + 1;
        _killStreak.put(killer.getObjectId(), streak);
        _killStreak.put(victim.getObjectId(), 0);

        // ===== Enchant Reward =====
        if (Config.ANONYMOUS_PVP_ENABLE_ENCHANT)
        {
            if (streak >= Config.ANONYMOUS_PVP_KILLS_FOR_ENCHANT)
            {
                boolean enchanted = enchantRandomEquippedItem(killer);

                if (enchanted)
                    _killStreak.put(killer.getObjectId(), 0);
            }
        }
    }

  
    private static List<Map.Entry<Player, Integer>> getTopKillers()
    {
        Map<Player, Integer> playerKillMap = new HashMap<>();
        for (Player player : _registered)
        {
            int kills = _playerKills.getOrDefault(player.getObjectId(), 0);
            playerKillMap.put(player, kills);
        }
        
        return playerKillMap.entrySet().stream()
            .sorted(Map.Entry.<Player, Integer>comparingByValue().reversed())
            .limit(3)
            .collect(Collectors.toList());
    }
    

    private static void rewardTopKillers() 
    {
        List<Map.Entry<Player, Integer>> topKillers = getTopKillers();
        for (int i = 0; i < topKillers.size(); i++) 
        {
            Map.Entry<Player, Integer> entry = topKillers.get(i);
            Player player = entry.getKey();
            int kills = entry.getValue();
            
            int reward;
            String position;
            switch (i) 
            {
                case 0:
                    reward = getFirstPlaceReward();
                    position = "1º";
                    break;
                case 1:
                    reward = getSecondPlaceReward();
                    position = "2º";
                    break;
                case 2:
                    reward = getThirdPlaceReward();
                    position = "3º";
                    break;
                default:
                    continue;
            }
            
 
            String originalName = player.getName();
            OriginalData data = _originalData.get(player.getObjectId());
            if (data != null && data.name != null)
            {
                originalName = data.name;
            }
            
   
            String announcement = "Anonymous PvP: " + position + " Place: " + originalName + " with " + kills + " kills - Reward: " + reward + " PVP Coins!";
            for (Player p : EVENT_PARTICIPANTS) 
            {
                p.sendPacket(new CreatureSay(0, Say2.ANNOUNCEMENT, "Event", announcement));
            }
            
            player.addItem("AnonymousPvP", getRewardTokenId(), reward, player, true);
        }
    }


    private static final Map<Integer, OriginalData> _originalData = new ConcurrentHashMap<>();

    public static void register(Player player) 
    {
        synchronized (EVENT_LOCK)
        {
            if (player == null || !player.isOnline())
                return;

            if (!isEventActive())
            {
                player.sendMessage("The Anonymous PvP event is not currently active.");
                return;
            }

            if (player.isCursedWeaponEquipped())
            {
                player.sendMessage("You cannot join Anonymous PvP while holding a cursed weapon.");
                return;
            }

            if (RandomFight.isBusyWithOtherExclusiveEvents(player, false))
            {
                player.sendMessage("You cannot join Anonymous PvP while another event, Olympiad, or Random Fight is active.");
                return;
            }

            if (player.isInCombat())
            {
                player.sendMessage("You cannot join while in combat.");
                return;
            }

            if (player.getClassId().getId() == CARDINAL_CLASS_ID)
            {
                player.sendMessage("Cardinals cannot participate in this event.");
                return;
            }

            if (_registered.contains(player))
            {
                player.sendMessage("You are already registered in the event.");
                return;
            }

            if (isEventZone(player.getX(), player.getY(), player.getZ()))
            {
                int[] giran = {83400, 148600, -3400};
                player.sendMessage("Use .join from outside the event zone to register. You have been moved to Giran.");
                player.teleToLocation(giran[0], giran[1], giran[2], 0);
                return;
            }

            if (!Config.ANONYMOUS_PVP_ALLOW_DUALBOX)
            {
                try
                {
                    String playerAccount = player.getAccountName();
                    String playerHwid = player.getHWID();
                    if (playerHwid != null && playerHwid.isEmpty())
                        playerHwid = null;

                    for (Player p : EVENT_PARTICIPANTS)
                    {
                        if (p == null || !p.isOnline() || p.equals(player))
                            continue;

                        String regAccount = p.getAccountName();
                        String regHwid = p.getHWID();
                        if (regHwid != null && regHwid.isEmpty())
                            regHwid = null;

                        if (playerAccount != null && playerAccount.equals(regAccount))
                        {
                            player.sendMessage("Only one character per account can join this event.");
                            return;
                        }

                        if (playerHwid != null && regHwid != null && playerHwid.equals(regHwid))
                        {
                            player.sendMessage("Only one client per PC can join this event.");
                            return;
                        }
                    }
                }
                catch (Exception ignored) {}
            }

            try
            {
                _registered.add(player);
                EVENT_PARTICIPANTS.add(player);

                saveOriginalPlayerData(player);
                applyAnonymousIdentity(player);

                if (Config.EVENT_ANONYMOUS_RACES != null && Config.EVENT_ANONYMOUS_RACES.length > 0)
                {
                    try
                    {
                        int race = Integer.parseInt(
                                Config.EVENT_ANONYMOUS_RACES[
                                        _rand.nextInt(Config.EVENT_ANONYMOUS_RACES.length)
                                ].trim());

                        _anonymousRaceId.put(player.getObjectId(), race);
                    }
                    catch (Exception e)
                    {
                        _anonymousRaceId.put(player.getObjectId(), 1);
                    }
                }

                if (ANONYMOUS_SKINS.length > 0)
                {
                    try
                    {
                        int itemId = Integer.parseInt(
                                ANONYMOUS_SKINS[_rand.nextInt(ANONYMOUS_SKINS.length)].trim());

                        DressMe dress = DressMeData.getInstance().getItemId(itemId);

                        if (dress != null)
                        {
                            player.setDress(dress);
                            _anonymousSkinId.put(player.getObjectId(), itemId);
                        }
                    }
                    catch (Exception e)
                    {
                        LOGGER.warning("[AnonymousPvP] Skin error " + e.getMessage());
                    }
                }

                int[] loc = getZoneSpawnLocation();
                player.teleToLocation(loc[0], loc[1], loc[2], 0);

                if (player.isInParty())
                {
                    for (Player member : player.getParty().getPartyMembers())
                    {
                        if (member == null || member == player || !member.isPhantom())
                            continue;

                        registerPhantomInZone(member);
                        int[] mLoc = getZoneSpawnLocation();
                        member.teleToLocation(mLoc[0], mLoc[1], mLoc[2], 0);
                    }
                }

                player.sendPacket(new CharInfo(player));
                player.sendPacket(new UserInfo(player));
                player.broadcastUserInfo();

                applyNoblesseBlessing(player);
                AnonymousPvPEventExitHandler.showExitButton(player);
                if (player instanceof FakePlayer fakeReg)
                    prepareFakePlayerForAnonymousCombat(fakeReg);
                player.sendMessage("You entered the Anonymous PvP Event!");
            }
            catch (Exception e)
            {
                LOGGER.warning("[AnonymousPvP] Register error: " + e.getMessage());
            }
        }
    }
    
    public static void unregister(Player player)
    {
        synchronized (EVENT_LOCK)
        {
            if (player == null)
                return;

            if (!_registered.remove(player))
            {
                player.sendMessage("You are not participating in the event.");
                return;
            }

            EVENT_PARTICIPANTS.remove(player);

            if (player.isInParty())
            {
                for (Player member : new ArrayList<>(player.getParty().getPartyMembers()))
                {
                    if (member != null && member != player && member.isPhantom() && _registered.contains(member))
                    {
                        _registered.remove(member);
                        EVENT_PARTICIPANTS.remove(member);
                        _anonymousSkinId.remove(member.getObjectId());
                        _anonymousRaceId.remove(member.getObjectId());
                        _killStreak.remove(member.getObjectId());
                        _identityBackupTime.remove(member.getObjectId());
                        member.setDress(null);
                        member.updatePvPFlag(0);
                        int[] giran = {83400, 148600, -3400};
                        member.teleToLocation(giran[0], giran[1], giran[2], 0);
                        restoreFakePlayerAfterAnonymous(member);
                        restoreOriginalPlayerData(member);
                        member.setTitle(member.getTitle());
                        member.broadcastUserInfo();
                        member.sendPacket(new CharInfo(member));
                        member.sendPacket(new UserInfo(member));
                    }
                }
            }

            AnonymousPvPEventExitHandler.hideExitButton(player);
            clearPlayerScreenMessages(player);

            _anonymousSkinId.remove(player.getObjectId());
            _anonymousRaceId.remove(player.getObjectId());
            _killStreak.remove(player.getObjectId());
            _identityBackupTime.remove(player.getObjectId());

            player.setDress(null);
            player.updatePvPFlag(0);

            int[] giran = {83400, 148600, -3400};
            player.teleToLocation(giran[0], giran[1], giran[2], 0);

            restoreFakePlayerAfterAnonymous(player);
            restoreOriginalPlayerData(player);

            player.setTitle(player.getTitle());
            player.broadcastUserInfo();

            player.sendPacket(new CharInfo(player));
            player.sendPacket(new UserInfo(player));

            player.sendMessage("You left the Anonymous PvP event.");
        }
    }

    public static void init()
    {
        if (!Config.ANONYMOUS_PVP_EVENT_AUTO)
            return;
        LOGGER.info("AnonymousPvPEvent: Initializing…");
        scheduleNextEvent();
        String nextTimes = String.join(", ", Config.ANONYMOUS_PVP_EVENT_INTERVAL);
        LOGGER.info("AnonymousPvPEvent: Configured schedules: " + nextTimes);
    }

    private static void scheduleNextEvent()
    {
        long now = System.currentTimeMillis();
        long next = -1;
        Calendar cal = Calendar.getInstance();
        for (String timeStr : Config.ANONYMOUS_PVP_EVENT_INTERVAL)
        {
            String[] parts = timeStr.trim().split(":");
            if (parts.length != 2) continue;
            int hour = Integer.parseInt(parts[0]);
            int min = Integer.parseInt(parts[1]);
            cal.setTimeInMillis(now);
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, min);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long candidate = cal.getTimeInMillis();
            if (candidate <= now) {
                candidate += 24 * 60 * 60 * 1000; // próximo dia
            }
            if (next == -1 || candidate < next)
            {
                next = candidate;
            }
        }
        long delay = next - now;
        LOGGER.info("AnonymousPvPEvent: Next event in " + (delay / 60000) + " minutes.");
        
        if (delay > 60000)
        {
            _timer.schedule(new TimerTask()
            {
                @Override
                public void run() 
                {
                    String announce = "[Anonymous PvP] The battle is about to begin! Use .join to enter.";
                    CreatureSay cs = new CreatureSay(0, Say2.ANNOUNCEMENT, "Event", announce);
                    for (Player player : World.getInstance().getPlayers())
                    {
                        if (player != null && player.isOnline())
                        {
                            player.sendPacket(cs);
                        }
                    }
                }
            }, delay - 60000);
        }
        _timer.schedule(new TimerTask()
        {
            @Override
            public void run() 
            {
                startEvent();
            }
        }, delay);
    }

    public static void startEvent()
    {
        synchronized (EVENT_LOCK)
        {
            if (_eventRunning)
                return;

            notifiedMobClean = false;
            
            for (TimerTask task : _activeTimerTasks)
            {
                if (task != null)
                {
                    task.cancel();
                }
            }
            _activeTimerTasks.clear();
            
            LOGGER.info("[AnonymousPvP] The event is starting now!");
            _eventRunning = true;
            _eventStartTime = System.currentTimeMillis(); 
            ANONYMOUS_RETURN_FAKE_AI.clear();
            _registered.clear();
            EVENT_PARTICIPANTS.clear();
            _playerKills.clear();
            _killStreak.clear();
            _identityBackupTime.clear();
            disableZoneMonsterSpawns();
            clearMobsInZone();
            kickUnregisteredPlayersFromZone();
            startFlagTask();
            LOGGER.info("[Anonymous PvP] Event started! Use .join to enter and .leave to exit at any time.");
        }

        String announcement = "[Anonymous PvP] Event started! Use .join to enter and .leave to exit at any time.";
        CreatureSay cs = new CreatureSay(0, Say2.ANNOUNCEMENT, "Event", announcement);
        for (Player player : World.getInstance().getPlayers())
        {
            if (player != null && player.isOnline())
            {
                player.sendPacket(cs);
            }
        }
        
        sendEventConfirmDlg();
        autoRegisterPhantomsOnEventStart();
        
        final long halfEventTime = (EVENT_DURATION_MINUTES * 60 * 1000L) / 2;
        TimerTask halfTimeConfirmTask = new TimerTask()
        {
            @Override
            public void run() 
            {
                if (_eventRunning) 
                {
                    sendEventConfirmDlg();
                }
            }
        };
        _activeTimerTasks.add(halfTimeConfirmTask);
        _timer.schedule(halfTimeConfirmTask, halfEventTime);

        for (Player p : EVENT_PARTICIPANTS) 
        {
            if (isEventZone(p.getX(), p.getY(), p.getZ()))
            {
                p.updatePvPFlag(1);
                p.broadcastUserInfo();
            }
        }

        scheduleNextEvent();
        
        final int totalSeconds = EVENT_DURATION_MINUTES * 60;
        for (int i = 300; i <= totalSeconds; i += 300) 
        { 
            final int secondsLeft = totalSeconds - i;
            TimerTask announceTask = new TimerTask() 
            {
                @Override
                public void run()
                {
                    if (_eventRunning)
                    {
                        String periodicMsg = "[Anonymous PvP] Event in progress! Use .join to enter and .leave to exit at any time. Remaining " + (secondsLeft / 60) + " minutes.";
                        for (Player player : EVENT_PARTICIPANTS) 
                        {
                            if (player != null && player.isOnline()) 
                            {
                                player.sendPacket(new CreatureSay(0, Say2.ANNOUNCEMENT, "Event", periodicMsg));
                            }
                        }
                    }
                }
            };
            _activeTimerTasks.add(announceTask);
            _timer.schedule(announceTask, i * 1000L);
        }

        TimerTask endTask = new TimerTask()
        {
            @Override
            public void run() 
            {
                endEvent();
            }
        };
        _activeTimerTasks.add(endTask);
        _timer.schedule(endTask, EVENT_DURATION_MINUTES * 60 * 1000L);
    }
    
    private static void sendEventConfirmDlg() 
    {
        if (!_eventRunning)
            return;
        
        for (Player player : World.getInstance().getPlayers())
        {
            if (player == null || !player.isOnline())
                continue;
            
            
            if (isRegistered(player))
                continue;
            
            
            if (player.getClassId().getId() == CARDINAL_CLASS_ID)
                continue;
            
            
            ConfirmDlg confirm = new ConfirmDlg(SystemMessageId.S1);
            confirm.addString("Anonymous PvP Event started! Do you want to participate??");
            confirm.addTime(30000); 
            confirm.addRequesterId(ANONYMOUS_PVP_CONFIRM_DLG_ID);
            player.sendPacket(confirm);
        }
    }
    
    
    public static void handleConfirmDlgResponse(Player player, boolean accepted)
    {
        if (player == null || !player.isOnline())
            return;
        
        if (!_eventRunning)
            return;
        
        if (accepted)
        {

            register(player);
        }
    }

    public static void endEvent()
    {
        synchronized (EVENT_LOCK)
        {
            if (!_eventRunning)
                return;

            _eventRunning = false;
            stopFlagTask();

            for (TimerTask task : _activeTimerTasks)
            {
                if (task != null)
                    task.cancel();
            }
            _activeTimerTasks.clear();

            LOGGER.info("[Anonymous PvP] Event finished!");
            rewardTopKillers();

            String announcement = "[Anonymous PvP] The event has ended! Returning players to city...";
            CreatureSay csEnd = new CreatureSay(0, Say2.ANNOUNCEMENT, "Event", announcement);
            for (Player p : World.getInstance().getPlayers())
            {
                if (p != null && p.isOnline())
                {
                    p.sendPacket(csEnd);
                }
            }

            for (Player p : new ArrayList<>(_registered))
            {
                if (p == null || !p.isOnline())
                    continue;

                try
                {
                    AnonymousPvPEventExitHandler.hideExitButton(p);
                    clearPlayerScreenMessages(p);

                    p.setDress(null);
                    _anonymousSkinId.remove(p.getObjectId());
                    _anonymousRaceId.remove(p.getObjectId());
                    _killStreak.remove(p.getObjectId());
                    _identityBackupTime.remove(p.getObjectId());

                    p.updatePvPFlag(0);
                    restoreFakePlayerAfterAnonymous(p);
                    restoreOriginalPlayerData(p);

                    int[] loc = getTownLocation(p);
                    p.teleToLocation(loc[0], loc[1], loc[2], 0);

                    OriginalData data = _originalData.get(p.getObjectId());
                    if (data != null && data.title != null)
                        p.setTitle(data.title);
                    p.broadcastUserInfo();
                    p.sendPacket(new CharInfo(p));
                    p.sendPacket(new UserInfo(p));

                    p.sendMessage("The Anonymous PvP event has ended. You have been returned to the city.");
                }
                catch (Exception e)
                {
                    LOGGER.warning("[AnonymousPvP] Cleanup error: " + e.getMessage());
                }
            }

            restoreZoneMonsterSpawns();

            _registered.clear();
            _originalData.clear();
            _fakeIdentity.clear();
            _anonymousSkinId.clear();
            _anonymousRaceId.clear();
            _playerKills.clear();
            _killStreak.clear();
            _identityBackupTime.clear();
            ANONYMOUS_RETURN_FAKE_AI.clear();
            EVENT_PARTICIPANTS.clear();
        }
    }
    
  
    public static boolean isAnonymized(Player p)
    {
        return p != null && _fakeIdentity.containsKey(p.getObjectId());
    }
 

    private static void kickUnregisteredPlayersFromZone()
    {
        for (Player p : World.getInstance().getPlayers()) 
        {
            if (p == null || !p.isOnline())
                continue;

            if (isEventZone(p.getX(), p.getY(), p.getZ()) && !_registered.contains(p))
            {
                int[] giran = {83400, 148600, -3400};
                p.teleToLocation(giran[0], giran[1], giran[2], 0);
                p.sendMessage("Only participants can stay inside the event zone during Anonymous PvP. You have been teleported to Giran.");
            }
        }
    }


    private static final int EVENT_REGION_SCAN_STEP = 4096;

    private static void clearMobsInZone() 
    {
        synchronized (EVENT_LOCK)
        {
            int removedCount = 0;
            try 
            {
                final World world = World.getInstance();
                final WorldRegion[][] regions = world.getWorldRegions();
                final int rs = EVENT_REGION_SCAN_STEP;
                final int xMin = 136000;
                final int xMax = 149000;
                final int yMin = -179000;
                final int yMax = -165000;

                int ix0 = Math.max(0, (xMin - World.WORLD_X_MIN) / rs);
                int ix1 = Math.min(regions.length - 1, (xMax - World.WORLD_X_MIN) / rs);
                int iy0 = Math.max(0, (yMin - World.WORLD_Y_MIN) / rs);
                int iy1 = Math.min(regions[0].length - 1, (yMax - World.WORLD_Y_MIN) / rs);

                for (int ix = ix0; ix <= ix1; ix++)
                {
                    for (int iy = iy0; iy <= iy1; iy++)
                    {
                        WorldRegion region = regions[ix][iy];
                        if (region == null)
                            continue;

                        for (WorldObject obj : region.getObjects())
                        {
                            if (obj == null || !(obj instanceof Monster monster) || monster.isDecayed())
                                continue;
                            if (monster instanceof RaidBoss || monster instanceof GrandBoss)
                                continue;

                            if (isEventZone(monster.getX(), monster.getY(), monster.getZ()))
                            {
                                final L2Spawn spawn = monster.getSpawn();
                                if (spawn != null)
                                {
                                    _disabledZoneSpawns.putIfAbsent(spawn, new DisabledSpawnState(spawn.getRespawnDelay(), spawn.getRespawnRandom()));
                                    spawn.setRespawnState(false);
                                }

                                monster.deleteMe();
                                removedCount++;
                            }
                        }
                    }
                }
                if (!notifiedMobClean && removedCount > 0) 
                {
                    LOGGER.info("[AnonymousPvP] Removed " + removedCount + " mobs from the event zone.");
                    notifiedMobClean = true;
                }
            } 
            catch (Exception e)
            {
                LOGGER.warning("[AnonymousPvP] Error while clearing mobs from the zone: " + e.getMessage());
            }
        }
    }

    private static void disableZoneMonsterSpawns()
    {
        for (L2Spawn spawn : SpawnTable.getInstance().getSpawnTable())
        {
            if (spawn == null || spawn.getTemplate() == null || spawn.getLoc() == null)
                continue;

            if (!isEventZone(spawn.getLocX(), spawn.getLocY(), spawn.getLocZ()))
                continue;

            final Npc npc = spawn.getNpc();
            final boolean monsterSpawn = npc instanceof Monster || spawn.getTemplate().isType("Monster");
            if (!monsterSpawn)
                continue;

            if (npc instanceof RaidBoss || npc instanceof GrandBoss)
                continue;

            _disabledZoneSpawns.putIfAbsent(spawn, new DisabledSpawnState(spawn.getRespawnDelay(), spawn.getRespawnRandom()));
            spawn.setRespawnState(false);
        }
    }

    private static void restoreZoneMonsterSpawns()
    {
        for (Map.Entry<L2Spawn, DisabledSpawnState> entry : _disabledZoneSpawns.entrySet())
        {
            final L2Spawn spawn = entry.getKey();
            final DisabledSpawnState state = entry.getValue();
            if (spawn == null || state == null)
                continue;

            try
            {
                spawn.setRespawnDelay(state.respawnDelay);
                spawn.setRespawnRandom(state.respawnRandom);
                spawn.setRespawnState(true);

                final Npc npc = spawn.getNpc();
                if (npc == null || npc.isDecayed())
                    spawn.doSpawn(false);
            }
            catch (Exception e)
            {
                LOGGER.warning("[AnonymousPvP] Failed to restore spawn " + spawn + ": " + e.getMessage());
            }
        }

        _disabledZoneSpawns.clear();
    }

    private static void saveOriginalPlayerData(Player p)
    {
        if (p == null)
            return;

        OriginalData data = new OriginalData(
                p.getName(),
                p.getTitle(),
                p.getClanId()
        );

        _originalData.put(p.getObjectId(), data);

        // Save into memo storage
        saveIdentityMemo(p);
    }

    
    private static void saveIdentityMemo(Player p)
    {
        if (p == null)
            return;

        try (var con = L2DatabaseFactory.getInstance().getConnection();
             var ps = con.prepareStatement("REPLACE INTO character_memo (charId, var, val) VALUES (?,?,?)"))
        {

            int objId = p.getObjectId();

            // Name
            ps.setInt(1, objId);
            ps.setString(2, "anon_original_name");
            ps.setString(3, p.getName());
            ps.executeUpdate();

            // Title
            ps.setInt(1, objId);
            ps.setString(2, "anon_original_title");
            ps.setString(3, p.getTitle());
            ps.executeUpdate();

            // Clan
            ps.setInt(1, objId);
            ps.setString(2, "anon_original_clan");
            ps.setString(3, String.valueOf(p.getClanId()));
            ps.executeUpdate();

        }
        catch (Exception e)
        {
            LOGGER.warning("[AnonymousPvP] Memo save error " + e.getMessage());
        }
    }
    
    private static void restoreOriginalPlayerData(Player p)
    {
        if (p == null)
            return;

        OriginalData data = _originalData.get(p.getObjectId());

        String name = null;
        String title = null;
        int clanId = 0;

        
        if (data != null)
        {
            name = data.name;
            title = data.title;
            clanId = data.clanId;
        }
        else
        {
            try (var con = L2DatabaseFactory.getInstance().getConnection();
                 var ps = con.prepareStatement("SELECT var,val FROM character_memo WHERE charId=?"))
            {
                ps.setInt(1, p.getObjectId());

                try (var rs = ps.executeQuery())
                {
                    while (rs.next())
                    {
                        String var = rs.getString("var");
                        String val = rs.getString("val");

                        switch (var)
                        {
                            case "anon_original_name":
                                name = val;
                                break;

                            case "anon_original_title":
                                title = val;
                                break;

                            case "anon_original_clan":
                                clanId = Integer.parseInt(val);
                                break;
                        }
                    }
                }
            }
            catch (Exception e)
            {
                LOGGER.warning("[AnonymousPvP] Memo restore fallback error " + e.getMessage());
            }

            if (name == null || title == null)
            {
                try (var con = L2DatabaseFactory.getInstance().getConnection();
                     var ps = con.prepareStatement("SELECT char_name, title FROM characters WHERE obj_Id=?"))
                {
                    ps.setInt(1, p.getObjectId());
                    try (var rs = ps.executeQuery())
                    {
                        if (rs.next())
                        {
                            if (name == null)
                                name = rs.getString("char_name");
                            if (title == null)
                                title = rs.getString("title");
                        }
                    }
                }
                catch (Exception e)
                {
                    LOGGER.warning("[AnonymousPvP] DB restore fallback error " + e.getMessage());
                }
            }
        }

        _fakeIdentity.remove(p.getObjectId());

        try
        {
            if (name != null)
                p.setName(name);

            if (title != null)
                p.setTitle(title);

            if (clanId == 0)
            {
                p.setClan(null);
            }
            else
            {
                Clan clan = ClanTable.getInstance().getClan(clanId);
                if (clan != null)
                    p.setClan(clan);
            }
        }
        catch (Exception ignored) {}
    }

    public static void applyAnonymousVisual(Player target, Player observer)
    {
        if (target == null || observer == null)
            return;

        if (isPlayerInEvent(target) && !target.equals(observer))
        {
            
            DressMe dress = target.getDress();

            if (dress != null)
            {
                target.setDress(dress);
            }
        }
        else
        {
            
            DressMe dress = target.getDress();

            if (dress != null)
                target.setDress(dress);
        }
    }

    /**
     * Skin storage getter
     * @param p 
     * @return 
     */
    public static int getAnonymousSkinId(Player p)
    {
        if (p == null)
            return 0;

        return _anonymousSkinId.getOrDefault(p.getObjectId(), 0);
    }

    public static int getAnonymousRaceId(Player p) 
    {
        return _anonymousRaceId.getOrDefault(p.getObjectId(), 1); 
    }


    private static void applyNoblesseBlessing(Player player)
    {
        if (player == null || player.isNoblesseBlessed())
        {
            return;
        }
        try
        {
            L2Skill noblesse = SkillTable.getInstance().getInfo(1323, 1);
            if (noblesse != null)
            {
                noblesse.getEffects(player, player);
            }
        } 
        catch (Exception e)
        {
            
        }
    }


    public static boolean shouldHideCrest(Player p, Player observer)
    {
        return isPlayerInEvent(p) && !p.equals(observer);
    }
    
    private static boolean enchantRandomEquippedItem(Player player)
    {
        if (player == null)
            return false;

        if (!Config.ANONYMOUS_PVP_ENABLE_ENCHANT)
            return false;

        if (Config.ANONYMOUS_PVP_EVENT_ENCHANT_CHANCE <= 0)
            return false;

        if (Config.ANONYMOUS_PVP_EVENT_ENCHANT_CHANCE < 100)
        {
            if (_rand.nextInt(100) >= Config.ANONYMOUS_PVP_EVENT_ENCHANT_CHANCE)
                return false;
        }

        // Get equipped items
        ItemInstance[] equipped = {
            player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_RHAND),
            player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_HEAD),
            player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_CHEST),
            player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_LEGS),
            player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_FEET),
            player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_GLOVES),
            player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_NECK),
            player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_LFINGER),
            player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_RFINGER)
        };

        List<ItemInstance> valid = new ArrayList<>();

        for (ItemInstance item : equipped)
        {
            if (item == null || item.isShadowItem())
                continue;

            int maxEnchant = 0;

            if (item == player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_RHAND))
                maxEnchant = Config.ANONYMOUS_PVP_EVENT_ENCHANT_WEAPON_MAX;
            else if (item == player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_HEAD) ||
                     item == player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_CHEST) ||
                     item == player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_LEGS) ||
                     item == player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_FEET) ||
                     item == player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_GLOVES))
                maxEnchant = Config.ANONYMOUS_PVP_EVENT_ENCHANT_ARMOR_MAX;
            else if (item == player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_NECK) ||
                     item == player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_LFINGER) ||
                     item == player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_RFINGER))
                maxEnchant = Config.ANONYMOUS_PVP_EVENT_ENCHANT_JEWELRY_MAX;

            if (maxEnchant <= 0 || item.getEnchantLevel() >= maxEnchant)
                continue;

            valid.add(item);
        }

        if (valid.isEmpty())
            return false;

        ItemInstance itemToEnchant = valid.get(_rand.nextInt(valid.size()));
        int newEnchant = itemToEnchant.getEnchantLevel() + 1;
        itemToEnchant.setEnchantLevel(newEnchant);

        player.sendMessage("Kill Streak Reward: Your " + itemToEnchant.getItemName() + " is now +" + newEnchant + "!");
        player.broadcastPacket(new MagicSkillUse(player, player, 2025, 1, 1000, 0));
        player.broadcastPacket(new SocialAction(player.getObjectId(), 3));
        player.broadcastUserInfo();

        return true;
    }
    
    // Utilitário: pegar local de spawn da zona
    private static int[] getZoneSpawnLocation() 
    {
        // Retorna uma das localizações do evento aleatoriamente
        return EVENT_LOCATIONS[_rand.nextInt(EVENT_LOCATIONS.length)];
    }

    // Utilitário: pegar localização da cidade do player 
    private static int[] getTownLocation(Player p)
    {
        // Retorna o centro de Giran para todos os players
        return new int[]{83400, 148600, -3400}; // Centro de Giran
    }

    // Classe auxiliar para guardar dados originais
    private static class OriginalData
    {
        String name;
        String title;
        int clanId;

        OriginalData(String n, String t, int c)
        {
            name = n;
            title = t;
            clanId = c;
        }
    }

    public static long scheduleNextEventTime()
    {
        long now = System.currentTimeMillis();
        long next = Long.MAX_VALUE;

        for (String timeStr : Config.ANONYMOUS_PVP_EVENT_INTERVAL)
        {
            String[] parts = timeStr.split(":");

            if (parts.length != 2)
                continue;

            int hour = Integer.parseInt(parts[0]);
            int min = Integer.parseInt(parts[1]);

            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(now);
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, min);
            cal.set(Calendar.SECOND, 0);

            long candidate = cal.getTimeInMillis();

            if (candidate <= now)
                candidate += 86400000; // next day

            if (candidate < next)
                next = candidate;
        }

        return next;
    }
    
    public static String getEventStatusHtml()
    {
        if (isEventActive())
        {
            int remaining = getRemainingTimeSeconds();

            int minutes = remaining / 60;
            int seconds = remaining % 60;

            return "<html><body>" +
                    "<center>" +
                    "<font color='00FF00'>Anonymous PvP Event is ACTIVE!</font><br>" +
                    "Remaining Time: " + minutes + "m " + seconds + "s<br><br>" +
                    "<button value='Join Event' action='bypass -h npc_%objectId%_anonymous_event_join' width=140 height=25 back='L2UI_CT1.Button_DF' fore='L2UI_CT1.Button_DF'>" +
                    "</center>" +
                    "</body></html>";
        }

        long now = System.currentTimeMillis();
        long next = scheduleNextEventTime(); // We will create this

        long diff = next - now;

        long minutes = diff > 0 ? diff / 60000 : 0;
        long seconds = diff > 0 ? (diff % 60000) / 1000 : 0;

        return "<html><body>" +
                "<center>" +
                "<font color='FF0000'>Anonymous PvP Event is NOT active</font><br>" +
                "Next Event In:<br>" +
                minutes + "m " + seconds + "s<br>" +
                "</center>" +
                "</body></html>";
    }
    
    public static String formatMenuTime(long nextEventTime)
    {
        if (isEventActive())
            return "In Progress";

        long diff = nextEventTime - System.currentTimeMillis();

        if (diff <= 0)
            return "00:00";

        long totalSeconds = diff / 1000;

        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        return String.format("%02d:%02d", minutes, seconds);
    }
    
    // Handler de comandos de voz
    @Override
    public boolean useVoicedCommand(String command, Player player, String target)
    {
        if (command.equalsIgnoreCase("join"))
        {
            register(player);
            return true;
        }
        if (command.equalsIgnoreCase("leave"))
        {
            unregister(player);
            return true;
        }
        return false;
    }

    @Override
    public String[] getVoicedCommandList()
    {
        return new String[] { "join", "leave" };
    }
}


