package phantom.ai;

import java.util.Collections;
import java.util.List;
import com.l2jmega.gameserver.model.ShotType;
import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.helpers.FakeHelpers;
import phantom.model.HealingSpell;
import phantom.model.OffensiveSpell;
import phantom.model.SupportSpell;

public class FallbackAI extends AdvancedCombatAI {
   public FallbackAI(FakePlayer character) {
      super(character);
   }

   @Override
   public void thinkAndAct() {
      super.thinkAndAct();
      if (this.isActiveSiegeParticipant()) {
         return;
      }
      this.setBusyThinking(true);
      this.scheduleDespawnOnce(FakePlayerConfig.DESPAWN_PVP_RANDOM_TIME_1 * 60 * 1000, FakePlayerConfig.DESPAWN_PVP_RANDOM_TIME_2 * 60 * 1000);
      this.handleShots();
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
      return Collections.emptyList();
   }

   @Override
   protected List<HealingSpell> getHealingSpells() {
      return Collections.emptyList();
   }

   @Override
   protected List<SupportSpell> getSelfSupportSpells() {
      return Collections.emptyList();
   }
}
