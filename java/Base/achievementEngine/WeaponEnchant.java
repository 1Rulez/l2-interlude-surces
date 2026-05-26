package Base.achievementEngine;
 

import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.item.instance.ItemInstance;
import com.l2jmega.gameserver.model.itemcontainer.Inventory;
 
 /**
  * @author L2Fol
  * @version v1
  */
 public class WeaponEnchant extends Condition
 {
         public WeaponEnchant(Object value)
         {
                 super(value);
                 setName("Weapon Enchant");
         }
 
         @Override
         public boolean meetConditionRequirements(Player player)
         {
                 if (getValue() == null)
                 {
                         return false;
                 }
                 
                 int val = Integer.parseInt(getValue().toString());
                 
                 ItemInstance weapon = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_RHAND);
                 
                 if (weapon != null)
                 {
                         if (weapon.getEnchantLevel() >= val)
                         {
                                 return true;
                         }
                 }
                 return false;
         }
 }
