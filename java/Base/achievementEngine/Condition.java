 package Base.achievementEngine;

import com.l2jmega.gameserver.model.actor.instance.Player;

/**
  * @author L2FOl
  * @version v1
  */
 public abstract class Condition
 {
         private Object _value;
         private String _name;
         
         public Condition(Object value)
         {
                 _value = value;
         }
         
         public abstract boolean meetConditionRequirements(Player paramplayer);

         public Object getValue() {
           return _value;
         }

         public void setName(String s)
         {
           _name = s;
         }

         public String getName()
         {
           return _name;
         }
         
         public String getDisplayValue() {
        	    // By default, just show the value
        	    return getValue() != null ? getValue().toString() : "None";
        	}

       }

