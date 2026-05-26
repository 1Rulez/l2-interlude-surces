package com.l2jmega.gameserver.handler.admincommandhandlers;

import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.commons.random.Rnd;

import com.l2jmega.gameserver.data.SpawnTable;
import com.l2jmega.gameserver.handler.IAdminCommandHandler;
import com.l2jmega.gameserver.instancemanager.RaidBossSpawnManager;
import com.l2jmega.gameserver.model.L2Spawn;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.WorldObject;
import com.l2jmega.gameserver.model.actor.Npc;
import com.l2jmega.gameserver.model.actor.instance.Player;

import com.l2jmega.gameserver.network.SystemMessageId;

import phantom.FakePlayer;
import phantom.AdminSpawnedFakesStorage;
import phantom.FakeAccountService;

/**
 * This class handles following admin commands: - delete = deletes target
 */
public class AdminDelete implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_delete",
		"admin_deleteallphantomtown",
		"admin_deleteallphantompvp",
		"admin_deleteallphantomfarm"
	};
	
	@Override
	public boolean useAdminCommand(String command, Player activeChar)
	{
		if (command.equals("admin_delete"))
		{
			WorldObject target = activeChar.getTarget();
			
			if (target instanceof FakePlayer)
			{
				final FakePlayer fake = (FakePlayer) target;
				final int curX = fake.getX();
				final int curY = fake.getY();
				final int curZ = fake.getZ();
				final int lastX = fake.getLastX();
				final int lastY = fake.getLastY();
				final int lastZ = fake.getLastZ();
				
				fake.despawnPlayer();
				
				// Permanently remove from startup storage and DB for //delete.
				AdminSpawnedFakesStorage.removeNear(null, curX, curY, curZ, 1200, true);
				AdminSpawnedFakesStorage.removeNear(null, lastX, lastY, lastZ, 1200, true);
				FakeAccountService.deletePersistentFakeCharacter(fake.getObjectId());
				
				activeChar.sendMessage("Fake deleted permanently.");
			}
			else
			{
				handleDelete(activeChar);
			}
		}
		else if (command.startsWith("admin_deleteallphantomtown")
			|| command.startsWith("admin_deleteallphantompvp")
			|| command.startsWith("admin_deleteallphantomfarm"))
		{
			for (Player player : World.getInstance().getPlayers())
			{
				if (player instanceof FakePlayer)
				{
					final FakePlayer fp = (FakePlayer) player;
					ThreadPool.schedule(new Runnable()
					{
						@Override
						public void run()
						{
							fp.despawnPlayer();
						}
					}, Rnd.get(10000, 30000));
				}
			}
			activeChar.sendMessage("Removing all fake players...");
		}
		
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
	
	private static void handleDelete(Player activeChar)
	{
		if (activeChar.getAccessLevel().getLevel() < 7)
			return;
		
		WorldObject obj = activeChar.getTarget();
		if (obj != null && obj instanceof Npc)
		{
			Npc target = (Npc) obj;
			
			L2Spawn spawn = target.getSpawn();
			if (spawn != null)
			{
				spawn.setRespawnState(false);
				
				if (RaidBossSpawnManager.getInstance().isDefined(spawn.getNpcId()))
					RaidBossSpawnManager.getInstance().deleteSpawn(spawn, true);
				else
					SpawnTable.getInstance().deleteSpawn(spawn, true);
			}
			target.deleteMe();
			
			activeChar.sendMessage("Deleted " + target.getName() + " from " + target.getObjectId() + ".");
		}
		else
			activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
	}
}