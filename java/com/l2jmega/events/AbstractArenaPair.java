package com.l2jmega.events;

import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.network.serverpackets.ExShowScreenMessage;
import com.l2jmega.gameserver.network.serverpackets.MagicSkillUse;
import com.l2jmega.gameserver.model.zone.ZoneId;

abstract class AbstractArenaPair
{
	private final Player[] members;
	
	protected AbstractArenaPair(Player... members)
	{
		this.members = members;
	}
	
	protected abstract void setModeFlag(Player player, boolean value);
	
	protected Player[] members()
	{
		return members;
	}
	
	protected void forEachOnline(MemberAction action)
	{
		for (Player member : members)
		{
			if (member != null && member.isOnline())
				action.accept(member);
		}
	}
	
	protected void forEach(MemberAction action)
	{
		for (Player member : members)
		{
			if (member != null)
				action.accept(member);
		}
	}
	
	protected void sendPacket(String message, int duration)
	{
		forEachOnline(member -> member.sendPacket(new ExShowScreenMessage(message, duration * 1000)));
	}
	
	protected void setInTournamentEvent(boolean value)
	{
		// When clearing (false), use forEach so inArenaEvent is always reset for all members.
		if (value)
			forEachOnline(member -> member.setInArenaEvent(true));
		else
			forEach(member -> member.setInArenaEvent(false));
	}
	
	protected void reviveAll()
	{
		forEachOnline(member ->
		{
			member.setCurrentHpMp(member.getMaxHp(), member.getMaxMp());
			member.setCurrentCp(member.getMaxCp());
			member.doRevive();
		});
	}
	
	protected void teleportAll(int x, int y, int z, int... yOffsets)
	{
		for (int i = 0; i < members.length; i++)
		{
			Player member = members[i];
			if (member != null && member.isOnline())
			{
				int yOffset = (yOffsets != null && i < yOffsets.length) ? yOffsets[i] : 0;
				member.teleToLocation(x, y + yOffset, z, 20);
			}
		}
	}
	
	protected void setImobilised(boolean value)
	{
		if (value)
			forEachOnline(member -> member.setIsInvul(true));
		else
			forEach(member -> member.setIsInvul(false));
	}
	
	protected void setArenaAttack(boolean value)
	{
		// When clearing (false), use forEach so arena attack is always reset for re-registration.
		if (value)
			forEachOnline(member -> member.setArenaAttack(true));
		else
			forEach(member -> member.setArenaAttack(false));
	}
	
	protected void saveTitle()
	{
		forEachOnline(member ->
		{
			member._originalTitleColorTournament = member.getAppearance().getTitleColor();
			member._originalTitleTournament = member.getTitle();
		});
	}
	
	protected void backTitle()
	{
		forEach(member ->
		{
			member.setTitle(member._originalTitleTournament);
			member.getAppearance().setTitleColor(member._originalTitleColorTournament);
			if (member.isOnline())
			{
				member.broadcastUserInfo();
				member.broadcastTitleInfo();
			}
		});
	}
	
	protected void eventTitle(String title, String color)
	{
		forEachOnline(member ->
		{
			member.setTitle(title);
			member.getAppearance().setTitleColor(Integer.decode("0x" + color));
			member.broadcastUserInfo();
			member.broadcastTitleInfo();
		});
	}
	
	protected void setTourAura(int value)
	{
		if (value != 0)
			forEachOnline(member -> member.setTeamTour(value));
		else
			forEach(member -> member.setTeamTour(0));
	}
	
	protected void setArenaProtectionAndMode(boolean value)
	{
		// When clearing (false), use forEach so flags are always reset for all members (e.g. FakePlayers
		// and edge cases where isOnline() may be false at duel end), allowing re-registration.
		if (value)
			forEachOnline(member ->
			{
				member.setArenaProtection(true);
				setModeFlag(member, true);
			});
		else
			forEach(member ->
			{
				member.setArenaProtection(false);
				setModeFlag(member, false);
			});
	}
	
	protected void removeMessage()
	{
		forEach(member ->
		{
			if (member.isOnline())
				member.sendMessage("Tournament: Your participation has been removed.");
			member.setArenaProtection(false);
			member.setArenaAttack(false);
			member.setInArenaEvent(false);
			member.setTeamTour(0);
			member.setIsInvul(false);
			setModeFlag(member, false);
		});
	}
	
	protected void broadcastVictoryEffect()
	{
		forEachOnline(member -> member.broadcastPacket(new MagicSkillUse(member, member, 2024, 1, 1, 0)));
	}
	
	protected boolean allMembersUnavailable()
	{
		for (Player member : members)
		{
			if (!isMemberUnavailable(member))
				return false;
		}
		return true;
	}
	
	protected boolean allMembersDeadOrNull()
	{
		for (Player member : members)
		{
			if (!isMemberDeadOrNull(member))
				return false;
		}
		return true;
	}
	
	protected boolean isMemberUnavailable(Player player)
	{
		return player == null || player.isDead() || !player.isOnline() || !player.isInsideZone(ZoneId.ARENA_EVENT) || !player.isArenaAttack();
	}
	
	protected boolean isMemberDeadOrNull(Player player)
	{
		return player == null || player.isDead();
	}
	
	protected static boolean isForbiddenSupportClass(Player player)
	{
		if (player == null)
			return false;
		
		switch (player.getClassId())
		{
			case SHILLIEN_ELDER:
			case SHILLIEN_SAINT:
			case BISHOP:
			case CARDINAL:
			case ELVEN_ELDER:
			case EVAS_SAINT:
				return true;
			default:
				return false;
		}
	}
	
	protected boolean hasForbiddenSupportClass()
	{
		for (Player member : members)
		{
			if (isForbiddenSupportClass(member))
				return true;
		}
		return false;
	}
	
	@FunctionalInterface
	protected interface MemberAction
	{
		void accept(Player player);
	}
}
