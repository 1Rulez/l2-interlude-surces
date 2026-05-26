package phantom.model;

import com.l2jmega.gameserver.model.actor.Creature;
import com.l2jmega.gameserver.model.actor.instance.Monster;
import com.l2jmega.gameserver.model.actor.instance.Player;
import java.util.List;

/**
 * Target prioritization rules for phantom AI.
 */
public final class TargetPriority {

   private TargetPriority() {
   }

   /**
    * Target type used by the prioritization logic.
    */
   public enum TargetType {
      HEALER(100),
      MAGE(80),
      ARCHER(70),
      SUMMON(60),
      WARRIOR(50),
      TANK(30),
      MONSTER(10);

      private final int priority;

      TargetType(int priority) {
         this.priority = priority;
      }

      public int getPriority() {
         return priority;
      }
   }

   /**
    * Fake role used to adjust selection behavior.
    */
   public enum FakeRole {
      TANK,
      HEALER,
      DPS_MELEE,
      DPS_RANGE,
      SUPPORT,
      ASSASSIN
   }

   /**
    * Resolve the target type for the given player.
    *
    * @param player candidate player
    * @return detected target type
    */
   public static TargetType identifyTargetType(Player player) {
      if (player == null) {
         return TargetType.WARRIOR;
      }

      int classId = player.getClassId().getId();

      if (isHealerClass(classId)) {
         return TargetType.HEALER;
      }

      if (isMageClass(classId)) {
         return TargetType.MAGE;
      }

      if (isArcherClass(classId)) {
         return TargetType.ARCHER;
      }

      if (isTankClass(classId)) {
         return TargetType.TANK;
      }

      return TargetType.WARRIOR;
   }

   private static boolean isHealerClass(int classId) {
      return classId == 110 || classId == 111 || classId == 50 || classId == 49 || classId == 112;
   }

   public static boolean isMageClass(int classId) {
      return classId == 113 || classId == 114 || classId == 115 || classId == 116 || classId == 117 || classId == 118;
   }

   public static boolean isArcherClass(int classId) {
      return classId == 106 || classId == 107 || classId == 119 || classId == 120;
   }

   public static boolean isTankClass(int classId) {
      return classId == 108 || classId == 109 || classId == 121 || classId == 122;
   }

   public static boolean isHealerClassPublic(int classId) {
      return isHealerClass(classId);
   }

   /**
    * Pick the best target from the provided list.
    *
    * @param <T> creature type
    * @param fakePlayer acting fake player
    * @param targets candidate targets
    * @param role fake combat role
    * @return best target, or {@code null}
    */
   public static <T extends Creature> T selectBestTarget(T fakePlayer, List<T> targets, FakeRole role) {
      if (targets == null || targets.isEmpty()) {
         return null;
      }

      T bestTarget = null;
      int bestScore = -1;

      for (T target : targets) {
         int score = calculateTargetScore(fakePlayer, target, role);
         if (score > bestScore) {
            bestScore = score;
            bestTarget = target;
         }
      }

      return bestTarget;
   }

   /**
    * Calculate a score for the target based on role and combat context.
    *
    * @param <T> creature type
    * @param fakePlayer acting fake player
    * @param target candidate target
    * @param role fake combat role
    * @return target priority score
    */
   public static <T extends Creature> int calculateTargetScore(T fakePlayer, T target, FakeRole role) {
      if (fakePlayer == null || target == null) {
         return 0;
      }

      int score = 0;

      if (target instanceof Player player) {
         TargetType targetType = identifyTargetType(player);
         score += targetType.getPriority();

         switch (role) {
            case ASSASSIN:
               double hpPercent = getHpPercent(player);
               score += (int) ((1 - hpPercent) * 50);
               break;

            case TANK:
               score += getAggroScore(fakePlayer, player);
               break;

            case HEALER:
               score = 10;
               break;

            case DPS_RANGE:
            case DPS_MELEE:
               if (targetType == TargetType.HEALER || targetType == TargetType.MAGE) {
                  score += 20;
               }
               break;

            default:
               break;
         }

         if (player.getPvpFlag() > 0) {
            score += 15;
         }
         if (player.getKarma() > 0) {
            score += 20;
         }

         double targetHpPercent = getHpPercent(player);
         if (targetHpPercent < 0.3) {
            score += 30;
         } else if (targetHpPercent < 0.5) {
            score += 15;
         }

         double distance = fakePlayer.getDistanceSq(target);
         if (role == FakeRole.DPS_RANGE && distance > 90000) {
            score += 10;
         } else if (role == FakeRole.DPS_MELEE && distance > 10000) {
            score -= 20;
         }
      } else if (target instanceof Monster monster) {
         score = TargetType.MONSTER.getPriority();
         double hpPercent = getHpPercent(monster);
         if (hpPercent < 0.3) {
            score += 40;
         }
      }

      if (!fakePlayer.isInsideRadius(target, 1500, false, false)) {
         score -= 50;
      }

      return score;
   }

   /**
    * Calculate extra score for tank-style target selection.
    *
    * @param fakePlayer acting fake player
    * @param target candidate target
    * @return additional aggro-based score
    */
   private static int getAggroScore(Creature fakePlayer, Player target) {
      int score = 0;

      if (fakePlayer.getParty() != null) {
         for (Player partyMember : fakePlayer.getParty().getPartyMembers()) {
            if (partyMember != null && partyMember.getTarget() == target) {
               score += 30;
            }
         }
      }

      if (target.getTarget() == fakePlayer) {
         score += 40;
      }

      return score;
   }

   /**
    * Check whether the fake should attack this target.
    *
    * @param fakePlayer acting fake player
    * @param target candidate target
    * @param role fake combat role
    * @return {@code true} if target should be attacked
    */
   public static boolean shouldAttackTarget(Creature fakePlayer, Creature target, FakeRole role) {
      if (target == null || target.isDead() || target.isGM() || target.isInvul()) {
         return false;
      }

      if (!fakePlayer.isInsideRadius(target, 1500, false, false)) {
         return false;
      }

      switch (role) {
         case HEALER:
            return target.getTarget() == fakePlayer;
         default:
            return true;
      }
   }

   private static double getHpPercent(Creature creature) {
      double maxHp = creature.getMaxHp();
      if (maxHp <= 0) {
         return 0;
      }
      return creature.getCurrentHp() / maxHp;
   }
}
