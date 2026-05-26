package phantom.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.commons.random.Rnd;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.actor.instance.RaidBoss;
import com.l2jmega.gameserver.model.L2Party;
import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.FakePlayerManager;
import phantom.model.FakeEmotion;

/**
 * Система помощи фантомов в рейд боссах.
 * Упрощенная версия для совместимости.
 */
@SuppressWarnings({"javadoc", "unused", "static-method"})
public class RaidPartyHelper {

   private static volatile RaidPartyHelper _instance;
   private Map<Integer, RaidHelperData> _activeRaids;
   private List<FakePlayer> _availableHelpers;
   private int _minPlayerLevel;
   private int _maxHelpersPerRaid;
   private int _helperSpawnRange;
   private int _helperDespawnDelay;
   private int _helpRequestChance;

   public static class RaidHelperData {
      public RaidBoss boss;
      public Player requester;
      public List<FakePlayer> helpers;
      public long startTime;
      public boolean isCompleted;

      public RaidHelperData(RaidBoss boss, Player requester) {
         this.boss = boss;
         this.requester = requester;
         this.helpers = new ArrayList<>();
         this.startTime = System.currentTimeMillis();
         this.isCompleted = false;
      }
   }

   private RaidPartyHelper() {
      _activeRaids = new ConcurrentHashMap<>();
      _availableHelpers = new CopyOnWriteArrayList<>();
      loadConfig();
      startCleanupTask();
   }

   public static RaidPartyHelper getInstance() {
      if (_instance == null) {
         synchronized (RaidPartyHelper.class) {
            if (_instance == null) {
               _instance = new RaidPartyHelper();
            }
         }
      }
      return _instance;
   }

   private void loadConfig() {
      _minPlayerLevel = FakePlayerConfig.FAKE_RAID_HELPER_MIN_PLAYER_LEVEL;
      _maxHelpersPerRaid = Math.max(1, FakePlayerConfig.FAKE_RAID_MAX_HELPERS);
      _helperSpawnRange = Math.max(50, FakePlayerConfig.FAKE_RAID_HELPER_SPAWN_RANGE);
      _helperDespawnDelay = Math.max(1000, FakePlayerConfig.FAKE_RAID_HELPER_DESPAWN_DELAY);
      _helpRequestChance = Math.max(0, Math.min(100, FakePlayerConfig.FAKE_RAID_HELP_REQUEST_CHANCE));
   }

   /**
    * Проверка, может ли игрок получить помощь
    */
   public void checkRaidHelpRequest(Player player) {
      if (!FakePlayerConfig.FAKE_RAID_HELPER_ENABLED || player == null || player.isDead()) {
         return;
      }

      if (player.getParty() != null && player.getParty().getPartyMembers().size() >= 9) {
         return;
      }

      if (player.getLevel() < _minPlayerLevel) {
         return;
      }

      if (_activeRaids.containsKey(player.getObjectId())) {
         return;
      }

      com.l2jmega.gameserver.model.WorldObject target = player.getTarget();
      if (!(target instanceof RaidBoss)) {
         return;
      }

      RaidBoss boss = (RaidBoss) target;

      if (Rnd.get(100) > _helpRequestChance) {
         return;
      }

      createHelpRequest(player, boss);
   }

   /**
    * Создать запрос помощи
    */
   private void createHelpRequest(Player player, RaidBoss boss) {
      if (FakePlayerConfig.FAKE_PLAYERS_DEBUG) {
         player.broadcastPacket(new com.l2jmega.gameserver.network.serverpackets.CreatureSay(
            player.getObjectId(), 0, player.getName(), "[RAID] need help!"));
      }

      assignHelpers(player, boss);
   }

   /**
    * Назначить помощников к запросу
    */
   private void assignHelpers(Player requester, RaidBoss boss) {
      int availableSlots = _maxHelpersPerRaid;
      if (requester.getParty() != null) {
         availableSlots = Math.min(_maxHelpersPerRaid, Math.max(0, 9 - requester.getParty().getPartyMembers().size()));
      }
      if (availableSlots <= 0) {
         return;
      }

      int helpersNeeded = Rnd.get(1, availableSlots);
      List<FakePlayer> assigned = new ArrayList<>();

      for (int i = 0; i < helpersNeeded; i++) {
         FakePlayer helper = spawnRaidHelper(requester, boss);
         if (helper != null) {
            assigned.add(helper);
            _availableHelpers.add(helper);
         }
      }

      RaidHelperData raidData = new RaidHelperData(boss, requester);
      raidData.helpers.addAll(assigned);
      _activeRaids.put(requester.getObjectId(), raidData);

      for (FakePlayer helper : assigned) {
         if (helper == null || !helper.isOnline()) {
            continue;
         }

         if (helper.getFakeAi() == null) {
            helper.assignDefaultAI();
         }

         helper.setTarget(boss);
         if (helper.getFakeAi() != null) {
            helper.getFakeAi().moveToPawn(boss, 100);
         }

         if (FakePlayerConfig.FAKE_PLAYERS_DEBUG) {
            helper.say("[RAID] here to help!");
         }
      }
   }

   /**
    * Заспавнить помощника для рейда
    */
   private FakePlayer spawnRaidHelper(Player requester, RaidBoss boss) {
      int spawnX = requester.getX() + Rnd.get(-_helperSpawnRange, _helperSpawnRange);
      int spawnY = requester.getY() + Rnd.get(-_helperSpawnRange, _helperSpawnRange);
      int spawnZ = requester.getZ();

      FakePlayer helper;
      int roleRoll = Rnd.get(100);

      if (roleRoll < 45) {
         helper = FakePlayerManager.spawnPlayer(spawnX, spawnY, spawnZ);
      } else if (roleRoll < 80) {
         helper = FakePlayerManager.spawnNuker(spawnX, spawnY, spawnZ);
      } else {
         helper = FakePlayerManager.spawnTanker(spawnX, spawnY, spawnZ);
      }

      if (helper == null) {
         return null;
      }

      helper.setFakeFarm(true);
      helper.setTarget(boss);

      if (requester.getParty() != null) {
         L2Party party = requester.getParty();
         if (party.getPartyMembers().size() < 9) {
            party.addPartyMember(helper);
         }
      }

      return helper;
   }

   /**
    * Обновление AI помощника
    */
   public void updateRaidHelperAI(FakePlayer helper, RaidHelperData raidData, FakeEmotion emotion) {
      if (raidData == null || raidData.boss == null || raidData.boss.isDead()) {
         return;
      }

      RaidBoss boss = raidData.boss;

      if (raidData.requester != null && !raidData.requester.isDead()) {
         Player requester = raidData.requester;
         double hpPercent = requester.getCurrentHp() / requester.getMaxHp() * 100;

         if (hpPercent < 50 && isHealer(helper)) {
            helper.setTarget(requester);
            return;
         }
      }

      helper.setTarget(boss);
   }

   /**
    * Проверка, является ли фантом хилером
    */
   private boolean isHealer(FakePlayer fake) {
      int classId = fake.getClassId().getId();
      return classId == 110 || classId == 111 || classId == 50 || classId == 49;
   }

   /**
    * Обработка смерти рейд босса
    */
   public void onRaidBossDeath(RaidBoss boss) {
      List<Integer> toRemove = new ArrayList<>();

      for (Map.Entry<Integer, RaidHelperData> entry : _activeRaids.entrySet()) {
         RaidHelperData data = entry.getValue();

         if (data.boss == boss || data.boss.getObjectId() == boss.getObjectId()) {
            data.isCompleted = true;

            for (FakePlayer helper : data.helpers) {
               if (helper != null && !helper.isDead()) {
                  if (Rnd.get(100) < 40) {
                     String[] phrases = {"gg", "nice drop?", "lets gooo", "ez", "w"};
                     helper.say(phrases[Rnd.get(0, phrases.length - 1)]);
                  }
                  if (helper.getEmotion() != null) {
                     helper.getEmotion().onKill(true);
                  }
               }
            }

            scheduleHelperDespawn(data);
            toRemove.add(entry.getKey());
         }
      }

      for (Integer key : toRemove) {
         _activeRaids.remove(key);
      }
   }

   /**
    * Запланировать деспавн помощников
    */
   private void scheduleHelperDespawn(RaidHelperData data) {
      ThreadPool.schedule(() -> {
         for (FakePlayer helper : data.helpers) {
            if (helper != null && helper.isOnline()) {
               helper.despawnPlayer();
               _availableHelpers.remove(helper);
            }
         }
      }, _helperDespawnDelay);
   }

   /**
    * Задача очистки
    */
   private void startCleanupTask() {
      ThreadPool.scheduleAtFixedRate(() -> {
         long now = System.currentTimeMillis();
         long timeout = 600000;

         _activeRaids.entrySet().removeIf(entry ->
            entry.getValue().isCompleted && now - entry.getValue().startTime > timeout);

         _availableHelpers.removeIf(helper ->
            helper == null || helper.isDead() || !helper.isOnline());

      }, 60000, 60000);
   }

   /**
    * Добавить фантома в доступные помощники
    */
   public void addAvailableHelper(FakePlayer helper) {
      if (helper != null && !_availableHelpers.contains(helper)) {
         _availableHelpers.add(helper);
      }
   }

   /**
    * Удалить фантома из доступных
    */
   public void removeAvailableHelper(FakePlayer helper) {
      _availableHelpers.remove(helper);
   }

   /**
    * Получить количество активных рейдов
    */
   public int getActiveRaidCount() {
      return _activeRaids.size();
   }

   /**
    * Получить количество доступных помощников
    */
   public int getAvailableHelperCount() {
      return _availableHelpers.size();
   }
}
