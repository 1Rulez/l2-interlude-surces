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

public class GrandKhavatariAI extends AdvancedCombatAI {
   public GrandKhavatariAI(FakePlayer character) {
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
   protected double changeOfUsingSkill() {
      return 0.5;
   }

   @Override
   protected ShotType getShotType() {
      return ShotType.SOULSHOT;
   }

   @Override
   protected List<OffensiveSpell> getOffensiveSpells() {
      List<OffensiveSpell> _offensiveSpells = new ArrayList<>();
      _offensiveSpells.add(new OffensiveSpell(284, 1));
      _offensiveSpells.add(new OffensiveSpell(281, 2));
      _offensiveSpells.add(new OffensiveSpell(280, 3));
      _offensiveSpells.add(new OffensiveSpell(54, 4));
      _offensiveSpells.add(new OffensiveSpell(346, 5));
      _offensiveSpells.add(new OffensiveSpell(81, 6));
      return _offensiveSpells;
   }

   @Override
   protected List<HealingSpell> getHealingSpells() {
      return Collections.emptyList();
   }

   @Override
   protected List<SupportSpell> getSelfSupportSpells() {
      List<SupportSpell> _selfSupportSpells = new ArrayList<>();
      _selfSupportSpells.add(new SupportSpell(109, 1));
      _selfSupportSpells.add(new SupportSpell(423, 3));
      _selfSupportSpells.add(new SupportSpell(222, 4));
      _selfSupportSpells.add(new SupportSpell(420, SpellUsageCondition.LESSHPPERCENT, 30, 2)); /* Zealot */
      _selfSupportSpells.add(new SupportSpell(443, SpellUsageCondition.LESSHPPERCENT, 29, 5));
      _selfSupportSpells.add(new SupportSpell(2037, SpellUsageCondition.LESSHPPERCENT, 85, 1));
      _selfSupportSpells.add(new SupportSpell(2166, SpellUsageCondition.MISSINGCP, 500, 1));
      _selfSupportSpells.add(new SupportSpell(2005, SpellUsageCondition.MISSINGMP, 2000, 1));
      return _selfSupportSpells;
   }
}
