package Base.achievementEngine;

import com.l2jmega.gameserver.model.actor.instance.Player;

public class CursedWeapon extends Condition
{
  public CursedWeapon(Object value)
  {
    super(value);
    setName("Cursed Weapon");
  }

  @Override
public boolean meetConditionRequirements(Player player)
  {
    if (getValue() == null) {
      return false;
    }

    return player.isCursedWeaponEquipped();
  }
}
