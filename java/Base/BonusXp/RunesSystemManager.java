package Base.BonusXp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.concurrent.ScheduledFuture;

import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.commons.util.Mysql;

import com.l2jmega.L2DatabaseFactory;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.item.instance.ItemInstance;
import com.l2jmega.gameserver.model.item.type.EtcItemType;
import com.l2jmega.gameserver.network.serverpackets.ExShowScreenMessage;
import com.l2jmega.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS;


/**
 * 
 * @author l2fol
 *
 */
public class RunesSystemManager
{

	public void deleteRune(Player player, ItemInstance runeItem)
	{
	    player.deleteTempItem(runeItem.getObjectId());

	    Rune rune = RuneData.getInstance().getRune(runeItem.getItemId());
	    if (rune != null)
	    {
	        // Removes all instances of the rune with the same ID
	        boolean removed = player.getRunes().removeIf(r -> r.getId() == rune.getId());

	     // If this was the active rune, deactivate
	        if (player.getActiveRune() != null && player.getActiveRune().getId() == rune.getId())
	        {
	            player.setActiveRune(null);
	        }

	        if (removed)
	        {
	            player.sendMessage("Your rune '" + rune.getName() + "' expired.");
	            player.sendPacket(new ExShowScreenMessage("Your rune '" + rune.getName() + "' expired!", 5000, SMPOS.BOTTOM_RIGHT, false));
	        }
	    }
	}


	class ScheduleDeleteRune implements Runnable
	{
		Player player;
		ItemInstance runeItem;

		public ScheduleDeleteRune(Player player, ItemInstance runeItem)
		{
			this.player = player;
			this.runeItem = runeItem;
		}

		@Override
		public void run()
		{
			deleteRune(player, runeItem);
		}
	}


	public long getExpireTime(ItemInstance rune)
	{
		Connection con = null;
		PreparedStatement offline = null;
		ResultSet rs = null;
		long expire_time = 0L;
		long curr = System.currentTimeMillis();
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			offline = con.prepareStatement("SELECT * FROM runes WHERE obj_id =?");
			offline.setInt(1, rune.getObjectId());
			rs = offline.executeQuery();

			while (rs.next())
			{

				expire_time = rs.getLong("expire_time");

			}
			if (expire_time - curr <= 0)
			{
				return 0L;
			}
			con.close();
			return expire_time - curr;

		}
		catch (Exception e)
		{

			e.printStackTrace();
			return expire_time;
		}
		finally
		{
			Mysql.closeQuietly(con, offline, rs);
		}
	}

	public void addRune(Player player, ItemInstance runeItem)
	{
	    Rune rune = RuneData.getInstance().getRune(runeItem.getItemId());
	    if (rune != null)
	    {
	    	// Remove any previously active runes
	        if (player.hasRune())
	        {
	            player.getRunes().remove(player.getActiveRune());
	        }
	        
	        int totalMinutes = rune.getTime();
	        int hours = totalMinutes / 60;
	        int minutes = totalMinutes % 60;

	        String durationMessage = "";
	        if (hours > 0) {
	        	durationMessage += hours + " time" + (hours > 1 ? "s" : "");
	        }
	        if (minutes > 0) {
	        	if (!durationMessage.isEmpty()) {
	        		durationMessage += " e ";
	        	}
	        	durationMessage += minutes + " minutes" + (minutes > 1 ? "s" : "");
	        }

	     // Message in chat
	        player.sendMessage("[Rune System]: Activated '" + rune.getName() + "' by " + durationMessage + ".");

	     // Visual message
	        player.sendPacket(new ExShowScreenMessage("[Rune System]: '" + rune.getName() + "' activated by " + durationMessage + "!", 5000, SMPOS.BOTTOM_RIGHT, false));

	     // Add the new rune and set it to active
	        player.getRunes().add(rune);
	        player.setActiveRune(rune);

	     // Saves in the bank with expiration time in minutes
	        Mysql.set("REPLACE INTO runes (obj_id, expire_time) VALUES (?,?)", runeItem.getObjectId(), System.currentTimeMillis() + rune.getTime() * 60 * 1000);
	        
	     // Saves in the bank with expiration time in hours
	        //Mysql.set("REPLACE INTO runes (obj_id, expire_time) VALUES (?,?)", runeItem.getObjectId(), System.currentTimeMillis() + rune.getTime() * 60 * 60 * 1000);

	     // Schedule for automatic removal
	        ScheduledFuture<?> task = ThreadPool.schedule
	        (
	            new ScheduleDeleteRune(player, runeItem),
	            getExpireTime(runeItem)
	        );
	        player.getRuneTasks().add(task);
	    }
	}



	public void onPlayerEnter(Player player)
	{
		if (player.getRunes() == null)
		{
			player.setRunes(new ArrayList<>());
		}

		for (ItemInstance item : player.getInventory().getItems())
		{
			if (item.isEtcItem() && item.getItem().getItemType() == EtcItemType.RUNE)
			{
				Rune rune = RuneData.getInstance().getRune(item.getItemId());
				if (rune != null)
				{
					long remainingTime = getExpireTime(item);

					if (remainingTime <= 0)
					{
						// The rune has already expired while the player was offline
						player.sendMessage("Your rune '" + rune.getName() + "' expired while you were offline.");
						player.sendPacket(new ExShowScreenMessage("Your rune '" + rune.getName() + "' expired!", 5000, SMPOS.BOTTOM_RIGHT, false));
						
						// Remove a rune
						deleteRune(player, item);
					}
					else
					{
						// Still valid, activates normally
						player.getRunes().add(rune);
						player.setActiveRune(rune); // Activate the rune

						ScheduledFuture<?> task = ThreadPool.schedule(new ScheduleDeleteRune(player, item), remainingTime);
						player.getRuneTasks().add(task);

						// Send message with remaining time
						int totalMinutes = (int)(remainingTime / 1000 / 60);
						int hours = totalMinutes / 60;
						int minutes = totalMinutes % 60;

						String durationMsg = "";
						if (hours > 0)
							durationMsg += hours + " time" + (hours > 1 ? "s" : "");
						if (minutes > 0)
						{
							if (!durationMsg.isEmpty())
								durationMsg += " e ";
							durationMsg += minutes + " minutes" + (minutes > 1 ? "s" : "");
						}

						player.sendMessage("[Rune System]: '" + rune.getName() + "' is still active for " + durationMsg + ".");
						player.sendPacket(new ExShowScreenMessage("[Rune System]: '" + rune.getName() + "' active for more " + durationMsg + "!", 5000, SMPOS.BOTTOM_RIGHT, false));
					}
				}
			}
		}
	}




	public RunesSystemManager()
	{
		RuneData.getInstance();
	}

	private static class SingleTonHolder
	{
		protected static final RunesSystemManager _instance = new RunesSystemManager();
	}

	public static RunesSystemManager getInstance()
	{
		return SingleTonHolder._instance;
	}
}