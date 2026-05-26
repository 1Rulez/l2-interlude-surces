package Base.achievementEngine;

import com.l2jmega.gameserver.model.actor.instance.Player;

public class Maxmp extends Condition
{
  public Maxmp(Object value)
  {
    super(value);
    setName("Max MP");
  }

  @Override
public boolean meetConditionRequirements(Player player)
  {
    if (getValue() == null) {
      return false;
    }
    int val = Integer.parseInt(getValue().toString());

    return player.getMaxMp() >= val;
  }
}
