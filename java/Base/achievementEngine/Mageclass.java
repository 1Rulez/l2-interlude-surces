package Base.achievementEngine;

import com.l2jmega.gameserver.model.actor.instance.Player;

public class Mageclass extends Condition
{
  public Mageclass(Object value)
  {
    super(value);
    setName("Mage Class");
  }

  @Override
public boolean meetConditionRequirements(Player player)
  {
    if (getValue() == null) {
      return false;
    }

    return player.isMageClass();
  }
}
