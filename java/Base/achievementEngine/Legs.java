package Base.achievementEngine;
 

import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.item.instance.ItemInstance;
import com.l2jmega.gameserver.model.itemcontainer.Inventory;
 
 /**
  * @author L2Fol
  * @version v1
  */
 public class Legs extends Condition
 {
         public Legs(Object value)
         {
                 super(value);
                 setName("Legs");
         }
 
         @Override
         public boolean meetConditionRequirements(Player player)
         {
                 if (getValue() == null)
                 {
                         return false;
                 }
                 
                 int val = Integer.parseInt(getValue().toString());
                 
                 ItemInstance armor = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_LEGS);
                 
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
