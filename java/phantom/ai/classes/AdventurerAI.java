package phantom.ai.classes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.l2jmega.gameserver.model.ShotType;
import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.ai.AdvancedCombatAI;
import phantom.helpers.FakeHelpers;
import phantom.model.HealingSpell;
import phantom.model.OffensiveSpell;
import phantom.model.SpellUsageCondition;
import phantom.model.SupportSpell;

public class AdventurerAI extends AdvancedCombatAI {
   public AdventurerAI(FakePlayer character) {
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
      this.tryTargetRandomCreatureByTypeInRadius(FakeHelpers.getTestTargetClass(), FakeHelpers.getTestTargetRange());
      this.tryAttackingUsingFighterOffensiveSkill();
      this.setBusyThinking(false);
   }

   @Override
   protected ShotType getShotType() {
      return ShotType.SOULSHOT;
   }

   @Override
   protected List<OffensiveSpell> getOffensiveSpells() {
      List<OffensiveSpell> _offensiveSpells = new ArrayList<>();
      _offensiveSpells.add(new OffensiveSpell(344, 1));
      _offensiveSpells.add(new OffensiveSpell(358, 2));
      _offensiveSpells.add(new OffensiveSpell(263, 3));
      _offensiveSpells.add(new OffensiveSpell(409, 4));
      _offensiveSpells.add(new OffensiveSpell(12, 5));
      _offensiveSpells.add(new OffensiveSpell(11, 6));
      return _offensiveSpells;
   }

   @Override
   protected List<HealingSpell> getHealingSpells() {
      return Collections.emptyList();
   }

   @Override
   protected List<SupportSpell> getSelfSupportSpells() {
      List<SupportSpell> _selfSupportSpells = new ArrayList<>();
      _selfSupportSpells.add(new SupportSpell(445, 1));
      _selfSupportSpells.add(new SupportSpell(111, SpellUsageCondition.LESSHPPERCENT, 75, 1));
      _selfSupportSpells.add(new SupportSpell(2037, SpellUsageCondition.LESSHPPERCENT, 85, 1));
      _selfSupportSpells.add(new SupportSpell(2166, SpellUsageCondition.MISSINGCP, 500, 1));
      _selfSupportSpells.add(new SupportSpell(2005, SpellUsageCondition.MISSINGMP, 2000, 1));
      return _selfSupportSpells;
   }
}
