 package Base.achievementEngine;

 import com.l2jmega.gameserver.model.actor.instance.Player;


 public class OnlineTime extends Condition
 {
 public OnlineTime(Object value)
  {
   super(value);
    setName("Online Time");
  }

 @Override
 public boolean meetConditionRequirements(Player player)
 {
    if (getValue() == null) {
      return false;
    }
    int val = Integer.parseInt(getValue().toString());
  
    return player.getOnlineTime() >= val;
  }
}
