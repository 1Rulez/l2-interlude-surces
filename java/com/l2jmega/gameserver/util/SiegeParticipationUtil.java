package com.l2jmega.gameserver.util;

import com.l2jmega.gameserver.instancemanager.CastleManager;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.entity.Castle;
import com.l2jmega.gameserver.model.entity.Siege;
import com.l2jmega.gameserver.model.pledge.Clan;

public final class SiegeParticipationUtil
{
	private SiegeParticipationUtil()
	{
	}

	public static boolean isClanInActiveSiege(Clan clan)
	{
		if (clan == null)
			return false;

		for (Castle castle : CastleManager.getInstance().getCastles())
		{
			final Siege siege = castle.getSiege();
			if (siege != null && siege.isInProgress() && siege.checkSides(clan))
				return true;
		}

		return false;
	}

	public static boolean isPlayerOrClanInActiveSiege(Player player)
	{
		if (player == null)
			return false;

		return player.isInSiege() || player.getSiegeState() > 0 || isClanInActiveSiege(player.getClan());
	}
}
