package Base.achievementEngine;

import com.l2jmega.gameserver.model.actor.instance.Player;

public class MinCMcount extends Condition
 {
  public MinCMcount(Object value)
  {
    super(value);
    setName("Clan Members Count");
  }

  @Override
 public boolean meetConditionRequirements(Player player)
  {
    if (getValue() == null) 
    {
      return false;
    }
    if (player.getClan() != null)
    {
      int val = Integer.parseInt(getValue().toString());

      if (player.getClan().getMembersCount() >= val)
        return true;
   }
    return false;
  }
 }
