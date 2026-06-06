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

public class EvasTemplarAI extends AdvancedCombatAI {
    public EvasTemplarAI(FakePlayer character) {
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
        return 0.15;
    }

    @Override
    protected ShotType getShotType() {
        return ShotType.SPIRITSHOT;
    }

    @Override
    protected List<OffensiveSpell> getOffensiveSpells() {
        List<OffensiveSpell> _offensiveSpells = new ArrayList<>();
        _offensiveSpells.add(new OffensiveSpell(400, 1));
        _offensiveSpells.add(new OffensiveSpell(352, 2));
        _offensiveSpells.add(new OffensiveSpell(402, 4));

        return _offensiveSpells;
    }

    @Override
    public List<SupportSpell> getSelfSupportSpells() {
        List<SupportSpell> _selfSupportSpells = new ArrayList<>();
        _selfSupportSpells.add(new SupportSpell(2037, SpellUsageCondition.LESSHPPERCENT, 85, 1));
        _selfSupportSpells.add(new SupportSpell(368, SpellUsageCondition.LESSHPPERCENT, 30, 1));
        _selfSupportSpells.add(new SupportSpell(341, SpellUsageCondition.LESSHPPERCENT, 30, 2));
        _selfSupportSpells.add(new SupportSpell(110, SpellUsageCondition.LESSHPPERCENT, 60, 1));
        _selfSupportSpells.add(new SupportSpell(288, 6));
        _selfSupportSpells.add(new SupportSpell(123, 5));
        _selfSupportSpells.add(new SupportSpell(112, 4));
        _selfSupportSpells.add(new SupportSpell(335, 3));
        _selfSupportSpells.add(new SupportSpell(351, 2));
        _selfSupportSpells.add(new SupportSpell(2166, SpellUsageCondition.MISSINGCP, 500, 1));
        _selfSupportSpells.add(new SupportSpell(2005, SpellUsageCondition.MISSINGMP, 2000, 1));
        return _selfSupportSpells;
    }

    @Override
    protected List<HealingSpell> getHealingSpells() {
        return Collections.emptyList();
    }
}
