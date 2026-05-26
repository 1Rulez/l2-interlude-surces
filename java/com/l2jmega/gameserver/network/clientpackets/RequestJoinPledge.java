package com.l2jmega.gameserver.network.clientpackets;

import com.l2jmega.Config;
import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.commons.random.Rnd;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.pledge.Clan;
import com.l2jmega.gameserver.network.SystemMessageId;
import com.l2jmega.gameserver.network.serverpackets.AskJoinPledge;
import com.l2jmega.gameserver.network.serverpackets.JoinPledge;
import com.l2jmega.gameserver.network.serverpackets.PledgeShowInfoUpdate;
import com.l2jmega.gameserver.network.serverpackets.PledgeShowMemberListAdd;
import com.l2jmega.gameserver.network.serverpackets.PledgeShowMemberListAll;
import com.l2jmega.gameserver.network.serverpackets.SystemMessage;

import phantom.FakePlayer;
import phantom.FakePlayerConfig;

public final class RequestJoinPledge extends L2GameClientPacket
{
	private int _target;
	private int _pledgeType;
	
	@Override
	protected void readImpl()
	{
		_target = readD();
		_pledgeType = readD();
	}
	
	@Override
	protected void runImpl()
	{
		final Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		final Clan clan = activeChar.getClan();
		if (clan == null)
			return;
		
		final Player target = World.getInstance().getPlayer(_target);
		if (target == null)
		{
			activeChar.sendPacket(SystemMessageId.YOU_HAVE_INVITED_THE_WRONG_TARGET);
			return;
		}
		
		if (!clan.checkClanJoinCondition(activeChar, target, _pledgeType))
			return;
		
		// If the target has a pending request from a fake player, clear it so a real invite takes priority
		final Player existingPartner = target.getRequest().getPartner();
		if (existingPartner instanceof FakePlayer)
			target.getRequest().onRequestResponse();
		
		// Handle fake player auto-response (they have no client to respond)
		if (target instanceof FakePlayer)
		{
			if (!FakePlayerConfig.FAKE_ALLOW_CLAN_INVITE)
			{
				activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_IS_BUSY_TRY_LATER).addCharName(target));
				return;
			}
			
			final boolean accept = Rnd.get(100) < FakePlayerConfig.FAKE_CLAN_INVITE_ACCEPT_CHANCE;
			final int pledgeType = _pledgeType;
			
			// Delayed response to simulate real player thinking
			ThreadPool.schedule(() ->
			{
				if (accept && clan.checkClanJoinCondition(activeChar, target, pledgeType))
				{
					target.sendPacket(new JoinPledge(activeChar.getClanId()));
					target.setPledgeType(pledgeType);
					target.setPowerGrade(6);
					
					clan.addClanMember(target);
					target.setClanPrivileges(clan.getPriviledgesByRank(target.getPowerGrade()));
					
					clan.broadcastToOtherOnlineMembers(SystemMessage.getSystemMessage(SystemMessageId.S1_HAS_JOINED_CLAN).addCharName(target), target);
					clan.broadcastToOtherOnlineMembers(new PledgeShowMemberListAdd(target), target);
					clan.broadcastToOnlineMembers(new PledgeShowInfoUpdate(clan));
					target.sendPacket(new PledgeShowMemberListAll(clan, 0));
					target.setClanJoinExpiryTime(0);
					target.broadcastUserInfo();
					
					activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_HAS_JOINED_CLAN).addCharName(target));
				}
				else
				{
					activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_DID_NOT_RESPOND_TO_CLAN_INVITATION).addCharName(target));
				}
			}, Rnd.get(2000, 5000));
			return;
		}
		
		if (!activeChar.getRequest().setRequest(target, this))
			return;
		
		if (_pledgeType != 0 && Config.DISABLE_ROYAL)
		{
			activeChar.sendMessage("Function disabled.");
			return;
		}
		
		target.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_HAS_INVITED_YOU_TO_JOIN_THE_CLAN_S2).addCharName(activeChar).addString(clan.getName()));
		target.sendPacket(new AskJoinPledge(activeChar.getObjectId(), clan.getName()));
	}
	
	public int getPledgeType()
	{
		return _pledgeType;
	}
}