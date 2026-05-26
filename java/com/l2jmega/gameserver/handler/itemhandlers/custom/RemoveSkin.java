package com.l2jmega.gameserver.handler.itemhandlers.custom;

import com.l2jmega.gameserver.handler.IItemHandler;
import com.l2jmega.gameserver.model.actor.Playable;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.item.instance.ItemInstance;

public class RemoveSkin implements IItemHandler
{
	@Override
	public void useItem(Playable playable, ItemInstance item, boolean forceUse)
	{
		final Player player = (Player) playable;
		
		if (!(playable instanceof Player))
			return;
		
		if (player.getDress() == null)
		{
			player.sendMessage("You are not wearing any skin.");
			return;
		}
		
		player.setDress(null);
		player.broadcastUserInfo();
		player.sendMessage("Skin removed successfully!");
	}
}
