package com.l2jmega.gameserver.handler.itemhandlers.custom;

import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.Config;
import com.l2jmega.gameserver.data.xml.DressMeData;
import com.l2jmega.gameserver.handler.IItemHandler;
import com.l2jmega.gameserver.model.DressMe;
import com.l2jmega.gameserver.model.WorldObject;
import com.l2jmega.gameserver.model.actor.Playable;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.item.instance.ItemInstance;
import com.l2jmega.gameserver.network.serverpackets.MagicSkillUse;
import com.l2jmega.gameserver.network.serverpackets.SetupGauge;
import com.l2jmega.gameserver.network.serverpackets.SetupGauge.GaugeColor;
import com.l2jmega.gameserver.util.Broadcast;

public class Skins implements IItemHandler
{
	@Override
	public void useItem(Playable playable, ItemInstance item, boolean forceUse)
	{
		if (!(playable instanceof Player))
			return;
		
		final Player player = (Player) playable;
		
		final DressMe dress = DressMeData.getInstance().getItemId(item.getItemId());
		if (dress == null)
			return;
		
		// Toggle off when the player clicks the same skin item again.
		if (player.getDress() != null && player.getDress().getItemId() == item.getItemId())
		{
			player.setDress(null);
			player.broadcastUserInfo();
			player.sendMessage("Skin removed!");
			return;
		}
		
		if (Config.SKIN_ENABLE_ANIMATIONS)
		{
			ThreadPool.schedule(new Runnable()
			{
				@Override
				public void run()
				{
					playable.setIsParalyzed(false);
				}
			}, Config.SKIN_ANIMATION_TIME);
			
			final WorldObject oldTarget = playable.getTarget();
			playable.setTarget(playable);
			
			Broadcast.toSelfAndKnownPlayers(playable, new MagicSkillUse(playable, Config.SKIN_SKILL_ID, 1, Config.SKIN_ANIMATION_TIME, 0));
			playable.setTarget(oldTarget);
			
			playable.sendPacket(new SetupGauge(GaugeColor.BLUE, Config.SKIN_ANIMATION_TIME));
			playable.setIsParalyzed(true);
		}
		
		player.setDress(dress);
		player.broadcastUserInfo();
		
		if (Config.SKIN_ENABLE_MESSAGES)
		{
			player.sendMessage(Config.SKIN_TRANSFORMATION_MESSAGE);
		}
	}
}
