package phantom.ai.classes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.l2jmega.gameserver.model.ShotType;
import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.ai.AdvancedCombatAI;
import phantom.ai.addon.IConsumableSpender;
import phantom.helpers.FakeHelpers;
import phantom.model.HealingSpell;
import phantom.model.OffensiveSpell;
import phantom.model.SpellUsageCondition;
import phantom.model.SupportSpell;

public class SoultakerAI extends AdvancedCombatAI implements IConsumableSpender {
   @SuppressWarnings("unused")
   private final int boneId = 2508;

   public SoultakerAI(FakePlayer character) {
      super(character);
   }

   @Override
   public void thinkAndAct() {
      this.handleConsumable(this._fakePlayer, 2508);
      super.thinkAndAct();
      if (this._fakePlayer.isInOlympiadMode() || this.isActiveSiegeParticipant()) {
         return;
      }
      this.setBusyThinking(true);
      this.scheduleDespawnOnce(FakePlayerConfig.DESPAWN_PVP_RANDOM_TIME_1 * 60 * 1000, FakePlayerConfig.DESPAWN_PVP_RANDOM_TIME_2 * 60 * 1000);
      this.handleShots();
      this.selfSupportBuffs();
      this.tryTargetRandomCreatureByTypeInRadius(FakeHelpers.getTestTargetClass(), FakeHelpers.getTestTargetRange());
      this.tryAttackingUsingMageOffensiveSkill();
      this.setBusyThinking(false);
   }

   @Override
   protected ShotType getShotType() {
      return ShotType.BLESSED_SPIRITSHOT;
   }

   @Override
   protected List<OffensiveSpell> getOffensiveSpells() {
      List<OffensiveSpell> _offensiveSpells = new ArrayList<>();
      _offensiveSpells.add(new OffensiveSpell(1148, 5)); /* Death Spike */
      _offensiveSpells.add(new OffensiveSpell(1343, 4)); /* Dark Vortex */
      _offensiveSpells.add(new OffensiveSpell(1234, 6)); /* Vampiric Claw */
      _offensiveSpells.add(new OffensiveSpell(1169, 3)); /* Curse Fear */
      _offensiveSpells.add(new OffensiveSpell(1170, 1)); /* Anchor */
      _offensiveSpells.add(new OffensiveSpell(1337, 2));  /* Curse of Abyss */
      return _offensiveSpells;
   }

   @Override
   protected List<HealingSpell> getHealingSpells() {
      return Collections.emptyList();
   }

   @Override
   protected List<SupportSpell> getSelfSupportSpells() {
      List<SupportSpell> _selfSupportSpells = new ArrayList<>();
      _selfSupportSpells.add(new SupportSpell(337, 2));
      _selfSupportSpells.add(new SupportSpell(2037, SpellUsageCondition.LESSHPPERCENT, 85, 1));
      _selfSupportSpells.add(new SupportSpell(2166, SpellUsageCondition.MISSINGCP, 500, 1));
      _selfSupportSpells.add(new SupportSpell(2005, SpellUsageCondition.MISSINGMP, 2000, 1));
      return _selfSupportSpells;
   }
}
