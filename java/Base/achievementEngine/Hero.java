package Base.achievementEngine;

import com.l2jmega.gameserver.model.actor.instance.Player;

public class Hero extends Condition
{
  public Hero(Object value)
  {
    super(value);
    setName("Hero");
  }

  @Override
public boolean meetConditionRequirements(Player player)
  {
    if (getValue() == null) 
    {
      return false;
    }

    return player.isHero();
  }
}
