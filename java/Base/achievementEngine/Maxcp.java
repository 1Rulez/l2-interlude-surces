package Base.achievementEngine;

import com.l2jmega.gameserver.model.actor.instance.Player;

public class Maxcp extends Condition
{
  public Maxcp(Object value)
  {
    super(value);
    setName("Max CP");
  }

  @Override
public boolean meetConditionRequirements(Player player)
  {
    if (getValue() == null) 
    {
      return false;
    }
    int val = Integer.parseInt(getValue().toString());

    return player.getMaxCp() >= val;
  }
}
