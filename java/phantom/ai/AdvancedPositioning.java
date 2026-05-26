package phantom.ai;

import com.l2jmega.gameserver.geoengine.GeoEngine;
import com.l2jmega.gameserver.model.location.Location;
import com.l2jmega.gameserver.model.actor.Creature;
import com.l2jmega.commons.random.Rnd;
import com.l2jmega.events.BossEvent;
import com.l2jmega.events.BossEvent.EventState;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.item.kind.Weapon;
import com.l2jmega.gameserver.model.item.type.WeaponType;
import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.model.FakeEmotion;

/**
 * Продвинутая система позиционирования для боевых AI.
 * Включает: kiting, strafing, line of sight, range management
 */
@SuppressWarnings({"javadoc", "unused", "static-method"})
public class AdvancedPositioning {
   public enum CombatRole {
      TANK,
      HEALER,
      DPS_MELEE,
      DPS_RANGE,
      SUPPORT,
      ASSASSIN
   }
   
   // Константы позиционирования
   private static final int KITE_DISTANCE = 300;      // Дистанция для кайта
   private static final int STRAFE_RADIUS = 150;      // Радиус стрейфа
   private static final int LOS_CHECK_DISTANCE = 500; // Дистанция проверки LOS
   private static final int OPTIMAL_MELEE_RANGE = 80; // Оптимальная мили дистанция
   private static final int OPTIMAL_RANGE_DISTANCE = 400; // Оптимальная рейндж дистанция
   
   // Состояния движения
   private Location _strafeTarget;
   private long _lastKiteTime;
   private long _lastStrafeChange;
   private int _strafeDirection; // 1 = clockwise, -1 = counter-clockwise
   private boolean _isKiting;
   private boolean _isStrafing;
   
   public AdvancedPositioning() {
      this._strafeDirection = Rnd.get(2) == 0 ? 1 : -1;
      this._isKiting = false;
      this._isStrafing = false;
   }
   
   /**
    * Основная логика позиционирования
    */
   public void updatePosition(FakePlayer fakePlayer, Creature target, CombatRole role, FakeEmotion emotion) {
      if (target == null || target.isDead()) {
         return;
      }

      double distance = Math.sqrt(fakePlayer.getDistanceSq(target));

      // BossEvent: keep movement minimal for realism.
      // Only archers (bow users) are allowed to kite/strafe; everyone else stands and hits.
      if (isInBossEvent(fakePlayer) && !isBowUser(fakePlayer)) {
         if (distance > OPTIMAL_MELEE_RANGE + 50) {
            moveToOptimalRange(fakePlayer, target, OPTIMAL_MELEE_RANGE);
         }
         return;
      }

      // Эмоциональное влияние на позиционирование
      if (emotion != null && emotion.getFear() > 60) {
         // Испуганный фантом пытается отступить
         tryRetreat(fakePlayer, target);
         return;
      }

      // Позиционирование в зависимости от роли
      switch (role) {
         case DPS_RANGE:
         case ASSASSIN:
            handleRangePositioning(fakePlayer, target, distance);
            break;

         case DPS_MELEE:
         case TANK:
            handleMeleePositioning(fakePlayer, target, distance);
            break;

         case HEALER:
         case SUPPORT:
            handleSupportPositioning(fakePlayer, target, distance);
            break;
      }
   }

   private static boolean isInBossEvent(Player fakePlayer) {
      try {
         return BossEvent.getInstance().getState() == EventState.FIGHTING
            && BossEvent.getInstance().isRegistered(fakePlayer);
      } catch (Exception e) {
         return false;
      }
   }

   private static boolean isBowUser(FakePlayer fakePlayer) {
      final Weapon weapon = fakePlayer != null ? fakePlayer.getActiveWeaponItem() : null;
      return weapon != null && weapon.getItemType() == WeaponType.BOW;
   }
   
   /**
    * Позиционирование для рейндж классов (арчеры, маги)
    */
   private void handleRangePositioning(FakePlayer fakePlayer, Creature target, double distance) {
      // Kiting - отступление при сближении
      if (distance < KITE_DISTANCE && canKite(fakePlayer, target)) {
         performKiting(fakePlayer, target);
         return;
      }

      // Strafe - движение по кругу
      if (distance < OPTIMAL_RANGE_DISTANCE * 1.5 && canStrafe(fakePlayer, target)) {
         performStrafe(fakePlayer, target);
         return;
      }

      // Держать оптимальную дистанцию
      if (distance > OPTIMAL_RANGE_DISTANCE + 100) {
         moveToOptimalRange(fakePlayer, target, OPTIMAL_RANGE_DISTANCE);
      } else if (distance < OPTIMAL_RANGE_DISTANCE - 100) {
         moveAwayFromTarget(fakePlayer, target, OPTIMAL_RANGE_DISTANCE);
      }
   }

   /**
    * Позиционирование для мили классов
    */
   private void handleMeleePositioning(FakePlayer fakePlayer, Creature target, double distance) {
      // Если слишком далеко - подойти
      if (distance > OPTIMAL_MELEE_RANGE + 50) {
         moveToOptimalRange(fakePlayer, target, OPTIMAL_MELEE_RANGE);
         return;
      }

      // Strafe вокруг цели (избегание фронтала)
      if (distance <= OPTIMAL_MELEE_RANGE && canStrafe(fakePlayer, target)) {
         performStrafe(fakePlayer, target);
         return;
      }

      // Проверка на фронтал атаки (пытаться зайти за спину)
      if (shouldMoveToBack(fakePlayer, target)) {
         moveToTargetBack(fakePlayer, target);
      }
   }

   /**
    * Позиционирование для саппортов/хилеров
    */
   private void handleSupportPositioning(FakePlayer fakePlayer, Creature target, double distance) {
      // Хилеры держатся на безопасной дистанции
      int safeDistance = 400;

      if (distance > safeDistance + 100) {
         moveToOptimalRange(fakePlayer, target, safeDistance);
      } else if (distance < safeDistance - 100) {
         moveAwayFromTarget(fakePlayer, target, safeDistance);
      }
   }
   
   /**
    * Kiting - отступление с атакой
    */
   private void performKiting(FakePlayer fakePlayer, Creature target) {
      long now = System.currentTimeMillis();
      
      // Не кайтить слишком часто
      if (now - _lastKiteTime < 2000) {
         return;
      }
      
      _lastKiteTime = now;
      _isKiting = true;
      
      // Направление отступления
      int kiteX = fakePlayer.getX() - (target.getX() - fakePlayer.getX()) * 2;
      int kiteY = fakePlayer.getY() - (target.getY() - fakePlayer.getY()) * 2;
      int kiteZ = fakePlayer.getZ();
      
      // Проверка гео
      if (GeoEngine.getInstance().canMoveToTargetLoc(fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(),
              kiteX, kiteY, kiteZ) != null) {
         fakePlayer.getFakeAi().moveTo(kiteX, kiteY, kiteZ);
         
         if (FakePlayerConfig.FAKE_PLAYERS_DEBUG) {
            fakePlayer.say("[KITE] retreating!");
         }
      }
      
      _isKiting = false;
   }
   
   /**
    * Strafe - движение по кругу вокруг цели
    */
   private void performStrafe(FakePlayer fakePlayer, Creature target) {
      long now = System.currentTimeMillis();
      
      // Менять направление стрейфа периодически
      if (now - _lastStrafeChange > 5000) {
         _strafeDirection = -_strafeDirection;
         _lastStrafeChange = now;
      }
      
      _isStrafing = true;
      
      // Расчет точки для стрейфа
      double angle = Math.atan2(fakePlayer.getY() - target.getY(), fakePlayer.getX() - target.getX());
      angle += (_strafeDirection * Math.PI / 8); // 22.5 градусов
      
      int strafeX = target.getX() + (int) (STRAFE_RADIUS * Math.cos(angle));
      int strafeY = target.getY() + (int) (STRAFE_RADIUS * Math.sin(angle));
      int strafeZ = target.getZ();
      
      // Проверка гео
      if (GeoEngine.getInstance().canMoveToTargetLoc(fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(),
              strafeX, strafeY, strafeZ) != null) {
         fakePlayer.getFakeAi().moveTo(strafeX, strafeY, strafeZ);
      }
      
      _isStrafing = false;
   }
   
   /**
    * Отступление при низком HP или страхе
    */
   private void tryRetreat(FakePlayer fakePlayer, Creature target) {
      // Найти безопасное место
      int retreatX = fakePlayer.getX() - (target.getX() - fakePlayer.getX()) * 3;
      int retreatY = fakePlayer.getY() - (target.getY() - fakePlayer.getY()) * 3;
      int retreatZ = fakePlayer.getZ();

      if (GeoEngine.getInstance().canMoveToTargetLoc(fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(),
              retreatX, retreatY, retreatZ) != null) {
         fakePlayer.getFakeAi().moveTo(retreatX, retreatY, retreatZ);

         if (FakePlayerConfig.FAKE_PLAYERS_DEBUG) {
            fakePlayer.say("[RETREAT] too dangerous!");
         }
      }
   }
   
   /**
    * Движение к оптимальной дистанции
    */
   private void moveToOptimalRange(FakePlayer fakePlayer, Creature target, int optimalDistance) {
      if (fakePlayer.isMovementDisabled()) {
         return;
      }
      
      // Двигаться к цели
      fakePlayer.getFakeAi().moveToPawn(target, optimalDistance);
   }
   
   /**
    * Отступление от цели
    */
   private void moveAwayFromTarget(FakePlayer fakePlayer, Creature target, int safeDistance) {
      int awayX = fakePlayer.getX() - (target.getX() - fakePlayer.getX());
      int awayY = fakePlayer.getY() - (target.getY() - fakePlayer.getY());
      int awayZ = fakePlayer.getZ();
      
      if (GeoEngine.getInstance().canMoveToTargetLoc(fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(),
              awayX, awayY, awayZ) != null) {
         fakePlayer.getFakeAi().moveTo(awayX, awayY, awayZ);
      }
   }
   
   /**
    * Проверка возможности кайта
    */
   private boolean canKite(FakePlayer fakePlayer, Creature target) {
      // Не кайтить если застрял или в стене
      if (fakePlayer.isMovementDisabled()) {
         return false;
      }
      
      // Проверка гео
      return GeoEngine.getInstance().canSeeTarget(fakePlayer, target);
   }
   
   /**
    * Проверка возможности стрейфа
    */
   private boolean canStrafe(FakePlayer fakePlayer, Creature target) {
      if (fakePlayer.isMovementDisabled()) {
         return false;
      }
      
      // Не стрейфить если уже двигается к другой цели
      if (_isKiting) {
         return false;
      }
      
      return true;
   }
   
   /**
    * Проверка Line of Sight
    */
   private boolean hasLineOfSight(FakePlayer fakePlayer, Creature target) {
      return GeoEngine.getInstance().canSeeTarget(fakePlayer, target);
   }
   
   /**
    * Движение к позиции с LOS
    */
   private void moveToLOS(FakePlayer fakePlayer, Creature target) {
      // Найти точку с видимостью
      int[] directions = {0, 90, 180, 270, 45, 135, 225, 315};

      for (int dir : directions) {
         double rad = Math.toRadians(dir);
         int checkX = target.getX() + (int) (300 * Math.cos(rad));
         int checkY = target.getY() + (int) (300 * Math.sin(rad));
         int checkZ = target.getZ();

         if (GeoEngine.getInstance().canSeeTarget(fakePlayer, target)) {
            if (GeoEngine.getInstance().canMoveToTargetLoc(fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(),
                    checkX, checkY, checkZ) != null) {
               fakePlayer.getFakeAi().moveTo(checkX, checkY, checkZ);
               return;
            }
         }
      }
   }
   
   /**
    * Проверка необходимости зайти за спину
    */
   private boolean shouldMoveToBack(FakePlayer fakePlayer, Creature target) {
      // Проверка угла между направлением цели и фантомом
      int targetHeading = target.getHeading();
      int angleToFake = calculateAngle(target, fakePlayer);
      
      // Если фантом спереди (в угле атаки цели)
      int angleDiff = Math.abs(angleToFake - targetHeading);
      if (angleDiff > 32768) {
         angleDiff = 65536 - angleDiff;
      }
      
      // Если угол меньше 90 градусов (спереди) - нужно зайти за спину
      return angleDiff < 16384; // 90 градусов в L2 координатах
   }
   
   /**
    * Движение за спину цели
    */
   private void moveToTargetBack(FakePlayer fakePlayer, Creature target) {
      int backX = target.getX() - (fakePlayer.getX() - target.getX());
      int backY = target.getY() - (fakePlayer.getY() - target.getY());
      int backZ = target.getZ();
      
      if (GeoEngine.getInstance().canMoveToTargetLoc(fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(),
              backX, backY, backZ) != null) {
         fakePlayer.getFakeAi().moveTo(backX, backY, backZ);
      }
   }
   
   /**
    * Рассчитать угол между двумя объектами
    */
   private int calculateAngle(Creature from, Creature to) {
      int dx = to.getX() - from.getX();
      int dy = to.getY() - from.getY();
      
      return (int) (Math.atan2(dy, dx) * 32768 / Math.PI);
   }
   
   /**
    * Получить текущее состояние кайта
    */
   public boolean isKiting() {
      return _isKiting;
   }
   
   /**
    * Получить текущее состояние стрейфа
    */
   public boolean isStrafing() {
      return _isStrafing;
   }
   
   /**
    * Сброс состояния
    */
   public void reset() {
      _isKiting = false;
      _isStrafing = false;
      _strafeTarget = null;
   }
}
