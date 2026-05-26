package Base.achievementEngine;

import com.l2jmega.gameserver.model.actor.instance.Player;


public class Pk extends Condition
{
  public Pk(Object value)
  {
    super(value);
    setName("PK Count");
  }

  @Override
public boolean meetConditionRequirements(Player player)
  {
    if (getValue() == null) 
    {
      return false;
    }
    int val = Integer.parseInt(getValue().toString());

    return player.getPkKills() >= val;
  }
}
