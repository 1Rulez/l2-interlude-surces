package Base.Dungeon;

import com.l2jmega.Config;
import com.l2jmega.gameserver.data.DoorTable;
import com.l2jmega.gameserver.data.NpcTable;
import com.l2jmega.gameserver.data.SpawnTable;
import com.l2jmega.gameserver.model.L2Spawn;
import com.l2jmega.gameserver.model.actor.instance.Door;
import com.l2jmega.gameserver.model.actor.instance.DungeonMob;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.actor.template.NpcTemplate;
import com.l2jmega.gameserver.model.location.Location;
import com.l2jmega.gameserver.model.memo.PlayerMemo;
import com.l2jmega.gameserver.network.serverpackets.ExShowScreenMessage;
import com.l2jmega.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS;
import com.l2jmega.gameserver.network.serverpackets.MagicSkillUse;
import com.l2jmega.gameserver.network.serverpackets.NpcHtmlMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

import com.l2jmega.commons.concurrent.ThreadPool;

/**
 * @author L2Fol
 */
public class Dungeon
{
	private DungeonTemplate template;
	private List<Player> players;
	private ScheduledFuture<?> dungeonCancelTask = null;
	private ScheduledFuture<?> nextTask = null;
	private ScheduledFuture<?> timerTask = null;
	private DungeonStage currentStage = null;
	private long stageBeginTime = 0;
	private List<DungeonMob> mobs = new CopyOnWriteArrayList<>();
	private Instance instance;
	
	public Dungeon(DungeonTemplate template, List<Player> players)
	{
		this.template = template;
		this.players = players;
		instance = InstanceManager.getInstance().createInstance();
		
		for (Player player : players)
		{
			player.setDungeon(this);
			player.setPlayerInstance(instance);
		}
		
		for (Player player : players)
		{
		    if (player.isCursedWeaponEquipped())
		    {
		        player.sendMessage("You cannot enter dungeon while holding a cursed weapon!");
		        player.teleToLocation(
		            Config.DUNGEON_SPAWN_X,
		            Config.DUNGEON_SPAWN_Y,
		            Config.DUNGEON_SPAWN_Z,
		            Config.DUNGEON_SPAWN_RND
		        );

		        DungeonManager.getInstance().removeDungeon(this);
		        return;
		    }
		}
		
		// Find and add doors in dungeon area to instance
		findAndAddDoors();
		
		beginTeleport();
	}
	
	public void onPlayerDeath(Player player)
	{
		if (!players.contains(player))
			return;
		
		if (players.size() == 1)
			ThreadPool.schedule(() -> cancelDungeon(), 5 * 1000);
		else
			player.sendMessage("You will be ressurected if your team completes this stage.");
	}
	
	public synchronized void onMobKill(DungeonMob mob)
	{
		if (!mobs.contains(mob))
			return;
		
		deleteMob(mob);
		
		if (mobs.isEmpty())
		{
			// Open doors when all mobs are killed
			instance.openDoors();
			
			if (dungeonCancelTask != null)
				dungeonCancelTask.cancel(false);
			if (timerTask != null)
				timerTask.cancel(true);
			if (nextTask != null)
				nextTask.cancel(true);
			
			for (Player player : players)
				if (player.isDead())
					player.doRevive();
				
			getNextStage();
			if (currentStage == null) // No more stages
			{
				rewardPlayers();
				InstanceManager.getInstance().deleteInstance(instance.getId());
				DungeonManager.getInstance().removeDungeon(this);
			}
			else
			{
				broadcastScreenMessage("You have completed stage " + (currentStage.getOrder() - 1) + "! Next stage begins in 10 seconds.", 5);
				ThreadPool.schedule(() -> teleToStage(), 5 * 1000);
				nextTask = ThreadPool.schedule(() -> beginStage(), 10 * 1000);
			}
		}
	}
	
	private void rewardPlayers()
	{
		// First Add Fixed Reward
		for (Player player : players)
		{
			PlayerMemo.setVar(player, "dungeon_atleast1time", "true", -1);
			for (Entry<Integer, Integer> itemId : template.getRewards().entrySet())
			{
				player.addItem("dungeon reward", itemId.getKey(), itemId.getValue(), null, true);
			}
		}
		
		if (!template.getRewardHtm().equals("NULL"))
		{
			// Show reward selection HTML
			broadcastScreenMessage("You have completed the dungeon! Select your reward", 5);
			NpcHtmlMessage htm = new NpcHtmlMessage(0);
			htm.setFile(template.getRewardHtm());
			for (Player player : players)
				player.sendPacket(htm);
		}
		else
		{
			// Auto teleport to town with rewards
			broadcastScreenMessage("You have completed the dungeon!", 5);
			for (Player player : players)
			{
				player.setDungeon(null);
				player.setPlayerInstance(null);
				player.teleToLocation(Config.DUNGEON_SPAWN_X, Config.DUNGEON_SPAWN_Y, Config.DUNGEON_SPAWN_Z, Config.DUNGEON_SPAWN_RND);
				
				// Delay visibility update to ensure teleportation is complete
				ThreadPool.schedule(() -> {
					player.decayMe();
					player.spawnMe();
				}, 100);
			}
		}
	}
	
	private void teleToStage()
	{
		if (!currentStage.teleport())
			return;
		
		for (Player player : players)
		{
			player.teleToLocation(currentStage.getLocation(), 25);
			player.decayMe();
			player.spawnMe();
		}
	}
	
	@SuppressWarnings("unused")
	private void teleToTown()
	{
		for (Player player : players)
		{
			if (!player.isOnline() || player.getClient().isDetached())
				continue;
			
			DungeonManager.getInstance().getDungeonParticipants().remove(player.getObjectId());
			player.setDungeon(null);
			player.setPlayerInstance(null);
			player.teleToLocation(Config.DUNGEON_SPAWN_X, Config.DUNGEON_SPAWN_Y, Config.DUNGEON_SPAWN_Z, Config.DUNGEON_SPAWN_RND);
			
			// Delay visibility update to ensure teleportation is complete
			ThreadPool.schedule(() -> 
			{
				player.decayMe();
				player.spawnMe();
			}, 100);
		}
	}
	
	private void cancelDungeon()
	{
		// Cancel all tasks first to prevent concurrent execution
		if (nextTask != null)
		{
			nextTask.cancel(true);
			nextTask = null;
		}
		if (timerTask != null)
		{
			timerTask.cancel(true);
			timerTask = null;
		}
		if (dungeonCancelTask != null)
		{
			dungeonCancelTask.cancel(true);
			dungeonCancelTask = null;
		}
		
		// Create a copy to avoid concurrent modification
		List<DungeonMob> mobsCopy = new ArrayList<>(mobs);
		for (DungeonMob mob : mobsCopy)
			deleteMob(mob);

		// Teleport players back to town
		for (Player player : players)
		{
			if (!player.isOnline() || player.getClient().isDetached())
				continue;
				
			if (player.isDead())
				player.doRevive();
			
			// Remove from dungeon participants safely
			try
			{
				DungeonManager.getInstance().getDungeonParticipants().remove(player.getObjectId());
			}
			catch (Exception e)
			{
				// Ignore if already removed
			}
			
			player.setDungeon(null);
			player.setPlayerInstance(null);
			player.teleToLocation(Config.DUNGEON_SPAWN_X, Config.DUNGEON_SPAWN_Y, Config.DUNGEON_SPAWN_Z, Config.DUNGEON_SPAWN_RND);
			
			// Delay visibility update to ensure teleportation is complete
			ThreadPool.schedule(() -> {
				player.decayMe();
				player.spawnMe();
			}, 100);
		}

		broadcastScreenMessage("You have failed to complete the dungeon. You will be teleported back in 10 seconds.", 5);
		
		InstanceManager.getInstance().deleteInstance(instance.getId());
		DungeonManager.getInstance().removeDungeon(this);
	}
	
	private void deleteMob(DungeonMob mob)
	{
		if (!mobs.contains(mob))
			return;
		
		mobs.remove(mob);
		if (mob.getSpawn() != null)
			SpawnTable.getInstance().deleteSpawn(mob.getSpawn(), false);
		mob.deleteMe();
	}
	
	private void beginStage()
	{
		// Close all doors in the instance when stage begins
		instance.closeDoors();
		
		for (int mobId : currentStage.getMobs().keySet())
			spawnMob(mobId, currentStage.getMobs().get(mobId));
		
		stageBeginTime = System.currentTimeMillis();
		timerTask = ThreadPool.scheduleAtFixedRate(() -> broadcastTimer(), 5 * 1000, 1000);
		nextTask = null;
		dungeonCancelTask = ThreadPool.schedule(() -> cancelDungeon(), 1000 * 60 * currentStage.getMinutes());
		broadcastScreenMessage("You have " + currentStage.getMinutes() + " minutes to finish stage " + currentStage.getOrder() + "!", 5);
	}
	
	private void spawnMob(int mobId, List<Location> locations)
	{
	    NpcTemplate template = NpcTable.getInstance().getTemplate(mobId);
	    try
	    {
	        for (Location loc : locations)
	        {
	            L2Spawn spawn = new L2Spawn(template);
	            spawn.setLoc(loc.getX(), loc.getY(), loc.getZ(), 0);
	            spawn.setRespawnDelay(1);
	            spawn.setRespawnState(false);

	            
	            spawn.setInstance(instance);

	           
	            spawn.doSpawn(false);

	            
	            DungeonMob mob = (DungeonMob) spawn.getNpc();
	            mob.setDungeon(this);

	            mobs.add(mob);
	        }
	    }
	    catch (Exception e)
	    {
	        e.printStackTrace();
	    }
	}

	
	private void teleportPlayers()
	{
	    players.removeIf(player ->
	    {
	        if (player == null)
	            return true;

	        if (player.isCursedWeaponEquipped())
	        {
	            player.sendMessage("You cannot continue dungeon while holding a cursed weapon!");
	            player.teleToLocation(
	                    Config.DUNGEON_SPAWN_X,
	                    Config.DUNGEON_SPAWN_Y,
	                    Config.DUNGEON_SPAWN_Z,
	                    Config.DUNGEON_SPAWN_RND
	            );

	            return true;
	        }

	        return false;
	    });

	    for (Player player : players)
	        player.setPlayerInstance(instance);

	    teleToStage();

	    broadcastScreenMessage("Stage " + currentStage.getOrder() + " begins in 10 seconds!", 5);

	    nextTask = ThreadPool.schedule(() -> beginStage(), 10 * 1000);
	}
	
	private void beginTeleport()
	{
	    getNextStage();

	    for (Player player : players)
	    {
	        if (player == null)
	            continue;

	        if (player.isCursedWeaponEquipped())
	        {
	            player.sendMessage("Cursed weapon holders cannot participate in dungeon.");
	            continue;
	        }

	        player.broadcastPacket(new MagicSkillUse(player, 1050, 1, 10000, 10000));
	        broadcastScreenMessage("You will be teleported in 10 seconds!", 3);
	    }

	    nextTask = ThreadPool.schedule(() -> teleportPlayers(), 10 * 1000);
	}
	
	private void getNextStage()
	{
		currentStage = currentStage == null ? template.getStages().get(1) : template.getStages().get(currentStage.getOrder() + 1);
	}
	
	private void broadcastTimer()
	{
		int secondsLeft = (int) (((stageBeginTime + (1000 * 60 * currentStage.getMinutes())) - System.currentTimeMillis()) / 1000);
		
		// Check if time expired
		if (secondsLeft <= 0)
		{
			// Cancel timer task
			if (timerTask != null)
			{
				timerTask.cancel(true);
				timerTask = null;
			}
			// Cancel dungeon cancel task (it should have already fired)
			if (dungeonCancelTask != null)
			{
				dungeonCancelTask.cancel(true);
				dungeonCancelTask = null;
			}
			// Force cancel dungeon
			cancelDungeon();
			return;
		}
		
		int minutes = secondsLeft / 60;
		int seconds = secondsLeft % 60;
		ExShowScreenMessage packet = new ExShowScreenMessage(String.format("%02d:%02d", minutes, seconds), 1010, SMPOS.BOTTOM_RIGHT, true);
		for (Player player : players)
			player.sendPacket(packet);
	}
	
	private void broadcastScreenMessage(String msg, int seconds)
	{
		ExShowScreenMessage packet = new ExShowScreenMessage(msg, seconds * 1000, SMPOS.TOP_CENTER, false);
		for (Player player : players)
			player.sendPacket(packet);
	}
	
	public List<Player> getPlayers()
	{
		return players;
	}
	
	private void findAndAddDoors()
	{
		// Find doors in dungeon area and add them to instance
		// This is a simple implementation - you might want to specify door IDs in XML
		for (Door door : DoorTable.getInstance().getDoors())
		{
			// Check if door is in dungeon area (you can customize this logic)
			// For now, we'll add doors that are near dungeon spawn points
			boolean inDungeonArea = false;
			
			for (DungeonStage stage : template.getStages().values())
			{
				Location stageLoc = stage.getLocation();
				// Check if door is within 1000 units of any stage location
				if (Math.abs(door.getX() - stageLoc.getX()) < 1000 && 
					Math.abs(door.getY() - stageLoc.getY()) < 1000)
				{
					inDungeonArea = true;
					break;
				}
			}
			
			if (inDungeonArea)
			{
				// Set door instance and add to instance
				door.setInstanceId(instance.getId());
				instance.addDoor(door);
			}
		}
	}
}