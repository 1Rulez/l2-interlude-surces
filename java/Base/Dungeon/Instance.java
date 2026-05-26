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

import java.util.ArrayList;
import java.util.List;

import com.l2jmega.gameserver.model.actor.instance.Door;

/**
 * @author L2FOL
 *
 */
public class Instance
{
	private int id;
	private List<Door> doors;
	
	public Instance(int id)
	{
		this.id = id;
		doors = new ArrayList<>();
	}
	
	public void openDoors()
	{
		for (Door door : doors)
			door.openMe();
	}
	
	public void closeDoors()
	{
		for (Door door : doors)
			door.closeMe();
	}
	
	public void addDoor(Door door)
	{
		doors.add(door);
	}
	
	public List<Door> getDoors()
	{
		return doors;
	}
	
	public int getId()
	{
		return id;
	}
}
