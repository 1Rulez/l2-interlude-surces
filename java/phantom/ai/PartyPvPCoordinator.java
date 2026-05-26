package phantom.ai;

import java.util.ArrayList;
import java.util.List;
import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.model.FakeEmotion;
import com.l2jmega.gameserver.model.actor.Creature;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.L2Party;

/**
 * Система координации Party PvP для фантомов.
 * Упрощенная версия для совместимости.
 */
@SuppressWarnings({"javadoc", "unused", "static-method", "null"})
public class PartyPvPCoordinator {
   public enum PartyRole {
      LEADER, MAIN_TANK, OFF_TANK, MAIN_HEALER, SUPPORT, DPS_PRIMARY, DPS_SECONDARY
   }

   public enum PartyState {
      ENGAGING, FIGHTING, FOCUSING, RETREATING, RECOVERING, IDLE
   }

   private PartyState _currentState;
   private Creature _focusTarget;
   private long _lastStateChange;

   public PartyPvPCoordinator() {
      this._currentState = PartyState.IDLE;
      this._lastStateChange = System.currentTimeMillis();
   }

   /**
    * Основная логика координации партии
    */
   public void updateCoordination(FakePlayer fakePlayer, FakeEmotion emotion) {
      if (fakePlayer.getParty() == null) {
         return;
      }

      L2Party party = fakePlayer.getParty();

      // Обновление состояния партии
      updatePartyState(party, emotion);

      // Выполнение действий в зависимости от роли
      PartyRole myRole = getPartyRole(fakePlayer);
      switch (myRole) {
         case LEADER:
            handleLeaderRole(fakePlayer, party, emotion);
            break;
         case MAIN_TANK:
         case OFF_TANK:
            handleTankRole(fakePlayer, party);
            break;
         case MAIN_HEALER:
         case SUPPORT:
            handleSupportRole(fakePlayer);
            break;
         case DPS_PRIMARY:
         case DPS_SECONDARY:
            handleDpsRole(fakePlayer, party, emotion);
            break;
      }
   }

   /**
    * Обновление состояния партии
    */
   private void updatePartyState(L2Party party, FakeEmotion emotion) {
      List<Player> members = party.getPartyMembers();
      int aliveCount = 0;
      int lowHpCount = 0;

      for (Player member : members) {
         if (member != null && !member.isDead()) {
            aliveCount++;
            double hpPercent = member.getCurrentHp() / member.getMaxHp() * 100;
            if (hpPercent < 30) {
               lowHpCount++;
            }
         }
      }

      PartyState newState = PartyState.IDLE;

      if (aliveCount == 0) {
         newState = PartyState.IDLE;
      } else if (lowHpCount > members.size() / 2) {
         newState = PartyState.RETREATING;
      } else if (_focusTarget != null && !_focusTarget.isDead()) {
         newState = PartyState.FOCUSING;
      } else {
         newState = PartyState.FIGHTING;
      }

      if (newState != _currentState) {
         _currentState = newState;
         _lastStateChange = System.currentTimeMillis();
      }
   }

   /**
    * Логика для лидера партии
    */
   private void handleLeaderRole(FakePlayer fakePlayer, L2Party party, FakeEmotion emotion) {
      if (_focusTarget == null || _focusTarget.isDead()) {
         _focusTarget = selectFocusTarget(party);
      }

      if (shouldRetreat(party, emotion)) {
         orderRetreat(fakePlayer, party);
      }
   }

   /**
    * Логика для танков
    */
   private void handleTankRole(FakePlayer fakePlayer, L2Party party) {
      if (_focusTarget != null) {
         fakePlayer.setTarget(_focusTarget);
      }
   }

   /**
    * Логика для хилеров/саппортов
    */
   private void handleSupportRole(FakePlayer fakePlayer) {
      // Хилеры лечат союзников
   }

   /**
    * Логика для DPS
    */
   private void handleDpsRole(FakePlayer fakePlayer, L2Party party, FakeEmotion emotion) {
      if (_focusTarget != null && !_focusTarget.isDead()) {
         fakePlayer.setTarget(_focusTarget);
      }

      // Проверка на отступление при низком HP
      double hpPercent = fakePlayer.getCurrentHp() / fakePlayer.getMaxHp() * 100;
      if (hpPercent < 20) {
         retreatFromCombat(fakePlayer);
      }
   }

   /**
    * Выбрать фокус цель для партии
    */
   private Creature selectFocusTarget(L2Party party) {
      List<Creature> potentialTargets = new ArrayList<>();

      for (Player member : party.getPartyMembers()) {
         if (member != null && member.getTarget() instanceof Creature) {
            Creature target = (Creature) member.getTarget();
            if (!target.isDead() && !target.isGM()) {
               potentialTargets.add(target);
            }
         }
      }

      if (potentialTargets.isEmpty()) {
         return null;
      }

      return potentialTargets.get(0);
   }

   /**
    * Объявить фокус цель партии
    */
   private void announceFocusTarget(FakePlayer leader, Creature target) {
      if (FakePlayerConfig.FAKE_PLAYERS_DEBUG) {
         leader.say("[LEADER] focus");
      }
   }

   /**
    * Приказать отступление
    */
   private void orderRetreat(FakePlayer leader, L2Party party) {
      if (_currentState == PartyState.RETREATING) {
         return;
      }

      _currentState = PartyState.RETREATING;

      for (Player member : party.getPartyMembers()) {
         if (member instanceof FakePlayer && !member.isDead()) {
            FakePlayer fake = (FakePlayer) member;
            retreatFromCombat(fake);
         }
      }
   }

   /**
    * Отступление от боя
    */
   private void retreatFromCombat(FakePlayer fakePlayer) {
      com.l2jmega.gameserver.model.WorldObject targetObj = fakePlayer.getTarget();
      if (!(targetObj instanceof Creature)) {
         return;
      }
      Creature target = (Creature) targetObj;

      if (target != null) {
         int retreatX = fakePlayer.getX() - (target.getX() - fakePlayer.getX()) * 2;
         int retreatY = fakePlayer.getY() - (target.getY() - fakePlayer.getY()) * 2;
         int retreatZ = fakePlayer.getZ();

         fakePlayer.getFakeAi().moveTo(retreatX, retreatY, retreatZ);
         fakePlayer.abortAttack();
      }
   }

   /**
    * Проверить, нужно ли отступать
    */
   private boolean shouldRetreat(L2Party party, FakeEmotion emotion) {
      int aliveCount = 0;
      int lowHpCount = 0;

      for (Player member : party.getPartyMembers()) {
         if (member != null) {
            if (!member.isDead()) {
               aliveCount++;
               double hpPercent = member.getCurrentHp() / member.getMaxHp() * 100;
               if (hpPercent < 30) {
                  lowHpCount++;
               }
            }
         }
      }

      return aliveCount < party.getPartyMembers().size() / 2 ||
             lowHpCount > party.getPartyMembers().size() / 2 ||
             (emotion != null && emotion.getFear() > 70);
   }

   /**
    * Получить роль фантома в партии
    */
   private PartyRole getPartyRole(FakePlayer fakePlayer) {
      int classId = fakePlayer.getClassId().getId();

      if (classId == 110 || classId == 111 || classId == 50 || classId == 49 || classId == 112) {
         return PartyRole.MAIN_HEALER;
      }

      if (classId == 108 || classId == 109 || classId == 121 || classId == 122) {
         return PartyRole.MAIN_TANK;
      }

      if (classId == 113 || classId == 114 || classId == 115 || classId == 116 || classId == 117 || classId == 118) {
         return PartyRole.DPS_PRIMARY;
      }

      if (classId == 106 || classId == 107 || classId == 119 || classId == 120) {
         return PartyRole.DPS_SECONDARY;
      }

      return PartyRole.DPS_PRIMARY;
   }

   /**
    * Установить роль для фантома
    */
   public void setPartyRole(FakePlayer fakePlayer, PartyRole role) {
   }

   /**
    * Получить текущее состояние партии
    */
   public PartyState getPartyState() {
      return _currentState;
   }

   /**
    * Получить текущую фокус цель
    */
   public Creature getFocusTarget() {
      return _focusTarget;
   }

   /**
    * Установить фокус цель
    */
   public void setFocusTarget(Creature target) {
      _focusTarget = target;
   }

   /**
    * Сброс состояния
    */
   public void reset() {
      _currentState = PartyState.IDLE;
      _focusTarget = null;
   }
}
