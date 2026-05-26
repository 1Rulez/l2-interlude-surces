package Base.achievementEngine;


import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.L2Skill;


public class SkillEnchant extends Condition
{
  public SkillEnchant(Object value)
  {
    super(value);
    setName("Skill Enchant");
  }

  @Override
public boolean meetConditionRequirements(Player player)
  {
    if (getValue() == null) 
    {
      return false;
    }
    int val = Integer.parseInt(getValue().toString());

    for (L2Skill s : player.getSkills().values())
    {
      String lvl = String.valueOf(s.getLevel());
      if (lvl.length() <= 2)
        continue;
      int sklvl = Integer.parseInt(lvl.substring(1));
      if (sklvl >= val) {
        return true;
      }
    }

    return false;
  }
}
