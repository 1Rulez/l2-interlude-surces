package phantom.model;

public abstract class BotSkill {
   protected int _skillId;
   protected SpellUsageCondition _condition;
   protected int _conditionValue;
   protected int _priority;

   public BotSkill(int skillId, SpellUsageCondition condition, int conditionValue, int priority) {
      this._skillId = skillId;
      this._condition = condition;
      this._conditionValue = conditionValue;
      this._priority = priority;
   }

   public BotSkill(int skillId) {
      this._skillId = skillId;
      this._condition = SpellUsageCondition.NONE;
      this._conditionValue = 0;
      this._priority = 0;
   }

   public int getSkillId() {
      return this._skillId;
   }

   public SpellUsageCondition getCondition() {
      return this._condition;
   }

   public int getConditionValue() {
      return this._conditionValue;
   }

   public int getPriority() {
      return this._priority;
   }
}
