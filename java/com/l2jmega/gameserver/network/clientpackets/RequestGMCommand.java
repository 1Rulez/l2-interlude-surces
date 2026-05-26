package com.l2jmega.gameserver.network.clientpackets;

import com.l2jmega.gameserver.data.sql.ClanTable;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.pledge.Clan;
import com.l2jmega.gameserver.network.serverpackets.GMViewCharacterInfo;
import com.l2jmega.gameserver.network.serverpackets.GMViewHennaInfo;
import com.l2jmega.gameserver.network.serverpackets.GMViewItemList;
import com.l2jmega.gameserver.network.serverpackets.GMViewPledgeInfo;
import com.l2jmega.gameserver.network.serverpackets.GMViewSkillInfo;
import com.l2jmega.gameserver.network.serverpackets.GMViewWarehouseWithdrawList;
import com.l2jmega.gameserver.network.serverpackets.L2GameServerPacket;

public final class RequestGMCommand extends L2GameClientPacket
{
	private String _targetName;
	private int _command;
	
	@Override
	protected void readImpl()
	{
		_targetName = readS();
		_command = readD();
	}
	
	@Override
	protected void runImpl()
	{
		final Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		// prevent non gm or low level GMs from viewing player stuff
		if (!activeChar.isGM() || !activeChar.getAccessLevel().allowAltG())
			return;
		
		Player target = World.getInstance().getPlayer(_targetName);
		if (target == null && _targetName != null && !_targetName.isEmpty())
		{
			for (Player p : World.getInstance().getPlayers())
			{
				if (p != null && p.getName().equalsIgnoreCase(_targetName))
				{
					target = p;
					break;
				}
			}
		}
		final Clan clan = ClanTable.getInstance().getClanByName(_targetName);
		
		if (target == null && (clan == null || _command != 6))
			return;
		
		switch (_command)
		{
			case 1: // target status
				sendPacket(new GMViewCharacterInfo(target));
				sendPacket(new GMViewHennaInfo(target));
				break;
			
			case 2: // target clan
				if (target != null && target.getClan() != null)
					sendPacket(new GMViewPledgeInfo(target.getClan(), target));
				break;
			
			case 3: // target skills
				sendPacket(new GMViewSkillInfo(target));
				break;
			
			case 4: // target quests
				sendQuestViewPacket(target);
				break;
			
			case 5: // target inventory
				sendPacket(new GMViewItemList(target));
				sendPacket(new GMViewHennaInfo(target));
				break;
			
			case 6: // player or clan warehouse
				if (target != null)
					sendPacket(new GMViewWarehouseWithdrawList(target));
				else
					sendPacket(new GMViewWarehouseWithdrawList(clan));
				break;
		}
	}

	private void sendQuestViewPacket(Player target)
	{
		try
		{
			final Class<?> packetClass = Class.forName("com.l2jmega.gameserver.network.serverpackets.GMViewQuestList");
			final Object packet = packetClass.getConstructor(Player.class).newInstance(target);
			if (packet instanceof L2GameServerPacket)
				sendPacket((L2GameServerPacket) packet);
		}
		catch (Exception e)
		{
			// Keep other GM views usable even if the quest packet class is unavailable at runtime.
		}
	}
}
