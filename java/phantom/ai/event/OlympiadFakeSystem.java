package phantom.ai.event;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.l2jmega.Config;
import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.commons.random.Rnd;
import com.l2jmega.gameserver.geoengine.GeoEngine;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.actor.ai.CtrlIntention;
import com.l2jmega.gameserver.model.actor.instance.OlympiadManagerNpc;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.item.kind.Weapon;
import com.l2jmega.gameserver.model.item.type.WeaponType;
import com.l2jmega.gameserver.model.location.Location;
import com.l2jmega.gameserver.network.serverpackets.CreatureSay;
import com.l2jmega.gameserver.util.Broadcast;
import com.l2jmega.gameserver.util.SiegeParticipationUtil;
import com.l2jmega.gameserver.model.olympiad.CompetitionType;
import com.l2jmega.gameserver.model.olympiad.Olympiad;
import com.l2jmega.gameserver.model.olympiad.OlympiadManager;
import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.FakePlayerManager;
import phantom.ai.CombatAI;
import phantom.ai.FakePlayerAI;
import phantom.ai.party.PartyFollowerAI;
import phantom.ai.shop.PrivateStoreBuyAI;
import phantom.ai.shop.PrivateStoreSellAI;
import phantom.helpers.FakeHelpers;
import phantom.model.FakeEmotion;

/**
 * Система фантомов для Олимпиады.
 * Автоматически регистрируются и участвуют в боях.
 */
public class OlympiadFakeSystem {

   private static final long STATS_RETENTION_MILLIS = 15 * 60 * 1000L;
   private static final long CLEANUP_INTERVAL_MILLIS = 5 * 60 * 1000L;
   
   private static OlympiadFakeSystem _instance;
   
   // Активные фантомы на олимпе
   private Map<Integer, FakePlayer> _activeOlyFakes;
   private Map<Integer, OlympiadFakeContext> _olympiadContexts;
   private Map<Integer, Long> _nextRegistrationAttemptTime;
   private Map<Integer, Long> _nextManagerActionTime;
   private Map<Integer, Long> _nextOlympiadShoutTime;
   private Map<Integer, Integer> _managerShuffleCounts;
   
   // Очередь фантомов для регистрации
   private List<FakePlayer> _registrationQueue;
   
   // Статистика
   private Map<Integer, OlyStats> _fakeStats;
   
   // Конфигурация
   private int _minFakeLevel;
   private int _maxFakeLevel;
   private int _fakeRegisterChance;
   private int _fixedClassedCount;
   private int _fixedNonClassedCount;
   private int _maxFakeClassedPerClass;
   private boolean _allowFake1v1;
   private long _nextQueueProfileRefresh;
    private long _nextCleanupTime;
   private int _backgroundClassedBudget;
   private int _backgroundNonClassedBudget;
   private static final String[] OLYMPIAD_SHOUTS = {
      "go oly",
      "gogo oly",
      "any one oly",
      "oly?",
      "come oly",
      "join oly",
      "oly fast",
      "who for oly"
   };
   private static final int[][] MANAGER_APPROACH_POINTS = {
      {90, 120},
      {90, -120},
      {140, 40},
      {140, -40},
      {160, 180},
      {160, -180},
      {220, 90},
      {220, -90},
      {250, 180},
      {250, -180},
      {300, 40},
      {300, -40},
      {320, 140},
      {320, -140},
      {360, 0},
      {420, 110},
      {420, -110},
      {470, 40},
      {470, -40},
      {520, 180},
      {520, -180},
      {560, 0},
      {620, 120},
      {620, -120},
      {680, 40},
      {680, -40},
      {740, 0},
      {260, 260},
      {260, -260},
      {420, 260},
      {420, -260},
      {580, 260},
      {580, -260}
   };
   
   public static class OlyStats {
      public int wins;
      public int losses;
      public int kills;
      public int deaths;
      public int damageDealt;
      public int damageReceived;
      public long lastFightTime;
      
      public OlyStats() {
         wins = 0;
         losses = 0;
         kills = 0;
         deaths = 0;
         damageDealt = 0;
         damageReceived = 0;
      }
      
      public void addWin() { wins++; }
      public void addLoss() { losses++; }
      public void addKill() { kills++; }
      public void addDeath() { deaths++; }
   }

   private static class OlympiadFakeContext {
      final FakePlayerAI originalAi;
      final boolean fakePvp;
      final boolean fakeFarm;
      final String mood;

      OlympiadFakeContext(FakePlayer fake) {
         originalAi = fake.getFakeAi();
         fakePvp = fake.isFakePvp();
         fakeFarm = fake.isFakeFarm();
         mood = fake.getMood();
      }
   }
   
   private OlympiadFakeSystem() {
      _activeOlyFakes = new ConcurrentHashMap<>();
      _olympiadContexts = new ConcurrentHashMap<>();
      _nextRegistrationAttemptTime = new ConcurrentHashMap<>();
      _nextManagerActionTime = new ConcurrentHashMap<>();
      _nextOlympiadShoutTime = new ConcurrentHashMap<>();
      _managerShuffleCounts = new ConcurrentHashMap<>();
      _registrationQueue = new ArrayList<>();
      _fakeStats = new ConcurrentHashMap<>();
      
      loadConfig();
      startRegistrationTask();
   }
   
   public static OlympiadFakeSystem getInstance() {
      if (_instance == null) {
         _instance = new OlympiadFakeSystem();
      }
      return _instance;
   }
   
   /**
    * Загрузка конфигурации
    */
   private void loadConfig() {
      _minFakeLevel = FakePlayerConfig.FAKE_OLYMPIAD_MIN_LEVEL;
      _maxFakeLevel = 85;
      _fakeRegisterChance = FakePlayerConfig.FAKE_OLYMPIAD_REGISTER_CHANCE;
      _fixedClassedCount = FakePlayerConfig.FAKE_OLYMPIAD_CLASSED_COUNT;
      _fixedNonClassedCount = FakePlayerConfig.FAKE_OLYMPIAD_NON_CLASSED_COUNT;
      _maxFakeClassedPerClass = FakePlayerConfig.FAKE_OLYMPIAD_MAX_PER_CLASS;
      _allowFake1v1 = FakePlayerConfig.FAKE_OLYMPIAD_ALLOW_1V1;
   }
   
   /**
    * Задача автоматической регистрации
    */
   private void startRegistrationTask() {
      ThreadPool.scheduleAtFixedRate(() -> {
         if (FakePlayerConfig.FAKE_OLYMPIAD_ENABLED && FakePlayerConfig.isAutomatedFakePopulationEnabled()) {
            processRegistrationQueue();
         }
      }, 30000, 15000); // Check every 15 seconds
   }
   
   /**
    * Обработка очереди регистрации
    */
   private void processRegistrationQueue() {
      if (!FakePlayerConfig.isAutomatedFakePopulationEnabled()) {
         return;
      }
      if (!Config.ALT_OLY_UNLIMITED_TEST_MODE && !Olympiad.getInstance().inCompPeriod()) {
         return;
      }

      refreshQueueProfileIfNeeded();
      cleanupStaleStateIfNeeded();
      seedAutonomousRegistrationQueue();
      trimRegisteredFakes();

      synchronized (_registrationQueue) {
         List<FakePlayer> toRemove = new ArrayList<>();
         
         for (FakePlayer fake : _registrationQueue) {
            if (!isEligibleOlympiadFake(fake)) {
               clearRegistrationState(fake);
               toRemove.add(fake);
               continue;
            }
            
            // Попытка регистрации
            final long now = System.currentTimeMillis();
            if (now < _nextRegistrationAttemptTime.getOrDefault(fake.getObjectId(), 0L)) {
               continue;
            }

            if (Rnd.get(100) < getAdjustedRegisterChance(fake)) {
               if (tryRegisterInOlympiad(fake)) {
                  clearRegistrationState(fake);
                  toRemove.add(fake);
               } else {
                  scheduleNextRegistrationAttempt(fake, false);
                  if (FakePlayerConfig.FAKE_PLAYERS_DEBUG)
                     fake.say("[OLY] registration failed");
               }
            } else {
               scheduleNextRegistrationAttempt(fake, false);
               if (FakePlayerConfig.FAKE_PLAYERS_DEBUG)
                  fake.say("[OLY] registration failed");
            }
         }
         
         _registrationQueue.removeAll(toRemove);
      }
   }

   private void seedAutonomousRegistrationQueue() {
      final int desiredTotal = Math.max(4, getDesiredFakeClassedCount() + getDesiredFakeNonClassedCount());
      final int currentTotal = countRegisteredFakeClassed() + countRegisteredFakeNonClassed() + getQueuedEligibleCount();
      int missing = desiredTotal - currentTotal;
      if (missing <= 0) {
         return;
      }

      final List<FakePlayer> candidates = new ArrayList<>();
      for (FakePlayer fake : FakePlayerManager.getFakePlayers()) {
         if (isAutonomousOlympiadCandidate(fake)) {
            candidates.add(fake);
         }
      }

      while (missing > 0 && !candidates.isEmpty()) {
         FakePlayer best = null;
         long bestScore = Long.MAX_VALUE;
         for (FakePlayer candidate : candidates) {
            final long score = getAutonomousCandidateScore(candidate);
            if (score < bestScore) {
               bestScore = score;
               best = candidate;
            }
         }

         if (best == null) {
            break;
         }

         candidates.remove(best);
         registerForOlympiad(best);
         missing--;
      }
   }
   
   /**
    * Register a fake player for olympiad participation.
    * @param fake fake player queued for registration
    */
   public void registerForOlympiad(FakePlayer fake) {
      if (!FakePlayerConfig.isAutomatedFakePopulationEnabled()) {
         return;
      }
      if (!isEligibleOlympiadFake(fake)) {
         return;
      }
      
      // Проверка уровня
      if (fake.getLevel() < _minFakeLevel || fake.getLevel() > _maxFakeLevel) {
         return;
      }
      
      // Проверка класса (только 3rd class)
      if (fake.getClassId().level() < 3) {
         return;
      }
      
      // Добавить в очередь
      synchronized (_registrationQueue) {
         if (!_registrationQueue.contains(fake)) {
            _registrationQueue.add(fake);
            scheduleInitialRegistrationAttempt(fake);
            
            if (FakePlayerConfig.FAKE_PLAYERS_DEBUG) {
               fake.say("[OLY] registered for olympiad!");
            }
         }
      }
   }

   public void onExternalRegistrationSuccess(FakePlayer fake) {
      if (fake == null) {
         return;
      }

      clearRegistrationState(fake);
      setWaitingForOlympiadMatch(fake, true);
      scheduleNextRegistrationAttempt(fake, true);
   }

   private int getQueuedEligibleCount() {
      int count = 0;
      synchronized (_registrationQueue) {
         for (FakePlayer fake : _registrationQueue) {
            if (isEligibleOlympiadFake(fake) && !OlympiadManager.getInstance().isRegistered(fake)) {
               count++;
            }
         }
      }
      return count;
   }

   private void scheduleInitialRegistrationAttempt(FakePlayer fake) {
      if (fake == null) {
         return;
      }

      final OlympiadManagerNpc manager = OlympiadManagerNpc.getGiranManager();
      final long now = System.currentTimeMillis();
      if (manager != null && fake.isInsideRadius(manager, 320, false, false)) {
         _nextRegistrationAttemptTime.put(fake.getObjectId(), now + Rnd.get(2000, 7000));
         return;
      }

      scheduleNextRegistrationAttempt(fake, false);
   }

   private boolean tryRegisterInOlympiad(FakePlayer fake) {
      if (!FakePlayerConfig.isAutomatedFakePopulationEnabled()) {
         return false;
      }
      if (!isEligibleOlympiadFake(fake)) {
         return false;
      }

      refreshQueueProfileIfNeeded();

      if (!approachOlympiadManager(fake)) {
         return false;
      }

      if (!fake.isNoble()) {
         fake.setNoble(true, true);
      }

      if (!fake.isNoble()) {
         return false;
      }

      final CompetitionType primaryType = chooseCompetitionType(fake);
      if (primaryType == null) {
         return false;
      }

      if (OlympiadManager.getInstance().registerNoble(fake, primaryType)) {
         fake.setOlympiadParticipant(true);
         setWaitingForOlympiadMatch(fake, true);
         scheduleNextRegistrationAttempt(fake, true);
         return true;
      }

      final CompetitionType secondaryType = (primaryType == CompetitionType.CLASSED) ? CompetitionType.NON_CLASSED : CompetitionType.CLASSED;
      if (!hasCapacityForType(fake, secondaryType)) {
         return false;
      }

      final boolean registered = OlympiadManager.getInstance().registerNoble(fake, secondaryType);
      if (registered) {
         fake.setOlympiadParticipant(true);
         setWaitingForOlympiadMatch(fake, true);
         scheduleNextRegistrationAttempt(fake, true);
      }
      return registered;
   }

   private CompetitionType chooseCompetitionType(FakePlayer fake) {
      final boolean classedEnabled = Config.OLY_CLASSED_FIGHT;
      final boolean nonClassedEnabled = _allowFake1v1;

      final boolean canJoinClassed = classedEnabled && hasCapacityForType(fake, CompetitionType.CLASSED);
      final boolean canJoinNonClassed = nonClassedEnabled && hasCapacityForType(fake, CompetitionType.NON_CLASSED);

      if (!canJoinClassed && !canJoinNonClassed) {
         return null;
      }

      if (!canJoinClassed) {
         return CompetitionType.NON_CLASSED;
      }

      if (!canJoinNonClassed) {
         return CompetitionType.CLASSED;
      }

      if (shouldPreferNonClassedAutonomous(fake)) {
         return CompetitionType.NON_CLASSED;
      }

      final int classedDeficit = getDesiredFakeClassedCount() - countRegisteredFakeClassed();
      final int nonClassedDeficit = getDesiredFakeNonClassedCount() - countRegisteredFakeNonClassed();

      if (classedDeficit > nonClassedDeficit) {
         return CompetitionType.CLASSED;
      }

      if (nonClassedDeficit > classedDeficit) {
         return CompetitionType.NON_CLASSED;
      }

      final int realClassedForClass = countRegisteredRealClassed(fake.getBaseClass());
      final int fakeClassedForClass = countRegisteredFakeClassed(fake.getBaseClass());
      if (realClassedForClass > fakeClassedForClass) {
         return CompetitionType.CLASSED;
      }

      return (Rnd.get(100) < 65) ? CompetitionType.NON_CLASSED : CompetitionType.CLASSED;
   }

   private void refreshQueueProfileIfNeeded() {
      final long now = System.currentTimeMillis();
      if (now < _nextQueueProfileRefresh) {
         return;
      }

      final Calendar calendar = Calendar.getInstance();
      final int hour = calendar.get(Calendar.HOUR_OF_DAY);
      final int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
      final boolean primeTime = hour >= 19 && hour <= 23;
      final boolean lowTime = hour <= 6 || (hour >= 8 && hour <= 10);
      final boolean weekend = dayOfWeek == Calendar.FRIDAY || dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY;
      final int realOnline = countOnlineRealPlayers();
      final int realOlympiadRegistered = countRegisteredRealClassed() + countRegisteredRealNonClassed();
      final int onlinePressure = Math.max(0, realOnline / 12);
      final int registrationPressure = Math.max(0, realOlympiadRegistered / 4);
      final int timeBias = primeTime ? 2 : (lowTime ? -1 : 0);
      final int weekendBias = weekend ? 1 : 0;
      _backgroundNonClassedBudget = Math.max(2, Math.min(12, 2 + onlinePressure + registrationPressure + timeBias + weekendBias + Rnd.get(-1, 1)));
      _backgroundClassedBudget = Math.max(0, Math.min(8, onlinePressure + (registrationPressure / 2) + timeBias + weekendBias + Rnd.get(-1, 1)));
      _nextQueueProfileRefresh = now + Rnd.get(90000, 210000);
   }

   private int getAdjustedRegisterChance(FakePlayer fake) {
      final int classedDeficit = getDesiredFakeClassedCount() - countRegisteredFakeClassed();
      final int nonClassedDeficit = getDesiredFakeNonClassedCount() - countRegisteredFakeNonClassed();
      final int strongestDeficit = Math.max(classedDeficit, nonClassedDeficit);

      if (strongestDeficit <= 0) {
         return 0;
      }

      if (strongestDeficit == 1) {
         return Math.max(10, _fakeRegisterChance / 3);
      }

      if (strongestDeficit == 2) {
         return Math.max(20, _fakeRegisterChance / 2);
      }

      if (fake != null) {
         final OlyStats stats = _fakeStats.get(fake.getObjectId());
         if (stats != null && stats.lastFightTime > 0 && System.currentTimeMillis() - stats.lastFightTime < 180000L) {
            return Math.min(15, _fakeRegisterChance / 4);
         }
      }

      return _fakeRegisterChance;
   }

   private boolean hasCapacityForType(FakePlayer fake, CompetitionType type) {
      if (type == CompetitionType.NON_CLASSED) {
         return countRegisteredFakeNonClassed() < getDesiredFakeNonClassedCount();
      }

      if (type != CompetitionType.CLASSED || fake == null) {
         return false;
      }

      if (countRegisteredFakeClassed() >= getDesiredFakeClassedCount()) {
         return false;
      }

      return countRegisteredFakeClassed(fake.getBaseClass()) < getDesiredFakeClassedPerClass(fake.getBaseClass());
   }

   private int getDesiredFakeNonClassedCount() {
      if (_fixedNonClassedCount >= 0) {
         return Math.max(0, _fixedNonClassedCount);
      }

      final int realNonClassed = countRegisteredRealNonClassed();
      final int desired = _backgroundNonClassedBudget + ((realNonClassed + 1) / 2);
      return Math.max(2, Math.min(14, desired));
   }

   private int getDesiredFakeClassedCount() {
      if (_fixedClassedCount >= 0) {
         return Math.max(0, _fixedClassedCount);
      }

      if (countRegisteredRealClassed() <= 0) {
         return 0;
      }

      final int realClassed = countRegisteredRealClassed();
      final int realClassBuckets = countRegisteredRealClassBuckets();
      final int desired = _backgroundClassedBudget + realClassBuckets + (realClassed / 4);
      return Math.max(2, Math.min(12, desired));
   }

   private boolean shouldPreferNonClassedAutonomous(FakePlayer fake) {
      if (fake == null) {
         return false;
      }

      if (_fixedClassedCount >= 0 || _fixedNonClassedCount >= 0) {
         return false;
      }

      if (countRegisteredRealClassed() > 0) {
         return false;
      }

      return _allowFake1v1;
   }

   private int getDesiredFakeClassedPerClass(int classId) {
      if (_fixedClassedCount >= 0) {
         return _maxFakeClassedPerClass > 0 ? Math.max(1, _maxFakeClassedPerClass) : 3;
      }

      final int realClassedForClass = countRegisteredRealClassed(classId);
      final int dynamicLimit;
      if (realClassedForClass > 0) {
         dynamicLimit = Math.min(3, 1 + (realClassedForClass / 2));
      } else {
         dynamicLimit = 2;
      }

      if (_maxFakeClassedPerClass > 0) {
         return Math.max(1, Math.min(dynamicLimit, _maxFakeClassedPerClass));
      }

      return dynamicLimit;
   }

   private void scheduleNextRegistrationAttempt(FakePlayer fake, boolean success) {
      if (fake == null) {
         return;
      }

      final long delay;
      if (success) {
         delay = Rnd.get(90000, 240000);
      } else {
         final int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
         final boolean primeTime = hour >= 19 && hour <= 23;
         final long baseDelay = primeTime ? Rnd.get(12000, 35000) : Rnd.get(20000, 55000);
         delay = baseDelay + Rnd.get(0, 12000);
      }

      _nextRegistrationAttemptTime.put(fake.getObjectId(), System.currentTimeMillis() + delay);
   }

   private void clearRegistrationState(FakePlayer fake) {
      if (fake == null) {
         return;
      }

      final Integer objectId = fake.getObjectId();
      _nextRegistrationAttemptTime.remove(objectId);
      _nextManagerActionTime.remove(objectId);
      _nextOlympiadShoutTime.remove(objectId);
      _managerShuffleCounts.remove(objectId);

      if (!OlympiadManager.getInstance().isRegistered(fake) && !fake.isInOlympiadMode() && fake.getOlympiadGameId() == -1) {
         setWaitingForOlympiadMatch(fake, false);
      }
   }

   private void trimRegisteredFakes() {
      trimNonClassedFakes();
      trimClassedFakes();
   }

   private void trimNonClassedFakes() {
      int fakeCount = countRegisteredFakeNonClassed();
      final int limit = getDesiredFakeNonClassedCount();
      if (fakeCount <= limit) {
         return;
      }

      final List<Integer> objectIds = new ArrayList<>(OlympiadManager.getInstance().getRegisteredNonClassBased());
      for (int index = objectIds.size() - 1; index >= 0 && fakeCount > limit; index--) {
         final Player player = World.getInstance().getPlayer(objectIds.get(index).intValue());
         if (player == null || !player.isPhantom()) {
            continue;
         }

         if (OlympiadManager.getInstance().unRegisterNoble(player)) {
            if (player instanceof FakePlayer) {
               setWaitingForOlympiadMatch((FakePlayer) player, false);
            }
            fakeCount--;
         }
      }
   }

   private void trimClassedFakes() {
      int fakeCount = countRegisteredFakeClassed();
      final int totalLimit = getDesiredFakeClassedCount();

      boolean hasClassOverflow = false;
      for (Map.Entry<Integer, List<Integer>> entry : OlympiadManager.getInstance().getRegisteredClassBased().entrySet()) {
         if (countRegisteredFakeClassed(entry.getKey().intValue()) > getDesiredFakeClassedPerClass(entry.getKey().intValue())) {
            hasClassOverflow = true;
            break;
         }
      }

      if (fakeCount <= totalLimit && !hasClassOverflow) {
         return;
      }

      for (Map.Entry<Integer, List<Integer>> entry : OlympiadManager.getInstance().getRegisteredClassBased().entrySet()) {
         final List<Integer> objectIds = entry.getValue();
         if (objectIds == null || objectIds.isEmpty()) {
            continue;
         }

         int fakeInClass = countRegisteredFakeClassed(entry.getKey().intValue());
         final int classLimit = getDesiredFakeClassedPerClass(entry.getKey().intValue());
         if (fakeInClass <= classLimit && fakeCount <= totalLimit) {
            continue;
         }

         for (int index = objectIds.size() - 1; index >= 0 && (fakeInClass > classLimit || fakeCount > totalLimit); index--) {
            final Player player = World.getInstance().getPlayer(objectIds.get(index).intValue());
            if (player == null || !player.isPhantom()) {
               continue;
            }

            if (OlympiadManager.getInstance().unRegisterNoble(player)) {
               if (player instanceof FakePlayer) {
                  setWaitingForOlympiadMatch((FakePlayer) player, false);
               }
               fakeInClass--;
               fakeCount--;
            }
         }
      }
   }

   private static int countOnlineRealPlayers() {
      int count = 0;
      for (Player player : World.getInstance().getPlayers()) {
         if (player != null && player.isOnline() && !player.isPhantom()) {
            count++;
         }
      }
      return count;
   }

   private static int countRegisteredFakeNonClassed() {
      int count = 0;
      for (Integer objectId : OlympiadManager.getInstance().getRegisteredNonClassBased()) {
         final Player player = World.getInstance().getPlayer(objectId.intValue());
         if (player != null && player.isPhantom()) {
            count++;
         }
      }
      return count;
   }

   private static int countRegisteredRealNonClassed() {
      int count = 0;
      for (Integer objectId : OlympiadManager.getInstance().getRegisteredNonClassBased()) {
         final Player player = World.getInstance().getPlayer(objectId.intValue());
         if (player != null && !player.isPhantom()) {
            count++;
         }
      }
      return count;
   }

   private static int countRegisteredFakeClassed() {
      int count = 0;
      for (List<Integer> entries : OlympiadManager.getInstance().getRegisteredClassBased().values()) {
         if (entries == null) {
            continue;
         }

         for (Integer objectId : entries) {
            final Player player = World.getInstance().getPlayer(objectId.intValue());
            if (player != null && player.isPhantom()) {
               count++;
            }
         }
      }
      return count;
   }

   private static int countRegisteredRealClassed() {
      int count = 0;
      for (List<Integer> entries : OlympiadManager.getInstance().getRegisteredClassBased().values()) {
         if (entries == null) {
            continue;
         }

         for (Integer objectId : entries) {
            final Player player = World.getInstance().getPlayer(objectId.intValue());
            if (player != null && !player.isPhantom()) {
               count++;
            }
         }
      }
      return count;
   }

   private static int countRegisteredRealClassBuckets() {
      int count = 0;
      for (Map.Entry<Integer, List<Integer>> entry : OlympiadManager.getInstance().getRegisteredClassBased().entrySet()) {
         if (entry.getValue() == null || entry.getValue().isEmpty()) {
            continue;
         }

         if (countRegisteredRealClassed(entry.getKey().intValue()) > 0) {
            count++;
         }
      }
      return count;
   }

   private static int countRegisteredFakeClassed(int classId) {
      int count = 0;
      final List<Integer> entries = OlympiadManager.getInstance().getRegisteredClassBased().get(classId);
      if (entries == null) {
         return 0;
      }

      for (Integer objectId : entries) {
         final Player player = World.getInstance().getPlayer(objectId.intValue());
         if (player != null && player.isPhantom()) {
            count++;
         }
      }
      return count;
   }

   private static int countRegisteredRealClassed(int classId) {
      int count = 0;
      final List<Integer> entries = OlympiadManager.getInstance().getRegisteredClassBased().get(classId);
      if (entries == null) {
         return 0;
      }

      for (Integer objectId : entries) {
         final Player player = World.getInstance().getPlayer(objectId.intValue());
         if (player != null && !player.isPhantom()) {
            count++;
         }
      }
      return count;
   }
   
   /**
    * Handle olympiad fight start.
    * @param players players taking part in the match
    */
   public void onOlympiadStart(List<Player> players) {
      for (Player player : players) {
         if (player instanceof FakePlayer) {
            FakePlayer fake = (FakePlayer) player;
            _activeOlyFakes.put(fake.getObjectId(), fake);
            _olympiadContexts.putIfAbsent(fake.getObjectId(), new OlympiadFakeContext(fake));
            prepareFakeForOlympiad(fake);

            // Инициализировать статистику
            _fakeStats.putIfAbsent(fake.getObjectId(), new OlyStats());

            // Установить эмоцию для PvP
            fake.getEmotion().setAggression(70);

            if (FakePlayerConfig.FAKE_PLAYERS_DEBUG) {
               fake.say("[OLY] fight started!");
            }
         }
      }
   }

   /**
    * Handle olympiad fight end.
    * @param winners winning players
    * @param losers losing players
    */
   public void onOlympiadEnd(List<Player> winners, List<Player> losers) {
      // Обработка победителей
      for (Player player : winners) {
         if (player instanceof FakePlayer) {
            FakePlayer fake = (FakePlayer) player;
            OlyStats stats = _fakeStats.get(fake.getObjectId());
            if (stats != null) {
               stats.addWin();
               fake.getEmotion().onKill(true); // Boost morale

            }
         }
      }

      // Обработка проигравших
      for (Player player : losers) {
         if (player instanceof FakePlayer) {
            FakePlayer fake = (FakePlayer) player;
            OlyStats stats = _fakeStats.get(fake.getObjectId());
            if (stats != null) {
               stats.addLoss();
               fake.getEmotion().onDeath(true);

            }
         }
      }

      // Очистка активных
      for (Player player : winners) {
         _activeOlyFakes.remove(player.getObjectId());
      }
      for (Player player : losers) {
         _activeOlyFakes.remove(player.getObjectId());
      }
   }

   public void clearActiveFakes(List<Player> players) {
      if (players == null) {
         return;
      }

      for (Player player : players) {
         if (player == null) {
            continue;
         }

         if (player instanceof FakePlayer) {
            restoreFakeAfterOlympiad((FakePlayer) player);
         }

         _activeOlyFakes.remove(player.getObjectId());
         final OlyStats stats = _fakeStats.get(player.getObjectId());
         if (stats != null) {
            stats.lastFightTime = System.currentTimeMillis();
         }
      }
   }

   public void forgetFake(FakePlayer fake) {
      if (fake == null) {
         return;
      }

      synchronized (_registrationQueue) {
         _registrationQueue.remove(fake);
      }

      _activeOlyFakes.remove(fake.getObjectId());
      _olympiadContexts.remove(fake.getObjectId());
      _fakeStats.remove(fake.getObjectId());
   }
   
   /**
    * Update olympiad AI state for an active fake player.
    * @param fake active fake player
    * @param emotion emotion state used by the AI
    */
   public void updateOlympiadAI(FakePlayer fake, FakeEmotion emotion) {
      if (!_activeOlyFakes.containsKey(fake.getObjectId())) {
         return;
      }
      
      // Олимпийский AI более агрессивный
      emotion.setAggression(80);
      
      // Приоритет целей
      Player target = getBestOlyTarget(fake);
      if (target != null) {
         fake.setTarget(target);
      }
      
      // Использование всех кулдаунов
   }
   
   /**
    * Select the best target for olympiad combat.
    * @param fake fake player searching for a target
    * @return selected target or {@code null} when none is available
    */
   private Player getBestOlyTarget(FakePlayer fake) {
      List<Player> potentialTargets = new ArrayList<>();
      
      // Собрать всех противников
      for (Player player : _activeOlyFakes.values()) {
         if (player != fake && !player.isDead() &&
             player.getOlympiadGameId() == fake.getOlympiadGameId() &&
             fake.isInsideRadius(player, 1500, false, false)) {
            potentialTargets.add(player);
         }
      }
      
      if (potentialTargets.isEmpty()) {
         return null;
      }
      
      // Keep selection simple to avoid hard dependency issues.
      return potentialTargets.get(0);
   }
   
   /**
    * Get stored statistics for a fake player.
    * @param objectId fake player object id
    * @return stats for the fake player or {@code null} if absent
    */
   public OlyStats getFakeStats(int objectId) {
      return _fakeStats.get(objectId);
   }
   
   /**
    * Get the current count of active olympiad fakes.
    * @return active fake player count
    */
   public int getActiveFakeCount() {
      return _activeOlyFakes.size();
   }
   
   /**
    * Get the current registration queue size.
    * @return number of queued fake players
    */
   public int getQueueSize() {
      return _registrationQueue.size();
   }

   private void cleanupStaleStateIfNeeded() {
      final long now = System.currentTimeMillis();
      if (now < _nextCleanupTime) {
         return;
      }

      _nextCleanupTime = now + CLEANUP_INTERVAL_MILLIS;

      synchronized (_registrationQueue) {
         _registrationQueue.removeIf(fake -> fake == null || !fake.isOnline() || fake.isDead());
      }

      _activeOlyFakes.entrySet().removeIf(entry -> {
         final FakePlayer fake = entry.getValue();
         final boolean remove = fake == null || !fake.isOnline() || fake.isDead();
         if (remove) {
            _olympiadContexts.remove(entry.getKey());
         }
         return remove;
      });

      _fakeStats.entrySet().removeIf(entry -> {
         if (_activeOlyFakes.containsKey(entry.getKey())) {
            return false;
         }

         final Player player = World.getInstance().getPlayer(entry.getKey().intValue());
         if (player != null && player.isPhantom() && player.isOnline()) {
            return false;
         }

         final OlyStats stats = entry.getValue();
         return stats == null || stats.lastFightTime <= 0L || now - stats.lastFightTime > STATS_RETENTION_MILLIS;
      });
   }

   private static void prepareFakeForOlympiad(FakePlayer fake) {
      if (fake == null) {
         return;
      }

      setWaitingForOlympiadMatch(fake, false);

      if (fake.getFakeAi() == null || fake.getFakeAi() instanceof PartyFollowerAI || !(fake.getFakeAi() instanceof CombatAI)) {
         fake.assignDefaultAI();
      }

      fake.setFakePvp(true);
      fake.setFakeFarm(false);
      fake.setMood("");
      fake.setIsRunning(true);
      FakeHelpers.ensureOlympiadLoadout(fake);
      ensureOlympiadConsumables(fake);
      fake.heal();
      fake.broadcastUserInfo();
   }

   private static void ensureOlympiadConsumables(FakePlayer fake) {
      if (fake == null) {
         return;
      }

      final int physicalShotItemId = getOlympiadPhysicalShotItemId(fake.getLevel());
      if (physicalShotItemId > 0) {
         ensureItemCount(fake, physicalShotItemId, 3000);
      }

      final int magicalShotItemId = getOlympiadMagicalShotItemId(fake.getLevel());
      if (magicalShotItemId > 0) {
         ensureItemCount(fake, magicalShotItemId, 3000);
      }

      final int arrowItemId = getOlympiadArrowItemId(fake);
      if (arrowItemId > 0) {
         ensureItemCount(fake, arrowItemId, 1500);
      }

      fake.getAutoSoulShot().clear();

      final int preferredShotItemId = getOlympiadShotItemId(fake);
      if (preferredShotItemId > 0) {
         fake.addAutoSoulShot(preferredShotItemId);
      }
      if (physicalShotItemId > 0 && physicalShotItemId != preferredShotItemId) {
         fake.addAutoSoulShot(physicalShotItemId);
      }
      if (magicalShotItemId > 0 && magicalShotItemId != preferredShotItemId) {
         fake.addAutoSoulShot(magicalShotItemId);
      }
      fake.rechargeShots(true, true);
   }

   private static void ensureItemCount(FakePlayer fake, int itemId, int amount) {
      if (itemId <= 0 || amount <= 0) {
         return;
      }

      // Use Player.addItem so bow arrows trigger checkAndEquipArrows (inventory-only add skips it).
      if (fake.getInventory().getItemByItemId(itemId) == null) {
         fake.addItem("", itemId, amount, null, false);
      } else if (fake.getInventory().getItemByItemId(itemId).getCount() < amount) {
         fake.addItem("", itemId, amount - fake.getInventory().getItemByItemId(itemId).getCount(), null, false);
      }
   }

   private static int getOlympiadShotItemId(FakePlayer fake) {
      final Weapon weapon = fake.getActiveWeaponItem();
      final boolean bowWeapon = weapon != null && weapon.getItemType() == WeaponType.BOW;
      final boolean magicalWeapon = weapon != null && weapon.isMagical() && !bowWeapon;
      return magicalWeapon ? getOlympiadMagicalShotItemId(fake.getLevel()) : getOlympiadPhysicalShotItemId(fake.getLevel());
   }

   private static int getOlympiadPhysicalShotItemId(int level) {
      if (level < 20) {
         return 1835;
      }
      if (level < 40) {
         return 1463;
      }
      if (level < 52) {
         return 1464;
      }
      if (level < 61) {
         return 1465;
      }
      if (level < 76) {
         return 1466;
      }
      return FakePlayerConfig.FAKE_PLAYER_SOULSHOT > 0 ? FakePlayerConfig.FAKE_PLAYER_SOULSHOT : 1467;
   }

   private static int getOlympiadMagicalShotItemId(int level) {
      if (level < 20) {
         return 3947;
      }
      if (level < 40) {
         return 3948;
      }
      if (level < 52) {
         return 3949;
      }
      if (level < 61) {
         return 3950;
      }
      if (level < 76) {
         return 3951;
      }
      return FakePlayerConfig.FAKE_PLAYER_BLESSED_SOULSHOT > 0 ? FakePlayerConfig.FAKE_PLAYER_BLESSED_SOULSHOT : 3952;
   }

   private static int getOlympiadArrowItemId(FakePlayer fake) {
      final Weapon weapon = fake.getActiveWeaponItem();
      if (weapon == null || weapon.getItemType() != WeaponType.BOW) {
         return 0;
      }
      return FakeHelpers.getArrowItemIdForWeapon(weapon, fake.getLevel());
   }

   private boolean approachOlympiadManager(FakePlayer fake) {
      final OlympiadManagerNpc manager = OlympiadManagerNpc.getGiranManager();
      if (manager == null) {
         return true;
      }

      final int objectId = fake.getObjectId();
      final long now = System.currentTimeMillis();

      if (fake.getFakeAi() == null) {
         fake.assignDefaultAI();
      }

      clearOlympiadApproachCombatState(fake);

      if (fake.isInsideRadius(manager, 220, false, false)) {
         maybeShoutForOlympiad(fake, now);
         Long nextActionAtBoxed = _nextManagerActionTime.get(objectId);
         if (nextActionAtBoxed == null) {
            nextActionAtBoxed = now + Rnd.get(1200, 4500);
            _nextManagerActionTime.put(objectId, nextActionAtBoxed);
         }
         final long nextActionAt = nextActionAtBoxed.longValue();
         final int shuffles = _managerShuffleCounts.getOrDefault(objectId, 0);

         if (now < nextActionAt) {
            return false;
         }

         if (shuffles == 0 && Rnd.get(100) < 28) {
            _managerShuffleCounts.put(objectId, 1);
            _nextManagerActionTime.put(objectId, now + Rnd.get(1500, 4000));
            final int sidestepX = manager.getX() + Rnd.get(-140, 140);
            final int sidestepY = manager.getY() + Rnd.get(-140, 140);
            fake.getFakeAi().moveTo(sidestepX, sidestepY, manager.getZ());
            return false;
         }

         if (Rnd.get(100) < 8) {
            _nextManagerActionTime.put(objectId, now + Rnd.get(18000, 42000));
            final int resetX = manager.getX() + Rnd.get(-260, 260);
            final int resetY = manager.getY() + Rnd.get(-260, 260);
            fake.getFakeAi().moveTo(resetX, resetY, manager.getZ());
            return false;
         }

         _nextManagerActionTime.put(objectId, now + Rnd.get(30000, 70000));
         return true;
      }

      if (fake.getFakeAi() == null) {
         fake.assignDefaultAI();
      }

      if (fake.getFakeAi() == null) {
         return false;
      }

      fake.setIsRunning(true);

      if (!fake.isInsideRadius(manager, 2000, false, false)) {
         final Location spawnPoint = getSafeManagerPoint(manager, 180, 320);
         fake.teleToLocation(spawnPoint, 0);
         _nextManagerActionTime.put(objectId, now + Rnd.get(1500, 4000));
         return false;
      }

      final Location approachPoint = getSafeManagerPoint(manager, 70, 140);
      fake.getFakeAi().moveTo(approachPoint.getX(), approachPoint.getY(), approachPoint.getZ());
      _nextManagerActionTime.put(objectId, now + Rnd.get(1000, 2800));
      return false;
   }

   public static boolean isEligibleOlympiadFake(FakePlayer fake) {
      if (fake == null || fake.isDead() || !fake.isOnline()) {
         return false;
      }

      if (SiegeParticipationUtil.isPlayerOrClanInActiveSiege(fake)) {
         return false;
      }

      if (fake.isInOlympiadMode() || fake.getOlympiadGameId() != -1 || fake.isOlympiadProtection()) {
         return false;
      }

      if (OlympiadManager.getInstance().isRegistered(fake)) {
         return false;
      }

      if (fake.isInStoreMode()) {
         return false;
      }

      if (fake.isFakeFarm() || fake.isFakePvp() || fake.isFakeEvent() || fake.isFakeKTBEvent() || fake.isTour()) {
         return false;
      }

      if (fake._inEventTvT || fake._inEventCTF) {
         return false;
      }

      if (fake.isArena1x1() || fake.isArena2x2() || fake.isArena5x5() || fake.isArena9x9() || fake.isInArenaEvent() || fake.isArenaAttack() || fake.isArenaProtection() || fake.isTournamentTeleport()) {
         return false;
      }

      if (fake.isUnderControl() || fake.getParty() != null) {
         return false;
      }

      final FakePlayerAI fakeAi = fake.getFakeAi();
      return !(fakeAi instanceof PrivateStoreSellAI) && !(fakeAi instanceof PrivateStoreBuyAI);
   }

   private static boolean isAutonomousOlympiadCandidate(FakePlayer fake) {
      if (!isEligibleOlympiadFake(fake)) {
         return false;
      }

      if (fake.isInOlympiadMode() || OlympiadManager.getInstance().isRegistered(fake)) {
         return false;
      }

      if (fake.isFakeFarm() || fake.isFakePvp() || fake.isFakeEvent() || fake.isFakeKTBEvent() || fake.isTour()) {
         return false;
      }

      if (fake.isArena1x1() || fake.isArena2x2() || fake.isArena5x5() || fake.isInArenaEvent() || fake.isArenaAttack() || fake.isArenaProtection() || fake.isTournamentTeleport()) {
         return false;
      }

      if (fake.isUnderControl() || fake.getParty() != null || fake.isInCombat() || fake.isCastingNow()) {
         return false;
      }

      return true;
   }

   private static long getAutonomousCandidateScore(FakePlayer fake) {
      long score = Rnd.get(0, 40);
      final OlympiadManagerNpc manager = OlympiadManagerNpc.getGiranManager();
      if (manager != null) {
         final long distanceSq = (long) fake.getDistanceSq(manager);
         if (fake.isInsideRadius(manager, 320, false, false)) {
            score -= 400;
         } else if (fake.isInsideRadius(manager, 1400, false, false)) {
            score -= 120;
         } else {
            score += Math.min(6000L, distanceSq / 1000L);
         }
      }

      if (fake.isInsideZone(com.l2jmega.gameserver.model.zone.ZoneId.TOWN) || fake.isInsideZone(com.l2jmega.gameserver.model.zone.ZoneId.PEACE)) {
         score -= 50;
      }

      return score;
   }

   private void restoreFakeAfterOlympiad(FakePlayer fake) {
      if (fake == null) {
         return;
      }

      clearRegistrationState(fake);
      setWaitingForOlympiadMatch(fake, false);

      final OlympiadFakeContext context = _olympiadContexts.remove(fake.getObjectId());
      if (context == null) {
         return;
      }

      fake.setFakePvp(context.fakePvp);
      fake.setFakeFarm(context.fakeFarm);
      fake.setMood(context.mood);
      if (context.originalAi != null) {
         fake.setFakeAi(context.originalAi);
         context.originalAi.setBusyThinking(false);
      }

      // Return fake to regular (non-olympiad) equipment profile after olympiad ends.
      FakeHelpers.ensureCombatLoadout(fake);
      fake.broadcastUserInfo();
   }

   private void maybeShoutForOlympiad(FakePlayer fake, long now) {
      if (fake == null || fake.isDead() || fake.isInOlympiadMode()) {
         return;
      }

      if (!Config.ALT_OLY_UNLIMITED_TEST_MODE && !Olympiad.getInstance().inCompPeriod()) {
         return;
      }

      if (!fake.isInsideZone(com.l2jmega.gameserver.model.zone.ZoneId.TOWN) && !fake.isInsideZone(com.l2jmega.gameserver.model.zone.ZoneId.PEACE)) {
         return;
      }

      final long nextShoutAt = _nextOlympiadShoutTime.getOrDefault(fake.getObjectId(), 0L);
      if (now < nextShoutAt) {
         return;
      }

      _nextOlympiadShoutTime.put(fake.getObjectId(), now + Rnd.get(120000, 300000));
      if (Rnd.get(100) >= 18) {
         return;
      }

      final String phrase = OLYMPIAD_SHOUTS[Rnd.get(OLYMPIAD_SHOUTS.length)];
      Broadcast.toAllOnlinePlayers(new CreatureSay(fake.getObjectId(), 1, fake.getName(), phrase));
   }

   private static void clearOlympiadApproachCombatState(FakePlayer fake) {
      if (fake == null) {
         return;
      }

      if (fake.getTarget() != null) {
         fake.setTarget(null);
      }

      fake.abortAttack();
      fake.abortCast();
      fake.getAI().setIntention(CtrlIntention.IDLE);
   }

   private static Location getSafeManagerPoint(OlympiadManagerNpc manager, int minRadius, int maxRadius) {
      final int baseZ = GeoEngine.getInstance().getHeight(manager.getX(), manager.getY(), manager.getZ());

      for (int[] point : MANAGER_APPROACH_POINTS) {
         final int x = manager.getX() + point[0];
         final int y = manager.getY() + point[1];
         final int z = GeoEngine.getInstance().getHeight(x, y, baseZ);
         final Location destiny = GeoEngine.getInstance().canMoveToTargetLoc(manager.getX(), manager.getY(), baseZ, x, y, z);
         if (destiny != null) {
            return destiny;
         }
      }

      for (int attempt = 0; attempt < 12; attempt++) {
         final int radius = Rnd.get(minRadius, maxRadius);
         final int x = manager.getX() + Rnd.get(-radius, radius);
         final int y = manager.getY() + Rnd.get(-radius, radius);
         final int z = GeoEngine.getInstance().getHeight(x, y, baseZ);
         final Location destiny = GeoEngine.getInstance().canMoveToTargetLoc(manager.getX(), manager.getY(), baseZ, x, y, z);
         if (destiny != null) {
            return destiny;
         }
      }

      return new Location(manager.getX() + 180, manager.getY(), baseZ);
   }

   private static void setWaitingForOlympiadMatch(FakePlayer fake, boolean waiting) {
      if (fake == null) {
         return;
      }

      fake.setUnderControl(waiting);
      fake.setTarget(null);
      fake.abortAttack();
      fake.abortCast();
      fake.stopMove(null);

      if (fake.getFakeAi() != null) {
         fake.getFakeAi().setBusyThinking(waiting);
      }

      if (waiting) {
         fake.setIsRunning(false);
         fake.getAI().setIntention(CtrlIntention.IDLE);
      } else {
         fake.setIsRunning(true);
      }
   }
}
