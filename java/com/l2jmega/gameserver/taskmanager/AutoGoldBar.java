package com.l2jmega.gameserver.taskmanager;

import com.l2jmega.Config;
import com.l2jmega.gameserver.model.actor.Creature;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.network.serverpackets.ItemList;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.l2jmega.commons.concurrent.ThreadPool;

public class AutoGoldBar implements Runnable
{
	@Override
	public final void run()
	{
		if (_players.isEmpty())
			return;
		
		for (Map.Entry<Player, Long> entry : _players.entrySet())
		{
			final Player player = entry.getKey();
			
			if (player.getInventory().getInventoryItemCount(57, 0) >= Config.BANKING_SYSTEM_ADENA)
			{
				player.getInventory().reduceAdena("Goldbar", Config.BANKING_SYSTEM_ADENA, player, null);
				player.getInventory().addItem("Goldbar", Config.BANKING_SYSTEM_GOLDBAR_ID, Config.BANKING_SYSTEM_GOLDBARS, player, null);
				player.getInventory().updateDatabase();
				player.sendPacket(new ItemList(player, false));
			}
		}
	}
	
	private final Map<Player, Long> _players = new ConcurrentHashMap<>();
	
	protected AutoGoldBar()
	{
		// Run task each 10 second.
		ThreadPool.scheduleAtFixedRate(this, 1000, 1000);
	}
	
	public final void add(Player player)
	{
		_players.put(player, System.currentTimeMillis());
	}
	
	public final void remove(Creature player)
	{
		_players.remove(player);
	}
	
	public static final AutoGoldBar getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		protected static final AutoGoldBar _instance = new AutoGoldBar();
	}
}
