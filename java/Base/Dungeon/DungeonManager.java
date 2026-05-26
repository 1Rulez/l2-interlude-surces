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

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.l2jmega.commons.concurrent.ThreadPool;

import com.l2jmega.L2DatabaseFactory;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.location.Location;
import com.l2jmega.gameserver.xmlfactory.XMLDocumentFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * @author L2Fol
 */
public class DungeonManager
{
	private static Logger log = Logger.getLogger(DungeonManager.class.getName());
	private static final long DUNGEON_COOLDOWN_MILLIS = 1000L * 60 * 60 * 12;
	
	private Map<Integer, DungeonTemplate> templates;
	private List<Dungeon> running;
	private Set<Integer> dungeonParticipants;
	private boolean reloading = false;
	private Map<String, Long[]> dungeonPlayerData;
	
	protected DungeonManager()
	{
		templates = new ConcurrentHashMap<>();
		running = new CopyOnWriteArrayList<>();
		dungeonParticipants = ConcurrentHashMap.newKeySet();
		dungeonPlayerData = new ConcurrentHashMap<>();
		
		load();
		ThreadPool.scheduleAtFixedRate(() -> updateDatabase(), 1000*60*30, 1000*60*60);
	}
	
	private void updateDatabase()
	{
		cleanupExpiredPlayerData();

		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			try (PreparedStatement deleteExpired = con.prepareStatement("DELETE FROM dungeon WHERE lastjoin <= ?"))
			{
				deleteExpired.setLong(1, System.currentTimeMillis() - DUNGEON_COOLDOWN_MILLIS);
				deleteExpired.executeUpdate();
			}

			try (PreparedStatement deleteRow = con.prepareStatement("DELETE FROM dungeon WHERE dungid=? AND ipaddr=?");
				 PreparedStatement insertRow = con.prepareStatement("INSERT INTO dungeon VALUES (?,?,?)"))
			{
				for (Map.Entry<String, Long[]> entry : dungeonPlayerData.entrySet())
				{
					final String ip = entry.getKey();
					final Long[] times = entry.getValue();
					if (times == null)
						continue;

					for (int i = 1; i < times.length; i++)
					{
						final long lastJoin = times[i] != null ? times[i] : 0L;
						if (lastJoin <= 0L)
							continue;

						deleteRow.setInt(1, i);
						deleteRow.setString(2, ip);
						deleteRow.addBatch();

						insertRow.setInt(1, i);
						insertRow.setString(2, ip);
						insertRow.setLong(3, lastJoin);
						insertRow.addBatch();
					}
				}

				deleteRow.executeBatch();
				insertRow.executeBatch();
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public boolean reload()
	{
		if (!running.isEmpty())
		{
			reloading = true;
			return false;
		}
		
		templates.clear();
		load();
		return true;
	}
	
	private void load()
	{	
		try
		{
			File f = new File("./data/xml/dungeons.xml");
			Document doc = XMLDocumentFactory.getInstance().loadDocument(f);
			
			Node n = doc.getFirstChild();
			for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling())
			{
				if (d.getNodeName().equals("dungeon"))
				{
					int id = Integer.parseInt(d.getAttributes().getNamedItem("id").getNodeValue());
					String name = d.getAttributes().getNamedItem("name").getNodeValue();
					int players = Integer.parseInt(d.getAttributes().getNamedItem("players").getNodeValue());
					Map<Integer, Integer> rewards = new HashMap<>();
					String rewardHtm = d.getAttributes().getNamedItem("rewardHtm").getNodeValue();
					Map<Integer, DungeonStage> stages = new HashMap<>();
					
					String rewards_data = d.getAttributes().getNamedItem("rewards").getNodeValue();
					if(!rewards_data.isEmpty()) // If config is empty do not feed the rewards
					{
						String[] rewards_data_split = rewards_data.split(";");
						for (String reward : rewards_data_split)
						{
							String[] reward_split = reward.split(",");
							rewards.put(Integer.parseInt(reward_split[0]), Integer.parseInt(reward_split[1]));
						}
					}

					for (Node cd = d.getFirstChild(); cd != null; cd = cd.getNextSibling())
					{
						NamedNodeMap attrs = cd.getAttributes();
						
						if (cd.getNodeName().equals("stage"))
						{
							int order = Integer.parseInt(attrs.getNamedItem("order").getNodeValue());
							String loc_data = attrs.getNamedItem("loc").getNodeValue();
							String[] loc_data_split = loc_data.split(",");
							Location loc = new Location(Integer.parseInt(loc_data_split[0]), Integer.parseInt(loc_data_split[1]), Integer.parseInt(loc_data_split[2]));
							boolean teleport = Boolean.parseBoolean(attrs.getNamedItem("teleport").getNodeValue());
							int minutes = Integer.parseInt(attrs.getNamedItem("minutes").getNodeValue());
							Map<Integer, List<Location>> mobs = new HashMap<>();
							
							for (Node ccd = cd.getFirstChild(); ccd != null; ccd = ccd.getNextSibling())
							{
								NamedNodeMap attrs2 = ccd.getAttributes();
								
								if (ccd.getNodeName().equals("mob"))
								{
									int npcId = Integer.parseInt(attrs2.getNamedItem("npcId").getNodeValue());
									List<Location> locs = new ArrayList<>();
									
									String locs_data = attrs2.getNamedItem("locs").getNodeValue();
									String[] locs_data_split = locs_data.split(";");
									for (String locc : locs_data_split)
									{
										String[] locc_data = locc.split(",");
										locs.add(new Location(Integer.parseInt(locc_data[0]), Integer.parseInt(locc_data[1]), Integer.parseInt(locc_data[2])));
									}
									
									mobs.put(npcId, locs);
								}
							}
							
							stages.put(order, new DungeonStage(order, loc, teleport, minutes, mobs));
						}
					}
					
					templates.put(id, new DungeonTemplate(id, name, players, rewards, rewardHtm, stages));
				}
			}
		}
		catch (Exception e)
		{
			log.log(Level.WARNING, "DungeonManager: Error loading dungeons.xml", e);
			e.printStackTrace();
		}
		
		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			PreparedStatement stm = con.prepareStatement("SELECT * FROM dungeon");
			ResultSet rset = stm.executeQuery();
			
			while (rset.next())
			{
				int dungid = rset.getInt("dungid");
				String ipaddr = rset.getString("ipaddr");
				long lastjoin = rset.getLong("lastjoin");
				if (lastjoin <= 0L || System.currentTimeMillis() - lastjoin >= DUNGEON_COOLDOWN_MILLIS)
					continue;
				
				if (!dungeonPlayerData.containsKey(ipaddr))
				{
					Long[] times = new Long[templates.size()+1];
					for (int i = 0; i < times.length; i++)
						times[i] = 0L;
					if (dungid >= 0 && dungid < times.length)
						times[dungid] = lastjoin;
					
					dungeonPlayerData.put(ipaddr, times);
				}
				else
				{
					Long[] times = dungeonPlayerData.get(ipaddr);
					if (dungid >= 0 && dungid < times.length)
						times[dungid] = lastjoin;
				}
			}
			
			rset.close();
			stm.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
		log.info("DungeonManager: Loaded "+templates.size()+" dungeon templates");
	}

	private void cleanupExpiredPlayerData()
	{
		final long now = System.currentTimeMillis();
		dungeonPlayerData.entrySet().removeIf(entry ->
		{
			final Long[] times = entry.getValue();
			if (times == null)
				return true;

			boolean hasActiveCooldown = false;
			for (int i = 1; i < times.length; i++)
			{
				final long lastJoin = times[i] != null ? times[i] : 0L;
				if (lastJoin <= 0L)
					continue;

				if (now - lastJoin >= DUNGEON_COOLDOWN_MILLIS)
				{
					times[i] = 0L;
					continue;
				}

				hasActiveCooldown = true;
			}

			return !hasActiveCooldown;
		});
	}
	
	public synchronized void removeDungeon(Dungeon dungeon)
	{
		running.remove(dungeon);
		
		if (reloading && running.isEmpty())
		{
			reloading = false;
			reload();
		}
	}
	
	public synchronized void enterDungeon(int id, Player player)
	{
		if (reloading)
		{
			player.sendMessage("The Dungeon system is reloading, please try again in a few minutes.");
			return;
		}
	
		
		DungeonTemplate template = templates.get(id);
		if (template.getPlayers() > 1 && (!player.isInParty() || player.getParty().getMemberCount() != template.getPlayers()))
		{
			player.sendMessage("You need a party of "+template.getPlayers()+" players to enter this Dungeon.");
			return;
		}
		else if (template.getPlayers() == 1 && player.isInParty())
		{
			player.sendMessage("You can only enter this Dungeon alone.");
			return;
		}
		
		List<Player> players = new ArrayList<>();
		if (player.isInParty())
		{
			for (Player pm : player.getParty().getPartyMembers())
			{
				String pmip = pm.getClient().getConnection().getInetAddress().getHostAddress();
				if (getLastJoin(pmip, template.getId()) > 0L && (System.currentTimeMillis() - getLastJoin(pmip, template.getId()) < DUNGEON_COOLDOWN_MILLIS))
				{
					player.sendMessage("One of your party members cannot join this Dungeon because 12 hours have not passed since they last joined.");
					return;
				}
			}
			
			for (Player pm : player.getParty().getPartyMembers())
			{

				String pmip = pm.getClient().getConnection().getInetAddress().getHostAddress();
				dungeonParticipants.add(pm.getObjectId());
				players.add(pm);
				markPlayerEntry(pmip, template.getId(), System.currentTimeMillis());
			}
		}
		else
		{
			String pmip = player.getClient().getConnection().getInetAddress().getHostAddress();
			if (getLastJoin(pmip, template.getId()) > 0L && (System.currentTimeMillis() - getLastJoin(pmip, template.getId()) < DUNGEON_COOLDOWN_MILLIS))
			{
				player.sendMessage("12 hours have not passed since you last entered this Dungeon.");
				return;
			}
			
			dungeonParticipants.add(player.getObjectId());
			players.add(player);
			markPlayerEntry(pmip, template.getId(), System.currentTimeMillis());
		}
		
		running.add(new Dungeon(template, players));
	}
	
	public boolean isReloading()
	{
		return reloading;
	}
	
	public boolean isInDungeon(Player player)
	{
		for (Dungeon dungeon : running)
			for (Player p : dungeon.getPlayers())
				if (p == player)
					return true;
		
		return false;
	}
	
	public Map<String, Long[]> getPlayerData()
	{
		return dungeonPlayerData;
	}
	
	public Set<Integer> getDungeonParticipants()
	{
		return dungeonParticipants;
	}

	public long getLastJoin(String ip, int dungeonId)
	{
		final Long[] times = dungeonPlayerData.get(ip);
		if (times == null || dungeonId < 0 || dungeonId >= times.length || times[dungeonId] == null)
			return 0L;

		return times[dungeonId];
	}

	private void markPlayerEntry(String ip, int dungeonId, long time)
	{
		Long[] times = dungeonPlayerData.get(ip);
		if (times == null)
		{
			times = new Long[templates.size() + 1];
			for (int i = 0; i < times.length; i++)
				times[i] = 0L;
			dungeonPlayerData.put(ip, times);
		}

		if (dungeonId >= 0 && dungeonId < times.length)
			times[dungeonId] = time;
	}
	
	public static DungeonManager getInstance()
	{
		return SingletonHolder.instance;
	}
	
	private static class SingletonHolder
	{
		protected static final DungeonManager instance = new DungeonManager();
	}
}
