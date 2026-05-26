package Base.achievementEngine;

import com.l2jmega.gameserver.model.actor.instance.Player;

public class Vip extends Condition
{
  public Vip(Object value)
  {
    super(value);
    setName("Vip");
  }

  @Override
public boolean meetConditionRequirements(Player player)
  {
    if (getValue() == null) 
    {
      return false;
    }

    return player.isVip();
  }
}
