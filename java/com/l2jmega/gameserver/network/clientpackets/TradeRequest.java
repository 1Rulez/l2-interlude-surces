package com.l2jmega.gameserver.network.clientpackets;

import com.l2jmega.Config;
import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.commons.math.MathUtil;
import com.l2jmega.commons.random.Rnd;
import com.l2jmega.events.CTF;
import com.l2jmega.events.TvT;
import com.l2jmega.gameserver.model.BlockList;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.actor.Npc;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.network.SystemMessageId;
import com.l2jmega.gameserver.network.serverpackets.SendTradeRequest;
import com.l2jmega.gameserver.network.serverpackets.SystemMessage;

import phantom.FakePlayer;
import phantom.FakePlayerConfig;

public final class TradeRequest extends L2GameClientPacket
{
	private int _objectId;
	
	@Override
	protected void readImpl()
	{
		_objectId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		final Player player = getClient().getActiveChar();
		if (player == null)
			return;
		
		if (!player.getAccessLevel().allowTransaction())
		{
			player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return;
		}
		
		final Player target = World.getInstance().getPlayer(_objectId);
		if (target == null || !player.getKnownType(Player.class).contains(target) || target.equals(player))
		{
			player.sendPacket(SystemMessageId.TARGET_IS_INCORRECT);
			return;
		}
		
		if ((target._inEventTvT && TvT.is_started() || target._inEventCTF && CTF.is_started()) && !player.isGM())
		{
			player.sendMessage("You or your target cannot trade during Event.");
			return;
		}
		
		if ((target.isInCombat() || player.isInCombat()) && !player.isGM())
		{
			player.sendMessage("You or your target cannot trade during Combat mode.");
			return;
		}
		
		if (target.isInOlympiadMode() || player.isInOlympiadMode())
		{
			player.sendMessage("You or your target cannot trade during Olympiad.");
			return;
		}
		
		if (player.isSubmitingPin() || target.isSubmitingPin())
		{
			player.sendMessage("Unable to do any action while PIN is not submitted");
			return;
		}

		// Alt game - Karma punishment
		if (!Config.KARMA_PLAYER_CAN_TRADE && (player.getKarma() > 0 || target.getKarma() > 0))
		{
			player.sendMessage("You cannot trade in a chaotic state.");
			return;
		}
		
		if (player.isInStoreMode() || target.isInStoreMode())
		{
			player.sendPacket(SystemMessageId.CANNOT_TRADE_DISCARD_DROP_ITEM_WHILE_IN_SHOPMODE);
			return;
		}
		
		if (player.isProcessingTransaction())
		{
			player.sendPacket(SystemMessageId.ALREADY_TRADING);
			return;
		}
		
		if (target.isProcessingRequest() || target.isProcessingTransaction())
		{
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S1_IS_BUSY_TRY_LATER).addCharName(target);
			player.sendPacket(sm);
			return;
		}
		
		if (target.getTradeRefusal())
		{
			player.sendMessage("Your target is in trade refusal mode.");
			return;
		}
		
		if (BlockList.isBlocked(target, player))
		{
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.S1_HAS_ADDED_YOU_TO_IGNORE_LIST).addCharName(target);
			player.sendPacket(sm);
			return;
		}
		
		if (MathUtil.calculateDistance(player, target, true) > Npc.INTERACTION_DISTANCE)
		{
			player.sendPacket(SystemMessageId.TARGET_TOO_FAR);
			return;
		}
		
		if (target instanceof FakePlayer && (target.isSitting() || target.isInStoreMode()))
		{
			player.sendMessage(target.getName() + " is busy (sitting or trading).");
			return;
		}
		
		player.onTransactionRequest(target);
		target.sendPacket(new SendTradeRequest(player.getObjectId()));
		player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.REQUEST_S1_FOR_TRADE).addCharName(target));
		
		if (target instanceof FakePlayer)
		{
			int delay = Rnd.get(FakePlayerConfig.FAKE_TRADE_ACCEPT_DELAY_MIN, FakePlayerConfig.FAKE_TRADE_ACCEPT_DELAY_MAX);
			final Player tradeRequester = player;
			final Player fakeTarget = target;
			ThreadPool.schedule(() ->
			{
				try
				{
					Player requester = fakeTarget.getActiveRequester();
					if (requester == null || requester != tradeRequester || tradeRequester.isRequestExpired())
					{
						fakeTarget.setActiveRequester(null);
						tradeRequester.onTransactionResponse();
						return;
					}
					if (Rnd.get(100) < FakePlayerConfig.FAKE_TRADE_ACCEPT_CHANCE)
						fakeTarget.startTrade(tradeRequester);
					else
						tradeRequester.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_DENIED_TRADE_REQUEST).addCharName(fakeTarget));
					
					fakeTarget.setActiveRequester(null);
					tradeRequester.onTransactionResponse();
				}
				catch (Exception e)
				{
					fakeTarget.setActiveRequester(null);
					tradeRequester.onTransactionResponse();
				}
			}, delay);
		}
	}
}