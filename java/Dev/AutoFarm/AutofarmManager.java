package Dev.AutoFarm;

import com.l2jmega.Config;
import com.l2jmega.gameserver.handler.voicedcommandhandlers.VoicedMenu;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.zone.ZoneId;
import com.l2jmega.gameserver.network.serverpackets.ExShowScreenMessage;
import com.l2jmega.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import com.l2jmega.commons.concurrent.ThreadPool;

public enum AutofarmManager 
{
    INSTANCE;
    
    private final Long iterationSpeedMs = 450L;
    
    private final ConcurrentHashMap<Integer, AutofarmPlayerRoutine> activeFarmers = new ConcurrentHashMap<>();
    private ScheduledFuture<?> onUpdateTask = ThreadPool.scheduleAtFixedRate(onUpdate(), 1000, iterationSpeedMs);
    
    private Runnable onUpdate() 
    {
        return () -> activeFarmers.values().forEach(AutofarmPlayerRoutine::executeRoutine);
    }

    public void startFarm(Player player)
    {
    	if(Config.ENABLE_COMMAND_VIP_AUTOFARM)
    	{
    		if(!player.isVip())
    		{
    			VoicedMenu.showMenuHtml(player);
    			player.sendMessage("You are not VIP member.");
    			return;
    		}
    	}
    	
    	if(Config.NO_USE_FARM_IN_PEACE_ZONE)
    	{
    		if(player.isInsideZone(ZoneId.PEACE))
    		{
    			VoicedMenu.showMenuHtml(player);
    			player.sendMessage("No Use Auto farm in Peace Zone.");
    			return;
    		}
    	}
    	
    	if (isAutofarming(player)) 
        {
            player.sendMessage("You are already autofarming.");
            return;
        }
        
        activeFarmers.put(player.getObjectId(), new AutofarmPlayerRoutine(player));
        player.sendMessage("Autofarming Activated.");
		player.sendPacket(new ExShowScreenMessage("Auto Farming Actived...", 5*1000, SMPOS.BOTTOM_RIGHT, false));
		//player.doCast(SkillTable.getInstance().getInfo(9501, 1));
    }
    
    public void stopFarm(Player player)
    {
        if (!isAutofarming(player)) 
        {
            player.sendMessage("You are not autofarming.");
            return;
        }

        activeFarmers.remove(player.getObjectId());
        player.sendMessage("Autofarming Deactivated.");
        player.sendPacket(new ExShowScreenMessage("Auto Farming Deactivated...", 5*1000, SMPOS.BOTTOM_RIGHT, false));
        //player.stopSkillEffects(9501);
    }

	public synchronized void stopFarmTask()
	{
		if (onUpdateTask != null)
		{
			onUpdateTask.cancel(false);
			onUpdateTask = null;
		}
	}
	
    public void toggleFarm(Player player)
    {
        if (isAutofarming(player))
        {
            stopFarm(player);
            return;
        }
        if(Config.NO_USE_FARM_IN_PEACE_ZONE)
    	{
    		if(player.isInsideZone(ZoneId.PEACE))
    		{
    			VoicedMenu.showMenuHtml(player);
    			player.sendMessage("No Use Auto farm in Peace Zone.");
    			return;
    		}
    	}
        startFarm(player);
    }
    
    public Boolean isAutofarming(Player player)
    {
        return activeFarmers.containsKey(player.getObjectId());
    }
    
    public void onPlayerLogout(Player player)
    {
        stopFarm(player);
    }

    public void onDeath(Player player) 
    {
        if (isAutofarming(player)) 
            activeFarmers.remove(player.getObjectId());
    }
}