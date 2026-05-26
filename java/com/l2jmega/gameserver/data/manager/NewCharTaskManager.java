package com.l2jmega.gameserver.data.manager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.l2jmega.commons.concurrent.ThreadPool;

import com.l2jmega.gameserver.model.actor.Creature;
import com.l2jmega.gameserver.model.actor.instance.Player;

/**
 * @author L2Fol
 */
public final class NewCharTaskManager implements Runnable
{
	private final Map<Player, Long> players = new ConcurrentHashMap<>();

	protected NewCharTaskManager()
	{
		// Run task each 10 second.
		ThreadPool.scheduleAtFixedRate(this, 1000, 1000);
	}

	public final void add(Player player)
	{
		players.put(player, System.currentTimeMillis());
	}

	public final void remove(Creature player)
	{
		players.remove(player);
	}

	@Override
	public final void run()
	{
		if (players.isEmpty())
			return;

		for (Map.Entry<Player, Long> entry : players.entrySet())
		{
			final Player player = entry.getKey();

			if (player.getMemos().getLong("newEndTime") < System.currentTimeMillis())
			{
				Player.removeNewChar(player);
				remove(player);
			}
		}
	}

	public static final NewCharTaskManager getInstance()
	{
		return SingletonHolder.instance;
	}

	private static class SingletonHolder
	{
		protected static final NewCharTaskManager instance = new NewCharTaskManager();
	}
}
