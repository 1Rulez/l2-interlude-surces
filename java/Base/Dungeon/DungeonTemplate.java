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

import java.util.Map;

/**
 * @author L2Fol
 *
 */
public class DungeonTemplate
{
	private int id;
	private String name;
	private int players;
	private Map<Integer, Integer> rewards;
	private String rewardHtm;
	private Map<Integer, DungeonStage> stages;
	
	public DungeonTemplate(int id, String name, int players, Map<Integer, Integer> rewards, String rewardHtm, Map<Integer, DungeonStage> stages)
	{
		this.id = id;
		this.name = name;
		this.players = players;
		this.rewards = rewards;
		this.rewardHtm = rewardHtm;
		this.stages = stages;
	}
	
	public int getId()
	{
		return id;
	}
	
	public String getName()
	{
		return name;
	}
	
	public int getPlayers()
	{
		return players;
	}
	
	public Map<Integer, Integer> getRewards()
	{
		return rewards;
	}
	
	public String getRewardHtm()
	{
		return rewardHtm;
	}
	
	public Map<Integer, DungeonStage> getStages()
	{
		return stages;
	}
}
