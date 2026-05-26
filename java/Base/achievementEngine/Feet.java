package Base.achievementEngine;

import com.l2jmega.gameserver.model.actor.instance.Player;


import com.l2jmega.gameserver.model.item.instance.ItemInstance;
import com.l2jmega.gameserver.model.itemcontainer.Inventory;


/**
 * @author L2Fol
 * @version v1
 */
public class Feet extends Condition
{
        public Feet(Object value)
        {
                super(value);
                setName("Boots");
        }

        @Override
        public boolean meetConditionRequirements(Player player)
        {
                if (getValue() == null)
                {
                        return false;
                }
                
                int val = Integer.parseInt(getValue().toString());
                
                ItemInstance armor = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_FEET);
                
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
