 package Base.achievementEngine;

import com.l2jmega.gameserver.model.actor.instance.Player;

public class ClanLeader extends Condition
   {
  public ClanLeader(Object value)
    {
    super(value);
    setName("Be Clan Leader");
   }

  @Override
  public boolean meetConditionRequirements(Player player)
  {
    if (getValue() == null)
    {
      return false;
    }

    return (player.getClan() != null) && 
      (player.isClanLeader());
    }
  }
