package phantom.helpers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.commons.random.Rnd;
import com.l2jmega.gameserver.model.L2Party;
import com.l2jmega.gameserver.model.L2Skill;
import com.l2jmega.gameserver.model.L2Skill.SkillTargetType;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.holder.IntIntHolder;
import com.l2jmega.gameserver.model.item.instance.ItemInstance;
import com.l2jmega.gameserver.network.serverpackets.MagicSkillUse;
import com.l2jmega.gameserver.templates.skills.L2SkillType;

import phantom.FakePlayer;
import phantom.FakePlayerConfig;

public final class FakeResurrectionSupport
{
	private static final int BLESSED_RES_SCROLL_ID = 3936;
	private static final long REQUEST_COOLDOWN_MS = 6000L;
	private static final int RESPONSE_DELAY_MIN_MS = 1200;
	private static final int RESPONSE_DELAY_MAX_MS = 2500;
	private static final int ALL_CHAT_RADIUS = 1250;
	private static final int MAX_RES_ATTEMPTS = 3;
	private static final int DEFAULT_RES_CAST_RANGE = 400;
	private static final int RES_MOVE_MARGIN = 60;
	private static final int RES_RETRY_DELAY_MS = 900;

	private static final Map<Integer, Long> PENDING_REQUESTS = new ConcurrentHashMap<>();

	private FakeResurrectionSupport()
	{
	}

	public static boolean handlePartyResurrectionRequest(Player player, String text)
	{
		if (!shouldHandleRequest(player, text))
			return false;

		return scheduleResurrection(player, selectPartyResponder(player));
	}

	public static boolean handleAllResurrectionRequest(Player player, String text)
	{
		if (!shouldHandleRequest(player, text))
			return false;

		FakePlayer responder = selectPartyResponder(player);
		if (responder == null)
			responder = selectNearbyResponder(player);

		return scheduleResurrection(player, responder);
	}

	private static boolean shouldHandleRequest(Player player, String text)
	{
		return FakePlayerConfig.PHANTOM_RES && player != null && player.isOnline() && player.isAlikeDead() && !player.isReviveRequested() && isResurrectionCommand(text);
	}

	private static boolean isResurrectionCommand(String text)
	{
		if (text == null)
			return false;

		final String normalized = text.trim().toLowerCase();
		if (normalized.isEmpty())
			return false;

		return normalized.equals("res") || normalized.equals("ress") || normalized.equals("rez")
			|| normalized.startsWith("res ") || normalized.startsWith("ress ") || normalized.startsWith("rez ");
	}

	private static boolean scheduleResurrection(Player player, FakePlayer responder)
	{
		if (player == null || responder == null)
			return false;

		final long now = System.currentTimeMillis();
		final Long pendingUntil = PENDING_REQUESTS.get(player.getObjectId());
		if (pendingUntil != null && pendingUntil > now)
			return false;

		PENDING_REQUESTS.put(player.getObjectId(), now + REQUEST_COOLDOWN_MS);

		final int delay = Rnd.get(RESPONSE_DELAY_MIN_MS, RESPONSE_DELAY_MAX_MS);
		ThreadPool.schedule(() -> attemptResurrection(player, responder, 0), delay);
		return true;
	}

	private static void attemptResurrection(Player player, FakePlayer responder, int attempt)
	{
		if (!isValidResurrectionTarget(player) || !isAvailableResponder(responder))
			return;

		if (responder.isSitting())
			responder.standUp();

		responder.abortAttack();
		responder.abortCast();
		responder.setTarget(player);

		final L2Skill resurrectionSkill = getBestResurrectionSkill(responder);
		final int castRange = resurrectionSkill != null ? Math.max(DEFAULT_RES_CAST_RANGE, resurrectionSkill.getCastRange()) : DEFAULT_RES_CAST_RANGE;
		final double distance = Math.sqrt(responder.getDistanceSq(player));
		if (distance > castRange - RES_MOVE_MARGIN)
		{
			if (attempt >= MAX_RES_ATTEMPTS)
				return;

			responder.getFakeAi().moveTo(player.getX(), player.getY(), player.getZ());
			ThreadPool.schedule(() -> attemptResurrection(player, responder, attempt + 1), RES_RETRY_DELAY_MS);
			return;
		}

		if (resurrectionSkill != null)
		{
			if (responder.checkUseMagicConditions(resurrectionSkill, false, false))
			{
				beginResurrectionCast(responder, player, resurrectionSkill, false, attempt);
				return;
			}
		}

		useBlessedResurrectionScroll(responder, player, attempt);
	}

	private static void useBlessedResurrectionScroll(FakePlayer responder, Player target, int attempt)
	{
		if (responder == null || target == null || !target.isAlikeDead() || target.isReviveRequested())
			return;

		FakeHelpers.grantBlessedResurrectionScrolls(responder);

		final ItemInstance scroll = responder.getInventory().getItemByItemId(BLESSED_RES_SCROLL_ID);
		if (scroll == null || scroll.getCount() <= 0)
			return;

		responder.setTarget(target);
		final L2Skill scrollSkill = getScrollResurrectionSkill(scroll);
		if (scrollSkill == null)
			return;

		beginResurrectionCast(responder, target, scrollSkill, true, attempt);
	}

	private static L2Skill getBestResurrectionSkill(FakePlayer responder)
	{
		L2Skill bestSkill = null;
		double bestPower = Double.NEGATIVE_INFINITY;

		for (L2Skill skill : responder.getSkills().values())
		{
			if (skill == null || skill.getSkillType() != L2SkillType.RESURRECT)
				continue;

			final SkillTargetType targetType = skill.getTargetType();
			if (targetType != SkillTargetType.TARGET_CORPSE_ALLY && targetType != SkillTargetType.TARGET_CORPSE_PLAYER)
				continue;

			if (bestSkill == null || skill.getPower() > bestPower || (skill.getPower() == bestPower && skill.getLevel() > bestSkill.getLevel()))
			{
				bestSkill = skill;
				bestPower = skill.getPower();
			}
		}

		return bestSkill;
	}

	private static L2Skill getScrollResurrectionSkill(ItemInstance scroll)
	{
		if (scroll == null || scroll.getEtcItem() == null)
			return null;

		final IntIntHolder[] skills = scroll.getEtcItem().getSkills();
		if (skills == null)
			return null;

		for (IntIntHolder skillInfo : skills)
		{
			if (skillInfo == null)
				continue;

			final L2Skill skill = skillInfo.getSkill();
			if (skill != null)
				return skill;
		}

		return null;
	}

	private static void broadcastResurrectionEffect(FakePlayer responder, Player target, L2Skill skill)
	{
		if (responder == null || target == null || skill == null)
			return;

		final int hitTime = Math.max(1, skill.getHitTime());
		responder.broadcastPacket(new MagicSkillUse(responder, target, skill.getId(), skill.getLevel(), hitTime, 0));
	}

	private static void beginResurrectionCast(FakePlayer responder, Player target, L2Skill skill, boolean consumeScroll, int attempt)
	{
		if (responder == null || target == null || skill == null)
			return;

		responder.abortAttack();
		responder.abortCast();
		responder.setTarget(target);
		broadcastResurrectionEffect(responder, target, skill);

		// Mirror the server cast pipeline: the actual skill effect is launched slightly
		// before the visual cast fully ends, so the resurrection request lands in sync.
		final int skillTime = Math.max(1, skill.getHitTime() + skill.getCoolTime());
		final int delay = Math.max(1, skillTime - 200);
		ThreadPool.schedule(() -> finishResurrectionCast(responder, target, skill, consumeScroll, attempt), delay);
	}

	private static void finishResurrectionCast(FakePlayer responder, Player target, L2Skill skill, boolean consumeScroll, int attempt)
	{
		if (!isValidResurrectionTarget(target) || responder == null || !responder.isOnline() || responder.isDead() || responder.isOutOfControl())
			return;

		if (consumeScroll && !responder.destroyItemByItemId("PhantomResurrection", BLESSED_RES_SCROLL_ID, 1, target, false))
		{
			if (attempt < MAX_RES_ATTEMPTS)
				ThreadPool.schedule(() -> attemptResurrection(target, responder, attempt + 1), RES_RETRY_DELAY_MS);
			return;
		}

		target.reviveRequest(responder, skill, false);

		if (!target.isReviveRequested() && attempt < MAX_RES_ATTEMPTS)
		{
			responder.getFakeAi().moveTo(target.getX(), target.getY(), target.getZ());
			ThreadPool.schedule(() -> attemptResurrection(target, responder, attempt + 1), RES_RETRY_DELAY_MS);
		}
	}

	private static FakePlayer selectPartyResponder(Player player)
	{
		if (player == null || !player.isInParty())
			return null;

		final L2Party party = player.getParty();
		if (party == null)
			return null;

		FakePlayer bestSkillResponder = null;
		double bestSkillDistance = Double.MAX_VALUE;
		FakePlayer bestScrollResponder = null;
		double bestScrollDistance = Double.MAX_VALUE;

		for (Player member : party.getPartyMembers())
		{
			if (!(member instanceof FakePlayer))
				continue;

			final FakePlayer fake = (FakePlayer) member;
			FakeHelpers.grantBlessedResurrectionScrolls(fake);
			if (!isAvailableResponder(fake))
				continue;

			final double distance = fake.getDistanceSq(player);
			if (getBestResurrectionSkill(fake) != null)
			{
				if (distance < bestSkillDistance)
				{
					bestSkillDistance = distance;
					bestSkillResponder = fake;
				}
			}
			else if (hasBlessedResurrectionScroll(fake) && distance < bestScrollDistance)
			{
				bestScrollDistance = distance;
				bestScrollResponder = fake;
			}
		}

		return bestSkillResponder != null ? bestSkillResponder : bestScrollResponder;
	}

	private static FakePlayer selectNearbyResponder(Player player)
	{
		if (player == null)
			return null;

		FakePlayer bestSkillResponder = null;
		double bestSkillDistance = Double.MAX_VALUE;
		FakePlayer bestScrollResponder = null;
		double bestScrollDistance = Double.MAX_VALUE;

		for (Player knownPlayer : player.getKnownTypeInRadius(Player.class, ALL_CHAT_RADIUS))
		{
			if (!(knownPlayer instanceof FakePlayer))
				continue;

			final FakePlayer fake = (FakePlayer) knownPlayer;
			FakeHelpers.grantBlessedResurrectionScrolls(fake);
			if (!isAvailableResponder(fake))
				continue;

			final double distance = fake.getDistanceSq(player);
			if (getBestResurrectionSkill(fake) != null)
			{
				if (distance < bestSkillDistance)
				{
					bestSkillDistance = distance;
					bestSkillResponder = fake;
				}
			}
			else if (hasBlessedResurrectionScroll(fake) && distance < bestScrollDistance)
			{
				bestScrollDistance = distance;
				bestScrollResponder = fake;
			}
		}

		return bestSkillResponder != null ? bestSkillResponder : bestScrollResponder;
	}

	private static boolean hasBlessedResurrectionScroll(FakePlayer responder)
	{
		final ItemInstance scroll = responder.getInventory().getItemByItemId(BLESSED_RES_SCROLL_ID);
		return scroll != null && scroll.getCount() > 0;
	}

	private static boolean isValidResurrectionTarget(Player player)
	{
		return player != null && player.isOnline() && player.isAlikeDead() && !player.isReviveRequested();
	}

	private static boolean isAvailableResponder(FakePlayer responder)
	{
		return responder != null && responder.isOnline() && !responder.isDead() && !responder.isOutOfControl() && !responder.isCastingNow()
			&& (getBestResurrectionSkill(responder) != null || hasBlessedResurrectionScroll(responder));
	}
}
