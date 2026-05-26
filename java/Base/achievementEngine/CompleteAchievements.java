package Base.achievementEngine;

import com.l2jmega.gameserver.model.actor.instance.Player;


public class CompleteAchievements extends Condition
{
  public CompleteAchievements(Object value)
  {
    super(value);
    setName("Complete Achievements");
  }

  @Override
public boolean meetConditionRequirements(Player player)
  {
    if (getValue() == null) {
      return false;
    }
    int val = Integer.parseInt(getValue().toString());

    return player.getCompletedAchievements().size() >= val;
  }
}
