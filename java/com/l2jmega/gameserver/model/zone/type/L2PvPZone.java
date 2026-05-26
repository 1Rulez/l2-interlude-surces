/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jmega.gameserver.model.zone.type;

import com.l2jmega.Config;
import com.l2jmega.gameserver.model.L2Party;
import com.l2jmega.gameserver.model.actor.Creature;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.zone.L2SpawnZone;
import com.l2jmega.gameserver.model.zone.ZoneId;
import com.l2jmega.gameserver.taskmanager.PvpFlagTaskManager;

import phantom.FakePlayer;

public class L2PvPZone extends L2SpawnZone
{
	public L2PvPZone(int id)
	{
		super(id);
	}

	@Override
	protected void onEnter(Creature character)
	{
		character.setInsideZone(ZoneId.PVP_CUSTOM, true);

		if (character instanceof FakePlayer)
		{
			((FakePlayer) character).updatePvPStatus();
		}
		else if (character instanceof Player)
		{
			Player activeChar = (Player) character;

			// Remove from flag timer so it doesn't expire while inside the zone
			if (activeChar.getPvpFlag() > 0)
				PvpFlagTaskManager.getInstance().remove(activeChar);

			activeChar.updatePvPFlag(1);

			if (Config.ANTZERG_CHECK_PARTY_INVITE)
				checkPartyMembers(activeChar);
		}

		character.sendMessage("You have entered a Pvp Zone!");
	}

	@Override
	protected void onExit(Creature character)
	{
		character.setInsideZone(ZoneId.PVP_CUSTOM, false);

		if (character instanceof Player)
		{
			Player activeChar = (Player) character;
			// Start countdown timer to remove the flag, same behaviour as FlagZone
			PvpFlagTaskManager.getInstance().add(activeChar, Config.PVP_NORMAL_TIME);
			activeChar.sendMessage("You have left a Pvp Zone!");
		}
		else
		{
			character.sendMessage("You have left a Pvp Zone!");
		}
	}

	@Override
	public void onDieInside(Creature character)
	{
		// Clear zone flag on death so it doesn't persist after reviving outside
		character.setInsideZone(ZoneId.PVP_CUSTOM, false);
	}

	@Override
	public void onReviveInside(Creature character)
	{
		// Re-apply the flag if the player revives back inside the zone
		character.setInsideZone(ZoneId.PVP_CUSTOM, true);

		if (character instanceof Player)
		{
			Player activeChar = (Player) character;
			if (activeChar.getPvpFlag() > 0)
				PvpFlagTaskManager.getInstance().remove(activeChar);
			activeChar.updatePvPFlag(1);
		}
	}

	public void checkPartyMembers(Player player)
	{
		L2Party party = player.getParty();

		if (party == null)
			return;

		for (Player member : party.getPartyMembers())
		{
			if (member == null)
				continue;

			if (!member.isOnline())
				continue;

			// Null checks must come before any usage of getClan()
			if (member.getClan() == null || player.getClan() == null)
				continue;

			if (member.getClan() != player.getClan()
				&& (member.getAllyId() == 0 || player.getAllyId() == 0
					|| member.getAllyId() != player.getAllyId()))
			{
				player.sendMessage("In pvp zone only clan/ally party allowed.");
				player.getParty().removePartyMember(player, L2Party.MessageType.Left);
				break;
			}
		}
	}
}
