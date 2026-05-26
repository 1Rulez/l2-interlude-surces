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

import com.l2jmega.gameserver.model.actor.Creature;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.zone.L2SpawnZone;
import com.l2jmega.gameserver.model.zone.ZoneId;
/**
 * 
 * @author Sarada
 *
 */
public class L2NewNameZone extends L2SpawnZone
{
	public L2NewNameZone(int id)
	{
		super(id);
	}
	
	@Override
	protected void onEnter(Creature character)
	{

		if (character instanceof Player)
		{
			final Player player = ((Player) character);
			character.setInsideZone(ZoneId.NEW_NAME, true);
			player.setNewNameZone(true);
			player.broadcastUserInfo();
			
		}
	}
	
	@Override
	protected void onExit(Creature character)
	{
		if (character instanceof Player)
		{
			final Player player = ((Player) character);
			character.setInsideZone(ZoneId.NEW_NAME, false);
			player.setNewNameZone(false);
			player.broadcastUserInfo();
		}
		
	}
	
	@Override
	public void onDieInside(Creature character)
	{
	}
	
	@Override
	public void onReviveInside(Creature character)
	{
	}
	
}