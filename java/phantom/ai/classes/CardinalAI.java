package phantom.ai.classes;

import java.util.ArrayList;
import java.util.List;
import com.l2jmega.gameserver.model.L2Skill;
import com.l2jmega.gameserver.model.ShotType;
import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.ai.AdvancedCombatAI;
import phantom.ai.addon.IHealer;
import phantom.helpers.FakeHelpers;
import phantom.model.HealingSpell;
import phantom.model.OffensiveSpell;
import phantom.model.SpellUsageCondition;
import phantom.model.SupportSpell;

public class CardinalAI extends AdvancedCombatAI implements IHealer {
   public CardinalAI(FakePlayer character) {
      super(character);
   }

   @Override
   public void thinkAndAct() {
      super.thinkAndAct();
      if (this._fakePlayer.isInOlympiadMode() || this.isActiveSiegeParticipant()) {
         return;
      }
      this.setBusyThinking(true);
      this.scheduleDespawnOnce(FakePlayerConfig.DESPAWN_PVP_RANDOM_TIME_1 * 60 * 1000, FakePlayerConfig.DESPAWN_PVP_RANDOM_TIME_2 * 60 * 1000);
      this.handleShots();
      this.selfSupportBuffs();
      this.tryTargetingLowestHpTargetInRadius(this._fakePlayer, FakePlayer.class, FakeHelpers.getTestTargetRange());
      this.tryHealingTarget(this._fakePlayer);
      this.setBusyThinking(false);
   }

   @Override
   protected ShotType getShotType() {
      return ShotType.BLESSED_SPIRITSHOT;
   }

   @Override
   protected List<OffensiveSpell> getOffensiveSpells() {
      List<OffensiveSpell> spells = new ArrayList<>();
      spells.add(new OffensiveSpell(1398, 1)); // Mana Burn (used on bosses only by PartyFollowerAI)
      spells.add(new OffensiveSpell(1394, 2));
      return spells;
   }

   @Override
   protected List<HealingSpell> getHealingSpells() {
      List<HealingSpell> _healingSpells = new ArrayList<>();
      _healingSpells.add(new HealingSpell(1401, L2Skill.SkillTargetType.TARGET_ONE, 70, 1));        // Major Heal
      _healingSpells.add(new HealingSpell(1402, L2Skill.SkillTargetType.TARGET_PARTY, 50, 2));      // Major Group Heal
      _healingSpells.add(new HealingSpell(1335, L2Skill.SkillTargetType.TARGET_PARTY, 40, 3));      // Balance Life
      return _healingSpells;
   }

   @Override
   protected List<SupportSpell> getSelfSupportSpells() {
      List<SupportSpell> _selfSupportSpells = new ArrayList<>();
      _selfSupportSpells.add(new SupportSpell(2037, SpellUsageCondition.LESSHPPERCENT, 85, 1));
      _selfSupportSpells.add(new SupportSpell(2166, SpellUsageCondition.MISSINGCP, 500, 1));
      _selfSupportSpells.add(new SupportSpell(2005, SpellUsageCondition.MISSINGMP, 2000, 1));
      _selfSupportSpells.add(new SupportSpell(1409, SpellUsageCondition.NONE, 0, 2));  // Cleanse
      return _selfSupportSpells;
   }
}
