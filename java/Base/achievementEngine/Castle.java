 package Base.achievementEngine;

import com.l2jmega.gameserver.model.actor.instance.Player;

public class Castle extends Condition
{
  public Castle(Object value)
  {
    super(value);
    setName("Have Castle");
  }

  @Override
 public boolean meetConditionRequirements(Player player)
  {
    if (getValue() == null) {
      return false;
    }
    if (player.getClan() != null)
    {
      if ((player.isCastleLord(5)) || (player.isCastleLord(3)) || (player.isCastleLord(7)))
        return true;
    }
    return false;
  }
}
