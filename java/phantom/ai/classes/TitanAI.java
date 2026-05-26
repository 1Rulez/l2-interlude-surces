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

public class TitanAI extends AdvancedCombatAI {
   public TitanAI(FakePlayer character) {
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
      return 0.1;
   }

   @Override
   protected ShotType getShotType() {
      return ShotType.SOULSHOT;
   }

   @Override
   protected List<OffensiveSpell> getOffensiveSpells() {
      List<OffensiveSpell> _offensiveSpells = new ArrayList<>();
      _offensiveSpells.add(new OffensiveSpell(315, 1));
      _offensiveSpells.add(new OffensiveSpell(190, 2));
      _offensiveSpells.add(new OffensiveSpell(362, 3));
      return _offensiveSpells;
   }

   @Override
   public List<SupportSpell> getSelfSupportSpells() {
      List<SupportSpell> _selfSupportSpells = new ArrayList<>();
      _selfSupportSpells.add(new SupportSpell(2037, SpellUsageCondition.LESSHPPERCENT, 85, 1));
      _selfSupportSpells.add(new SupportSpell(139, SpellUsageCondition.LESSHPPERCENT, 30, 1));
      /* _selfSupportSpells.add(new SupportSpell(176, SpellUsageCondition.LESSHPPERCENT, 30, 2)); /* Frenzy */
      _selfSupportSpells.add(new SupportSpell(420, SpellUsageCondition.LESSHPPERCENT, 30, 2)); /* Zealot */
      _selfSupportSpells.add(new SupportSpell(94, 1)); /* Rage */
      _selfSupportSpells.add(new SupportSpell(440, SpellUsageCondition.MISSINGCP, 1000, 3));
      _selfSupportSpells.add(new SupportSpell(121, SpellUsageCondition.LESSHPPERCENT, 25, 1));
      _selfSupportSpells.add(new SupportSpell(2166, SpellUsageCondition.MISSINGCP, 500, 1));
      _selfSupportSpells.add(new SupportSpell(2005, SpellUsageCondition.MISSINGMP, 2000, 1));
      return _selfSupportSpells;
   }

   @Override
   protected List<HealingSpell> getHealingSpells() {
      return Collections.emptyList();
   }
}
