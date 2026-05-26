package Base.achievementEngine;

import com.l2jmega.gameserver.model.actor.instance.Player;

public class Karma extends Condition
{
  public Karma(Object value)
  {
    super(value);
    setName("Karma Count");
  }

 @Override
public boolean meetConditionRequirements(Player player)
  {
    if (getValue() == null) {
      return false;
    }
    int val = Integer.parseInt(getValue().toString());

    return player.getKarma() >= val;
  }
}
