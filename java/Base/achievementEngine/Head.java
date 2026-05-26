package Base.achievementEngine;
 

import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.item.instance.ItemInstance;
import com.l2jmega.gameserver.model.itemcontainer.Inventory;
 
  /**
  * @author L2Fol
  * @version v1
  */
 public class Head extends Condition
 {
         public Head(Object value)
         {
                 super(value);
                 setName("Helmets");
         }
 
         @Override
         public boolean meetConditionRequirements(Player player)
         {
                 if (getValue() == null)
                 {
                         return false;
                 }
                 
                 int val = Integer.parseInt(getValue().toString());
                 
                 ItemInstance armor = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_HEAD);
                 
                 if (armor != null)
                 {
                         if (armor.getEnchantLevel() >= val)
                         {
                                 return true;
                         }
                 }
                 return false;
         }
 }
