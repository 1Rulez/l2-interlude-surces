package phantom.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import com.l2jmega.gameserver.geoengine.GeoEngine;
import com.l2jmega.gameserver.model.L2Skill;
import com.l2jmega.gameserver.model.ShotType;
import com.l2jmega.gameserver.model.WorldObject;
import com.l2jmega.gameserver.model.actor.Creature;
import com.l2jmega.gameserver.model.actor.Npc;
import com.l2jmega.gameserver.model.actor.Summon;
import com.l2jmega.gameserver.model.actor.instance.ControlTower;
import com.l2jmega.gameserver.model.actor.instance.Door;
import com.l2jmega.gameserver.model.actor.instance.FlameTower;
import com.l2jmega.gameserver.model.actor.instance.Guard;
import com.l2jmega.gameserver.model.actor.instance.HolyThing;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.entity.Castle;
import com.l2jmega.gameserver.model.entity.Siege;
import com.l2jmega.gameserver.model.entity.Siege.SiegeSide;
import com.l2jmega.gameserver.model.location.Location;
import com.l2jmega.gameserver.model.zone.ZoneId;
import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.commons.random.Rnd;
import com.l2jmega.events.CTF;
import Base.custom.event.AnonymousPvPEvent;
import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.model.FakeEmotion;
import phantom.model.OffensiveSpell;

/**
 * Улучшенный CombatAI с интеграцией всех новых систем:
 * - Эмоции
 * - Позиционирование (kiting, strafe, LOS)
 * - Приоритеты целей
 * - CC chain
 * - Party PvP координация
 */
@SuppressWarnings({"javadoc", "unused", "static-method"})
public abstract class AdvancedCombatAI extends CombatAI {
   private static final int TOURNAMENT_TARGET_RADIUS = 3000;
   private static final int SIEGE_NEARBY_ALLY_RADIUS = 700;
   private static final int SIEGE_FRONTLINE_FOLLOW_OFFSET = 100;
   private static final int SIEGE_BACKLINE_FOLLOW_OFFSET = 180;
   private static final int SIEGE_BACKLINE_MAX_GAP = 420;
   private static final int SIEGE_FRONTLINE_MAX_GAP = 320;
   private static final int SIEGE_LEADER_HARD_REGROUP_DISTANCE = 2200;
   private static final int SIEGE_OVERCROWD_RADIUS = 180;
   private static final int SIEGE_OVERCROWD_LIMIT = 4;
   private static final long SIEGE_REGROUP_TIMEOUT_MS = 12000L;
   private static final long SIEGE_LEADER_FAILOVER_TIMEOUT_MS = 8000L;
   private static final int SIEGE_HEAL_MASS_HP_THRESHOLD = 55;
   private static final int SIEGE_HEAL_MIN_TARGETS = 2;
   private static final double SIEGE_SINGLE_HEAL_TRIGGER_HP_PERCENT = 88.0;
   private static final int SIEGE_THREAT_RADIUS = 520;
   private static final int SIEGE_THREAT_SWITCH_COOLDOWN_MS = 1200;
   private static final int SIEGE_THREAT_FOCUS_MS = 3200;
   private static final int SIEGE_ENEMY_MAX_Z_DIFF = 220;
   private static final int SIEGE_GATE_ENEMY_RADIUS = 450;
   private static final int SIEGE_DOOR_APPROACH_OFFSET = 170;
   private static final int SIEGE_DOOR_ASSAULT_BASE_OFFSET = 108;
   private static final int SIEGE_DOOR_ASSAULT_COLUMNS = 5;
   private static final int SIEGE_DOOR_ASSAULT_LATERAL_STEP = 52;
   private static final int SIEGE_DOOR_ASSAULT_ROW_STEP = 24;
   private static final int SIEGE_DOOR_SLOT_REACHED_RADIUS = 65;
   private static final int SIEGE_LEADER_WAIT_ENTRY_RADIUS = 1900;
   private static final long SIEGE_PROGRESS_SAMPLE_MS = 2500L;
   private static final int SIEGE_STUCK_MIN_PROGRESS = 70;
   private static final int SIEGE_STUCK_REPATH_TICKS = 2;
   private static final int SIEGE_STUCK_TELEPORT_TICKS = 4;
   private static final long SIEGE_UNSTUCK_COOLDOWN_MS = 7000L;
   private static final int CLEANSE_SKILL_ID = 1409;
   private static final double OLY_HEALER_EMERGENCY_HP_PERCENT = 45.0;
   private static final double OLY_HEALER_RECOVERY_HP_PERCENT = 82.0;
   private static final double OLY_HEALER_SAFE_HP_FOR_BURN_PERCENT = 62.0;
   private static final double OLY_HEALER_MIN_MP_FOR_BURN_PERCENT = 18.0;
   private static final double OLY_HEALER_TARGET_MIN_MP_PERCENT = 14.0;
   private static final double OLY_HEALER_FINISH_WINDOW_HP_PERCENT = 32.0;
   private static final int OLY_HEALER_MIN_GAP = 220;
   protected enum FakeRole {
      TANK,
      HEALER,
      DPS_MELEE,
      DPS_RANGE,
      SUPPORT,
      ASSASSIN
   }

   // Компоненты AI
   protected AdvancedPositioning _positioning;
   protected PartyPvPCoordinator _partyCoordinator;
   
   // Состояния
   protected FakeRole _fakeRole;
   protected long _lastPositionUpdate;
   protected long _lastCCCheck;
   protected long _lastEmotionUpdate;
   protected boolean _isUsingCC;
   protected boolean _isKiting;
   protected boolean _isRetreating;
   
   // Human-like задержки
   protected long _nextActionTime;
   protected long _nextOlympiadPrepActionTime;
   protected long _lastOlympiadTargetUpdate;
   protected long _olympiadFightStartedAt;
   protected long _nextOlympiadStrafeTime;
   protected long _nextCtfObjectiveDecisionTime;
   protected long _ctfObjectiveExpireTime;
   protected int _ctfObjectiveFlagIndex;
   protected int _errorCount;
   protected long _siegeRegroupStartedAt;
   protected long _siegeLeaderLastSeenAt;
   protected int _lastSiegeLeaderObjectId;
   protected long _lastSiegeThreatSwitchAt;
   protected long _siegeThreatFocusUntilMs;
   protected long _lastSiegeProgressSampleAt;
   protected int _lastSiegeProgressX;
   protected int _lastSiegeProgressY;
   protected int _lastSiegeProgressZ;
   protected int _siegeStuckTicks;
   protected long _lastSiegeUnstuckAt;
   protected boolean _waitForLeaderRallyPending;
   
   public AdvancedCombatAI(FakePlayer character) {
      super(character);
      initializeComponents();
   }
   
   /**
    * Инициализация компонентов AI
    */
   private void initializeComponents() {
      _positioning = new AdvancedPositioning();
      _partyCoordinator = new PartyPvPCoordinator();
      _fakeRole = determineFakeRole();
      _lastPositionUpdate = System.currentTimeMillis();
      _lastCCCheck = System.currentTimeMillis();
      _lastEmotionUpdate = System.currentTimeMillis();
      _isUsingCC = false;
      _isKiting = false;
      _isRetreating = false;
      _nextActionTime = 0;
      _nextOlympiadPrepActionTime = 0;
      _lastOlympiadTargetUpdate = 0;
      _olympiadFightStartedAt = 0;
      _nextOlympiadStrafeTime = 0;
      _nextCtfObjectiveDecisionTime = 0;
      _ctfObjectiveExpireTime = 0;
      _ctfObjectiveFlagIndex = -1;
      _errorCount = 0;
      _siegeRegroupStartedAt = 0L;
      _siegeLeaderLastSeenAt = 0L;
      _lastSiegeLeaderObjectId = 0;
      _lastSiegeThreatSwitchAt = 0L;
      _siegeThreatFocusUntilMs = 0L;
      _lastSiegeProgressSampleAt = 0L;
      _lastSiegeProgressX = 0;
      _lastSiegeProgressY = 0;
      _lastSiegeProgressZ = 0;
      _siegeStuckTicks = 0;
      _lastSiegeUnstuckAt = 0L;
      _waitForLeaderRallyPending = false;
   }
   
   /**
    * Определить роль фантома на основе класса
    */
   protected FakeRole determineFakeRole() {
      if (isArcherClass()) {
         return FakeRole.DPS_RANGE;
      }

      switch (_fakePlayer.getClassId()) {
         case CARDINAL:
         case EVAS_SAINT:
         case SHILLIEN_SAINT:
            return FakeRole.HEALER;
         case PHOENIX_KNIGHT:
         case EVAS_TEMPLAR:
         case SHILLIEN_TEMPLAR:
            return FakeRole.TANK;
         case ARCHMAGE:
         case SOULTAKER:
         case MYSTIC_MUSE:
         case STORM_SCREAMER:
         case DOMINATOR:
            return FakeRole.DPS_RANGE;
         case ADVENTURER:
         case WIND_RIDER:
         case GHOST_HUNTER:
            return FakeRole.ASSASSIN;
         case HIEROPHANT:
         case SWORD_MUSE:
         case SPECTRAL_DANCER:
         case DOOMCRYER:
            return FakeRole.SUPPORT;
         default:
            return FakeRole.DPS_MELEE;
      }
   }

   protected boolean isArcherClass() {
      switch (_fakePlayer.getClassId()) {
         case SAGGITARIUS:
         case MOONLIGHT_SENTINEL:
         case GHOST_SENTINEL:
            return true;
         default:
            return false;
      }
   }

   protected int getPreferredArcherRange() {
      int preferredRange = Math.max(600, _fakePlayer.getPhysicalAttackRange());
      final List<OffensiveSpell> offensiveSpells = getOffensiveSpells();

      if (offensiveSpells != null) {
         for (OffensiveSpell offensiveSpell : offensiveSpells) {
            final L2Skill skill = _fakePlayer.getSkill(offensiveSpell.getSkillId());
            if (skill != null && skill.getCastRange() > 0) {
               preferredRange = Math.max(preferredRange, skill.getCastRange() - 40);
            }
         }
      }

      return Math.max(600, Math.min(preferredRange, 900));
   }

   protected int getMinimumArcherGap() {
      final int physicalRangeGap = Math.max(280, _fakePlayer.getPhysicalAttackRange() - 120);
      final int skillRangeGap = Math.max(420, getPreferredArcherRange() - 180);
      return Math.min(physicalRangeGap, skillRangeGap);
   }

   protected boolean tryMaintainArcherDistance(Creature target, int preferredRange, int minimumGap) {
      if (target == null || target.isDead()) {
         return false;
      }

      final int distance = (int) Math.sqrt(_fakePlayer.getDistanceSq(target));
      if (!GeoEngine.getInstance().canSeeTarget(_fakePlayer, target)) {
         return maybeMoveToPawn(target, Math.max(160, Math.min(preferredRange, _fakePlayer.getPhysicalAttackRange())));
      }

      if (distance > engageRangeCeiling(preferredRange)) {
         return maybeMoveToPawn(target, preferredRange);
      }

      if (distance >= minimumGap || _fakePlayer.isMovementDisabled()) {
         return false;
      }

      double dx = _fakePlayer.getX() - target.getX();
      double dy = _fakePlayer.getY() - target.getY();
      double distance2d = Math.sqrt((dx * dx) + (dy * dy));
      if (distance2d < 1.0) {
         dx = Rnd.get(2) == 0 ? 1 : -1;
         dy = Rnd.get(2) == 0 ? 1 : -1;
         distance2d = Math.sqrt((dx * dx) + (dy * dy));
      }

      final int retreatDistance = Math.max(preferredRange - 40, minimumGap + 80);
      final int retreatX = target.getX() + (int) Math.round((dx / distance2d) * retreatDistance);
      final int retreatY = target.getY() + (int) Math.round((dy / distance2d) * retreatDistance);
      final int retreatZ = _fakePlayer.getZ();

      if (GeoEngine.getInstance().canMoveToTargetLoc(_fakePlayer.getX(), _fakePlayer.getY(), _fakePlayer.getZ(), retreatX, retreatY, retreatZ) != null) {
         moveTo(retreatX, retreatY, retreatZ);
         return true;
      }

      final int sidestepX = _fakePlayer.getX() + (dy >= 0 ? 180 : -180);
      final int sidestepY = _fakePlayer.getY() + (dx >= 0 ? -180 : 180);
      if (GeoEngine.getInstance().canMoveToTargetLoc(_fakePlayer.getX(), _fakePlayer.getY(), _fakePlayer.getZ(), sidestepX, sidestepY, retreatZ) != null) {
         moveTo(sidestepX, sidestepY, retreatZ);
         return true;
      }

      return false;
   }

   protected int engageRangeCeiling(int preferredRange) {
      return preferredRange + 80;
   }

   protected int getPreferredMageCombatRange() {
      int maxCastRange = 0;
      final List<OffensiveSpell> offensiveSpells = getOffensiveSpells();
      if (offensiveSpells != null) {
         for (OffensiveSpell offensiveSpell : offensiveSpells) {
            final L2Skill skill = _fakePlayer.getSkill(offensiveSpell.getSkillId());
            if (skill != null && skill.getCastRange() > 0) {
               maxCastRange = Math.max(maxCastRange, skill.getCastRange());
            }
         }
      }

      if (maxCastRange <= 0) {
         return 600;
      }

      return Math.max(480, Math.min(900, maxCastRange - 40));
   }

   protected int getPreferredArcherCombatRange() {
      return Math.max(520, Math.min(900, getPreferredArcherRange()));
   }

   protected int getPreferredMeleeCombatRange() {
      final int physicalRange = Math.max(40, _fakePlayer.getPhysicalAttackRange());
      return Math.max(70, Math.min(160, physicalRange));
   }
   
   @Override
   public void thinkAndAct() {
      super.thinkAndAct();
      
      long now = System.currentTimeMillis();
      FakeEmotion emotion = _fakePlayer.getEmotion();
      
      if (_fakePlayer.isInOlympiadMode() && !_fakePlayer.isOlympiadStart()) {
         _olympiadFightStartedAt = 0;
         handleOlympiadPreparation(now);
         return;
      }

      if (_fakePlayer.isInOlympiadMode() && _fakePlayer.isOlympiadStart() && _olympiadFightStartedAt == 0) {
         _olympiadFightStartedAt = now;
         _nextOlympiadStrafeTime = now + Rnd.get(700, 1800);
      }
      
      // Обновление эмоций
      if (now - _lastEmotionUpdate > 5000) {
         _fakePlayer.getEmotion().update();
         _lastEmotionUpdate = now;
      }
      
      // Обработка смерти
      this.handleDeath();
      
      // Если мертв - не действовать
      if (_fakePlayer.isDead()) {
         return;
      }

      if (handleSiegeBehavior(now, emotion)) {
         return;
      }

      // Проверка на человеческие ошибки
      if (!_fakePlayer.isInOlympiadMode() && !isActiveSiegeParticipant() && FakePlayerConfig.FAKE_HUMAN_ERROR_ENABLED && shouldMakeHumanError()) {
         handleHumanError();
         return;
      }
      
      // Проверка задержки действия (не блокирует осадную логику выше)
      if (now < _nextActionTime) {
         return;
      }

      if (_fakePlayer._inEventCTF && handleCtfObjective(now)) {
         return;
      }
      
      // Party PvP координация
      if (_fakePlayer.getParty() != null && FakePlayerConfig.FAKE_PARTY_PVP_COORDINATION) {
         _partyCoordinator.updateCoordination(_fakePlayer, emotion);
      }
      
      // Выбор/обновление цели
      Creature target = selectOrUpdateTarget();
      
      if (target != null && !target.isDead()) {
         // Боевая логика
         handleCombat(target, emotion);
      } else {
         // Нет цели - поиск новой
         searchForTarget();
      }
      
      // Обновление позиционирования
      if (FakePlayerConfig.FAKE_ADVANCED_POSITIONING) {
         updatePositioning(target, emotion);
      }
   }

   protected FakeRole determineBaseRole(FakePlayer fake) {
      if (fake == null) {
         return FakeRole.DPS_MELEE;
      }

      switch (fake.getClassId()) {
         case SAGGITARIUS:
         case MOONLIGHT_SENTINEL:
         case GHOST_SENTINEL:
         case ARCHMAGE:
         case SOULTAKER:
         case MYSTIC_MUSE:
         case STORM_SCREAMER:
         case DOMINATOR:
            return FakeRole.DPS_RANGE;
         case CARDINAL:
         case EVAS_SAINT:
         case SHILLIEN_SAINT:
            return FakeRole.HEALER;
         case PHOENIX_KNIGHT:
         case EVAS_TEMPLAR:
         case SHILLIEN_TEMPLAR:
            return FakeRole.TANK;
         case HIEROPHANT:
         case SWORD_MUSE:
         case SPECTRAL_DANCER:
         case DOOMCRYER:
            return FakeRole.SUPPORT;
         case ADVENTURER:
         case WIND_RIDER:
         case GHOST_HUNTER:
            return FakeRole.ASSASSIN;
         default:
            return FakeRole.DPS_MELEE;
      }
   }

   protected FakeRole getEffectiveSiegeRole(Siege siege) {
      final FakeRole nativeRole = _fakeRole;
      final List<FakePlayer> clanFakes = getClanSiegeFakes(siege);
      if (clanFakes.isEmpty()) {
         return nativeRole;
      }

      if (nativeRole == FakeRole.HEALER) {
         return FakeRole.HEALER;
      }

      if (nativeRole == FakeRole.SUPPORT && !hasClanRole(clanFakes, FakeRole.HEALER)) {
         return FakeRole.HEALER;
      }

      if (isFrontlineCandidate(nativeRole)) {
         final int frontlineSlots = Math.max(1, Math.min(4, (clanFakes.size() + 2) / 3));
         final int frontlineIndex = getRoleAssignmentIndex(clanFakes, this._fakePlayer, true);
         if (frontlineIndex >= 0 && frontlineIndex < frontlineSlots) {
            return FakeRole.TANK;
         }
      }

      if (nativeRole == FakeRole.DPS_RANGE) {
         return FakeRole.DPS_RANGE;
      }

      if (nativeRole == FakeRole.SUPPORT) {
         return FakeRole.SUPPORT;
      }

      if (nativeRole == FakeRole.ASSASSIN && clanFakes.size() >= 4) {
         return FakeRole.ASSASSIN;
      }

      return FakeRole.DPS_MELEE;
   }

   protected boolean hasClanRole(List<FakePlayer> clanFakes, FakeRole role) {
      for (FakePlayer fake : clanFakes) {
         if (determineBaseRole(fake) == role) {
            return true;
         }
      }
      return false;
   }

   protected boolean isFrontlineCandidate(FakeRole role) {
      return role == FakeRole.TANK || role == FakeRole.DPS_MELEE || role == FakeRole.ASSASSIN;
   }

   protected boolean isBacklineSiegeRole(FakeRole role) {
      return role == FakeRole.HEALER || role == FakeRole.SUPPORT || role == FakeRole.DPS_RANGE;
   }

   protected int getRoleAssignmentIndex(List<FakePlayer> clanFakes, FakePlayer fake, boolean frontline) {
      int index = 0;
      for (FakePlayer member : clanFakes) {
         final boolean candidate = frontline ? isFrontlineCandidate(determineBaseRole(member)) : isBacklineSiegeRole(determineBaseRole(member));
         if (!candidate) {
            continue;
         }

         if (member == fake) {
            return index;
         }
         index++;
      }
      return -1;
   }

   protected List<FakePlayer> getClanSiegeFakes(Siege siege) {
      final List<FakePlayer> clanFakes = new ArrayList<>();
      if (siege == null || _fakePlayer.getClan() == null) {
         return clanFakes;
      }

      for (Player member : _fakePlayer.getClan().getOnlineMembers()) {
         if (!(member instanceof FakePlayer)) {
            continue;
         }

         final FakePlayer fake = (FakePlayer) member;
         final Siege memberSiege = fake == _fakePlayer ? siege : com.l2jmega.gameserver.instancemanager.CastleManager.getInstance().getSiege(fake);
         if (memberSiege != null && memberSiege == siege) {
            clanFakes.add(fake);
         } else if (fake.getSiegeState() > 0 && fake.getClan() == _fakePlayer.getClan()) {
            clanFakes.add(fake);
         }
      }

      clanFakes.sort(Comparator.comparingInt(FakePlayer::getObjectId));
      return clanFakes;
   }

   protected boolean handleSiegeBehavior(long now, FakeEmotion emotion) {
      final Siege siege = resolveActiveSiege();
      if (siege == null || _fakePlayer.getClan() == null) {
         return false;
      }

      final Castle castle = siege.getCastle();
      if (castle == null) {
         return false;
      }

      if (isAttackerInSiege(siege) && maybeRecoverFromSiegeStuck(siege, castle, now)) {
         return true;
      }

      if (isAttackerInSiege(siege) && waitForMissingClanLeader(siege, castle)) {
         return true;
      }
      if (isAttackerInSiege(siege) && rallyToRealLeaderAfterWait(siege)) {
         return true;
      }

      if (isAttackerInSiege(siege)
         && !areOuterDoorsBreached(castle)
         && handleSiegeSpawnLeaderWait(siege, castle)) {
         return true;
      }

      // Keep squad cohesion as the highest priority once siege starts:
      // if a real clan leader is present, attacker fakes should follow/assist leader
      // before generic rally/gate lane logic.
      if (handleSiegeLeaderAssist(siege, castle, emotion)) {
         return true;
      }

      // Safety lock: if real leader is online, attackers must never fall back to
      // autonomous gate-lane behavior in this tick.
      if (isAttackerInSiege(siege) && resolveRealClanLeaderForSiege(siege) != null) {
         return true;
      }

      if (isAttackerInSiege(siege)
         && !areOuterDoorsBreached(castle)
         && phantom.ai.event.SiegeFakeSystem.getInstance().isAttackerRallyActive(castle.getCastleId())) {
         final Location stagingPoint = findAttackerStagingPoint(siege, castle);
         if (stagingPoint != null) {
            maybeMoveToPosition(stagingPoint.getX(), stagingPoint.getY(), stagingPoint.getZ(), 120);
         }
         return true;
      }

      // Hard pre-gate assault mode: until outer gates are breached, attackers should not
      // branch into roaming combat logic. Keep them on gate lane only.
      if (isAttackerInSiege(siege) && !areOuterDoorsBreached(castle)) {
         if (handlePrimaryGateAssault(siege, castle, emotion)) {
            return true;
         }

         final Location stagingPoint = findAttackerStagingPoint(siege, castle);
         if (stagingPoint != null) {
            maybeMoveToPosition(stagingPoint.getX(), stagingPoint.getY(), stagingPoint.getZ(), 120);
         }
         return true;
      }

      final FakeRole siegeRole = getEffectiveSiegeRole(siege);
      final Creature immediateThreat = findImmediateSiegeThreat(SIEGE_THREAT_RADIUS);
      if (immediateThreat != null && shouldHandleAttackerEnemyNow(siege, castle, immediateThreat)) {
         if (_fakePlayer.getTarget() != immediateThreat && now - _lastSiegeThreatSwitchAt >= SIEGE_THREAT_SWITCH_COOLDOWN_MS) {
            _fakePlayer.setTarget(immediateThreat);
            _lastSiegeThreatSwitchAt = now;
         }
         _siegeThreatFocusUntilMs = now + SIEGE_THREAT_FOCUS_MS;
         handleCombat(immediateThreat, emotion);
         return true;
      }

      if (isAttackerInSiege(siege) && _fakePlayer.isInsideZone(ZoneId.WATER)) {
         final Location recoveryPoint = findAttackerPushPoint(castle);
         if (recoveryPoint != null) {
            return maybeMoveToPosition(recoveryPoint.getX(), recoveryPoint.getY(), recoveryPoint.getZ(), 140);
         }
      }

      if (isAttackerInSiege(siege) && handlePrimaryGateAssault(siege, castle, emotion)) {
         return true;
      }

      final Creature currentTarget = _fakePlayer.getTarget() instanceof Creature ? (Creature) _fakePlayer.getTarget() : null;
      if (isValidSiegeEnemy(currentTarget) || isValidSiegeObjective(currentTarget, siege)) {
         if (isValidSiegeObjective(currentTarget, siege)) {
            handleSiegeObjective(currentTarget, emotion);
         } else if (shouldHandleAttackerEnemyNow(siege, castle, currentTarget)) {
            handleCombat(currentTarget, emotion);
         } else {
            _fakePlayer.setTarget(null);
            return true;
         }
         return true;
      }

      final Creature enemyTarget = findNearestSiegeEnemy(isAttackerInSiege(siege) ? 1600 : 2200);
      if (enemyTarget != null && shouldHandleAttackerEnemyNow(siege, castle, enemyTarget)) {
         _siegeRegroupStartedAt = 0L;
         _fakePlayer.setTarget(enemyTarget);
         handleCombat(enemyTarget, emotion);
         return true;
      }

      if (isAttackerInSiege(siege)) {
         final Creature objective = findNearestSiegeObjective(siege, siegeRole);
         if (objective != null) {
            if (shouldResolveOvercrowding(objective)) {
               return spreadFromObjective(objective, siegeRole);
            }
            _siegeRegroupStartedAt = 0L;
            _fakePlayer.setTarget(objective);
            handleSiegeObjective(objective, emotion);
            return true;
         }
      }

      if (coordinateWithSiegeClan(siege, castle, siegeRole)) {
         return true;
      }

      if (isAttackerInSiege(siege)) {
         final Location pushPoint = findAttackerPushPoint(castle);
         if (pushPoint != null) {
            maybeMoveToPosition(pushPoint.getX(), pushPoint.getY(), pushPoint.getZ(), 180);
         }
         return true;
      }

      final Location defensivePoint = findDefenderAnchor(castle, siege, siegeRole);
      if (defensivePoint != null) {
         maybeMoveToPosition(defensivePoint.getX(), defensivePoint.getY(), defensivePoint.getZ(), 220);
      }
      return true;
   }

   protected boolean handleSiegeLeaderAssist(Siege siege, Castle castle, FakeEmotion emotion) {
      final Player realLeader = resolveRealClanLeaderForSiege(siege);
      if (realLeader != null && realLeader != _fakePlayer) {
         final FakeRole siegeRole = getEffectiveSiegeRole(siege);
         final int followOffset = isBacklineSiegeRole(siegeRole) ? SIEGE_BACKLINE_FOLLOW_OFFSET : SIEGE_FRONTLINE_FOLLOW_OFFSET;
         final Creature leaderTarget = realLeader.getTarget() instanceof Creature ? (Creature) realLeader.getTarget() : null;
         final boolean closeDoorEngage = leaderTarget instanceof Door && _fakePlayer.isInsideZone(ZoneId.SIEGE) && realLeader.isInsideZone(ZoneId.SIEGE);
         final Creature bodyguardThreat = findThreatAttackingLeader(realLeader);
         final Creature leaderThreat = findLeaderPriorityThreat(realLeader);
         final long hardRegroupSq = (long) SIEGE_LEADER_HARD_REGROUP_DISTANCE * SIEGE_LEADER_HARD_REGROUP_DISTANCE;

         if (_fakePlayer.getDistanceSq(realLeader) > hardRegroupSq) {
            final int seed = Math.abs(_fakePlayer.getObjectId());
            final int slot = seed % 6;
            final int ring = (seed / 6) % 3;
            final int radius = 90 + (ring * 65);
            final double angle = Math.toRadians(slot * 60.0);
            final int rallyX = realLeader.getX() + (int) Math.round(Math.cos(angle) * radius);
            final int rallyY = realLeader.getY() + (int) Math.round(Math.sin(angle) * radius);
            final int rallyZ = realLeader.getZ();

            _fakePlayer.setTarget(null);
            teleportToLocation(rallyX, rallyY, rallyZ, 20);
            return true;
         }

         if (bodyguardThreat != null) {
            _siegeRegroupStartedAt = 0L;
            _fakePlayer.setTarget(bodyguardThreat);
            handleCombat(bodyguardThreat, emotion);
            return true;
         }

         // Assist real clan leader in close-range siege skirmish (players/guards/objectives),
         // but only when the fight is near the leader so followers do not drift into gate lane.
         if (leaderThreat != null && leaderThreat.isInsideRadius(realLeader, 900, false, false)) {
            _siegeRegroupStartedAt = 0L;
            _fakePlayer.setTarget(leaderThreat);
            handleCombat(leaderThreat, emotion);
            return true;
         }

         if (leaderTarget != null && isValidSiegeEnemy(leaderTarget) && leaderTarget.isInsideRadius(realLeader, 1000, false, false)) {
            _siegeRegroupStartedAt = 0L;
            _fakePlayer.setTarget(leaderTarget);
            handleCombat(leaderTarget, emotion);
            return true;
         }

         if (leaderTarget != null
            && isValidSiegeObjective(leaderTarget, siege)
            && leaderTarget.isInsideRadius(realLeader, 1000, false, false)
            && (!(leaderTarget instanceof Door) || closeDoorEngage)) {
            _siegeRegroupStartedAt = 0L;
            _fakePlayer.setTarget(leaderTarget);
            handleSiegeObjective(leaderTarget, emotion);
            return true;
         }

         _fakePlayer.setTarget(null);

         if (shouldWaitForLeaderAfterSpawn(siege, castle, realLeader, leaderTarget)) {
            if (_fakePlayer.getDistanceSq(realLeader) > (long) (followOffset + 60) * (followOffset + 60)) {
               return closeDoorEngage ? maybeMoveToPawn(realLeader, Math.max(70, followOffset - 30)) : followLeaderInFormation(realLeader, followOffset);
            }
            _fakePlayer.stopMove(null);
            return true;
         }

         if (_fakePlayer.getDistanceSq(realLeader) > (long) Math.max(220, followOffset + 90) * Math.max(220, followOffset + 90)) {
            return closeDoorEngage ? maybeMoveToPawn(realLeader, Math.max(70, followOffset - 30)) : followLeaderInFormation(realLeader, followOffset);
         }

         if (_fakePlayer.getDistanceSq(realLeader) > (long) (followOffset + 60) * (followOffset + 60)) {
            return closeDoorEngage ? maybeMoveToPawn(realLeader, Math.max(70, followOffset - 30)) : followLeaderInFormation(realLeader, followOffset);
         }
         _fakePlayer.stopMove(null);
         return true;
      }

      final List<FakePlayer> clanFakes = getClanSiegeFakes(siege);
      if (clanFakes.size() <= 1) {
         return false;
      }

      final FakePlayer squadLeader = findSiegeSquadLeader(clanFakes);
      updateSiegeLeaderTracking(squadLeader);
      if (squadLeader == null || squadLeader.isDead() || squadLeader == _fakePlayer) {
         return false;
      }

      final FakeRole siegeRole = getEffectiveSiegeRole(siege);
      final int followOffset = isBacklineSiegeRole(siegeRole) ? SIEGE_BACKLINE_FOLLOW_OFFSET : SIEGE_FRONTLINE_FOLLOW_OFFSET;
      final Creature leaderTarget = squadLeader.getTarget() instanceof Creature ? (Creature) squadLeader.getTarget() : null;
      final Creature leaderThreat = findLeaderPriorityThreat(squadLeader);

      if (shouldFailoverFromIdleLeaderAssist(siege, castle, squadLeader, leaderTarget, leaderThreat)) {
         return false;
      }

      if (leaderThreat != null && (leaderTarget == null || isSiegeObjectiveTarget(leaderTarget) || leaderThreat.getTarget() == squadLeader)) {
         _siegeRegroupStartedAt = 0L;
         _fakePlayer.setTarget(leaderThreat);
         handleCombat(leaderThreat, emotion);
         return true;
      }

      if (isValidSiegeObjective(leaderTarget, siege)) {
         _siegeRegroupStartedAt = 0L;
         _fakePlayer.setTarget(leaderTarget);
         handleSiegeObjective(leaderTarget, emotion);
         return true;
      }

      if (isValidSiegeEnemy(leaderTarget)) {
         _siegeRegroupStartedAt = 0L;
         _fakePlayer.setTarget(leaderTarget);
         handleCombat(leaderTarget, emotion);
         return true;
      }

      if (shouldWaitForLeaderAfterSpawn(siege, castle, squadLeader, leaderTarget)) {
         if (_fakePlayer.getDistanceSq(squadLeader) > (long) (followOffset + 60) * (followOffset + 60)) {
            return maybeMoveToPawn(squadLeader, followOffset);
         }
         _fakePlayer.stopMove(null);
         return true;
      }

      if (_fakePlayer.getDistanceSq(squadLeader) > (long) Math.max(220, followOffset + 90) * Math.max(220, followOffset + 90)) {
         return maybeMoveToPawn(squadLeader, followOffset);
      }

      if (_fakePlayer.getDistanceSq(squadLeader) > (long) (followOffset + 60) * (followOffset + 60)) {
         return maybeMoveToPawn(squadLeader, followOffset);
      }
      _fakePlayer.stopMove(null);
      return true;
   }

   protected boolean shouldFailoverFromIdleLeaderAssist(Siege siege, Castle castle, Creature leader, Creature leaderTarget, Creature leaderThreat) {
      if (!isAttackerInSiege(siege) || castle == null || leader == null) {
         return false;
      }

      final long now = System.currentTimeMillis();
      final boolean leaderHasCommand = leaderThreat != null || isValidSiegeObjective(leaderTarget, siege) || isValidSiegeEnemy(leaderTarget);
      if (leaderHasCommand) {
         _lastSiegeLeaderObjectId = leader.getObjectId();
         _siegeLeaderLastSeenAt = now;
         return false;
      }

      if (_lastSiegeLeaderObjectId != leader.getObjectId()) {
         _lastSiegeLeaderObjectId = leader.getObjectId();
         _siegeLeaderLastSeenAt = now;
         return false;
      }

      if (_siegeLeaderLastSeenAt == 0L) {
         _siegeLeaderLastSeenAt = now;
         return false;
      }

      if (now - _siegeLeaderLastSeenAt < SIEGE_LEADER_FAILOVER_TIMEOUT_MS) {
         return false;
      }

      final Location entry = getAttackerEntryReference(castle);
      if (entry != null && _fakePlayer.isInsideRadius(entry.getX(), entry.getY(), entry.getZ(), SIEGE_LEADER_WAIT_ENTRY_RADIUS, false, false)) {
         // Leader is idle for too long near spawn/entry: break follow lock and switch to siege objectives.
         _siegeRegroupStartedAt = 0L;
         return true;
      }

      return false;
   }

   protected boolean followLeaderInFormation(Creature leader, int baseOffset) {
      if (leader == null) {
         return false;
      }

      final int seed = Math.abs(_fakePlayer.getObjectId());
      final int slot = seed % 8;
      final int ring = (seed / 8) % 3;
      final int radius = Math.max(90, baseOffset + 20 + (ring * 55));
      final double angle = Math.toRadians(slot * 45.0);

      final int x = leader.getX() + (int) Math.round(Math.cos(angle) * radius);
      final int y = leader.getY() + (int) Math.round(Math.sin(angle) * radius);
      final int z = leader.getZ();
      return maybeMoveToPosition(x, y, z, 45);
   }

   protected Creature findLeaderPriorityThreat(Creature leader) {
      if (leader == null) {
         return null;
      }

      return _fakePlayer.getKnownTypeInRadius(Creature.class, 700).stream()
         .filter(this::isValidSiegeEnemy)
         .filter(this::isReachableSiegeEnemy)
         .filter(enemy -> enemy.isInsideRadius(leader, 700, false, false))
         .sorted((first, second) -> {
            final boolean firstGuard = first instanceof Guard;
            final boolean secondGuard = second instanceof Guard;
            if (firstGuard != secondGuard) {
               return firstGuard ? -1 : 1;
            }
            return Double.compare(_fakePlayer.getDistanceSq(first), _fakePlayer.getDistanceSq(second));
         })
         .findFirst()
         .orElse(null);
   }

   protected Creature findThreatAttackingLeader(Player leader) {
      if (leader == null) {
         return null;
      }

      return _fakePlayer.getKnownTypeInRadius(Creature.class, 1000).stream()
         .filter(this::isValidSiegeEnemy)
         .filter(this::isReachableSiegeEnemy)
         .filter(enemy -> enemy.isInsideRadius(leader, 900, false, false))
         .filter(enemy -> isThreatTargetingLeaderOrClanFakes(enemy, leader) || (enemy instanceof Guard && leader.isInCombat()))
         .sorted((first, second) -> {
            final boolean firstLocksLeader = isThreatTargetingLeader(first, leader);
            final boolean secondLocksLeader = isThreatTargetingLeader(second, leader);
            if (firstLocksLeader != secondLocksLeader) {
               return firstLocksLeader ? -1 : 1;
            }
            final boolean firstLocksClanFake = isThreatTargetingClanFake(first, leader);
            final boolean secondLocksClanFake = isThreatTargetingClanFake(second, leader);
            if (firstLocksClanFake != secondLocksClanFake) {
               return firstLocksClanFake ? -1 : 1;
            }
            final boolean firstGuard = first instanceof Guard;
            final boolean secondGuard = second instanceof Guard;
            if (firstGuard != secondGuard) {
               return firstGuard ? -1 : 1;
            }
            return Double.compare(_fakePlayer.getDistanceSq(first), _fakePlayer.getDistanceSq(second));
         })
         .findFirst()
         .orElse(null);
   }

   protected boolean isThreatTargetingLeaderOrClanFakes(Creature enemy, Player leader) {
      return isThreatTargetingLeader(enemy, leader) || isThreatTargetingClanFake(enemy, leader);
   }

   protected boolean isThreatTargetingLeader(Creature enemy, Player leader) {
      return enemy != null && leader != null && enemy.getTarget() == leader;
   }

   protected boolean isThreatTargetingClanFake(Creature enemy, Player leader) {
      if (enemy == null || leader == null || _fakePlayer.getClan() == null) {
         return false;
      }

      if (enemy.getTarget() == _fakePlayer) {
         return true;
      }

      final Siege siege = resolveActiveSiege();
      if (siege == null) {
         return false;
      }

      final WorldObject enemyTarget = enemy.getTarget();
      if (!(enemyTarget instanceof FakePlayer)) {
         return false;
      }

      final FakePlayer targetFake = (FakePlayer) enemyTarget;
      if (targetFake.getClanId() != _fakePlayer.getClanId()) {
         return false;
      }

      return getClanSiegeFakes(siege).contains(targetFake);
   }

   protected boolean shouldWaitForLeaderAfterSpawn(Siege siege, Castle castle, Creature leader, Creature leaderTarget) {
      if (!isAttackerInSiege(siege) || castle == null || leader == null || areOuterDoorsBreached(castle)) {
         return false;
      }

      if (isValidSiegeObjective(leaderTarget, siege) || isValidSiegeEnemy(leaderTarget)) {
         return false;
      }

      final Location entry = getAttackerEntryReference(castle);
      if (entry == null) {
         return false;
      }

      final boolean selfNearEntry = _fakePlayer.isInsideRadius(entry.getX(), entry.getY(), entry.getZ(), SIEGE_LEADER_WAIT_ENTRY_RADIUS, false, false);
      final boolean leaderNearEntry = leader.isInsideRadius(entry.getX(), entry.getY(), entry.getZ(), SIEGE_LEADER_WAIT_ENTRY_RADIUS, false, false);
      return selfNearEntry && leaderNearEntry;
   }

   protected boolean handleSiegeSpawnLeaderWait(Siege siege, Castle castle) {
      if (siege == null || castle == null || !isAttackerInSiege(siege) || _fakePlayer.getClan() == null) {
         return false;
      }

      final Location entry = getAttackerEntryReference(castle);
      if (entry == null || !_fakePlayer.isInsideRadius(entry.getX(), entry.getY(), entry.getZ(), SIEGE_LEADER_WAIT_ENTRY_RADIUS, false, false)) {
         return false;
      }

      final Player realLeader = resolveRealClanLeaderForSiege(siege);
      final Creature effectiveLeader = realLeader;

      if (effectiveLeader == null || effectiveLeader == _fakePlayer || effectiveLeader.isDead()) {
         _fakePlayer.setTarget(null);
         _fakePlayer.stopMove(null);
         return true;
      }

      if (!effectiveLeader.isInsideRadius(entry.getX(), entry.getY(), entry.getZ(), SIEGE_LEADER_WAIT_ENTRY_RADIUS, false, false)) {
         return false;
      }

      final Creature leaderTarget = effectiveLeader.getTarget() instanceof Creature ? (Creature) effectiveLeader.getTarget() : null;
      if (isValidSiegeObjective(leaderTarget, siege) || isValidSiegeEnemy(leaderTarget)) {
         return false;
      }

      final FakeRole siegeRole = getEffectiveSiegeRole(siege);
      final int followOffset = isBacklineSiegeRole(siegeRole) ? SIEGE_BACKLINE_FOLLOW_OFFSET : SIEGE_FRONTLINE_FOLLOW_OFFSET;
      final long keepDistanceSq = (long) Math.max(220, followOffset + 80) * Math.max(220, followOffset + 80);

      _fakePlayer.setTarget(null);
      if (_fakePlayer.getDistanceSq(effectiveLeader) > keepDistanceSq) {
         return maybeMoveToPawn(effectiveLeader, followOffset);
      }

      _fakePlayer.stopMove(null);
      return true;
   }

   protected boolean waitForMissingClanLeader(Siege siege, Castle castle) {
      if (siege == null || castle == null || !isAttackerInSiege(siege) || _fakePlayer.getClan() == null) {
         return false;
      }

      final Player realLeader = resolveRealClanLeaderForSiege(siege);
      if (realLeader != null) {
         return false;
      }

      final Location entry = getAttackerEntryReference(castle);
      if (entry != null) {
         _fakePlayer.setTarget(null);
         if (!_fakePlayer.isInsideRadius(entry.getX(), entry.getY(), entry.getZ(), SIEGE_LEADER_WAIT_ENTRY_RADIUS, false, false)) {
            return maybeMoveToPosition(entry.getX(), entry.getY(), entry.getZ(), 180);
         }
         _fakePlayer.stopMove(null);
         _waitForLeaderRallyPending = true;
         return true;
      }

      final Location stagingPoint = findAttackerStagingPoint(siege, castle);
      if (stagingPoint != null) {
         _fakePlayer.setTarget(null);
         if (!_fakePlayer.isInsideRadius(stagingPoint.getX(), stagingPoint.getY(), stagingPoint.getZ(), 220, false, false)) {
            return maybeMoveToPosition(stagingPoint.getX(), stagingPoint.getY(), stagingPoint.getZ(), 140);
         }
         _fakePlayer.stopMove(null);
         _waitForLeaderRallyPending = true;
         return true;
      }

      _waitForLeaderRallyPending = true;
      return true;
   }

   protected boolean rallyToRealLeaderAfterWait(Siege siege) {
      if (!_waitForLeaderRallyPending || siege == null || !isAttackerInSiege(siege) || _fakePlayer.getClan() == null) {
         return false;
      }

      final Player realLeader = resolveRealClanLeaderForSiege(siege);
      if (realLeader == null || realLeader == _fakePlayer || realLeader.isDead()) {
         return false;
      }

      final int seed = Math.abs(_fakePlayer.getObjectId());
      final int slot = seed % 6;
      final int ring = (seed / 6) % 3;
      final int radius = 80 + (ring * 60);
      final double angle = Math.toRadians(slot * 60.0);
      final int targetX = realLeader.getX() + (int) Math.round(Math.cos(angle) * radius);
      final int targetY = realLeader.getY() + (int) Math.round(Math.sin(angle) * radius);
      final int targetZ = realLeader.getZ();

      _fakePlayer.setTarget(null);
      teleportToLocation(targetX, targetY, targetZ, 20);
      _waitForLeaderRallyPending = false;
      return true;
   }

   protected Player resolveRealClanLeaderForSiege(Siege siege) {
      if (siege == null || _fakePlayer.getClan() == null) {
         return null;
      }

      final com.l2jmega.gameserver.model.pledge.ClanMember leaderMember = _fakePlayer.getClan().getLeader();
      if (leaderMember == null) {
         return null;
      }

      final Player leader = leaderMember.getPlayerInstance();
      if (leader == null || !leader.isOnline() || leader.isDead() || leader instanceof FakePlayer || leader.getClanId() != _fakePlayer.getClanId()) {
         return null;
      }

      // Strict leader-follow mode: during active siege, followers should lock on the real leader
      // as soon as he is online, even before his personal siege flags fully propagate.
      if (siege.isInProgress()) {
         return leader;
      }

      final Siege leaderSiege = com.l2jmega.gameserver.instancemanager.CastleManager.getInstance().getSiege(leader);
      if (leaderSiege == siege) {
         return leader;
      }

      if (leader.getSiegeState() > 0 && leader.isInSiege()) {
         return leader;
      }

      return null;
   }

   @Override
   protected boolean isActiveSiegeParticipant() {
      // Keep siege mode active even when CastleManager lookup temporarily fails,
      // as long as fake already has siege flags from deployment.
      return super.isActiveSiegeParticipant() || _fakePlayer.isInSiege() || _fakePlayer.getSiegeState() > 0;
   }

   protected boolean handlePrimaryGateAssault(Siege siege, Castle castle, FakeEmotion emotion) {
      Door primaryDoor = resolveLeaderCommandDoor(siege, castle);
      if (primaryDoor == null) {
         primaryDoor = findPrimaryAttackerDoor(castle);
      }
      if (!canAssaultSiegeDoor(primaryDoor, siege)) {
         return false;
      }

      // Keep pre-gate assault stable: before outer doors are breached, do not peel off
      // from door focus to nearby enemies, otherwise attackers spread and lose lane.
      if (areOuterDoorsBreached(castle)) {
         final Creature gateEnemy = findPriorityEnemyNearObjective(primaryDoor, SIEGE_GATE_ENEMY_RADIUS);
         if (gateEnemy != null && shouldInterruptGateAssaultForEnemy(gateEnemy, primaryDoor)) {
            _siegeRegroupStartedAt = 0L;
            _fakePlayer.setTarget(gateEnemy);
            handleCombat(gateEnemy, emotion);
            return true;
         }
      }

      if (shouldResolveOvercrowding(primaryDoor)) {
         return spreadFromObjective(primaryDoor, getEffectiveSiegeRole(siege));
      }

      _siegeRegroupStartedAt = 0L;
      _fakePlayer.setTarget(primaryDoor);
      handleSiegeObjective(primaryDoor, emotion);
      return true;
   }

   protected Door resolveLeaderCommandDoor(Siege siege, Castle castle) {
      if (siege == null || castle == null) {
         return null;
      }

      final Player realLeader = resolveRealClanLeaderForSiege(siege);
      if (realLeader != null) {
         final Creature target = realLeader.getTarget() instanceof Creature ? (Creature) realLeader.getTarget() : null;
         if (target instanceof Door) {
            final Door commanded = (Door) target;
            if (isValidAssaultDoor(castle, commanded) && canAssaultSiegeDoor(commanded, siege)) {
               return commanded;
            }
         }
      }

      final FakePlayer squadLeader = findSiegeSquadLeader(getClanSiegeFakes(siege));
      if (squadLeader != null && !squadLeader.isDead()) {
         final Creature target = squadLeader.getTarget() instanceof Creature ? (Creature) squadLeader.getTarget() : null;
         if (target instanceof Door) {
            final Door commanded = (Door) target;
            if (isValidAssaultDoor(castle, commanded) && canAssaultSiegeDoor(commanded, siege)) {
               return commanded;
            }
         }
      }

      return null;
   }
   
   /**
    * Обработка боя с целью
    */
   protected void handleCombat(Creature target, FakeEmotion emotion) {
      // Проверка на отступление
      handleShots();
      selfSupportBuffs();
      final boolean archerClass = isArcherClass();
      final boolean siegeObjective = isSiegeObjectiveTarget(target);
      final Siege activeSiege = resolveActiveSiege();
      final FakeRole combatRole = activeSiege != null ? getEffectiveSiegeRole(activeSiege) : _fakeRole;

      if (siegeObjective) {
         handleSiegeObjectiveCombat(target);
         handleEmotionalReactions(target, emotion);
         return;
      }

      if (_fakePlayer.isInOlympiadMode() && handleOlympiadCombat(target, emotion)) {
         handleEmotionalReactions(target, emotion);
         return;
      }

      if (archerClass && tryMaintainArcherDistance(target, getPreferredArcherRange(), getMinimumArcherGap())) {
         handleEmotionalReactions(target, emotion);
         return;
      }

      if (shouldRetreat(target, emotion)) {
         handleRetreat(target);
         return;
      }

      if (activeSiege != null && (combatRole == FakeRole.HEALER || combatRole == FakeRole.SUPPORT) && handleSiegeSupportActions(activeSiege, combatRole)) {
         return;
      }

      if (archerClass) {
         if (maybeMoveToPawn(target, getPreferredArcherCombatRange())) {
            return;
         }
      } else if (combatRole == FakeRole.DPS_RANGE || combatRole == FakeRole.HEALER || combatRole == FakeRole.SUPPORT) {
         if (maybeMoveToPawn(target, getPreferredMageCombatRange())) {
            return;
         }
      } else {
         if (maybeMoveToPawn(target, getPreferredMeleeCombatRange())) {
            return;
         }
      }
      
      // CC chain логика
      if (FakePlayerConfig.FAKE_CC_CHAIN_ENABLED) {
         handleCCChain(target, emotion);
      }
      
      // Использование скиллов
      if (archerClass) {
         tryAttackingUsingFighterOffensiveSkill();
      } else if (combatRole == FakeRole.DPS_RANGE || combatRole == FakeRole.HEALER || combatRole == FakeRole.SUPPORT) {
         if (!tryAttackingUsingMageOffensiveSkill()) {
            _fakePlayer.forceAutoAttack(target);
         }
      } else {
         tryAttackingUsingFighterOffensiveSkill();
      }
      
      // Само-баффы
      selfSupportBuffs();
      
      // Обработка шотов
      handleShots();
      
      // Эмоциональные реакции
      handleEmotionalReactions(target, emotion);
   }

   protected void handleSiegeObjectiveCombat(Creature target) {
      if (target == null || target.isDead()) {
         return;
      }

      if (target instanceof Door) {
         final Door door = (Door) target;
         final Location approachPoint = getDoorApproachPoint(door);
         final Location safeApproachPoint = approachPoint != null ? approachPoint : door.getPosition();
         final int doorApproachOffset = Math.max(120, SIEGE_DOOR_APPROACH_OFFSET - 20);
         final int desiredRange = getSiegeDoorDesiredRange();
         if (approachPoint != null && !_fakePlayer.isInsideRadius(approachPoint.getX(), approachPoint.getY(), approachPoint.getZ(), SIEGE_DOOR_SLOT_REACHED_RADIUS, false, false)) {
            if (!maybeMoveToPawn(door, doorApproachOffset)) {
               maybeMoveToPosition(approachPoint.getX(), approachPoint.getY(), approachPoint.getZ(), 110);
            }
            return;
         }

         if (!GeoEngine.getInstance().canSeeTarget(_fakePlayer, target)) {
            // Doors are static objectives; if we are already close enough, keep assaulting
            // instead of looping movement on LOS edge cases near gate geometry.
            if (approachPoint != null && !_fakePlayer.isInsideRadius(approachPoint.getX(), approachPoint.getY(), approachPoint.getZ(), SIEGE_DOOR_SLOT_REACHED_RADIUS, false, false)) {
               if (!maybeMoveToPawn(door, doorApproachOffset)) {
                  maybeMoveToPosition(approachPoint.getX(), approachPoint.getY(), approachPoint.getZ(), 110);
               }
               return;
            }
         }

         if (!canAssaultSiegeDoor(door, resolveActiveSiege())) {
            if (!maybeMoveToPawn(door, doorApproachOffset)) {
               maybeMoveToPosition(safeApproachPoint.getX(), safeApproachPoint.getY(), safeApproachPoint.getZ(), 110);
            }
            return;
         }

         if (isSiegeRangedAssaulter() && maintainSiegeDoorRange(door, safeApproachPoint, desiredRange)) {
            return;
         }

         attackSiegeDoorByRole(door);
         return;
      } else if (_fakePlayer.getDistanceSq(target) > 220L * 220L && maybeMoveToPawn(target, Math.max(80, _fakePlayer.getPhysicalAttackRange()))) {
         return;
      }

      _fakePlayer.setTarget(target);
      _fakePlayer.forceAutoAttack(target);
   }

   protected boolean handleSiegeSupportActions(Siege siege, FakeRole combatRole) {
      if (tryCleanseSiegeAlly(siege)) {
         return true;
      }

      if (tryHealSiegeAlly(siege)) {
         return true;
      }

      if (combatRole == FakeRole.HEALER) {
         final FakePlayer ally = findNearestClanFrontliner(siege, getClanSiegeFakes(siege));
         if (ally != null && ally != _fakePlayer) {
            return maybeMoveToPawn(ally, SIEGE_BACKLINE_FOLLOW_OFFSET);
         }
      }

      return false;
   }

   protected boolean tryHealSiegeAlly(Siege siege) {
      final List<phantom.model.HealingSpell> healingSpells = getHealingSpells();
      if (healingSpells == null || healingSpells.isEmpty()) {
         return false;
      }

      final List<FakePlayer> clanFakes = getClanSiegeFakes(siege);
      FakePlayer bestTarget = null;
      double bestHpPercent = Double.MAX_VALUE;
      int lowHpCount = 0;

      for (FakePlayer member : clanFakes) {
         if (member == null || member.isDead()) {
            continue;
         }

         final double hpPercent = 100.0 * member.getCurrentHp() / Math.max(1.0, member.getMaxHp());
         if (hpPercent < SIEGE_HEAL_MASS_HP_THRESHOLD) {
            lowHpCount++;
         }
         if (hpPercent < bestHpPercent) {
            bestHpPercent = hpPercent;
            bestTarget = member;
         }
      }

      if (bestTarget == null || bestHpPercent >= 100.0) {
         return false;
      }

      if (bestHpPercent >= SIEGE_SINGLE_HEAL_TRIGGER_HP_PERCENT && lowHpCount == 0) {
         return false;
      }

      if (_fakePlayer.isCastingNow()) {
         return true;
      }

      final List<phantom.model.HealingSpell> sorted = healingSpells.stream()
         .sorted((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
         .collect(java.util.stream.Collectors.toList());

      if (lowHpCount >= SIEGE_HEAL_MIN_TARGETS) {
         for (phantom.model.HealingSpell spell : sorted) {
            final L2Skill healSkill = _fakePlayer.getSkill(spell.getSkillId());
            if (healSkill == null || healSkill.getTargetType() != L2Skill.SkillTargetType.TARGET_PARTY) {
               continue;
            }

            _fakePlayer.setTarget(_fakePlayer);
            if (_fakePlayer.checkUseMagicConditions(healSkill, true, false)) {
               castSpell(healSkill);
               return true;
            }
         }
      }

      for (phantom.model.HealingSpell spell : sorted) {
         final L2Skill healSkill = _fakePlayer.getSkill(spell.getSkillId());
         if (healSkill == null) {
            continue;
         }

         if (healSkill.getTargetType() == L2Skill.SkillTargetType.TARGET_PARTY) {
            continue;
         }

         _fakePlayer.setTarget(bestTarget);
         if (_fakePlayer.checkUseMagicConditions(healSkill, true, false)) {
            castSpell(healSkill);
            return true;
         }
      }

      return false;
   }

   protected boolean tryCleanseSiegeAlly(Siege siege) {
      final L2Skill cleanseSkill = _fakePlayer.getSkill(CLEANSE_SKILL_ID);
      if (cleanseSkill == null || _fakePlayer.isCastingNow()) {
         return false;
      }

      for (FakePlayer member : getClanSiegeFakes(siege)) {
         if (member == null || member.isDead()) {
            continue;
         }

         boolean hasDebuff = false;
         for (com.l2jmega.gameserver.model.L2Effect effect : member.getAllEffects()) {
            if (effect != null && effect.getSkill().isDebuff()) {
               hasDebuff = true;
               break;
            }
         }

         if (!hasDebuff) {
            continue;
         }

         _fakePlayer.setTarget(member);
         if (_fakePlayer.checkUseMagicConditions(cleanseSkill, true, false)) {
            castSpell(cleanseSkill);
            return true;
         }
      }

      return false;
   }

   protected void handleSiegeObjective(Creature target, FakeEmotion emotion) {
      if (target == null || target.isDead()) {
         _fakePlayer.setTarget(null);
         return;
      }

      if (target instanceof HolyThing) {
         maybeMoveToPosition(target.getX(), target.getY(), target.getZ(), 130);
         return;
      }

      if (target instanceof Door) {
         final Door door = (Door) target;
         if (!canAssaultSiegeDoor(door, resolveActiveSiege())) {
            _fakePlayer.setTarget(null);
            return;
         }

         final Location approachPoint = getDoorApproachPoint(door);
         final Location safeApproachPoint = approachPoint != null ? approachPoint : door.getPosition();
         final int doorApproachOffset = Math.max(120, SIEGE_DOOR_APPROACH_OFFSET - 20);
         final int desiredRange = getSiegeDoorDesiredRange();
         if (approachPoint != null && !_fakePlayer.isInsideRadius(approachPoint.getX(), approachPoint.getY(), approachPoint.getZ(), SIEGE_DOOR_SLOT_REACHED_RADIUS, false, false)) {
            if (!maybeMoveToPawn(door, doorApproachOffset)) {
               maybeMoveToPosition(approachPoint.getX(), approachPoint.getY(), approachPoint.getZ(), 110);
            }
            return;
         }

         if (!GeoEngine.getInstance().canSeeTarget(_fakePlayer, target)) {
            // Gate geometry often breaks LOS near hinges/walls; at close range keep hitting.
            if (approachPoint != null && !_fakePlayer.isInsideRadius(approachPoint.getX(), approachPoint.getY(), approachPoint.getZ(), SIEGE_DOOR_SLOT_REACHED_RADIUS, false, false)) {
               if (!maybeMoveToPawn(door, doorApproachOffset)) {
                  maybeMoveToPosition(approachPoint.getX(), approachPoint.getY(), approachPoint.getZ(), 110);
               }
               return;
            }
         }

         if (isSiegeRangedAssaulter() && maintainSiegeDoorRange(door, safeApproachPoint, desiredRange)) {
            return;
         }

         attackSiegeDoorByRole(door);
         return;
      }

      if (!GeoEngine.getInstance().canSeeTarget(_fakePlayer, target)) {
         maybeMoveToPawn(target, Math.max(80, _fakePlayer.getPhysicalAttackRange()));
         return;
      }

      handleCombat(target, emotion);
   }

   protected boolean isSiegeRangedAssaulter() {
      return isArcherClass() || _fakeRole == FakeRole.DPS_RANGE || _fakeRole == FakeRole.HEALER || _fakeRole == FakeRole.SUPPORT;
   }

   protected int getSiegeDoorDesiredRange() {
      if (isArcherClass()) {
         return Math.max(420, Math.min(900, getPreferredArcherRange() - 40));
      }
      if (_fakeRole == FakeRole.DPS_RANGE) {
         return 520;
      }
      if (_fakeRole == FakeRole.HEALER || _fakeRole == FakeRole.SUPPORT) {
         return 460;
      }
      return 160;
   }

   protected boolean maintainSiegeDoorRange(Door door, Location approachPoint, int desiredRange) {
      if (door == null || approachPoint == null || desiredRange <= 0) {
         return false;
      }

      final int minRange = Math.max(120, desiredRange - 120);
      final double distanceSq = _fakePlayer.getDistanceSq(door);
      if (distanceSq > (long) desiredRange * desiredRange) {
         return maybeMoveToPawn(door, desiredRange);
      }

      if (distanceSq < (long) minRange * minRange) {
         final int dx = _fakePlayer.getX() - door.getX();
         final int dy = _fakePlayer.getY() - door.getY();
         final double len = Math.sqrt((double) dx * dx + (double) dy * dy);

         int retreatX = approachPoint.getX();
         int retreatY = approachPoint.getY();
         final int retreatZ = approachPoint.getZ();
         if (len > 1.0d) {
            retreatX = door.getX() + (int) Math.round((dx / len) * desiredRange);
            retreatY = door.getY() + (int) Math.round((dy / len) * desiredRange);
         }
         return maybeMoveToPosition(retreatX, retreatY, retreatZ, 80);
      }

      return false;
   }

   protected void attackSiegeDoorByRole(Door door) {
      if (door == null) {
         return;
      }

      handleShots();
      _fakePlayer.setTarget(door);
      if (isArcherClass()) {
         _fakePlayer.forceAutoAttack(door);
         return;
      }

      if (_fakeRole == FakeRole.DPS_RANGE || _fakeRole == FakeRole.HEALER || _fakeRole == FakeRole.SUPPORT) {
         if (!tryAttackingUsingMageOffensiveSkill()) {
            _fakePlayer.forceAutoAttack(door);
         }
         return;
      }

      tryAttackingUsingFighterOffensiveSkill();
      _fakePlayer.forceAutoAttack(door);
   }

   protected boolean maybeRecoverFromSiegeStuck(Siege siege, Castle castle, long now) {
      final Location pushPoint = findAttackerPushPoint(castle);
      if (pushPoint == null) {
         return false;
      }

      if (_fakePlayer.isInsideRadius(pushPoint.getX(), pushPoint.getY(), pushPoint.getZ(), 180, false, false)) {
         _siegeStuckTicks = 0;
         sampleSiegeProgress(now);
         return false;
      }

      if (_lastSiegeProgressSampleAt == 0L) {
         sampleSiegeProgress(now);
         return false;
      }

      if (now - _lastSiegeProgressSampleAt < SIEGE_PROGRESS_SAMPLE_MS) {
         return false;
      }

      final long movedSq = distanceSq(_lastSiegeProgressX, _lastSiegeProgressY, _lastSiegeProgressZ, _fakePlayer.getX(), _fakePlayer.getY(), _fakePlayer.getZ());
      sampleSiegeProgress(now);

      if (movedSq >= (long) SIEGE_STUCK_MIN_PROGRESS * SIEGE_STUCK_MIN_PROGRESS) {
         _siegeStuckTicks = 0;
         return false;
      }

      _siegeStuckTicks++;

      if (_siegeStuckTicks >= SIEGE_STUCK_TELEPORT_TICKS
         && now - _lastSiegeUnstuckAt >= SIEGE_UNSTUCK_COOLDOWN_MS
         && findImmediateSiegeThreat(260) == null) {
         final Location unstuckPoint = areOuterDoorsBreached(castle) ? pushPoint : findAttackerStagingPoint(siege, castle);
         if (unstuckPoint != null) {
            _lastSiegeUnstuckAt = now;
            _siegeStuckTicks = 0;
            teleportToLocation(unstuckPoint.getX(), unstuckPoint.getY(), unstuckPoint.getZ(), 60);
            return true;
         }
      }

      if (_siegeStuckTicks >= SIEGE_STUCK_REPATH_TICKS) {
         return maybeMoveToPosition(pushPoint.getX(), pushPoint.getY(), pushPoint.getZ(), 140);
      }

      return false;
   }

   protected void sampleSiegeProgress(long now) {
      _lastSiegeProgressSampleAt = now;
      _lastSiegeProgressX = _fakePlayer.getX();
      _lastSiegeProgressY = _fakePlayer.getY();
      _lastSiegeProgressZ = _fakePlayer.getZ();
   }

   protected long distanceSq(int x1, int y1, int z1, int x2, int y2, int z2) {
      final long dx = x1 - x2;
      final long dy = y1 - y2;
      final long dz = z1 - z2;
      return dx * dx + dy * dy + dz * dz;
   }

   protected Location getDoorApproachPoint(Door door) {
      if (door == null) {
         return null;
      }

      final Siege siege = resolveActiveSiege();
      final Castle castle = siege != null ? siege.getCastle() : null;

      Location anchor = new Location(_fakePlayer.getX(), _fakePlayer.getY(), _fakePlayer.getZ());
      if (siege != null && castle != null && isAttackerInSiege(siege) && !areOuterDoorsBreached(castle)) {
         final Player realLeader = resolveRealClanLeaderForSiege(siege);
         final Creature effectiveLeader;
         if (realLeader != null) {
            effectiveLeader = realLeader;
         } else {
            effectiveLeader = findSiegeSquadLeader(getClanSiegeFakes(siege));
         }

         if (effectiveLeader != null && !effectiveLeader.isDead()) {
            anchor = new Location(effectiveLeader.getX(), effectiveLeader.getY(), effectiveLeader.getZ());
         }
      }

      final double dx = door.getX() - anchor.getX();
      final double dy = door.getY() - anchor.getY();
      final double len = Math.sqrt(dx * dx + dy * dy);
      if (len < 1.0) {
         return door.getPosition();
      }

      final double dirX = dx / len;
      final double dirY = dy / len;
      final double perpendicularX = -dirY;
      final double perpendicularY = dirX;

      final int formationIndex = getSiegeDoorAssaultFormationIndex(siege);
      final int row = Math.max(0, formationIndex / SIEGE_DOOR_ASSAULT_COLUMNS);
      final int column = formationIndex % SIEGE_DOOR_ASSAULT_COLUMNS;
      final int centeredColumn = column - (SIEGE_DOOR_ASSAULT_COLUMNS / 2);
      final int lateralOffset = centeredColumn * SIEGE_DOOR_ASSAULT_LATERAL_STEP;
      final int depthOffset = Math.max(90, SIEGE_DOOR_ASSAULT_BASE_OFFSET + (row * SIEGE_DOOR_ASSAULT_ROW_STEP));

      final int x = door.getX()
         - (int) Math.round(dirX * depthOffset)
         + (int) Math.round(perpendicularX * lateralOffset);
      final int y = door.getY()
         - (int) Math.round(dirY * depthOffset)
         + (int) Math.round(perpendicularY * lateralOffset);
      final int z = door.getZ();
      final Location geoPoint = GeoEngine.getInstance().canMoveToTargetLoc(_fakePlayer.getX(), _fakePlayer.getY(), _fakePlayer.getZ(), x, y, z);
      if (geoPoint != null) {
         return geoPoint;
      }

      final int fallbackX = door.getX() - (int) Math.round(dirX * SIEGE_DOOR_APPROACH_OFFSET);
      final int fallbackY = door.getY() - (int) Math.round(dirY * SIEGE_DOOR_APPROACH_OFFSET);
      return new Location(fallbackX, fallbackY, z);
   }

   protected int getSiegeDoorAssaultFormationIndex(Siege siege) {
      final List<FakePlayer> clanFakes = getClanSiegeFakes(siege);
      if (clanFakes.isEmpty()) {
         return 0;
      }

      final int directIndex = clanFakes.indexOf(_fakePlayer);
      return Math.max(0, directIndex);
   }
   
   /**
    * Обработка CC chain
    */
   protected void handleCCChain(Creature target, FakeEmotion emotion) {
      _isUsingCC = false;
   }
   
   /**
    * Обновление позиционирования
    */
   protected void updatePositioning(Creature target, FakeEmotion emotion) {
      long now = System.currentTimeMillis();
      
      if (now - _lastPositionUpdate < 500) {
         return;
      }
      _lastPositionUpdate = now;
      
      if (target != null && !target.isDead()) {
         if (_fakePlayer.isInOlympiadMode() && !isArcherClass()
            && (_fakeRole == FakeRole.DPS_MELEE || _fakeRole == FakeRole.TANK || _fakeRole == FakeRole.ASSASSIN)) {
            _isKiting = false;
            return;
         }

         if (isArcherClass()) {
            _isKiting = tryMaintainArcherDistance(target, getPreferredArcherRange(), getMinimumArcherGap());
         } else {
            _positioning.updatePosition(_fakePlayer, target, toCombatRole(_fakeRole), emotion);
            _isKiting = _positioning.isKiting();
         }
      }
   }
   
   /**
    * Выбрать или обновить цель
    */
   protected Creature selectOrUpdateTarget() {
      if (_fakePlayer.isInOlympiadMode()) {
         Creature olympiadTarget = findOlympiadOpponent();
         if (olympiadTarget != null) {
            Creature currentOlympiadTarget = _fakePlayer.getTarget() instanceof Creature ? (Creature) _fakePlayer.getTarget() : null;
            if (currentOlympiadTarget != olympiadTarget && System.currentTimeMillis() - _lastOlympiadTargetUpdate > 700) {
               _fakePlayer.setTarget(olympiadTarget);
               _lastOlympiadTargetUpdate = System.currentTimeMillis();
            }
            return olympiadTarget;
         }
      }

      Creature currentTarget = _fakePlayer.getTarget() instanceof Creature ? 
         (Creature) _fakePlayer.getTarget() : null;
      
      // Если текущая цель хороша - оставить (должна проходить checkTarget: ивент/зона/Anonymous и т.д.)
      if (currentTarget != null && !currentTarget.isDead()
          && GeoEngine.getInstance().canSeeTarget(_fakePlayer, currentTarget)
          && checkTarget(currentTarget)) {
         return currentTarget;
      }
      
      // Поиск новой цели
      return findNewTarget();
   }
   
   /**
    * Найти новую цель
    */
   protected Creature findNewTarget() {
      if (_fakePlayer.isInOlympiadMode()) {
         return findOlympiadOpponent();
      }

      // Использовать систему приоритетов
      if (_fakePlayer._inEventTvT || _fakePlayer._inEventCTF) {
         return findEventOpponent();
      }

      final int anonRadius = AnonymousPvPEvent.isPlayerInEvent(_fakePlayer) ? TOURNAMENT_TARGET_RADIUS : -1;
      final int targetRadius = _fakePlayer.isInArenaEvent() ? TOURNAMENT_TARGET_RADIUS
         : (_fakePlayer.isFakeKTBEvent() ? 5000 : (anonRadius > 0 ? anonRadius : 1000));
      List<Creature> potentialTargets = _fakePlayer.getKnownTypeInRadius(Creature.class, targetRadius)
         .stream()
         .filter(this::checkTarget)
         .collect(java.util.stream.Collectors.toList());
      
      if (potentialTargets.isEmpty()) {
         return null;
      }
      
      return potentialTargets.get(0);
   }
   
   /**
    * Поиск цели для атаки
    */
   protected void searchForTarget() {
      Creature newTarget = findNewTarget();
      if (newTarget != null) {
         _fakePlayer.setTarget(newTarget);
         if ((_fakePlayer._inEventTvT || _fakePlayer._inEventCTF) && _fakePlayer.getDistanceSq(newTarget) > 250000) {
            maybeMoveToPawn(newTarget, Math.max(120, _fakePlayer.getPhysicalAttackRange()));
         }
         if (AnonymousPvPEvent.isPlayerInEvent(_fakePlayer) && _fakePlayer.getDistanceSq(newTarget) > 250000) {
            maybeMoveToPawn(newTarget, Math.max(120, _fakePlayer.getPhysicalAttackRange()));
         }
         if (_fakePlayer.isInOlympiadMode()) {
            _lastOlympiadTargetUpdate = System.currentTimeMillis();
         }
         
         // Human-like реакция на новую цель
         applyHumanReactionDelay();
      }
   }

   protected Creature findEventOpponent() {
      Player bestTarget = null;
      double bestDistance = Double.MAX_VALUE;
      final int searchRadius = Math.max(2000, TOURNAMENT_TARGET_RADIUS);

      for (Player player : _fakePlayer.getKnownTypeInRadius(Player.class, searchRadius)) {
         if (player == null || player == _fakePlayer || !player.isOnline() || player.isDead() || player.isInObserverMode()) {
            continue;
         }

         if (_fakePlayer._inEventTvT) {
            if (!player._inEventTvT) {
               continue;
            }

            if (_fakePlayer._teamNameTvT != null && player._teamNameTvT != null && _fakePlayer._teamNameTvT.equals(player._teamNameTvT)) {
               continue;
            }
         } else if (_fakePlayer._inEventCTF) {
            if (!player._inEventCTF) {
               continue;
            }

            if (_fakePlayer._teamNameCTF != null && player._teamNameCTF != null && _fakePlayer._teamNameCTF.equals(player._teamNameCTF)) {
               continue;
            }
         } else {
            continue;
         }

         final double distance = _fakePlayer.getDistanceSq(player);
         if (distance < bestDistance) {
            bestDistance = distance;
            bestTarget = player;
         }
      }

      return bestTarget;
   }

   protected boolean handleCtfObjective(long now) {
      if (!CTF.is_started() || _fakePlayer._teamNameCTF == null || _fakePlayer._teamNameCTF.isEmpty()) {
         _ctfObjectiveFlagIndex = -1;
         _ctfObjectiveExpireTime = 0;
         return false;
      }

      if (_fakePlayer.isCastingNow() || _fakePlayer.isMovementDisabled()) {
         return false;
      }

      if (_fakePlayer._haveFlagCTF) {
         final int ownFlagIndex = CTF._teams.indexOf(_fakePlayer._teamNameCTF);
         if (ownFlagIndex < 0) {
            return false;
         }

         if (CTF.InRangeOfFlag(_fakePlayer, ownFlagIndex, 170)) {
            CTF.processInFlagRange(_fakePlayer);
            _ctfObjectiveFlagIndex = -1;
            _ctfObjectiveExpireTime = 0;
            _nextCtfObjectiveDecisionTime = now + Rnd.get(2500, 5000);
            return true;
         }

         maybeMoveToFlag(ownFlagIndex, 120);
         _ctfObjectiveFlagIndex = ownFlagIndex;
         _ctfObjectiveExpireTime = now + Rnd.get(5000, 9000);
         return true;
      }

      if (now < _nextCtfObjectiveDecisionTime) {
         if (_ctfObjectiveFlagIndex >= 0 && now < _ctfObjectiveExpireTime) {
            if (CTF.InRangeOfFlag(_fakePlayer, _ctfObjectiveFlagIndex, 170)) {
               CTF.processInFlagRange(_fakePlayer);
               _ctfObjectiveFlagIndex = -1;
               _ctfObjectiveExpireTime = 0;
               _nextCtfObjectiveDecisionTime = now + Rnd.get(2500, 5000);
            } else {
               maybeMoveToFlag(_ctfObjectiveFlagIndex, 120);
            }
            return true;
         }
         return false;
      }

      _nextCtfObjectiveDecisionTime = now + Rnd.get(4000, 9000);
      if (Rnd.get(100) >= 22) {
         _ctfObjectiveFlagIndex = -1;
         _ctfObjectiveExpireTime = 0;
         return false;
      }

      final int targetFlagIndex = getRandomEnemyFlagIndex();
      if (targetFlagIndex < 0) {
         _ctfObjectiveFlagIndex = -1;
         _ctfObjectiveExpireTime = 0;
         return false;
      }

      _ctfObjectiveFlagIndex = targetFlagIndex;
      _ctfObjectiveExpireTime = now + Rnd.get(5000, 10000);
      if (CTF.InRangeOfFlag(_fakePlayer, targetFlagIndex, 170)) {
         CTF.processInFlagRange(_fakePlayer);
      } else {
         maybeMoveToFlag(targetFlagIndex, 120);
      }
      return true;
   }

   protected int getRandomEnemyFlagIndex() {
      if (CTF._teams == null || CTF._flagsTaken == null || CTF._teams.isEmpty()) {
         return -1;
      }

      final List<Integer> availableFlags = new java.util.ArrayList<>();
      for (int index = 0; index < CTF._teams.size(); index++) {
         final String teamName = CTF._teams.get(index);
         if (teamName == null || teamName.equals(_fakePlayer._teamNameCTF)) {
            continue;
         }

         if (index < CTF._flagsTaken.size() && !CTF._flagsTaken.get(index)) {
            availableFlags.add(index);
         }
      }

      if (availableFlags.isEmpty()) {
         return -1;
      }

      return availableFlags.get(Rnd.get(availableFlags.size()));
   }

   protected boolean maybeMoveToFlag(int flagIndex, int offset) {
      if (flagIndex < 0 || flagIndex >= CTF._flagsX.size() || flagIndex >= CTF._flagsY.size() || flagIndex >= CTF._flagsZ.size()) {
         return false;
      }

      return maybeMoveTo(flagIndex, offset);
   }

   private boolean maybeMoveTo(int flagIndex, int offset) {
      final int x = CTF._flagsX.get(flagIndex);
      final int y = CTF._flagsY.get(flagIndex);
      final int z = CTF._flagsZ.get(flagIndex);
      return maybeMoveToPosition(x, y, z, offset);
   }

   protected boolean maybeMoveToPosition(int x, int y, int z, int offset) {
      if (_fakePlayer.isMovementDisabled()) {
         return false;
      }

      final double distanceSq = _fakePlayer.getDistanceSq(x, y, z);
      final double offsetSq = (double) offset * offset;
      if (distanceSq <= offsetSq) {
         _fakePlayer.stopMove(null);
         return false;
      }

      final int ox = _fakePlayer.getX();
      final int oy = _fakePlayer.getY();
      final int oz = _fakePlayer.getZ();

      final Location geoPoint = GeoEngine.getInstance().canMoveToTargetLoc(ox, oy, oz, x, y, z);
      if (geoPoint != null && ((long) (geoPoint.getX() - ox) * (geoPoint.getX() - ox) + (long) (geoPoint.getY() - oy) * (geoPoint.getY() - oy)) > 900L) {
         moveTo(geoPoint.getX(), geoPoint.getY(), geoPoint.getZ());
         return true;
      }

      // Siege-specific detour around walls/corners when direct geopath stalls at gates.
      final Location detour = findSiegeDetourStep(x, y, z);
      if (detour != null) {
         moveTo(detour.getX(), detour.getY(), detour.getZ());
         return true;
      }

      moveTo(x, y, z);
      return true;
   }

   protected Location findSiegeDetourStep(int targetX, int targetY, int targetZ) {
      if (!isActiveSiegeParticipant() && !_fakePlayer.isInSiege() && _fakePlayer.getSiegeState() <= 0) {
         return null;
      }

      final int ox = _fakePlayer.getX();
      final int oy = _fakePlayer.getY();
      final int oz = _fakePlayer.getZ();
      final double dx = targetX - ox;
      final double dy = targetY - oy;
      final double len = Math.hypot(dx, dy);
      if (len < 1.0d) {
         return null;
      }

      final double ux = dx / len;
      final double uy = dy / len;
      final double px = -uy;
      final double py = ux;

      final int[] forwardSteps = {180, 260, 340};
      final int[] lateralSteps = {120, 180, 240};
      Location best = null;
      double bestScore = Double.NEGATIVE_INFINITY;

      for (int forward : forwardSteps) {
         for (int lateral : lateralSteps) {
            for (int sign : new int[] {1, -1}) {
               final int cx = ox + (int) Math.round(ux * forward + px * lateral * sign);
               final int cy = oy + (int) Math.round(uy * forward + py * lateral * sign);
               final Location step = GeoEngine.getInstance().canMoveToTargetLoc(ox, oy, oz, cx, cy, targetZ);
               if (step == null) {
                  continue;
               }

               final long movedSq = (long) (step.getX() - ox) * (step.getX() - ox) + (long) (step.getY() - oy) * (step.getY() - oy);
               if (movedSq < 120L * 120L) {
                  continue;
               }

               final double progress = -distanceSq(step.getX(), step.getY(), step.getZ(), targetX, targetY, targetZ);
               final double score = progress + movedSq * 0.25d;
               if (score > bestScore) {
                  bestScore = score;
                  best = step;
               }
            }
         }
      }

      return best;
   }

   protected boolean isAttackerInSiege(Siege siege) {
      return siege != null && _fakePlayer.getClan() != null && siege.checkSide(_fakePlayer.getClan(), SiegeSide.ATTACKER);
   }

   protected Creature findNearestSiegeEnemy(int radius) {
      return _fakePlayer.getKnownTypeInRadius(Creature.class, radius)
         .stream()
         .filter(this::isValidSiegeEnemy)
         .filter(this::isReachableSiegeEnemy)
         .sorted((first, second) -> Double.compare(_fakePlayer.getDistanceSq(first), _fakePlayer.getDistanceSq(second)))
         .findFirst()
         .orElse(null);
   }

   protected Creature findPriorityEnemyNearObjective(Creature objective, int radius) {
      if (objective == null) {
         return null;
      }

      return _fakePlayer.getKnownTypeInRadius(Creature.class, radius)
         .stream()
         .filter(this::isValidSiegeEnemy)
         .filter(this::isReachableSiegeEnemy)
         .filter(enemy -> enemy.isInsideRadius(objective, radius, false, false))
         .sorted((first, second) -> Double.compare(_fakePlayer.getDistanceSq(first), _fakePlayer.getDistanceSq(second)))
         .findFirst()
         .orElse(null);
   }

   protected boolean isValidSiegeEnemy(Creature creature) {
      if (creature == null || creature.isDead() || creature == _fakePlayer) {
         return false;
      }

      if (creature instanceof Player) {
         final Player enemy = creature.getActingPlayer();
         if (enemy == null || enemy == _fakePlayer) {
            return false;
         }

         if (_fakePlayer.getClanId() > 0 && enemy.getClanId() > 0 && _fakePlayer.getClanId() == enemy.getClanId()) {
            return false;
         }

         if (_fakePlayer.getAllyId() > 0 && enemy.getAllyId() > 0 && _fakePlayer.getAllyId() == enemy.getAllyId()) {
            return false;
         }

         return enemy.getSiegeState() > 0 && enemy.getSiegeState() != _fakePlayer.getSiegeState();
      }

      if (creature instanceof Summon) {
         final Player owner = ((Summon) creature).getOwner();
         if (owner == null) {
            return false;
         }

         if (_fakePlayer.getClanId() > 0 && owner.getClanId() > 0 && _fakePlayer.getClanId() == owner.getClanId()) {
            return false;
         }

         if (_fakePlayer.getAllyId() > 0 && owner.getAllyId() > 0 && _fakePlayer.getAllyId() == owner.getAllyId()) {
            return false;
         }

         return owner.getSiegeState() > 0 && owner.getSiegeState() != _fakePlayer.getSiegeState();
      }

      if (creature instanceof Npc) {
         return ((Npc) creature).isAutoAttackable(_fakePlayer) && isReachableSiegeEnemy(creature);
      }

      return checkTarget(creature);
   }

   protected Creature findImmediateSiegeThreat(int radius) {
      return _fakePlayer.getKnownTypeInRadius(Creature.class, radius)
         .stream()
         .filter(this::isValidSiegeEnemy)
         .filter(enemy -> enemy.getTarget() == _fakePlayer)
         .filter(this::isReachableSiegeEnemy)
         .sorted((first, second) -> Double.compare(_fakePlayer.getDistanceSq(first), _fakePlayer.getDistanceSq(second)))
         .findFirst()
         .orElse(null);
   }

   protected boolean shouldInterruptGateAssaultForEnemy(Creature enemy, Door gate) {
      if (enemy == null) {
         return false;
      }

      if (enemy instanceof Npc && enemy.isInsideZone(ZoneId.WATER) && !_fakePlayer.isInsideZone(ZoneId.WATER)) {
         return false;
      }

      if (enemy.getTarget() == _fakePlayer && _fakePlayer.isInsideRadius(enemy, 320, false, false)) {
         return true;
      }

      if (_fakePlayer.isInsideRadius(enemy, 220, false, false)) {
         return true;
      }

      return false;
   }

   protected boolean isEnemyNearActiveGate(Creature enemy) {
      final Siege siege = resolveActiveSiege();
      if (siege == null || !isAttackerInSiege(siege)) {
         return false;
      }

      final Door gate = findPrimaryAttackerDoor(siege.getCastle());
      return shouldInterruptGateAssaultForEnemy(enemy, gate);
   }

   protected boolean shouldHandleAttackerEnemyNow(Siege siege, Castle castle, Creature enemy) {
      if (enemy == null || !isAttackerInSiege(siege)) {
         return enemy != null;
      }

      if (areOuterDoorsBreached(castle)) {
         return true;
      }

      final Door primaryDoor = findPrimaryAttackerDoor(castle);
      if (primaryDoor != null && shouldInterruptGateAssaultForEnemy(enemy, primaryDoor)) {
         return true;
      }

      return enemy.getTarget() == _fakePlayer && _fakePlayer.isInsideRadius(enemy, 280, false, false);
   }

   protected boolean isReachableSiegeEnemy(Creature enemy) {
      if (enemy == null || enemy.isDead()) {
         return false;
      }

      if (enemy instanceof Npc && enemy.isInsideZone(ZoneId.WATER) && !_fakePlayer.isInsideZone(ZoneId.WATER)) {
         return false;
      }

      if (Math.abs(enemy.getZ() - _fakePlayer.getZ()) > SIEGE_ENEMY_MAX_Z_DIFF) {
         return false;
      }

      final GeoEngine geo = GeoEngine.getInstance();
      if (!geo.canSeeTarget(_fakePlayer, enemy)) {
         return false;
      }

      if (enemy instanceof Npc) {
         return isEnemyNearActiveGate(enemy) || geo.canMoveToTarget(_fakePlayer.getX(), _fakePlayer.getY(), _fakePlayer.getZ(), enemy.getX(), enemy.getY(), enemy.getZ());
      }

      return true;
   }

   protected boolean coordinateWithSiegeClan(Siege siege, Castle castle, FakeRole siegeRole) {
      final List<FakePlayer> clanFakes = getClanSiegeFakes(siege);
      if (clanFakes.size() <= 1) {
         return false;
      }

      final FakePlayer squadLeader = findSiegeSquadLeader(clanFakes);
      updateSiegeLeaderTracking(squadLeader);
      if (isAttackerInSiege(siege) && squadLeader == _fakePlayer) {
         final int requiredPushAllies = getRequiredPushAllies(clanFakes.size());
         final int alliesAroundLeader = countNearbyClanSiegeAlliesAround(siege, _fakePlayer, SIEGE_NEARBY_ALLY_RADIUS);
         if (alliesAroundLeader < requiredPushAllies - 1 && !isRegroupTimeoutReached()) {
            final Location regroupPoint = areOuterDoorsBreached(castle) ? findAttackerPushPoint(castle) : findAttackerStagingPoint(siege, castle);
            if (regroupPoint != null) {
               if (_siegeRegroupStartedAt == 0L) {
                  _siegeRegroupStartedAt = System.currentTimeMillis();
               }
               return maybeMoveToPosition(regroupPoint.getX(), regroupPoint.getY(), regroupPoint.getZ(), 120);
            }
         }
         _siegeRegroupStartedAt = 0L;
      }

      if (isAttackerInSiege(siege) && squadLeader != null && squadLeader != _fakePlayer) {
         final int maxGap = isBacklineSiegeRole(siegeRole) ? SIEGE_BACKLINE_MAX_GAP : SIEGE_FRONTLINE_MAX_GAP;
         if (_fakePlayer.getDistanceSq(squadLeader) > (long) maxGap * maxGap) {
            return maybeMoveToPawn(squadLeader, isBacklineSiegeRole(siegeRole) ? SIEGE_BACKLINE_FOLLOW_OFFSET : SIEGE_FRONTLINE_FOLLOW_OFFSET);
         }
      }
      _siegeRegroupStartedAt = 0L;

      if (isBacklineSiegeRole(siegeRole)) {
         final FakePlayer frontline = findNearestClanFrontliner(siege, clanFakes);
         if (frontline != null && _fakePlayer != frontline && _fakePlayer.getDistanceSq(frontline) > (long) SIEGE_BACKLINE_MAX_GAP * SIEGE_BACKLINE_MAX_GAP) {
            return maybeMoveToPawn(frontline, SIEGE_BACKLINE_FOLLOW_OFFSET);
         }
      }

      return false;
   }

   protected FakePlayer findSiegeSquadLeader(List<FakePlayer> clanFakes) {
      FakePlayer leader = null;
      for (FakePlayer fake : clanFakes) {
         if (fake == null || fake.isDead()) {
            continue;
         }

         if (leader == null || fake.getObjectId() < leader.getObjectId()) {
            leader = fake;
         }
      }
      return leader;
   }

   protected boolean shouldRegroupWithClan(int clanSize, int nearbyAllies, FakePlayer squadLeader) {
      if (clanSize < 3) {
         return false;
      }

      if (squadLeader == null || squadLeader == _fakePlayer || isRegroupTimeoutReached()) {
         return false;
      }

      return nearbyAllies < Math.min(3, clanSize);
   }

   protected void updateSiegeLeaderTracking(FakePlayer squadLeader) {
      final long now = System.currentTimeMillis();
      if (squadLeader != null) {
         _lastSiegeLeaderObjectId = squadLeader.getObjectId();
         _siegeLeaderLastSeenAt = now;
      } else if (_lastSiegeLeaderObjectId != 0 && _siegeLeaderLastSeenAt == 0L) {
         _siegeLeaderLastSeenAt = now;
      }
   }

   protected boolean isRegroupTimeoutReached() {
      return _siegeRegroupStartedAt > 0L && System.currentTimeMillis() - _siegeRegroupStartedAt >= SIEGE_REGROUP_TIMEOUT_MS;
   }

   protected boolean shouldResolveOvercrowding(Creature objective) {
      if (objective == null || !isSiegeObjectiveTarget(objective)) {
         return false;
      }
      if (objective instanceof Door) {
         // For gates, spreading often causes mass idle at siege start.
         // Keep pressure on the same door instead of sidestep regroup.
         return false;
      }

      int stacked = 0;
      for (FakePlayer other : _fakePlayer.getKnownTypeInRadius(FakePlayer.class, SIEGE_OVERCROWD_RADIUS)) {
         if (other == null || other == _fakePlayer || other.getClanId() != _fakePlayer.getClanId()) {
            continue;
         }

         final com.l2jmega.gameserver.model.WorldObject otherTarget = other.getTarget();
         if (otherTarget == objective) {
            stacked++;
         }
      }

      return stacked >= SIEGE_OVERCROWD_LIMIT;
   }

   protected boolean spreadFromObjective(Creature objective, FakeRole siegeRole) {
      final int direction = (getRoleAssignmentIndex(getClanSiegeFakes(resolveActiveSiege()), _fakePlayer, false) & 1) == 0 ? 1 : -1;
      final int spreadDistance = isBacklineSiegeRole(siegeRole) ? 260 : 180;
      final int dx = _fakePlayer.getX() - objective.getX();
      final int dy = _fakePlayer.getY() - objective.getY();
      final int sidestepX = _fakePlayer.getX() + ((dy >= 0 ? 1 : -1) * direction * spreadDistance);
      final int sidestepY = _fakePlayer.getY() + ((dx >= 0 ? -1 : 1) * direction * spreadDistance);

      if (GeoEngine.getInstance().canMoveToTargetLoc(_fakePlayer.getX(), _fakePlayer.getY(), _fakePlayer.getZ(), sidestepX, sidestepY, _fakePlayer.getZ()) != null) {
         moveTo(sidestepX, sidestepY, _fakePlayer.getZ());
         return true;
      }
      return false;
   }

   protected int countNearbyClanSiegeAllies(Siege siege, int radius) {
      return countNearbyClanSiegeAlliesAround(siege, _fakePlayer, radius);
   }

   protected int countNearbyClanSiegeAlliesAround(Siege siege, FakePlayer anchor, int radius) {
      int count = 0;
      if (anchor == null) {
         return count;
      }

      for (FakePlayer member : getClanSiegeFakes(siege)) {
         if (member != null && member != anchor && anchor.isInsideRadius(member, radius, false, false)) {
            count++;
         }
      }
      return count;
   }

   protected int getRequiredPushAllies(int clanSize) {
      return Math.max(2, Math.min(4, clanSize >= 8 ? 4 : (clanSize >= 5 ? 3 : 2)));
   }

   protected Location findAttackerStagingPoint(Siege siege, Castle castle) {
      if (areOuterDoorsBreached(castle)) {
         return findAttackerPushPoint(castle);
      }

      final Location entryPoint = getAttackerEntryReference(castle);
      final Door primaryDoor = findPrimaryAttackerDoor(castle);
      if (entryPoint != null && primaryDoor != null) {
         final int stagedX = entryPoint.getX() + (int) Math.round((primaryDoor.getX() - entryPoint.getX()) * 0.28);
         final int stagedY = entryPoint.getY() + (int) Math.round((primaryDoor.getY() - entryPoint.getY()) * 0.28);
         final int stagedZ = entryPoint.getZ() + (int) Math.round((primaryDoor.getZ() - entryPoint.getZ()) * 0.28);
         return new Location(stagedX, stagedY, stagedZ);
      }

      final Location respawn = resolveSiegeRespawnLocation(siege);
      if (respawn != null) {
         return respawn;
      }

      if (castle != null && castle.getSiegeZone() != null) {
         final Location siegeSpawn = castle.getSiegeZone().getSpawnLoc();
         if (siegeSpawn != null) {
            return siegeSpawn;
         }
      }

      return findAttackerPushPoint(castle);
   }

   protected FakePlayer findNearestClanFrontliner(Siege siege, List<FakePlayer> clanFakes) {
      FakePlayer best = null;
      double bestDistance = Double.MAX_VALUE;

      for (FakePlayer fake : clanFakes) {
         if (fake == null || fake == _fakePlayer || fake.isDead()) {
            continue;
         }

         if (!isFrontlineCandidate(getEffectiveRoleForMember(siege, clanFakes, fake))) {
            continue;
         }

         final double distance = _fakePlayer.getDistanceSq(fake);
         if (distance < bestDistance) {
            bestDistance = distance;
            best = fake;
         }
      }

      return best;
   }

   protected FakeRole getEffectiveRoleForMember(Siege siege, List<FakePlayer> clanFakes, FakePlayer fake) {
      final FakeRole nativeRole = determineBaseRole(fake);
      if (nativeRole == FakeRole.HEALER) {
         return FakeRole.HEALER;
      }

      if (nativeRole == FakeRole.SUPPORT && !hasClanRole(clanFakes, FakeRole.HEALER)) {
         return FakeRole.HEALER;
      }

      if (isFrontlineCandidate(nativeRole)) {
         final int frontlineSlots = Math.max(1, Math.min(4, (clanFakes.size() + 2) / 3));
         final int frontlineIndex = getRoleAssignmentIndex(clanFakes, fake, true);
         if (frontlineIndex >= 0 && frontlineIndex < frontlineSlots) {
            return FakeRole.TANK;
         }
      }

      if (nativeRole == FakeRole.DPS_RANGE) {
         return FakeRole.DPS_RANGE;
      }

      if (nativeRole == FakeRole.SUPPORT) {
         return FakeRole.SUPPORT;
      }

      if (nativeRole == FakeRole.ASSASSIN && clanFakes.size() >= 4) {
         return FakeRole.ASSASSIN;
      }

      return FakeRole.DPS_MELEE;
   }

   protected Creature findNearestSiegeObjective(Siege siege, FakeRole siegeRole) {
      if (siegeRole == FakeRole.HEALER || siegeRole == FakeRole.SUPPORT) {
         return null;
      }

      final Door door = findPrimaryAttackerDoor(siege.getCastle());
      if (door != null) {
         return door;
      }

      if (siegeRole == FakeRole.DPS_RANGE) {
         final Npc tower = findNearestTowerObjective(siege);
         if (tower != null) {
            return tower;
         }
      }

      final Npc tower = findNearestTowerObjective(siege);
      if (tower != null) {
         return tower;
      }

      final HolyThing artifact = findNearestArtifactObjective(siege.getCastle());
      if (artifact != null) {
         return artifact;
      }

      return null;
   }

   protected Creature findNearestSiegeObjective(Siege siege) {
      return findNearestSiegeObjective(siege, getEffectiveSiegeRole(siege));
   }

   protected Door findNearestDoorObjective(Castle castle) {
      return castle.getDoors().stream()
         .filter(door -> isValidAssaultDoor(castle, door))
         .sorted((first, second) -> Double.compare(_fakePlayer.getDistanceSq(first), _fakePlayer.getDistanceSq(second)))
         .findFirst()
         .orElse(null);
   }

   protected Door findPrimaryAttackerDoor(Castle castle) {
      if (castle == null || castle.getDoors().isEmpty()) {
         return null;
      }
      if (areOuterDoorsBreached(castle)) {
         final Door innerDoor = findPreferredInnerGateDoor(castle);
         return innerDoor != null ? innerDoor : findNearestDoorObjective(castle);
      }

      final Location entryPoint = getAttackerEntryReference(castle);
      if (entryPoint == null) {
         return findNearestDoorObjective(castle);
      }

      final Door laneDoor = castle.getDoors().stream()
         .filter(door -> isValidAssaultDoor(castle, door))
         .sorted((first, second) -> Double.compare(distanceSq(entryPoint, first), distanceSq(entryPoint, second)))
         .findFirst()
         .orElse(null);
      return laneDoor != null ? laneDoor : findNearestDoorObjective(castle);
   }

   protected Door findPreferredInnerGateDoor(Castle castle) {
      if (castle == null || castle.getDoors().isEmpty()) {
         return null;
      }

      final List<Door> laneDoorsByDepth = getPrimaryLaneDoorsByDepth(castle);
      final List<Door> innerPool = new ArrayList<>();
      final List<Door> outerDoors = getPrimaryOuterLaneDoors(castle);
      final int outerThresholdDepth;
      if (!laneDoorsByDepth.isEmpty() && !outerDoors.isEmpty()) {
         final Location entryPoint = getAttackerEntryReference(castle);
         final Door deepestOuter = outerDoors.get(outerDoors.size() - 1);
         final double depth = entryPoint != null ? distanceSq(entryPoint, deepestOuter) : _fakePlayer.getDistanceSq(deepestOuter);
         outerThresholdDepth = (int) Math.round(depth);
      } else {
         outerThresholdDepth = Integer.MIN_VALUE;
      }

      final Location entryPoint = getAttackerEntryReference(castle);
      final String innerToken = "inner";
      final String gateToken = "gate";

      for (Door door : laneDoorsByDepth) {
         final double depth = entryPoint != null ? distanceSq(entryPoint, door) : _fakePlayer.getDistanceSq(door);
         if (depth > outerThresholdDepth) {
            innerPool.add(door);
         }
      }

      final java.util.stream.Stream<Door> source = innerPool.isEmpty() ? laneDoorsByDepth.stream() : innerPool.stream();
      return source
         .filter(door -> isValidAssaultDoor(castle, door))
         .filter(door -> {
            final String name = door.getName();
            if (name == null) {
               return false;
            }
            final String lowered = name.toLowerCase();
            return lowered.contains(innerToken) && lowered.contains(gateToken);
         })
         // Prefer the deeper gate relative to attacker entry reference.
         .sorted((first, second) -> {
            final double firstDepth = entryPoint != null ? distanceSq(entryPoint, first) : _fakePlayer.getDistanceSq(first);
            final double secondDepth = entryPoint != null ? distanceSq(entryPoint, second) : _fakePlayer.getDistanceSq(second);
            return Double.compare(secondDepth, firstDepth);
         })
         .findFirst()
         .orElse(null);
   }

   protected List<Door> getPrimaryLaneAssaultDoors(Castle castle) {
      if (castle == null || castle.getDoors().isEmpty()) {
         return new ArrayList<>();
      }

      final List<Door> laneDoors = castle.getDoors().stream()
         .filter(door -> isValidAssaultDoor(castle, door))
         .filter(door -> isDoorOnPrimaryAssaultLane(castle, door))
         .collect(java.util.stream.Collectors.toList());

      if (!laneDoors.isEmpty()) {
         return laneDoors;
      }

      return castle.getDoors().stream()
         .filter(door -> isValidAssaultDoor(castle, door))
         .collect(java.util.stream.Collectors.toList());
   }

   protected List<Door> getPrimaryLaneDoorsByDepth(Castle castle) {
      final List<Door> laneDoors = getPrimaryLaneAssaultDoors(castle);
      final Location entryPoint = getAttackerEntryReference(castle);
      laneDoors.sort((first, second) -> {
         final double firstDepth = entryPoint != null ? distanceSq(entryPoint, first) : _fakePlayer.getDistanceSq(first);
         final double secondDepth = entryPoint != null ? distanceSq(entryPoint, second) : _fakePlayer.getDistanceSq(second);
         return Double.compare(firstDepth, secondDepth);
      });
      return laneDoors;
   }

   protected List<Door> getPrimaryOuterLaneDoors(Castle castle) {
      final List<Door> laneDoorsByDepth = getPrimaryLaneDoorsByDepth(castle);
      if (laneDoorsByDepth.isEmpty()) {
         return laneDoorsByDepth;
      }

      final int outerCount = laneDoorsByDepth.size() >= 4 ? 2 : 1;
      return new ArrayList<>(laneDoorsByDepth.subList(0, Math.min(outerCount, laneDoorsByDepth.size())));
   }

   protected boolean isValidAssaultDoor(Castle castle, Door door) {
      if (door == null || door.isDead() || door.isOpened()) {
         return false;
      }
      return !isSideGateDoor(door);
   }

   protected boolean isSideGateDoor(Door door) {
      if (door == null || door.getName() == null) {
         return false;
      }
      final String lowered = door.getName().toLowerCase();
      return lowered.contains("side");
   }

   protected boolean isDoorOnPrimaryAssaultLane(Castle castle, Door door) {
      if (castle == null || door == null) {
         return true;
      }

      final Location entry = getAttackerEntryReference(castle);
      final Location keep = castle.getCastleZone() != null ? castle.getCastleZone().getSpawnLoc() : null;
      if (entry == null || keep == null) {
         return true;
      }

      final double ax = keep.getX() - entry.getX();
      final double ay = keep.getY() - entry.getY();
      final double len = Math.hypot(ax, ay);
      if (len < 1.0d) {
         return true;
      }

      final double ux = ax / len;
      final double uy = ay / len;
      final double dx = door.getX() - entry.getX();
      final double dy = door.getY() - entry.getY();
      final double projection = dx * ux + dy * uy;
      if (projection < 0.0d) {
         return false;
      }

      final double lateral = Math.abs(dx * uy - dy * ux);
      // Skip far lateral doors (typically side gates) and keep central lane.
      return lateral <= 900.0d;
   }

   protected Location getAttackerEntryReference(Castle castle) {
      if (castle == null) {
         return null;
      }

      final Location configuredSpawn = FakePlayerConfig.getFakeSiegeAttackerSpawnForCastle(castle.getCastleId());
      if (configuredSpawn != null) {
         return configuredSpawn;
      }

      if (castle.getCastleZone() != null) {
         final Location exteriorCastleSpawn = castle.getCastleZone().getChaoticSpawnLoc();
         if (exteriorCastleSpawn != null) {
            return exteriorCastleSpawn;
         }
      }

      return castle.getSiegeZone() != null ? castle.getSiegeZone().getSpawnLoc() : null;
   }

   protected double distanceSq(Location point, Creature target) {
      if (point == null || target == null) {
         return Double.MAX_VALUE;
      }

      final long dx = point.getX() - target.getX();
      final long dy = point.getY() - target.getY();
      final long dz = point.getZ() - target.getZ();
      return (double) dx * dx + (double) dy * dy + (double) dz * dz;
   }

   protected Npc findNearestTowerObjective(Siege siege) {
      ControlTower bestControlTower = null;
      double bestControlTowerDistance = Double.MAX_VALUE;

      for (ControlTower tower : siege.getControlTowers()) {
         if (tower == null || tower.isDead() || !tower.isAutoAttackable(_fakePlayer)) {
            continue;
         }

         final double distance = _fakePlayer.getDistanceSq(tower);
         if (distance < bestControlTowerDistance) {
            bestControlTowerDistance = distance;
            bestControlTower = tower;
         }
      }

      // Prioritize Life/Control Towers first, because they affect defender respawn.
      if (bestControlTower != null) {
         return bestControlTower;
      }

      FlameTower bestFlameTower = null;
      double bestFlameTowerDistance = Double.MAX_VALUE;
      for (FlameTower tower : siege.getFlameTowers()) {
         if (tower == null || tower.isDead() || !tower.isAutoAttackable(_fakePlayer)) {
            continue;
         }

         final double distance = _fakePlayer.getDistanceSq(tower);
         if (distance < bestFlameTowerDistance) {
            bestFlameTowerDistance = distance;
            bestFlameTower = tower;
         }
      }

      return bestFlameTower;
   }

   protected boolean isValidSiegeObjective(Creature target, Siege siege) {
      if (!isSiegeObjectiveTarget(target) || target.isDead()) {
         return false;
      }

      if (target instanceof Door) {
         return canAssaultSiegeDoor((Door) target, siege);
      }

      if (target instanceof HolyThing) {
         return castleOwnsArtifact(siege != null ? siege.getCastle() : null, (HolyThing) target);
      }

      return siege != null && target instanceof Npc && ((Npc) target).isAutoAttackable(_fakePlayer);
   }

   protected boolean canAssaultSiegeDoor(Door door, Siege siege) {
      if (door == null || door.isDead() || door.isOpened()) {
         return false;
      }

      if (door.isAutoAttackable(_fakePlayer)) {
         return true;
      }

      // Fallback for attacker fakes in siege when side resolution/packets briefly desync.
      return siege != null && isAttackerInSiege(siege) && _fakePlayer.getSiegeState() == 1;
   }

   protected boolean isSiegeObjectiveTarget(Creature target) {
      return target instanceof Door || target instanceof ControlTower || target instanceof FlameTower || target instanceof HolyThing;
   }

   protected HolyThing findNearestArtifactObjective(Castle castle) {
      if (castle == null || castle.getArtifacts().isEmpty()) {
         return null;
      }

      HolyThing bestTarget = null;
      double bestDistance = Double.MAX_VALUE;

      for (com.l2jmega.gameserver.model.WorldObject object : com.l2jmega.gameserver.model.World.getInstance().getObjects()) {
         if (!(object instanceof HolyThing)) {
            continue;
         }

         final HolyThing artifact = (HolyThing) object;
         if (!castleOwnsArtifact(castle, artifact)) {
            continue;
         }

         final double distance = _fakePlayer.getDistanceSq(artifact);
         if (distance < bestDistance) {
            bestDistance = distance;
            bestTarget = artifact;
         }
      }

      return bestTarget;
   }

   protected boolean castleOwnsArtifact(Castle castle, HolyThing artifact) {
      return castle != null && artifact != null && castle.getArtifacts().contains(artifact.getNpcId());
   }

   protected Location findAttackerPushPoint(Castle castle) {
      if (castle == null) {
         return null;
      }

      if (areOuterDoorsBreached(castle) && !areAllCastleDoorsBreached(castle)) {
         final Location postGateWaypoint = getPostOuterGateWaypointForCastle(castle.getCastleId());
         if (postGateWaypoint != null && !_fakePlayer.isInsideRadius(postGateWaypoint.getX(), postGateWaypoint.getY(), postGateWaypoint.getZ(), 220, false, false)) {
            final Location geoStep = GeoEngine.getInstance().canMoveToTargetLoc(_fakePlayer.getX(), _fakePlayer.getY(), _fakePlayer.getZ(), postGateWaypoint.getX(), postGateWaypoint.getY(), postGateWaypoint.getZ());
            if (geoStep != null) {
               return postGateWaypoint;
            }
         }
      }

      final Door door = findPrimaryAttackerDoor(castle);
      if (door != null) {
         final Location approach = getDoorApproachPoint(door);
         return approach != null ? approach : door.getPosition();
      }

      final Siege siege = castle.getSiege();
      if (siege != null) {
         final Npc tower = findNearestTowerObjective(siege);
         if (tower != null) {
            return tower.getPosition();
         }
      }

      final HolyThing artifact = findNearestArtifactObjective(castle);
      if (artifact != null) {
         return artifact.getPosition();
      }

      if (castle.getCastleZone() != null) {
         return castle.getCastleZone().getSpawnLoc();
      }

      return castle.getSiegeZone().getSpawnLoc();
   }

   protected Location getPostOuterGateWaypointForCastle(int castleId) {
      switch (castleId) {
         case 1: // Gludio
            return new Location(-18113, 108597, -2343);
         case 2: // Dion
            return new Location(22073, 161153, -2537);
         case 3: // Giran
            return new Location(117329, 145041, -2412);
         case 4: // Oren
            return new Location(83407, 37189, -2133);
         case 5: // Aden
            return new Location(147456, 5724, 158);
         case 6: // Innadril
            return new Location(116024, 249932, -633);
         case 7: // Goddard
            return new Location(147456, -49200, -1619);
         case 8: // Rune
            return new Location(10775, -48481, 83);
         case 9: // Schuttgart
            return new Location(77547, -153246, 112);
         default:
            return null;
      }
   }

   protected Location findDefenderAnchor(Castle castle) {
      return findDefenderAnchor(castle, castle != null ? castle.getSiege() : null, castle != null && castle.getSiege() != null ? getEffectiveSiegeRole(castle.getSiege()) : _fakeRole);
   }

   protected Location findDefenderAnchor(Castle castle, Siege siege, FakeRole siegeRole) {
      final List<Location> anchors = new ArrayList<>();
      final boolean outerDoorsBreached = areOuterDoorsBreached(castle);
      final boolean allDoorsBreached = areAllCastleDoorsBreached(castle);

      if (!outerDoorsBreached && (siegeRole == FakeRole.TANK || siegeRole == FakeRole.DPS_MELEE || siegeRole == FakeRole.ASSASSIN)) {
         for (Door door : castle.getDoors()) {
            if (door != null && !door.isDead()) {
               anchors.add(door.getPosition());
            }
         }
      }

      if (outerDoorsBreached || siegeRole == FakeRole.DPS_RANGE || siegeRole == FakeRole.HEALER || siegeRole == FakeRole.SUPPORT) {
         for (ControlTower tower : castle.getSiege().getControlTowers()) {
            if (tower != null && !tower.isDead()) {
               anchors.add(tower.getPosition());
            }
         }

         for (FlameTower tower : castle.getSiege().getFlameTowers()) {
            if (tower != null && !tower.isDead()) {
               anchors.add(tower.getPosition());
            }
         }
      }

      if (allDoorsBreached) {
         final HolyThing artifact = findNearestArtifactObjective(castle);
         if (artifact != null) {
            anchors.add(artifact.getPosition());
         }
      }

      if (castle.getCastleZone() != null && castle.getCastleZone().getSpawnLoc() != null) {
         anchors.add(castle.getCastleZone().getSpawnLoc());
      }

      if (anchors.isEmpty()) {
         return castle.getSiegeZone().getSpawnLoc();
      }

      final List<FakePlayer> clanFakes = getClanSiegeFakes(siege);
      final int formationIndex = clanFakes.isEmpty() ? 0 : Math.max(0, clanFakes.indexOf(_fakePlayer));
      return anchors.get(formationIndex % anchors.size());
   }

   protected boolean areOuterDoorsBreached(Castle castle) {
      if (castle == null) {
         return false;
      }

      final List<Door> outerDoors = getPrimaryOuterLaneDoors(castle);
      if (outerDoors.isEmpty()) {
         return false;
      }

      for (Door door : outerDoors) {
         if (door != null && !door.isDead() && !door.isOpened()) {
            return false;
         }
      }
      return true;
   }

   protected boolean areAllCastleDoorsBreached(Castle castle) {
      int total = 0;
      for (Door door : castle.getDoors()) {
         if (door == null) {
            continue;
         }
         total++;
         if (!door.isDead() && !door.isOpened()) {
            return false;
         }
      }
      return total > 0;
   }
   
   /**
    * Проверка на отступление
    */
   protected void handleOlympiadPreparation(long now) {
      handleShots();

      if (now < _nextOlympiadPrepActionTime) {
         return;
      }

      if (Rnd.get(100) < 65) {
         selfSupportBuffs();
      }

      final Creature opponent = findOlympiadOpponent();
      if (opponent != null && _fakePlayer.getTarget() != opponent) {
         _fakePlayer.setTarget(opponent);
         _lastOlympiadTargetUpdate = now;
         applyHumanReactionDelay();
      }

      _nextOlympiadPrepActionTime = now + Rnd.get(600, 1600);
   }

   protected Creature findOlympiadOpponent() {
      final int gameId = _fakePlayer.getOlympiadGameId();
      if (gameId < 0) {
         return null;
      }

      final Creature currentTarget = _fakePlayer.getTarget() instanceof Creature ? (Creature) _fakePlayer.getTarget() : null;
      if (isValidOlympiadTarget(currentTarget)) {
         return currentTarget;
      }

      final List<Player> players = _fakePlayer.getKnownTypeInRadius(Player.class, 2500)
         .stream()
         .filter(this::isValidOlympiadTarget)
         .sorted((first, second) -> Double.compare(_fakePlayer.getDistanceSq(first), _fakePlayer.getDistanceSq(second)))
         .collect(java.util.stream.Collectors.toList());

      return players.isEmpty() ? null : players.get(0);
   }

   protected boolean isValidOlympiadTarget(Creature target) {
      if (!(target instanceof Player)) {
         return false;
      }

      final Player player = (Player) target;
      if (player == _fakePlayer || player.isDead() || player.isInObserverMode()) {
         return false;
      }

      if (!player.isInOlympiadMode() || player.getOlympiadGameId() != _fakePlayer.getOlympiadGameId()) {
         return false;
      }

      return GeoEngine.getInstance().canSeeTarget(_fakePlayer, player);
   }

   protected boolean handleOlympiadCombat(Creature target, FakeEmotion emotion) {
      if (target == null || target.isDead()) {
         return false;
      }

      final long now = System.currentTimeMillis();
      final boolean archerClass = isArcherClass();
      final int engageRange = archerClass ? getPreferredArcherRange() : getPreferredOlympiadRange();
      final int minimumGap = archerClass ? getMinimumArcherGap() : 180;
      final int distance = (int) Math.sqrt(_fakePlayer.getDistanceSq(target));

      if (_fakeRole == FakeRole.HEALER) {
         return handleOlympiadHealerCombat(target, distance, Math.max(420, engageRange), Math.max(OLY_HEALER_MIN_GAP, minimumGap), now);
      }

      if (_olympiadFightStartedAt > 0L && now - _olympiadFightStartedAt < 7000L && tryUseOlympiadOpener(target)) {
         applyOlympiadCadence(450, 950);
         return true;
      }

      if (archerClass && tryMaintainArcherDistance(target, engageRange, minimumGap)) {
         applyOlympiadCadence(250, 650);
         return true;
      }

      if (distance > engageRange + 80 && target == _fakePlayer.getTarget() && !tryCloseOlympiadDistance(target, engageRange)) {
         return false;
      }

      if (_fakeRole == FakeRole.DPS_RANGE && distance < 180 && now >= _nextOlympiadStrafeTime) {
         performOlympiadSidestep(target);
         _nextOlympiadStrafeTime = now + Rnd.get(1200, 2600);
         applyOlympiadCadence(250, 700);
         return true;
      }

      if (tryUseOlympiadEmergencySkill()) {
         applyOlympiadCadence(350, 800);
         return true;
      }

      if (target.getCurrentHp() / Math.max(1.0, target.getMaxHp()) < 0.32 && tryUseOlympiadFinisher(target)) {
         applyOlympiadCadence(350, 900);
         return true;
      }

      if (archerClass) {
         tryAttackingUsingFighterOffensiveSkill();
      } else if (_fakeRole == FakeRole.DPS_RANGE) {
         if (!tryUseOlympiadMageRotation(target)) {
            tryCloseOlympiadDistance(target, Math.max(220, engageRange));
         }
      } else {
         tryAttackingUsingFighterOffensiveSkill();
      }

      applyOlympiadCadence(200, 650);
      return true;
   }

   protected boolean handleOlympiadHealerCombat(Creature target, int distance, int engageRange, int minimumGap, long now) {
      if (target == null || target.isDead()) {
         return false;
      }

      final double selfHpPercent = 100.0 * _fakePlayer.getCurrentHp() / Math.max(1.0, _fakePlayer.getMaxHp());
      final double selfMpPercent = 100.0 * _fakePlayer.getCurrentMp() / Math.max(1.0, _fakePlayer.getMaxMp());
      final double targetHpPercent = 100.0 * target.getCurrentHp() / Math.max(1.0, target.getMaxHp());
      final double targetMpPercent = 100.0 * target.getCurrentMp() / Math.max(1.0, target.getMaxMp());

      if (distance < minimumGap && now >= _nextOlympiadStrafeTime) {
         performOlympiadSidestep(target);
         _nextOlympiadStrafeTime = now + Rnd.get(1100, 2400);
         applyOlympiadCadence(220, 640);
         return true;
      }

      if (distance > engageRange + 90) {
         if (!tryCloseOlympiadDistance(target, engageRange)) {
            return false;
         }
         applyOlympiadCadence(220, 600);
         return true;
      }

      if (selfHpPercent <= OLY_HEALER_EMERGENCY_HP_PERCENT && tryUseOlympiadEmergencySkill()) {
         applyOlympiadCadence(300, 760);
         return true;
      }

      if (selfHpPercent <= OLY_HEALER_RECOVERY_HP_PERCENT && tryUseOlympiadHealerRecoverySkill(selfHpPercent)) {
         applyOlympiadCadence(280, 740);
         return true;
      }

      if (tryUseOlympiadClassSelfBuffs()) {
         applyOlympiadCadence(260, 680);
         return true;
      }

      if (targetHpPercent <= OLY_HEALER_FINISH_WINDOW_HP_PERCENT && tryUseOlympiadFinisher(target)) {
         applyOlympiadCadence(320, 820);
         return true;
      }

      if (selfHpPercent >= OLY_HEALER_SAFE_HP_FOR_BURN_PERCENT
         && selfMpPercent >= OLY_HEALER_MIN_MP_FOR_BURN_PERCENT
         && targetMpPercent >= OLY_HEALER_TARGET_MIN_MP_PERCENT
         && tryUseOlympiadManaBurn(target)) {
         applyOlympiadCadence(260, 700);
         return true;
      }

      if (!tryUseOlympiadMageRotation(target)) {
         tryCloseOlympiadDistance(target, Math.max(280, engageRange));
      }

      applyOlympiadCadence(220, 660);
      return true;
   }

   protected boolean tryUseOlympiadClassSelfBuffs() {
      if (!_fakePlayer.isInOlympiadMode() || !_fakePlayer.isOlympiadStart() || _fakePlayer.isCastingNow()) {
         return false;
      }

      final List<L2Skill> selfBuffs = _fakePlayer.getSkills()
         .values()
         .stream()
         .filter(Objects::nonNull)
         .filter(L2Skill::isActive)
         .filter(skill -> !skill.isPassive() && !skill.isOffensive() && !skill.isDebuff() && !skill.isToggle() && !skill.isPotion())
         .filter(skill -> skill.getTargetType() == L2Skill.SkillTargetType.TARGET_SELF || skill.getTargetType() == L2Skill.SkillTargetType.TARGET_NONE)
         .sorted((first, second) -> {
            final int levelCmp = Integer.compare(second.getLevel(), first.getLevel());
            return levelCmp != 0 ? levelCmp : Integer.compare(first.getId(), second.getId());
         })
         .collect(java.util.stream.Collectors.toList());

      for (L2Skill buff : selfBuffs) {
         if (_fakePlayer.getFirstEffect(buff.getId()) != null) {
            continue;
         }

         _fakePlayer.setTarget(_fakePlayer);
         if (_fakePlayer.checkUseMagicConditions(buff, true, false)) {
            castSpell(buff);
            return true;
         }
      }

      return false;
   }

   protected boolean tryUseOlympiadHealerRecoverySkill(double selfHpPercent) {
      final List<phantom.model.HealingSpell> healingSpells = getHealingSpells();
      if (healingSpells == null || healingSpells.isEmpty()) {
         return false;
      }

      final List<phantom.model.HealingSpell> sorted = healingSpells.stream()
         .sorted((first, second) -> Integer.compare(first.getPriority(), second.getPriority()))
         .collect(java.util.stream.Collectors.toList());

      for (phantom.model.HealingSpell spell : sorted) {
         final L2Skill healSkill = _fakePlayer.getSkill(spell.getSkillId());
         if (healSkill == null) {
            continue;
         }

         if (selfHpPercent > spell.getConditionValue() && selfHpPercent > OLY_HEALER_RECOVERY_HP_PERCENT) {
            continue;
         }

         _fakePlayer.setTarget(_fakePlayer);
         if (_fakePlayer.checkUseMagicConditions(healSkill, true, false)) {
            castSpell(healSkill);
            return true;
         }
      }

      return false;
   }

   protected boolean tryUseOlympiadManaBurn(Creature target) {
      if (target == null || target.isDead()) {
         return false;
      }

      final List<OffensiveSpell> offensiveSpells = getOffensiveSpells();
      if (offensiveSpells == null || offensiveSpells.isEmpty()) {
         return false;
      }

      final List<OffensiveSpell> sorted = offensiveSpells.stream()
         .sorted((first, second) -> Integer.compare(first.getPriority(), second.getPriority()))
         .collect(java.util.stream.Collectors.toList());

      for (OffensiveSpell offensiveSpell : sorted) {
         final L2Skill skill = _fakePlayer.getSkill(offensiveSpell.getSkillId());
         if (skill == null) {
            continue;
         }

         _fakePlayer.setTarget(target);
         if (canUseOlympiadMageSkill(skill, target) || _fakePlayer.checkUseMagicConditions(skill, true, false)) {
            castSpell(skill);
            return true;
         }
      }

      return false;
   }

   protected int getPreferredOlympiadRange() {
      if (isArcherClass()) {
         return getPreferredArcherRange();
      }

      if (_fakeRole == FakeRole.DPS_RANGE) {
         int preferredRange = 650;
         final List<OffensiveSpell> offensiveSpells = getOffensiveSpells();
         if (offensiveSpells != null) {
            for (OffensiveSpell offensiveSpell : offensiveSpells) {
               final L2Skill skill = _fakePlayer.getSkill(offensiveSpell.getSkillId());
               if (skill != null && skill.getCastRange() > 0) {
                  preferredRange = Math.max(preferredRange, Math.min(900, skill.getCastRange() - 30));
               }
            }
         }
         return preferredRange;
      }

      switch (_fakeRole) {
         case TANK:
         case DPS_MELEE:
         case ASSASSIN:
            return 90;
         case DPS_RANGE:
         case SUPPORT:
            return 650;
         default:
            return 500;
      }
   }

   protected boolean tryCloseOlympiadDistance(Creature target, int engageRange) {
      final int offset = Math.max(40, engageRange);
      return maybeMoveToPawn(target, offset);
   }

   protected void performOlympiadSidestep(Creature target) {
      final int dx = _fakePlayer.getX() - target.getX();
      final int dy = _fakePlayer.getY() - target.getY();
      final int sidestepX = _fakePlayer.getX() + (dy >= 0 ? 120 : -120);
      final int sidestepY = _fakePlayer.getY() + (dx >= 0 ? -120 : 120);
      moveTo(sidestepX, sidestepY, _fakePlayer.getZ());
   }

   protected boolean tryUseOlympiadOpener(Creature target) {
      return tryCastOlympiadSkill(getOlympiadOpenerSkillId(), target);
   }

   protected boolean tryUseOlympiadFinisher(Creature target) {
      return tryCastOlympiadSkill(getOlympiadFinisherSkillId(), target);
   }

   protected boolean tryUseOlympiadEmergencySkill() {
      if (_fakePlayer.getCurrentHp() / Math.max(1.0, _fakePlayer.getMaxHp()) > 0.45) {
         return false;
      }

      final int emergencySkillId = getOlympiadEmergencySkillId();
      if (emergencySkillId <= 0) {
         return false;
      }

      final L2Skill skill = _fakePlayer.getSkill(emergencySkillId);
      if (skill == null || !_fakePlayer.checkUseMagicConditions(skill, true, false)) {
         return false;
      }

      castSelfSpell(skill);
      return true;
   }

   protected boolean tryCastOlympiadSkill(int skillId, Creature target) {
      if (skillId <= 0) {
         return false;
      }

      final L2Skill skill = _fakePlayer.getSkill(skillId);
      if (skill == null) {
         return false;
      }

      _fakePlayer.setTarget(target);
      if (canUseOlympiadMageSkill(skill, target)) {
         castSpell(skill);
         return true;
      }

      if (_fakePlayer.checkUseMagicConditions(skill, true, false)) {
         castSpell(skill);
         return true;
      }
      return false;
   }

   protected boolean tryUseOlympiadMageRotation(Creature target) {
      if (target == null || target.isDead()) {
         return false;
      }

      _fakePlayer.setTarget(target);
      final List<OffensiveSpell> offensiveSpells = getOffensiveSpells();
      if (offensiveSpells == null || offensiveSpells.isEmpty()) {
         return false;
      }

      for (OffensiveSpell offensiveSpell : offensiveSpells) {
         final L2Skill skill = _fakePlayer.getSkill(offensiveSpell.getSkillId());
         if (skill == null) {
            continue;
         }

         if (canUseOlympiadMageSkill(skill, target) || _fakePlayer.checkUseMagicConditions(skill, true, false)) {
            castSpell(skill);
            return true;
         }
      }
      return false;
   }

   protected boolean canUseOlympiadMageSkill(L2Skill skill, Creature target) {
      return skill != null
         && target != null
         && _fakeRole == FakeRole.DPS_RANGE
         && !isArcherClass()
         && _fakePlayer.isInOlympiadMode()
         && _fakePlayer.isOlympiadStart()
         && target.getActingPlayer() != null
         && target.getActingPlayer().isInOlympiadMode()
         && _fakePlayer.getOlympiadGameId() == target.getActingPlayer().getOlympiadGameId()
         && !_fakePlayer.isCastingNow()
         && !_fakePlayer.isSkillDisabled(skill);
   }

   protected int getOlympiadOpenerSkillId() {
      switch (_fakePlayer.getClassId()) {
         case ARCHMAGE:
            return 1230;
         case SOULTAKER:
            return 1148;
         case MYSTIC_MUSE:
            return 1235;
         case STORM_SCREAMER:
            return 1267;
         case DUELIST:
            return 345;
         case TITAN:
            return 362;
         case DREADNOUGHT:
            return 347;
         case PHOENIX_KNIGHT:
            return 454;
         case ADVENTURER:
         case WIND_RIDER:
         case GHOST_HUNTER:
            return 344;
         case SAGGITARIUS:
         case MOONLIGHT_SENTINEL:
         case GHOST_SENTINEL:
            return 51;
         case GRAND_KHAVATARI:
            return 346;
         default:
            return getFallbackOffensiveSkillId();
      }
   }

   protected int getOlympiadFinisherSkillId() {
      switch (_fakePlayer.getClassId()) {
         case DUELIST:
            return 261;
         case TITAN:
            return 315;
         case DREADNOUGHT:
            return 347;
         case ARCHMAGE:
         case MYSTIC_MUSE:
         case STORM_SCREAMER:
            return getFallbackOffensiveSkillId();
         case SOULTAKER:
            return 1148;
         default:
            return getFallbackOffensiveSkillId();
      }
   }

   protected int getOlympiadEmergencySkillId() {
      switch (_fakePlayer.getClassId()) {
         case DUELIST:
         case TITAN:
            return 139;
         case PHOENIX_KNIGHT:
            return 368;
         case CARDINAL:
            return 1218;
         default:
            return 2037;
      }
   }

   protected int getFallbackOffensiveSkillId() {
      final List<OffensiveSpell> offensiveSpells = getOffensiveSpells();
      if (offensiveSpells == null || offensiveSpells.isEmpty()) {
         return 0;
      }
      return offensiveSpells.get(0).getSkillId();
   }

   protected void applyOlympiadCadence(int minDelay, int maxDelay) {
      _nextActionTime = System.currentTimeMillis() + Rnd.get(minDelay, maxDelay);
   }

   protected boolean shouldRetreat(Creature target, FakeEmotion emotion) {
      // Проверка по HP
      double hpPercent = _fakePlayer.getCurrentHp() / _fakePlayer.getMaxHp() * 100;
      if (hpPercent < 20) {
         return true;
      }

      // Проверка по эмоции страха
      if (emotion != null && emotion.getFear() > 70) {
         return true;
      }

      return false;
   }
   
   /**
    * Обработка отступления
    */
   protected void handleRetreat(Creature target) {
      if (_isRetreating) {
         return;
      }
      _isRetreating = true;
      
      int retreatX = _fakePlayer.getX() - (target.getX() - _fakePlayer.getX()) * 2;
      int retreatY = _fakePlayer.getY() - (target.getY() - _fakePlayer.getY()) * 2;
      int retreatZ = _fakePlayer.getZ();
      
      _fakePlayer.getFakeAi().moveTo(retreatX, retreatY, retreatZ);
      _fakePlayer.abortAttack();
      
      
      _isRetreating = false;
   }
   
   /**
    * Обработка эмоциональных реакций
    */
   protected void handleEmotionalReactions(Creature target, FakeEmotion emotion) {
      // Keep emotion changes internal; avoid combat chat spam.
   }
   
   /**
    * Проверка на человеческую ошибку
    */
   protected boolean shouldMakeHumanError() {
      if (_errorCount >= 3) {
         return false; // Не больше 3 ошибок подряд
      }
      
      return Rnd.get(100) < FakePlayerConfig.FAKE_HUMAN_ERROR_RATE;
   }
   
   /**
    * Обработка человеческой ошибки
    */
   protected void handleHumanError() {
      _errorCount++;
      
      int errorType = Rnd.get(4);
      switch (errorType) {
         case 0:
            // Забыть включить шоты
            _fakePlayer.getAutoSoulShot().clear();
            if (FakePlayerConfig.FAKE_PLAYERS_DEBUG) {
               _fakePlayer.say("[ERROR] forgot shots!");
            }
            break;
         case 1:
            // Использовать не тот скилл
            _nextActionTime = System.currentTimeMillis() + 3000;
            if (FakePlayerConfig.FAKE_PLAYERS_DEBUG) {
               _fakePlayer.say("[ERROR] wrong skill!");
            }
            break;
         case 2:
            // Затупить после смерти
            _nextActionTime = System.currentTimeMillis() + 5000;
            if (FakePlayerConfig.FAKE_PLAYERS_DEBUG) {
               _fakePlayer.say("[ERROR] brb...");
            }
            break;
         case 3:
            // Споткнуться (прервать каст)
            _fakePlayer.abortCast();
            if (FakePlayerConfig.FAKE_PLAYERS_DEBUG) {
               _fakePlayer.say("[ERROR] miscast!");
            }
            break;
      }
      
      // Сбросить счетчик ошибок со временем
      ThreadPool.schedule(() -> _errorCount = 0, 60000);
   }
   
   /**
    * Применить человеческую задержку реакции
    */
   protected void applyHumanReactionDelay() {
      if (FakePlayerConfig.FAKE_HUMAN_REACTION_DELAY_ENABLED) {
         int delay = Rnd.get(
            FakePlayerConfig.FAKE_HUMAN_REACTION_DELAY_MIN,
            FakePlayerConfig.FAKE_HUMAN_REACTION_DELAY_MAX
         );
         _nextActionTime = System.currentTimeMillis() + delay;
      }
   }
   
   /**
    * Посчитать врагов вокруг
    */
   protected int countEnemiesAround() {
      int count = 0;
      for (Creature c : _fakePlayer.getKnownTypeInRadius(Creature.class, 500)) {
         if (c != null && !c.isDead() && c.getTarget() == _fakePlayer) {
            count++;
         }
      }
      return count;
   }
   
   @Override
   protected void handleDeath() {
      super.handleDeath();

      // Обновление эмоции при смерти
      if (_fakePlayer.isDead()) {
         _fakePlayer.getEmotion().onDeath(true);
      } else {
         // Сброс при ресе
         _fakePlayer.getEmotion().reset();
      }
   }
   
   /**
    * Обработка убийства цели
    */
   protected void onTargetKilled(Creature target) {
      boolean wasPlayer = target instanceof Player && !(target instanceof FakePlayer);
      _fakePlayer.getEmotion().onKill(wasPlayer);
      
   }
   
   /**
    * Обработка получения урона
    */
   protected void onDamageReceived(double damagePercent) {
      _fakePlayer.getEmotion().onDamageReceived(damagePercent);
   }
   
   /**
    * Обработка нанесения урона
    */
   protected void onDamageDealt(double damagePercent) {
      _fakePlayer.getEmotion().onDamageDealt(damagePercent);
   }
   
   /**
    * Получить роль фантома
    */
   public FakeRole getFakeRole() {
      return _fakeRole;
   }

   private AdvancedPositioning.CombatRole toCombatRole(FakeRole role) {
      switch (role) {
         case HEALER:
            return AdvancedPositioning.CombatRole.HEALER;
         case TANK:
            return AdvancedPositioning.CombatRole.TANK;
         case DPS_RANGE:
            return AdvancedPositioning.CombatRole.DPS_RANGE;
         case SUPPORT:
            return AdvancedPositioning.CombatRole.SUPPORT;
         case ASSASSIN:
            return AdvancedPositioning.CombatRole.ASSASSIN;
         case DPS_MELEE:
         default:
            return AdvancedPositioning.CombatRole.DPS_MELEE;
      }
   }
   
   /**
    * Получить координатора партии
    */
   public PartyPvPCoordinator getPartyCoordinator() {
      return _partyCoordinator;
   }
   
   /**
    * Получить менеджера позиционирования
    */
   public AdvancedPositioning getPositioning() {
      return _positioning;
   }
}
