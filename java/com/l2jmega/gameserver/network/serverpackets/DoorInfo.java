package com.l2jmega.gameserver.network.serverpackets;

import com.l2jmega.gameserver.model.actor.instance.Door;
import com.l2jmega.gameserver.model.actor.instance.Player;

public class DoorInfo extends L2GameServerPacket
{
	private final Door _door;
	private final Player _activeChar;
	
	public DoorInfo(Door door, Player activeChar)
	{
		_door = door;
		_activeChar = activeChar;
	}
	
	@Override
	protected final void writeImpl()
	{
		final boolean isAttackable = _door.isAutoAttackable(_activeChar);
		final boolean showHpBar = _door.shouldShowHpBar();
		
		writeC(0x4c);
		writeD(_door.getObjectId());
		writeD(_door.getDoorId());
		writeD(isAttackable ? 1 : 0); // can attack
		writeD(1); // ??? (can target)
		writeD(_door.isOpened() ? 0 : 1);
		writeD(_door.getMaxHp());
		writeD((int) _door.getCurrentHp());
		writeD(showHpBar ? 1 : 0); // show HP
		writeD(_door.getDamage()); // damage stage
	}
}
