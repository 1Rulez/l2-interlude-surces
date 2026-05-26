package Base.achievementEngine;

import com.l2jmega.gameserver.model.actor.instance.Player;

public class Level extends Condition
 {
         public Level(Object value)
         {
                super(value);
                setName("Level");
         }
         
         @Override
         public boolean meetConditionRequirements(Player player)
         {
           if (getValue() == null) 
           {
             return false;
           }

           int val = Integer.parseInt(getValue().toString());

           return player.getLevel() >= val;
         }
       }
