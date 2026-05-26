package phantom.ai.addon;

import java.util.List;
import java.util.stream.Collectors;
import com.l2jmega.gameserver.model.actor.Creature;
import phantom.FakePlayer;
import phantom.ai.CombatAI;
import phantom.model.HealingSpell;

public interface IHealer {
   default void tryTargetingLowestHpTargetInRadius(FakePlayer player, Class<? extends Creature> creatureClass, int radius) {
      if (player.isInOlympiadMode()) {
         player.setTarget(player);
         return;
      }

      if (player.getTarget() == null) {
         List<Creature> targets = player
            .getKnownTypeInRadius(creatureClass, radius)
            .stream()
            .filter(x -> !x.isDead())
            .collect(Collectors.toList());
         if (!player.isDead()) {
            targets.add(player);
         }

         List<Creature> sortedTargets = targets.stream()
            .sorted((x1, x2) -> Double.compare(
               x1.getCurrentHp() / x1.getMaxHp(),
               x2.getCurrentHp() / x2.getMaxHp()))
            .collect(Collectors.toList());
         if (!sortedTargets.isEmpty()) {
            Creature target = sortedTargets.get(0);
            player.setTarget(target);
         }
      } else if (((Creature)player.getTarget()).isDead()) {
         player.setTarget(null);
      }
   }

   default void tryHealingTarget(FakePlayer player) {
      if (player.getTarget() != null && player.getTarget() instanceof Creature) {
         Creature target = player.isInOlympiadMode() ? player : (Creature)player.getTarget();
         if (player.getFakeAi() instanceof CombatAI) {
            HealingSpell skill = ((CombatAI)player.getFakeAi()).getRandomAvaiableHealingSpellForTarget();
            if (skill != null) {
               switch (skill.getCondition()) {
                  case LESSHPPERCENT:
                     double currentHpPercentage = Math.round(100.0 / target.getMaxHp() * target.getCurrentHp());
                     if (currentHpPercentage <= skill.getConditionValue()) {
                        if (skill.getTargetType() == com.l2jmega.gameserver.model.L2Skill.SkillTargetType.TARGET_PARTY) {
                           player.setTarget(player);
                        }
                        player.getFakeAi().castSpell(player.getSkill(skill.getSkillId()));
                     }
                     break;
                  default:
                     break;
               }
            }
         }
      }
   }
}
