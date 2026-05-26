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
package Base.Dungeon;

import java.util.List;
import java.util.Map;

import com.l2jmega.gameserver.model.location.Location;



/**
 * @author L2Fol
 *
 */
public class DungeonStage
{
	private int order;
	private Location location;
	private boolean teleport;
	private int minutes;
	private Map<Integer, List<Location>> mobs;
	
	public DungeonStage(int order, Location location, boolean teleport, int minutes, Map<Integer, List<Location>> mobs)
	{
		this.order = order;
		this.location = location;
		this.teleport = teleport;
		this.minutes = minutes;
		this.mobs = mobs;
	}
	
	public int getOrder()
	{
		return order;
	}
	
	public Location getLocation()
	{
		return location;
	}
	
	public boolean teleport()
	{
		return teleport;
	}
	
	public int getMinutes()
	{
		return minutes;
	}
	
	public Map<Integer, List<Location>> getMobs()
	{
		return mobs;
	}
}
