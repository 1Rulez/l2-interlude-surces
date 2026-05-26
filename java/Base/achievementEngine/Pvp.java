 package Base.achievementEngine;

import com.l2jmega.gameserver.model.actor.instance.Player;

public class Pvp extends Condition
{
  public Pvp(Object value)
  {
    super(value);
    setName("PvP Count");
  }

  @Override
public boolean meetConditionRequirements(Player player)
  {
    if (getValue() == null)
    {
      return false;
    }
    int val = Integer.parseInt(getValue().toString());

    return player.getPvpKills() >= val;
  }
}
