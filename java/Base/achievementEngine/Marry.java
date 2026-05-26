package Base.achievementEngine;

import com.l2jmega.gameserver.model.actor.instance.Player;

public class Marry extends Condition
 {
  public Marry(Object value)
  {
    super(value);
    setName("Married");
  }

  @Override
 public boolean meetConditionRequirements(Player player)
  {
    if (getValue() == null) 
    {
      return false;
    }

    if (player.getCoupleId() > 0)
    {
    	return true;
    }
	return false;
  }
}
