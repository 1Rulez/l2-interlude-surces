package phantom.model;

import com.l2jmega.gameserver.model.L2Skill;
import com.l2jmega.gameserver.model.actor.Creature;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import phantom.FakePlayer;

/**
 * Simple CC-chain helper for phantom AI.
 */
public class CCChainManager {

   /**
    * Supported crowd-control effect types.
    */
   public enum CCType {
      STUN(100),
      ROOT(80),
      SLEEP(75),
      FEAR(60),
      SILENCE(70),
      BLEED(40),
      POISON(35),
      DEBUFF(50),
      NONE(0);

      private final int priority;

      CCType(int priority) {
         this.priority = priority;
      }

      public int getPriority() {
         return priority;
      }
   }

   /**
    * Description of a crowd-control skill.
    */
   public static class CCSkill {
      public int skillId;
      public CCType ccType;
      public int duration;
      public int cooldown;
      public int mpCost;
      public int castRange;

      public CCSkill(int skillId, CCType ccType, int duration, int cooldown) {
         this.skillId = skillId;
         this.ccType = ccType;
         this.duration = duration;
         this.cooldown = cooldown;
         this.mpCost = 0;
         this.castRange = 0;
      }

      public CCSkill setCosts(int mpCost, int castRange) {
         this.mpCost = mpCost;
         this.castRange = castRange;
         return this;
      }
   }

   /**
    * Current CC state tracked for a target.
    */
   public static class CCState {
      public long lastStunTime;
      public long lastRootTime;
      public long lastSleepTime;
      public long ccEndTime;
      public int ccStacks;
      public boolean isStunned;
      public boolean isRooted;
      public boolean isSleeping;

      public CCState() {
         reset();
      }

      public void reset() {
         lastStunTime = 0;
         lastRootTime = 0;
         lastSleepTime = 0;
         ccEndTime = 0;
         ccStacks = 0;
         isStunned = false;
         isRooted = false;
         isSleeping = false;
      }

      public boolean hasActiveCC() {
         return System.currentTimeMillis() < ccEndTime;
      }

      public boolean canApplyStun() {
         return !isStunned && System.currentTimeMillis() - lastStunTime > 30000;
      }

      public boolean canApplyRoot() {
         return !isRooted && System.currentTimeMillis() - lastRootTime > 20000;
      }

      public boolean canApplySleep() {
         return !isSleeping && System.currentTimeMillis() - lastSleepTime > 30000;
      }
   }

   private final Map<Integer, List<CCSkill>> _ccSkillsByClass = new HashMap<>();
   private final Map<Integer, CCState> _ccStates = new HashMap<>();

   private static final double[] DR_MULTIPLIERS = {1.0, 0.75, 0.5, 0.25, 0.0};

   public CCChainManager() {
      initializeCCSkills();
   }

   /**
    * Initialize CC skills for supported classes.
    */
   private void initializeCCSkills() {
      List<CCSkill> warriorCC = new ArrayList<>();
      warriorCC.add(new CCSkill(45, CCType.STUN, 5000, 30000));
      warriorCC.add(new CCSkill(472, CCType.STUN, 4000, 25000));
      warriorCC.add(new CCSkill(1411, CCType.ROOT, 10000, 20000));
      _ccSkillsByClass.put(100, warriorCC);
      _ccSkillsByClass.put(101, warriorCC);
      _ccSkillsByClass.put(102, warriorCC);

      List<CCSkill> archerCC = new ArrayList<>();
      archerCC.add(new CCSkill(1176, CCType.STUN, 3000, 30000));
      archerCC.add(new CCSkill(1177, CCType.ROOT, 15000, 25000));
      _ccSkillsByClass.put(93, archerCC);
      _ccSkillsByClass.put(106, archerCC);

      List<CCSkill> mageCC = new ArrayList<>();
      mageCC.add(new CCSkill(1144, CCType.ROOT, 10000, 20000));
      mageCC.add(new CCSkill(1157, CCType.SILENCE, 5000, 30000));
      mageCC.add(new CCSkill(1160, CCType.SLEEP, 20000, 40000));
      _ccSkillsByClass.put(103, mageCC);
      _ccSkillsByClass.put(113, mageCC);

      List<CCSkill> supportCC = new ArrayList<>();
      supportCC.add(new CCSkill(1074, CCType.SILENCE, 5000, 30000));
      supportCC.add(new CCSkill(1144, CCType.ROOT, 10000, 20000));
      supportCC.add(new CCSkill(1054, CCType.SLEEP, 30000, 60000));
      _ccSkillsByClass.put(49, supportCC);
      _ccSkillsByClass.put(110, supportCC);
   }

   /**
    * Choose the best CC skill for the current target.
    *
    * @param fakePlayer fake player using CC
    * @param target current target
    * @param chainCC whether chain CC is enabled
    * @return best CC skill to use, or {@code null}
    */
   public CCSkill getBestCCSkill(FakePlayer fakePlayer, Creature target, boolean chainCC) {
      if (target == null || target.isDead()) {
         return null;
      }

      int classId = fakePlayer.getClassId().getId();
      List<CCSkill> availableCC = _ccSkillsByClass.get(classId);

      if (availableCC == null || availableCC.isEmpty()) {
         return null;
      }

      CCState targetState = getCCState(target);

      if (targetState.hasActiveCC()) {
         if (chainCC && targetState.isStunned && !targetState.isRooted) {
            return findCCSkillByType(availableCC, CCType.ROOT);
         }
         return null;
      }

      CCSkill stunSkill = findCCSkillByType(availableCC, CCType.STUN);
      if (stunSkill != null && targetState.canApplyStun()) {
         return stunSkill;
      }

      CCSkill rootSkill = findCCSkillByType(availableCC, CCType.ROOT);
      if (rootSkill != null && targetState.canApplyRoot()) {
         return rootSkill;
      }

      CCSkill sleepSkill = findCCSkillByType(availableCC, CCType.SLEEP);
      if (sleepSkill != null && targetState.canApplySleep()) {
         return sleepSkill;
      }

      return null;
   }

   /**
    * Find a CC skill by effect type.
    *
    * @param skills available skills
    * @param type requested CC type
    * @return matching skill, or {@code null}
    */
   private static CCSkill findCCSkillByType(List<CCSkill> skills, CCType type) {
      for (CCSkill skill : skills) {
         if (skill.ccType == type) {
            return skill;
         }
      }
      return null;
   }

   /**
    * Get or create tracked CC state for the target.
    *
    * @param target tracked target
    * @return target CC state
    */
   public CCState getCCState(Creature target) {
      CCState state = _ccStates.get(target.getObjectId());
      if (state == null) {
         state = new CCState();
         _ccStates.put(target.getObjectId(), state);
      }
      return state;
   }

   /**
    * Register a newly applied CC effect.
    *
    * @param target affected target
    * @param skill applied CC skill
    */
   public void onCCApplied(Creature target, CCSkill skill) {
      CCState state = getCCState(target);
      long now = System.currentTimeMillis();

      switch (skill.ccType) {
         case STUN:
            state.lastStunTime = now;
            state.isStunned = true;
            state.ccEndTime = now + applyDR(skill.duration, state.ccStacks);
            break;
         case ROOT:
            state.lastRootTime = now;
            state.isRooted = true;
            state.ccEndTime = Math.max(state.ccEndTime, now + applyDR(skill.duration, state.ccStacks));
            break;
         case SLEEP:
            state.lastSleepTime = now;
            state.isSleeping = true;
            state.ccEndTime = Math.max(state.ccEndTime, now + applyDR(skill.duration, state.ccStacks));
            break;
         default:
            break;
      }

      state.ccStacks = Math.min(DR_MULTIPLIERS.length - 1, state.ccStacks + 1);
   }

   /**
    * Apply diminishing returns to the CC duration.
    *
    * @param baseDuration original duration in milliseconds
    * @param stacks current DR stack count
    * @return adjusted duration after DR
    */
   private static int applyDR(int baseDuration, int stacks) {
      if (stacks >= DR_MULTIPLIERS.length) {
         return 0;
      }
      return (int) (baseDuration * DR_MULTIPLIERS[stacks]);
   }

   /**
    * Check whether the skill can currently be used.
    *
    * @param fakePlayer fake player using the skill
    * @param ccSkill candidate CC skill
    * @return {@code true} if the skill can be used now
    */
   public boolean isSkillReady(FakePlayer fakePlayer, CCSkill ccSkill) {
      if (ccSkill == null) {
         return false;
      }

      if (fakePlayer.getCurrentMp() < ccSkill.mpCost) {
         return false;
      }

      return fakePlayer.getSkill(ccSkill.skillId) != null;
   }

   /**
    * Placeholder for post-CC damage skill selection.
    *
    * @param fakePlayer fake player using the skill
    * @param target current target
    * @param isCCActive whether target is currently under CC
    * @return best damage skill, or {@code null}
    */
   public L2Skill getBestDamageSkill(FakePlayer fakePlayer, Creature target, boolean isCCActive) {
      if (fakePlayer == null || target == null || !isCCActive) {
         return null;
      }

      return null;
   }

   /**
    * Remove stale CC state entries.
    */
   public void cleanup() {
      long now = System.currentTimeMillis();
      _ccStates.entrySet().removeIf(entry -> now - entry.getValue().ccEndTime > 60000);
   }

   /**
    * Reset tracked CC state for the target.
    *
    * @param target target whose CC state should be reset
    */
   public void resetCCState(Creature target) {
      CCState state = _ccStates.get(target.getObjectId());
      if (state != null) {
         state.reset();
      }
   }
}
