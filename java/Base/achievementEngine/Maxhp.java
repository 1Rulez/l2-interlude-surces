package Base.achievementEngine;

import com.l2jmega.gameserver.model.actor.instance.Player;

public class Maxhp extends Condition
{
  public Maxhp(Object value)
  {
    super(value);
    setName("Max HP");
  }

  @Override
public boolean meetConditionRequirements(Player player)
  {
    if (getValue() == null) {
      return false;
    }
    int val = Integer.parseInt(getValue().toString());

    return player.getMaxHp() >= val;
  }
}
