 package Base.achievementEngine;

import com.l2jmega.gameserver.model.actor.instance.Player;

public class Sub extends Condition
{
  public Sub(Object value)
  {
    super(value);
    setName("Subclass Count");
  }

  @Override
public boolean meetConditionRequirements(Player player)
  {
    if (getValue() == null) 
    {
      return false;
    }
    int val = Integer.parseInt(getValue().toString());

    return player.getSubClasses().size() >= val;
  }
}
