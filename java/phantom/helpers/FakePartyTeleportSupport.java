package phantom.helpers;

import com.l2jmega.commons.random.Rnd;
import com.l2jmega.gameserver.model.L2Party;
import com.l2jmega.gameserver.model.actor.ai.CtrlIntention;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.location.TeleportLocation;
import com.l2jmega.gameserver.network.serverpackets.TeleportToLocation;

import phantom.FakePlayer;
import phantom.ai.party.PartyFollowerAI;

public final class FakePartyTeleportSupport
{
	private FakePartyTeleportSupport()
	{
	}

	public static void syncFakePartyToTeleport(Player leader, TeleportLocation loc, int randomOffset)
	{
		if (leader == null || loc == null)
			return;

		syncFakePartyToCoordinates(leader, loc.getX(), loc.getY(), loc.getZ(), randomOffset);
	}

	public static void syncFakePartyToCoordinates(Player leader, int x, int y, int z, int randomOffset)
	{
		if (leader == null || !leader.isInParty())
			return;

		final L2Party party = leader.getParty();
		if (party == null)
			return;

		for (Player member : party.getPartyMembers())
		{
			if (!(member instanceof FakePlayer) || member == leader)
				continue;

			final FakePlayer fake = (FakePlayer) member;
			if (!fake.isOnline() || fake.isInOlympiadMode() || fake.isOlympiadProtection() || fake.getOlympiadGameId() != -1)
				continue;

			int targetX = x;
			int targetY = y;
			if (randomOffset > 0)
			{
				targetX += Rnd.get(-randomOffset, randomOffset);
				targetY += Rnd.get(-randomOffset, randomOffset);
			}

			final int targetZ = z + 5;

			fake.stopMove(null);
			fake.abortAttack();
			fake.abortCast();
			fake.setIsTeleporting(true);
			fake.setTarget(null);
			fake.getAI().setIntention(CtrlIntention.ACTIVE);
			fake.broadcastPacket(new TeleportToLocation(fake, targetX, targetY, targetZ));
			fake.decayMe();
			fake.setXYZ(targetX, targetY, targetZ);
			fake.onTeleported();
			fake.revalidateZone(true);
			
			if (fake.getFakeAi() instanceof PartyFollowerAI)
				((PartyFollowerAI) fake.getFakeAi()).onLeaderRelocated();
		}
	}
}
