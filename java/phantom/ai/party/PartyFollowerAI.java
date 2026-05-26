package phantom.ai.party;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.l2jmega.Config;
import com.l2jmega.gameserver.model.actor.ai.CtrlIntention;
import com.l2jmega.gameserver.data.MapRegionTable;

import com.l2jmega.gameserver.data.SkillTable;
import com.l2jmega.gameserver.geoengine.GeoEngine;
import com.l2jmega.gameserver.model.L2Effect;
import com.l2jmega.gameserver.model.L2Skill;
import com.l2jmega.gameserver.model.WorldObject;
import com.l2jmega.gameserver.model.L2Party;
import com.l2jmega.gameserver.model.location.Location;
import com.l2jmega.gameserver.model.ShotType;
import com.l2jmega.gameserver.model.actor.Creature;
import com.l2jmega.gameserver.model.actor.Attackable;
import com.l2jmega.gameserver.model.actor.instance.Monster;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.actor.instance.RaidBoss;
import com.l2jmega.gameserver.model.zone.type.L2TownZone;
import com.l2jmega.gameserver.model.zone.ZoneId;
import com.l2jmega.commons.random.Rnd;
import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.ai.CombatAI;
import phantom.helpers.FakeResurrectionSupport;
import phantom.ai.addon.IConsumableSpender;
import phantom.ai.FakePlayerUtilsAI;
import phantom.helpers.FakeHelpers;
import phantom.model.HealingSpell;
import phantom.model.OffensiveSpell;
import phantom.model.SupportSpell;

public class PartyFollowerAI extends CombatAI implements IConsumableSpender
{
	@SuppressWarnings("unused")
	private static final int FOLLOW_MIN_DISTANCE = 150;
	private static final int FOLLOW_MAX_DISTANCE = 300;
	private static final int TELEPORT_DISTANCE = 2000;
	private static final int Z_STUCK_THRESHOLD = 150;
	private static final int PARTY_RESPAWN_RANGE_FROM_LEADER = 250;
	private static final int DEFEND_SCAN_RADIUS = 900;
	private static final long LEADER_MISSING_CONTINUE_MS = 5 * 60 * 1000L;
	private static final int PARTY_STUCK_TP_SPREAD_MIN = 24;
	private static final int PARTY_STUCK_TP_SPREAD_MAX = 52;
	/** В открытом мире: 30 с без движения вдали от ПЛ → ТП к лидеру (не в городе / peace / босс-зоне). */
	private static final long PARTY_STUCK_RECOVERY_MS = 30000L;
	private static final int PARTY_STUCK_POSITION_EPSILON = 48;
	/** Same radius as {@link phantom.ai.FakePlayerAI} tournament search — 5x5/2x2 spread on arena. */
	private static final int ARENA_TARGET_RADIUS = 3000;

	private final int _formationOffsetX;
	private final int _formationOffsetY;
	private long _leaderMissingSince = 0L;
	private long _partyDeathRecoveryUntil = 0L;
	private Location _partyDeathReturnLocation = null;
	private int _resRequestIteration = 1;
	private boolean _acceptResThisDeath = false;
	private boolean _wasDeadLastTick = false;
	private boolean _fakeResRequested = false;
	private boolean _holdAutoFarmUntilLeaderInitiates = false;
	private long _postReviveIdleUntil = 0L;
	private boolean _pendingMajorGroupHealAfterBalanceLife = false;
	private volatile Player _celestialShieldTarget = null;
	private int _manaBurnBossId = 0;
	private boolean _waitingForBossManaRegen = false;

	private long _partyStuckSinceMs = 0L;
	private int _partyStuckX = 0;
	private int _partyStuckY = 0;
	private int _partyStuckZ = 0;

	private List<OffensiveSpell> _offensiveSpells = java.util.Collections.emptyList();
	private List<HealingSpell> _healingSpells = java.util.Collections.emptyList();
	private List<SupportSpell> _selfSupportSpells = java.util.Collections.emptyList();

	public PartyFollowerAI(FakePlayer character)
	{
		super(character);
		_formationOffsetX = Rnd.get(-80, 80);
		_formationOffsetY = Rnd.get(-80, 80);
		rollNextResRequestIteration();
		initClassSpells();
	}

	@SuppressWarnings("unchecked")
	private void initClassSpells()
	{
		try
		{
			Class<? extends phantom.ai.FakePlayerAI> aiClass = FakeHelpers.getAIbyClassId(_fakePlayer.getClassId());
			if (aiClass != null && CombatAI.class.isAssignableFrom(aiClass))
			{
				CombatAI tempAI = (CombatAI) aiClass.getConstructor(FakePlayer.class).newInstance(_fakePlayer);

				java.lang.reflect.Method mOff = findMethod(aiClass, "getOffensiveSpells");
				java.lang.reflect.Method mHeal = findMethod(aiClass, "getHealingSpells");
				java.lang.reflect.Method mSup = findMethod(aiClass, "getSelfSupportSpells");

				if (mOff != null)
				{
					mOff.setAccessible(true);
					_offensiveSpells = (List<OffensiveSpell>) mOff.invoke(tempAI);
				}
				if (mHeal != null)
				{
					mHeal.setAccessible(true);
					_healingSpells = (List<HealingSpell>) mHeal.invoke(tempAI);
				}
				if (mSup != null)
				{
					mSup.setAccessible(true);
					_selfSupportSpells = (List<SupportSpell>) mSup.invoke(tempAI);
				}
			}
		}
		catch (Exception ignored) {}

		// Ensure healer fakes have all required skills regardless of class tree.
		if (isHealerClass())
			ensureHealerSkills();

		// Grant Blessed Resurrection Scrolls to all party fakes so they can res each other.
		FakeHelpers.grantBlessedResurrectionScrolls(_fakePlayer);
	}

	/**
	 * Grants all required healer skills at max level if the fake doesn't have them.
	 * Needed because non-Cardinal healers (Hierophant, Doomcryer, etc.) don't learn
	 * Cardinal skills through rewardSkills().
	 */
	private void ensureHealerSkills()
	{
		int[] requiredSkills = {
			1401, // Major Heal
			1402, // Major Group Heal
			1335, // Balance Life
			1409, // Cleanse
			1418, // Celestial Shield
			1398, // Mana Burn
			1016  // Resurrection
		};

		for (int skillId : requiredSkills)
		{
			if (_fakePlayer.getSkill(skillId) == null)
			{
				int maxLvl = com.l2jmega.gameserver.data.SkillTable.getInstance().getMaxLevel(skillId);
				L2Skill sk = com.l2jmega.gameserver.data.SkillTable.getInstance().getInfo(skillId, maxLvl);
				if (sk != null)
					_fakePlayer.addSkill(sk, false);
			}
		}
	}

	/** Ниже этого числа положительных эффектов считаем, что хилер «без бафов» и кастуем свои. */
	private static final int HEALER_SELF_BUFF_MIN_ACTIVE = 6;

	/** Порядок: без расходников → обычные бафы → Prophecy (нужны кристаллы души). */
	private static final int[] HEALER_SELF_BUFF_SKILL_IDS = {
		1047, // Mana Regeneration (TARGET_SELF)
		1304, // Advanced Block
		1243, // Bless Shield
		1045, // Blessed Body
		1048, // Blessed Soul
		1355, // Prophecy of Water
		1356, // Prophecy of Fire
		1357  // Prophecy of Wind
	};

	private int countHealerPositiveEffects()
	{
		int n = 0;
		for (L2Effect e : _fakePlayer.getAllEffects())
		{
			if (e != null && e.getSkill() != null && !e.getSkill().isDebuff())
				n++;
		}
		return n;
	}

	/**
	 * Селф-бафы епископа: TARGET_ONE иначе требуют корректную цель; в бою цель часто моб — скиллы не проходили.
	 *
	 * @return {@code true}, если был применён хотя бы один селф-баф; иначе {@code false}
	 */
	private boolean tryHealerSelfCombatBuffs()
	{
		if (!isHealerClass())
			return false;
		if (_fakePlayer.isCastingNow())
			return false;

		if (countHealerPositiveEffects() >= HEALER_SELF_BUFF_MIN_ACTIVE)
			return false;

		final List<Integer> activeIds = Arrays.stream(_fakePlayer.getAllEffects())
			.filter(e -> e != null && e.getSkill() != null)
			.map(e -> e.getSkill().getId())
			.collect(Collectors.toList());

		final WorldObject savedTarget = _fakePlayer.getTarget();
		try
		{
			_fakePlayer.setTarget(_fakePlayer);
			for (int skillId : HEALER_SELF_BUFF_SKILL_IDS)
			{
				if (activeIds.contains(skillId))
					continue;
				final L2Skill skill = _fakePlayer.getSkill(skillId);
				if (skill == null)
					continue;
				if (!_fakePlayer.checkUseMagicConditions(skill, true, false))
					continue;
				castSelfSpell(skill);
				return true;
			}
		}
		finally
		{
			_fakePlayer.setTarget(savedTarget);
		}
		return false;
	}

	@Override
	protected void selfSupportBuffs()
	{
		if (tryHealerSelfCombatBuffs())
			return;
		super.selfSupportBuffs();
	}

	private static java.lang.reflect.Method findMethod(Class<?> clazz, String name)
	{
		while (clazz != null)
		{
			try
			{
				return clazz.getDeclaredMethod(name);
			}
			catch (NoSuchMethodException e)
			{
				clazz = clazz.getSuperclass();
			}
		}
		return null;
	}

	@Override
	public void thinkAndAct()
	{
		handleDeath();

		if (Rnd.get(1, 100) < 5)
			return;

		if (_fakePlayer.isDead())
			return;

		if (handlePartyDeathRecovery())
			return;

		// Post-resurrection idle: stay still at res location.
		if (_postReviveIdleUntil > 0L)
		{
			if (System.currentTimeMillis() < _postReviveIdleUntil)
			{
				_fakePlayer.getAI().setIntention(CtrlIntention.ACTIVE);
				return;
			}
			_postReviveIdleUntil = 0L;
		}

		// Tournament arenas call leaveParty() before the fight — no party leader. Without this branch,
		// fakes only wander in handleLeaderMissingBehavior() and never acquire PvP targets (no karma/PvP flag).
		if (_fakePlayer.isInArenaEvent() && _fakePlayer.isArenaAttack())
		{
			resetPartyStuckTracking();
			handleShots();
			selfSupportBuffs();

			if (!isInSeriousActivity())
			{
				if (Rnd.get(1, 1000000) <= FakePlayerConfig.FAKE_CHANCE_TO_TALK_SOCIAL)
					FakePlayerUtilsAI.maybeAnnounce(this._fakePlayer);
				FakePlayerUtilsAI.maybeAnnounceNormalChat(this._fakePlayer);
			}

			tryTargetRandomCreatureByTypeInRadius(Player.class, ARENA_TARGET_RADIUS);
			if (_fakePlayer.getTarget() instanceof Creature)
			{
				Creature t = (Creature) _fakePlayer.getTarget();
				if (!t.isDead() && isValidCombatTarget(t))
					engageCombat(t);
			}
			this.setBusyThinking(false);
			return;
		}

		Player leader = getPartyLeader();
		if (leader == null || leader.isDead())
		{
			resetPartyStuckTracking();
			handleLeaderMissingBehavior();
			return;
		}
		_leaderMissingSince = 0L;
		
		if (_holdAutoFarmUntilLeaderInitiates && hasLeaderInitiatedCombat(leader))
			_holdAutoFarmUntilLeaderInitiates = false;

		if (tryRecoverPartyStuck(leader))
		{
			this.setBusyThinking(false);
			return;
		}

		if (_fakePlayer.getDistanceSq(leader) > TELEPORT_DISTANCE * TELEPORT_DISTANCE)
		{
			if (tryTeleportToLeaderWhenRecall(leader))
			{
				this.setBusyThinking(false);
				return;
			}
			moveToLeader(leader);
			return;
		}

		// Разница по Z / нет LOS — только бежать к лидеру (в городе и на боссе тоже без ТП).
		final int zDiff = leader.getZ() - _fakePlayer.getZ();
		if (zDiff > Z_STUCK_THRESHOLD && !GeoEngine.getInstance().canSeeTarget(_fakePlayer, leader))
		{
			moveToLeader(leader);
			return;
		}

		handleShots();
		selfSupportBuffs();

		if (!isInSeriousActivity())
		{
			if (Rnd.get(1, 1000000) <= FakePlayerConfig.FAKE_CHANCE_TO_TALK_SOCIAL)
				FakePlayerUtilsAI.maybeAnnounce(this._fakePlayer);
			FakePlayerUtilsAI.maybeAnnounceNormalChat(this._fakePlayer);
		}

		// Celestial Shield on request — HIGHEST priority, overrides everything.
		if (tryCastCelestialShield())
			return;

		// Healer priority: heal party members before engaging combat.
		if (tryHealPartyMember())
			return;

		// Cleanse debuffs from party members.
		if (tryCleansPartyMember())
			return;

		if (tryHandlePartyCombatPriority(leader))
			return;

		PartyMode mode = _fakePlayer.getPartyMode();
		switch (mode)
		{
			case FOLLOW:
				doFollow(leader);
				break;
			case ASSIST:
				doAssist(leader);
				break;
			case DEFEND:
				doDefend(leader);
				break;
			case FARM:
				doFarm(leader);
				break;
			case STAND:
				doStand();
				break;
		}
		this.setBusyThinking(false);
	}

	private boolean tryHandlePartyCombatPriority(Player leader)
	{
		Creature leaderTarget = findLeaderCombatTarget(leader);
		if (leaderTarget != null)
		{
			standUpForCombatIfNeeded(leaderTarget);
			_fakePlayer.setTarget(leaderTarget);
			engageCombat(leaderTarget);
			return true;
		}

		Creature attacker = findAttackerOnParty();
		if (attacker != null)
		{
			standUpForCombatIfNeeded(attacker);
			_fakePlayer.setTarget(attacker);
			engageCombat(attacker);
			return true;
		}

		Creature directAttacker = findDirectAttacker();
		if (directAttacker != null)
		{
			standUpForCombatIfNeeded(directAttacker);
			_fakePlayer.setTarget(directAttacker);
			engageCombat(directAttacker);
			return true;
		}

		return false;
	}

	private Creature findLeaderCombatTarget(Player leader)
	{
		if (leader == null || leader.isDead())
			return null;

		final Creature initiatedTarget = getLeaderInitiatedCombatTarget(leader);
		if (initiatedTarget != null)
			return initiatedTarget;

		return findAttackerTargetingMember(leader);
	}

	private Creature getLeaderInitiatedCombatTarget(Player leader)
	{
		if (leader == null || leader.isDead() || !hasLeaderInitiatedCombat(leader))
			return null;

		final WorldObject leaderTarget = leader.getTarget();
		if (leaderTarget instanceof Creature)
		{
			final Creature target = (Creature) leaderTarget;
			if (!target.isDead() && isValidCombatTarget(target))
				return target;
		}

		return null;
	}

	private boolean hasLeaderInitiatedCombat(Player leader)
	{
		if (leader == null || leader.isDead() || !(leader.getTarget() instanceof Creature))
			return false;

		final Creature target = (Creature) leader.getTarget();
		if (!isValidCombatTarget(target))
			return false;

		final CtrlIntention intention = leader.getAI().getIntention();
		return leader.isAttackingNow()
			|| (intention == CtrlIntention.ATTACK)
			|| (leader.isCastingNow() && intention == CtrlIntention.CAST)
			|| (leader.isInCombat() && target.getTarget() == leader);
	}

	private void doFollow(Player leader)
	{
		_fakePlayer.setTarget(null);
		if (!isCloseToLeader(leader))
			moveToLeader(leader);
	}

	private void doAssist(Player leader)
	{
		Creature target = getLeaderInitiatedCombatTarget(leader);
		if (target != null)
		{
			_fakePlayer.setTarget(target);
			engageCombat(target);
			return;
		}

		_fakePlayer.setTarget(null);
		if (!isCloseToLeader(leader))
			moveToLeader(leader);
	}

	private void doDefend(Player leader)
	{
		Creature attacker = findAttackerOnParty();
		if (attacker != null && !attacker.isDead())
		{
			_fakePlayer.setTarget(attacker);
			engageCombat(attacker);
		}
		else
		{
			_fakePlayer.setTarget(null);
			if (!isCloseToLeader(leader))
				moveToLeader(leader);
		}
	}

	private void doFarm(Player leader)
	{
		int farmRadius = FakePlayerConfig.FAKE_PARTY_FARM_RADIUS;
		if (_fakePlayer.getDistanceSq(leader) > farmRadius * farmRadius * 2.25)
		{
			_fakePlayer.setTarget(null);
			moveToLeader(leader);
			return;
		}

		// Prioritize leader target first.
		Creature leaderTarget = getLeaderInitiatedCombatTarget(leader);
		if (leaderTarget != null)
		{
			_fakePlayer.setTarget(leaderTarget);
			engageCombat(leaderTarget);
			return;
		}

		// If something attacks us, respond immediately.
		Creature attacker = findDirectAttacker();
		if (attacker != null)
		{
			_fakePlayer.setTarget(attacker);
			engageCombat(attacker);
			return;
		}

		if (_fakePlayer.getTarget() != null && _fakePlayer.getTarget() instanceof Creature)
		{
			Creature currentTarget = (Creature) _fakePlayer.getTarget();
			if (!currentTarget.isDead() && isValidCombatTarget(currentTarget))
			{
				engageCombat(currentTarget);
				return;
			}
			_fakePlayer.setTarget(null);
		}

		if (_holdAutoFarmUntilLeaderInitiates && leader != null && !leader.isDead())
		{
			if (!isCloseToLeader(leader))
				moveToLeader(leader);
			return;
		}

		Set<Integer> takenByOtherFakes = new HashSet<>();
		for (FakePlayer other : _fakePlayer.getKnownTypeInRadius(FakePlayer.class, farmRadius))
		{
			if (other != _fakePlayer && other.getPartyMode() != PartyMode.ASSIST
				&& other.getTarget() != null && other.getTarget() instanceof Creature)
				takenByOtherFakes.add(other.getTarget().getObjectId());
		}
		List<Monster> monsters = _fakePlayer
			.getKnownTypeInRadius(Monster.class, farmRadius)
			.stream()
			.filter(m -> !m.isDead() && !m.isInsideZone(ZoneId.PEACE)
				&& GeoEngine.getInstance().canSeeTarget(_fakePlayer, m)
				&& !takenByOtherFakes.contains(m.getObjectId()))
			.sorted(Comparator.comparingDouble(m -> _fakePlayer.getDistanceSq(m)))
			.collect(Collectors.toList());

		if (!monsters.isEmpty())
		{
			Monster target = monsters.get(0);
			_fakePlayer.setTarget(target);
			engageCombat(target);
		}
		else if (!isCloseToLeader(leader))
		{
			moveToLeader(leader);
		}
	}

	private void doStand()
	{
		Creature attacker = findDirectAttacker();
		if (attacker != null && !attacker.isDead())
		{
			_fakePlayer.setTarget(attacker);
			engageCombat(attacker);
		}
	}

	/**
	 * Returns true if the fake is in a boss zone, raid zone, siege, olympiad,
	 * arena event, or actively in combat — situations where random chat/emotes
	 * should be suppressed so the fake stays focused.
	 * @return true if the fake should suppress non-combat behavior
	 */
	private boolean isInSeriousActivity()
	{
		return _fakePlayer.isInsideZone(ZoneId.BOSS)
			|| _fakePlayer.isInsideZone(ZoneId.RAID)
			|| _fakePlayer.isInsideZone(ZoneId.RAID_NO_FLAG)
			|| _fakePlayer.isInsideZone(ZoneId.SIEGE)
			|| _fakePlayer.isInsideZone(ZoneId.OLYMPIAD)
			|| _fakePlayer.isInArenaEvent()
			|| _fakePlayer.isInCombat();
	}

	private void handleLeaderMissingBehavior()
	{
		final long now = System.currentTimeMillis();
		if (_leaderMissingSince == 0L)
			_leaderMissingSince = now;

		// Keep fighting/farming for 5 minutes to look human-like.
		if (now - _leaderMissingSince < LEADER_MISSING_CONTINUE_MS)
		{
			engageWithoutLeader();
			return;
		}

		// If leader never came back, do "unstuck" behavior to town.
		final Location loc = MapRegionTable.getInstance().getLocationToTeleport(_fakePlayer, MapRegionTable.TeleportType.TOWN);
		teleportToLocation(loc.getX(), loc.getY(), loc.getZ(), 40);
		_fakePlayer.setPartyMode(PartyMode.FOLLOW);
		_leaderMissingSince = 0L;
	}

	private boolean handlePartyDeathRecovery()
	{
		if (_partyDeathRecoveryUntil <= 0L)
			return false;

		final long now = System.currentTimeMillis();
		if (now < _partyDeathRecoveryUntil)
		{
			_fakePlayer.setTarget(null);
			_fakePlayer.abortAttack();
			_fakePlayer.abortCast();
			_fakePlayer.stopMove(null);
			_fakePlayer.getAI().setIntention(CtrlIntention.ACTIVE);
			return true;
		}

		final Location returnLocation = resolvePartyRecoveryReturnLocation(_partyDeathReturnLocation);
		_partyDeathRecoveryUntil = 0L;
		_partyDeathReturnLocation = null;
		_leaderMissingSince = 0L;
		_fakePlayer.setPartyMode(PartyMode.FOLLOW);

		if (returnLocation != null)
		{
			teleportToLocation(returnLocation.getX(), returnLocation.getY(), returnLocation.getZ(), 0);
			_fakePlayer.heal();
			applyCBBuffs();
			onLeaderRelocated();
			return true;
		}

		return false;
	}

	private void startPartyDeathRecovery()
	{
		_partyDeathReturnLocation = resolvePartyRespawnLocation();
		_partyDeathRecoveryUntil = System.currentTimeMillis() + 15000L;

		final Location town = MapRegionTable.getInstance().getLocationToTeleport(_fakePlayer, MapRegionTable.TeleportType.TOWN);
		if (_fakePlayer.isDead())
			_fakePlayer.doRevive();

		teleportToLocation(town.getX(), town.getY(), town.getZ(), 10);
		_fakePlayer.heal();
		applyCBBuffs();
		_fakePlayer.setTarget(null);
		_fakePlayer.abortAttack();
		_fakePlayer.abortCast();
		_fakePlayer.stopMove(null);
		_fakePlayer.getAI().setIntention(CtrlIntention.ACTIVE);
	}

	private Location resolvePartyRespawnLocation()
	{
		final Player leader = getPartyLeader();
		if (leader != null && !leader.isDead())
		{
			return new Location(
				leader.getX() + Rnd.get(-PARTY_RESPAWN_RANGE_FROM_LEADER, PARTY_RESPAWN_RANGE_FROM_LEADER),
				leader.getY() + Rnd.get(-PARTY_RESPAWN_RANGE_FROM_LEADER, PARTY_RESPAWN_RANGE_FROM_LEADER),
				leader.getZ());
		}

		final int returnX = _fakePlayer.getLastX();
		final int returnY = _fakePlayer.getLastY();
		final int returnZ = _fakePlayer.getLastZ();
		return new Location(returnX, returnY, returnZ);
	}

	private Location resolvePartyRecoveryReturnLocation(Location fallbackLocation)
	{
		final Player leader = getPartyLeader();
		if (leader != null && !leader.isDead())
		{
			final L2TownZone town = MapRegionTable.getTown(leader.getX(), leader.getY(), leader.getZ());
			if (town != null
				|| leader.isInsideZone(ZoneId.TOWN)
				|| leader.isInsideZone(ZoneId.PEACE)
				|| leader.isInsideZone(ZoneId.RAID)
				|| leader.isInsideZone(ZoneId.RAID_NO_FLAG)
				|| leader.isInsideZone(ZoneId.BOSS))
				return new Location(leader.getX(), leader.getY(), leader.getZ());

			return new Location(
				leader.getX() + Rnd.get(-PARTY_RESPAWN_RANGE_FROM_LEADER, PARTY_RESPAWN_RANGE_FROM_LEADER),
				leader.getY() + Rnd.get(-PARTY_RESPAWN_RANGE_FROM_LEADER, PARTY_RESPAWN_RANGE_FROM_LEADER),
				leader.getZ());
		}

		return fallbackLocation;
	}

	private void engageWithoutLeader()
	{
		if (_fakePlayer.getTarget() instanceof Creature)
		{
			Creature currentTarget = (Creature) _fakePlayer.getTarget();
			if (!currentTarget.isDead() && isValidCombatTarget(currentTarget))
			{
				engageCombat(currentTarget);
				return;
			}
			_fakePlayer.setTarget(null);
		}

		Creature attacker = findDirectAttacker();
		if (attacker != null)
		{
			_fakePlayer.setTarget(attacker);
			engageCombat(attacker);
			return;
		}

		List<Monster> monsters = _fakePlayer
			.getKnownTypeInRadius(Monster.class, FakePlayerConfig.FAKE_PARTY_FARM_RADIUS)
			.stream()
			.filter(m -> !m.isDead() && !m.isInsideZone(ZoneId.PEACE) && GeoEngine.getInstance().canSeeTarget(_fakePlayer, m))
			.sorted(Comparator.comparingDouble(m -> _fakePlayer.getDistanceSq(m)))
			.collect(Collectors.toList());
		if (!monsters.isEmpty())
		{
			Monster m = monsters.get(0);
			_fakePlayer.setTarget(m);
			engageCombat(m);
			return;
		}

		_fakePlayer.getAI().setIntention(CtrlIntention.ACTIVE);
	}

	private void engageCombat(Creature target)
	{
		if (target == null)
			return;

		standUpForCombatIfNeeded(target);

		// Stop if too far from leader.
		Player leader = getPartyLeader();
		if (leader != null && _fakePlayer.getDistanceSq(leader) > TELEPORT_DISTANCE * TELEPORT_DISTANCE)
		{
			_fakePlayer.setTarget(null);
			if (tryTeleportToLeaderWhenRecall(leader))
				return;
			moveToLeader(leader);
			return;
		}

		// Never keep combat in peace zone.
		if (target.isInsideZone(ZoneId.PEACE))
		{
			_fakePlayer.setTarget(null);
			_fakePlayer.getAI().setIntention(CtrlIntention.ACTIVE);
			return;
		}

		// Healer: check if party members need healing even during combat.
		if (tryCastCelestialShield())
			return;

		if (tryHealPartyMember())
			return;

		// Healers: use Mana Burn on RaidBoss only when nobody needs healing.
		if (isHealerClass() && target instanceof RaidBoss)
		{
			tryManaBurnOnBoss(target);
			return;
		}

		// Keep melee raid followers glued to the boss even when a skill attempt fails or delays.
		if (!isMageClass() && target instanceof RaidBoss)
		{
			_fakePlayer.setTarget(target);
			_fakePlayer.forceAutoAttack(target);
			tryAttackingUsingFighterOffensiveSkill();
			return;
		}

		if (isMageClass())
			tryAttackingUsingMageOffensiveSkill();
		else
			tryAttackingUsingFighterOffensiveSkill();
	}

	private void standUpForCombatIfNeeded(Creature target)
	{
		if (!_fakePlayer.isSitting())
			return;

		if (target instanceof RaidBoss || _fakePlayer.isInCombat() || findDirectAttacker() != null)
			_fakePlayer.standUp();
	}

	/**
	 * Scans party members for low HP and casts the best available healing spell.
	 * Returns true if healing was attempted (caller should skip combat).
	 */
	private static final int MASS_HEAL_LOW_HP_THRESHOLD = 50;
	private static final int MASS_HEAL_MIN_TARGETS = 2;
	private static final double SINGLE_HEAL_TRIGGER_HP_PERCENT = 92.0;
	private static final int BALANCE_LIFE_SKILL_ID = 1335;
	private static final int MAJOR_GROUP_HEAL_SKILL_ID = 1402;

	private boolean tryHealPartyMember()
	{
		if (_healingSpells.isEmpty())
			return false;

		L2Party party = _fakePlayer.getParty();
		if (party == null)
			return false;

		// Scan party: find lowest HP member and count members with HP <50%.
		Player bestTarget = null;
		double bestHpPercent = Double.MAX_VALUE;
		int lowHpCount = 0;


		for (Player member : party.getPartyMembers())
		{
			if (member == null || member.isDead())
				continue;

			double hpPercent = 100.0 * member.getCurrentHp() / member.getMaxHp();
			if (hpPercent < MASS_HEAL_LOW_HP_THRESHOLD)
				lowHpCount++;
			if (hpPercent < bestHpPercent)
			{
				bestHpPercent = hpPercent;
				bestTarget = member;
			}
		}

		if (bestTarget == null || bestHpPercent >= 100.0)
			return false;

		// Do not interrupt boss assist for tiny chip damage on party members.
		if (bestHpPercent >= SINGLE_HEAL_TRIGGER_HP_PERCENT && lowHpCount == 0)
			return false;

		if (_fakePlayer.isCastingNow())
			return true;

		if (_pendingMajorGroupHealAfterBalanceLife)
		{
			L2Skill majorGroupHeal = _fakePlayer.getSkill(MAJOR_GROUP_HEAL_SKILL_ID);
			if (majorGroupHeal != null)
			{
				_fakePlayer.setTarget(_fakePlayer);
				if (_fakePlayer.checkUseMagicConditions(majorGroupHeal, true, false))
				{
					_pendingMajorGroupHealAfterBalanceLife = false;
					castSpell(majorGroupHeal);
					return true;
				}
			}
			_pendingMajorGroupHealAfterBalanceLife = false;
		}

		// Sort spells by priority.
		List<HealingSpell> sorted = _healingSpells.stream()
			.sorted((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
			.collect(Collectors.toList());

		// Always use Balance Life if any party member HP < 40% (even если только один)
		for (HealingSpell spell : sorted)
		{
			if (spell.getSkillId() == BALANCE_LIFE_SKILL_ID && bestHpPercent <= 40.0)
			{
				L2Skill healSkill = _fakePlayer.getSkill(spell.getSkillId());
				if (healSkill != null && _fakePlayer.checkUseMagicConditions(healSkill, true, false))
				{
					_fakePlayer.setTarget(_fakePlayer);
					_pendingMajorGroupHealAfterBalanceLife = true;
					castSpell(healSkill);
					return true;
				}
			}
		}

		// When 2+ party members are below 50% HP, prefer mass heal (TARGET_PARTY).
		if (lowHpCount >= MASS_HEAL_MIN_TARGETS)
		{
			for (HealingSpell spell : sorted)
			{
				if (spell.getTargetType() != L2Skill.SkillTargetType.TARGET_PARTY)
					continue;

				if (spell.getCondition() == phantom.model.SpellUsageCondition.LESSHPPERCENT
					&& bestHpPercent > spell.getConditionValue())
					continue;

				L2Skill healSkill = _fakePlayer.getSkill(spell.getSkillId());
				if (healSkill == null)
					continue;

				_fakePlayer.setTarget(_fakePlayer);
				if (!_fakePlayer.checkUseMagicConditions(healSkill, true, false))
					continue;

				castSpell(healSkill);
				return true;
			}
		}

		// Collect all eligible single-target heals, then pick one randomly
		// weighted by priority (lower priority = more likely).
		List<HealingSpell> eligible = new ArrayList<>();
		for (HealingSpell spell : sorted)
		{
			if (spell.getCondition() == phantom.model.SpellUsageCondition.LESSHPPERCENT
				&& bestHpPercent > spell.getConditionValue())
				continue;

			L2Skill healSkill = _fakePlayer.getSkill(spell.getSkillId());
			if (healSkill == null)
				continue;

			if (healSkill.getTargetType() == L2Skill.SkillTargetType.TARGET_PARTY)
				_fakePlayer.setTarget(_fakePlayer);
			else
				_fakePlayer.setTarget(bestTarget);

			if (!_fakePlayer.checkUseMagicConditions(healSkill, true, false))
				continue;

			eligible.add(spell);
		}

		if (!eligible.isEmpty())
		{
			// Pick from eligible: 60% chance best priority, 40% chance random other.
			HealingSpell chosen;
			if (eligible.size() == 1 || Rnd.get(100) < 60)
				chosen = eligible.get(0);
			else
				chosen = eligible.get(Rnd.get(1, eligible.size() - 1));

			L2Skill healSkill = _fakePlayer.getSkill(chosen.getSkillId());
			if (healSkill.getTargetType() == L2Skill.SkillTargetType.TARGET_PARTY)
				_fakePlayer.setTarget(_fakePlayer);
			else
				_fakePlayer.setTarget(bestTarget);

			castSpell(healSkill);
			return true;
		}

		return false;
	}

	/**
	 * Scans party members for debuffs and casts Cleanse on the first one found.
	 */
	private static final int CLEANSE_SKILL_ID = 1409;

	// Hot Springs diseases — beneficial debuffs, do not cleanse
	private static final java.util.Set<Integer> IGNORED_DEBUFF_IDS = java.util.Set.of(4551, 4552, 4553, 4554);

	private boolean tryCleansPartyMember()
	{
		L2Skill cleanseSkill = _fakePlayer.getSkill(CLEANSE_SKILL_ID);
		if (cleanseSkill == null)
			return false;

		if (_fakePlayer.isCastingNow())
			return false;

		L2Party party = _fakePlayer.getParty();
		if (party == null)
			return false;

		for (Player member : party.getPartyMembers())
		{
			if (member == null || member.isDead())
				continue;

			boolean hasDebuff = false;
			for (com.l2jmega.gameserver.model.L2Effect effect : member.getAllEffects())
			{
				if (effect != null && effect.getSkill().isDebuff() && !IGNORED_DEBUFF_IDS.contains(effect.getSkill().getId()))
				{
					hasDebuff = true;
					break;
				}
			}

			if (!hasDebuff)
				continue;

			_fakePlayer.setTarget(member);
			if (_fakePlayer.checkUseMagicConditions(cleanseSkill, true, false))
			{
				castSpell(cleanseSkill);
				return true;
			}
		}

		return false;
	}

	private static final int CELESTIAL_SHIELD_SKILL_ID = 1418;

	/**
	 * Called from ChatParty when a party member writes "cs" (case-insensitive).
	 * Stores the requester so the healer casts Celestial Shield on the next tick.
	 * @param requester the player who requested Celestial Shield
	 */
	public void onCelestialShieldRequest(Player requester)
	{
		if (requester == null || requester.isDead())
			return;

		L2Skill skill = _fakePlayer.getSkill(CELESTIAL_SHIELD_SKILL_ID);
		if (skill == null)
			return;

		_celestialShieldTarget = requester;
	}

	private boolean tryCastCelestialShield()
	{
		Player target = _celestialShieldTarget;
		if (target == null)
			return false;

		if (target.isDead())
		{
			_celestialShieldTarget = null;
			return false;
		}

		// Don't clear the request while casting — retry next tick.
		if (_fakePlayer.isCastingNow())
			return true;

		L2Skill skill = _fakePlayer.getSkill(CELESTIAL_SHIELD_SKILL_ID);
		if (skill == null)
		{
			_celestialShieldTarget = null;
			return false;
		}

		_fakePlayer.setTarget(target);
		if (!_fakePlayer.checkUseMagicConditions(skill, true, false))
		{
			// Keep retrying — skill may be on cooldown.
			return true;
		}

		_celestialShieldTarget = null;
		castSpell(skill);
		return true;
	}

	private void rollNextResRequestIteration()
	{
		int min = Math.max(1, FakePlayerConfig.FAKE_PARTY_RES_REQUEST_MIN_ITERATION);
		int max = Math.max(min, FakePlayerConfig.FAKE_PARTY_RES_REQUEST_MAX_ITERATION);
		_resRequestIteration = Rnd.get(min, max);
	}

	private static String getRandomResRequestMessage()
	{
		return "res";
	}

	private boolean shouldSuppressResurrectionChat()
	{
		return _fakePlayer._inEventTvT
			|| _fakePlayer._inEventCTF
			|| _fakePlayer.isInArenaEvent()
			|| _fakePlayer.isArenaAttack();
	}

	private int getResTimeoutIterationsByContext()
	{
		int timeout = Math.max(3, FakePlayerConfig.FAKE_PARTY_RES_TIMEOUT_BASE_ITERATIONS);
		if (_fakePlayer.isInsideZone(ZoneId.PVP) || _fakePlayer.isInsideZone(ZoneId.PVP_CUSTOM) || _fakePlayer.isInsideZone(ZoneId.SIEGE))
			timeout += Math.max(0, FakePlayerConfig.FAKE_PARTY_RES_TIMEOUT_EXTRA_PVP_ITERATIONS);
		if (_fakePlayer.isFakeFarm())
			timeout += Math.max(0, FakePlayerConfig.FAKE_PARTY_RES_TIMEOUT_EXTRA_FARM_ITERATIONS);

		boolean nearRaid = !_fakePlayer.getKnownTypeInRadius(RaidBoss.class, 1400).isEmpty();
		if (nearRaid)
			timeout += Math.max(0, FakePlayerConfig.FAKE_PARTY_RES_TIMEOUT_EXTRA_RAID_ITERATIONS);
		return timeout;
	}

	public boolean shouldAutoAcceptResurrection()
	{
		return _fakePlayer.isDead() && _partyDeathRecoveryUntil <= 0L && _acceptResThisDeath;
	}

	private boolean isCloseToLeader(Player leader)
	{
		return _fakePlayer.getDistanceSq(leader) <= FOLLOW_MAX_DISTANCE * FOLLOW_MAX_DISTANCE;
	}

	private void moveToLeader(Player leader)
	{
		if (_fakePlayer.isInsideZone(ZoneId.TOWN) || _fakePlayer.isInsideZone(ZoneId.PEACE) || _fakePlayer.isInsideZone(ZoneId.BOSS))
			_fakePlayer.setIsRunning(true);
		else
			_fakePlayer.setIsRunning(leader.isRunning());

		int targetX = leader.getX() + _formationOffsetX;
		int targetY = leader.getY() + _formationOffsetY;
		moveTo(targetX, targetY, leader.getZ());
	}

	/**
	 * Анти-стак ТП к ПЛ выключен в городе, peace и босс-зоне — только бег к лидеру.
	 * @return true если ТП по таймеру застревания применять нельзя
	 */
	private boolean isPartyStuckTeleportForbiddenZone()
	{
		return _fakePlayer.isInsideZone(ZoneId.TOWN)
			|| _fakePlayer.isInsideZone(ZoneId.PEACE)
			|| _fakePlayer.isInsideZone(ZoneId.BOSS);
	}

	private static boolean isInTownOrPeaceArea(Player player)
	{
		if (player == null)
			return false;

		return player.isInsideZone(ZoneId.TOWN)
			|| player.isInsideZone(ZoneId.PEACE)
			|| MapRegionTable.getTown(player.getX(), player.getY(), player.getZ()) != null;
	}

	/**
	 * Точка отката лидера: город, peace или деревня по {@link MapRegionTable#getTown} (часто так после /unstuck, без флага town-зоны).
	 * @param leader партийный лидер
	 * @return true если лидер в безопасной точке отката
	 */
	private static boolean isLeaderInSafeRecallArea(Player leader)
	{
		if (leader == null || leader.isDead())
			return false;
		return isInTownOrPeaceArea(leader);
	}

	/**
	 * Лидер уже «дома» после ГК/unstuck/recall, фейк далеко на фарме — ТП к ПЛ (бег не дотянет).
	 * @param leader партийный лидер
	 * @return true если сделали телепорт
	 */
	private boolean tryTeleportToLeaderWhenRecall(Player leader)
	{
		if (leader == null || leader.isDead())
			return false;
		if (_fakePlayer.isInOlympiadMode() || _fakePlayer.isOlympiadProtection() || _fakePlayer.getOlympiadGameId() != -1)
			return false;
		if (_fakePlayer.getDistanceSq(leader) <= TELEPORT_DISTANCE * TELEPORT_DISTANCE)
			return false;
		if (isInTownOrPeaceArea(_fakePlayer))
			return false;
		if (!isLeaderInSafeRecallArea(leader))
			return false;

		resetPartyStuckTracking();
		_fakePlayer.setPartyMode(PartyMode.FOLLOW);
		teleportToLocation(leader.getX(), leader.getY(), leader.getZ(),
			Rnd.get(PARTY_STUCK_TP_SPREAD_MIN, PARTY_STUCK_TP_SPREAD_MAX));
		onLeaderRelocated();
		return true;
	}

	public void onLeaderRelocated()
	{
		final Player leader = getPartyLeader();
		if (leader != null && !leader.isDead())
			_holdAutoFarmUntilLeaderInitiates = true;

		_fakePlayer.setTarget(null);
		_fakePlayer.abortAttack();
		_fakePlayer.abortCast();
		_fakePlayer.stopMove(null);
		_fakePlayer.getAI().setIntention(CtrlIntention.ACTIVE);
	}

	private Player getPartyLeader()
	{
		L2Party party = _fakePlayer.getParty();
		if (party == null)
			return null;
		return party.getLeader();
	}

	private void resetPartyStuckTracking()
	{
		_partyStuckSinceMs = 0L;
	}

	/**
	 * 30 сек почти без движения вдали от ПЛ: в открытом мире — ТП к лидеру; в городе / peace / босс-зоне — не ТП, только сброс таймера (бег через {@link #moveToLeader}).
	 * @param leader current party leader (alive)
	 * @return true if unstuck teleport was performed
	 */
	private boolean tryRecoverPartyStuck(Player leader)
	{
		if (leader == null || leader.isDead())
		{
			resetPartyStuckTracking();
			return false;
		}

		if (_fakePlayer.isInOlympiadMode() || _fakePlayer.isOlympiadProtection() || _fakePlayer.getOlympiadGameId() != -1)
		{
			resetPartyStuckTracking();
			return false;
		}

		if (isPartyStuckTeleportForbiddenZone())
		{
			resetPartyStuckTracking();
			return false;
		}

		if (isCloseToLeader(leader))
		{
			resetPartyStuckTracking();
			return false;
		}

		final int x = _fakePlayer.getX();
		final int y = _fakePlayer.getY();
		final int z = _fakePlayer.getZ();
		final long now = System.currentTimeMillis();

		if (_partyStuckSinceMs == 0L)
		{
			_partyStuckX = x;
			_partyStuckY = y;
			_partyStuckZ = z;
			_partyStuckSinceMs = now;
			return false;
		}

		final long dx = (long) x - _partyStuckX;
		final long dy = (long) y - _partyStuckY;
		final long dz = (long) z - _partyStuckZ;
		final long distSq = dx * dx + dy * dy + dz * dz;
		final long eps = PARTY_STUCK_POSITION_EPSILON;
		if (distSq > eps * eps)
		{
			_partyStuckX = x;
			_partyStuckY = y;
			_partyStuckZ = z;
			_partyStuckSinceMs = now;
			return false;
		}

		if (now - _partyStuckSinceMs < PARTY_STUCK_RECOVERY_MS)
			return false;

		_partyStuckSinceMs = 0L;

		final Player pl = getPartyLeader();
		if (pl != null && !pl.isDead())
		{
			if (isInTownOrPeaceArea(_fakePlayer))
				return false;

			_fakePlayer.setPartyMode(PartyMode.FOLLOW);
			teleportToLocation(pl.getX(), pl.getY(), pl.getZ(),
				Rnd.get(PARTY_STUCK_TP_SPREAD_MIN, PARTY_STUCK_TP_SPREAD_MAX));
			onLeaderRelocated();
			return true;
		}

		final Location loc = MapRegionTable.getInstance().getLocationToTeleport(_fakePlayer, MapRegionTable.TeleportType.TOWN);
		teleportToLocation(loc.getX(), loc.getY(), loc.getZ(), Rnd.get(8, 22));
		_fakePlayer.setPartyMode(PartyMode.FOLLOW);
		_leaderMissingSince = 0L;
		return true;
	}

	private Creature findAttackerOnParty()
	{
		L2Party party = _fakePlayer.getParty();
		if (party == null)
			return null;

		for (Player member : party.getPartyMembers())
		{
			if (member == null || member.isDead())
				continue;

			Creature attacker = findAttackerTargetingMember(member);
			if (attacker != null)
				return attacker;
		}
		return null;
	}

	private Creature findAttackerTargetingMember(Player member)
	{
		if (member == null || member.isDead())
			return null;

		List<Creature> attackers = member
			.getKnownTypeInRadius(Creature.class, DEFEND_SCAN_RADIUS)
			.stream()
			.filter(c -> isTargetingPartyMember(c, member))
			.sorted(Comparator.comparingDouble(c -> member.getDistanceSq(c)))
			.collect(Collectors.toList());

		return attackers.isEmpty() ? null : attackers.get(0);
	}

	private boolean isTargetingPartyMember(Creature attacker, Player member)
	{
		if (!isValidCombatTarget(attacker))
			return false;

		if (attacker.getTarget() == member)
			return true;

		if (attacker instanceof Attackable)
		{
			final Attackable attackable = (Attackable) attacker;
			if (attackable.getMostHated() == member)
				return true;

			return attackable.getAggroList().containsKey(member);
		}

		return false;
	}

	private Creature findDirectAttacker()
	{
		List<Creature> attackers = _fakePlayer
			.getKnownTypeInRadius(Creature.class, 600)
			.stream()
			.filter(c -> !c.isDead() && c.getTarget() == _fakePlayer && isValidCombatTarget(c))
			.collect(Collectors.toList());

		return attackers.isEmpty() ? null : attackers.get(0);
	}

	private boolean isValidCombatTarget(Creature target)
	{
		if (target == null || target.isDead() || target.isInvul())
			return false;

		if (_fakePlayer.isInsideZone(ZoneId.PEACE) || target.isInsideZone(ZoneId.PEACE))
			return false;

		// In BOSS zones, only attack the boss itself — ignore raid minions (e.g. Archangels on Baium).
		if (_fakePlayer.isInsideZone(ZoneId.BOSS) && target instanceof Attackable)
		{
			if (((Attackable) target).isRaidMinion())
				return false;
		}

		if (_fakePlayer.isInParty() && target instanceof Player
			&& _fakePlayer.getParty().getPartyMembers().contains(target))
			return false;

		if (target instanceof Player)
		{
			Player p = (Player) target;
			if (_fakePlayer.isInArenaEvent() && p.isInArenaEvent())
				return checkTarget(p);
			if (target instanceof FakePlayer)
				return false;
			// In PvP zones, all players are valid targets
			if (_fakePlayer.isInsideZone(ZoneId.PVP) || _fakePlayer.isInsideZone(ZoneId.SIEGE)
				|| p.isInsideZone(ZoneId.PVP) || p.isInsideZone(ZoneId.SIEGE))
				return true;
			return p.getPvpFlag() > 0 || p.getKarma() > 0;
		}

		return true;
	}

	private boolean isMageClass()
	{
		switch (_fakePlayer.getClassId())
		{
			case ARCHMAGE:
			case SOULTAKER:
			case MYSTIC_MUSE:
			case STORM_SCREAMER:
			case DOMINATOR:
			case CARDINAL:
			case HIEROPHANT:
			case EVAS_SAINT:
			case SHILLIEN_SAINT:
				return true;
			default:
				return false;
		}
	}

	private boolean isHealerClass()
	{
		switch (_fakePlayer.getClassId())
		{
			case CARDINAL:
			case EVAS_SAINT:
			case SHILLIEN_SAINT:
			case HIEROPHANT:
			case DOOMCRYER:
				return true;
			default:
				return false;
		}
	}

	private static final int MANA_BURN_SKILL_ID = 1398;
	private static final int MANA_BURN_EMPTY_MP_THRESHOLD = 1;
	private static final int MANA_BURN_RESUME_MP_THRESHOLD = 500;

	private void tryManaBurnOnBoss(Creature target)
	{
		if (_fakePlayer.isCastingNow())
			return;
		if (!(target instanceof RaidBoss))
			return;

		final int targetId = target.getObjectId();
		if (_manaBurnBossId != targetId)
		{
			_manaBurnBossId = targetId;
			_waitingForBossManaRegen = false;
		}

		final int currentMp = (int) Math.round(target.getCurrentMp());
		if (_waitingForBossManaRegen)
		{
			if (currentMp < MANA_BURN_RESUME_MP_THRESHOLD)
				return;

			_waitingForBossManaRegen = false;
		}
		else if (currentMp <= MANA_BURN_EMPTY_MP_THRESHOLD)
		{
			_waitingForBossManaRegen = true;
			return;
		}

		L2Skill manaBurn = _fakePlayer.getSkill(MANA_BURN_SKILL_ID);
		if (manaBurn == null)
		{
			int maxLvl = com.l2jmega.gameserver.data.SkillTable.getInstance().getMaxLevel(MANA_BURN_SKILL_ID);
			manaBurn = com.l2jmega.gameserver.data.SkillTable.getInstance().getInfo(MANA_BURN_SKILL_ID, maxLvl);
			if (manaBurn != null)
				_fakePlayer.addSkill(manaBurn, false);
			else
				return;
		}

		_fakePlayer.setTarget(target);
		castSpell(manaBurn);
	}

	/**
	 * Apply CB full buffs (fighter or mage) based on class type.
	 */
	public void applyCBBuffs()
	{
		_fakePlayer.stopAllEffectsExceptThoseThatLastThroughDeath();

		List<Integer> buffList = isMageClass() ? Config.MAGE_BUFF_LIST : Config.FIGHTER_BUFF_LIST;
		for (int skillId : buffList)
		{
			L2Skill skill = SkillTable.getInstance().getInfo(skillId, SkillTable.getInstance().getMaxLevel(skillId));
			if (skill != null)
				skill.getEffects(_fakePlayer, _fakePlayer);
		}

		FakeHelpers.applyNoblesseBlessing(_fakePlayer);
		_fakePlayer.heal();
	}

	@Override
	protected void handleDeath()
	{
		if (_fakePlayer.isDead())
		{
			if (!_wasDeadLastTick)
				_acceptResThisDeath = Rnd.get(1, 100) <= Math.max(0, FakePlayerConfig.FAKE_PARTY_RES_ACCEPT_CHANCE);

			_wasDeadLastTick = true;
			iterationsOnDeath++;
			if (!shouldSuppressResurrectionChat()
				&& iterationsOnDeath == _resRequestIteration
				&& Rnd.get(1, 100) <= Math.max(0, FakePlayerConfig.FAKE_PARTY_RES_CHAT_CHANCE))
			{
				_fakePlayer.say(getRandomResRequestMessage());
			}
			// Request resurrection from another fake in party (skill or scroll).
			if (!_fakeResRequested && iterationsOnDeath >= _resRequestIteration)
			{
				_fakeResRequested = FakeResurrectionSupport.handlePartyResurrectionRequest(_fakePlayer, "res");
			}
			if (iterationsOnDeath >= getResTimeoutIterationsByContext())
			{
				startPartyDeathRecovery();
				iterationsOnDeath = 0;
				rollNextResRequestIteration();
			}
		}
		else
		{
			if (_wasDeadLastTick)
			{
				rollNextResRequestIteration();
				// Stay idle at res location for 5 seconds before resuming activity.
				_postReviveIdleUntil = System.currentTimeMillis() + 5000L;
			}
			_acceptResThisDeath = false;
			_wasDeadLastTick = false;
			_fakeResRequested = false;
			iterationsOnDeath = 0;
		}
	}

	@Override
	protected ShotType getShotType()
	{
		return isMageClass() ? ShotType.BLESSED_SPIRITSHOT : ShotType.SOULSHOT;
	}

	@Override
	protected List<OffensiveSpell> getOffensiveSpells()
	{
		return _offensiveSpells;
	}

	@Override
	protected List<HealingSpell> getHealingSpells()
	{
		return _healingSpells;
	}

	@Override
	protected List<SupportSpell> getSelfSupportSpells()
	{
		return _selfSupportSpells;
	}
}
