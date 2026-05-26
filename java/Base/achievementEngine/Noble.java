package Base.achievementEngine;

import com.l2jmega.gameserver.model.actor.instance.Player;

public class Noble extends Condition
{
  public Noble(Object value)
  {
    super(value);
    setName("Noble");
  }

  @Override
public boolean meetConditionRequirements(Player player)
  {
    if (getValue() == null) {
      return false;
    }

    return player.isNoble();
  }
}
