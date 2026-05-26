package phantom.model;

import com.l2jmega.gameserver.model.L2Skill;

public class HealingSpell extends BotSkill {
   private L2Skill.SkillTargetType _targetType;

   public HealingSpell(int skillId, L2Skill.SkillTargetType targetType, SpellUsageCondition condition, int conditionValue, int priority) {
      super(skillId, condition, conditionValue, priority);
      this._targetType = targetType;
   }

   public HealingSpell(int skillId, L2Skill.SkillTargetType targetType, int conditionValue, int priority) {
      super(skillId, SpellUsageCondition.LESSHPPERCENT, conditionValue, priority);
      this._targetType = targetType;
   }

   public L2Skill.SkillTargetType getTargetType() {
      return this._targetType;
   }
}
