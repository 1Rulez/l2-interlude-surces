package Base.achievementEngine;

import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.instancemanager.RaidBossPointsManager;

import java.util.Map;

public class RaidKill extends Condition {

    public RaidKill(Object value) {
        super(value);
        setName("Raid Kill");
    }

    @Override
    public boolean meetConditionRequirements(Player player) {
        if (getValue() == null) return false;

        int bossId = Integer.parseInt(getValue().toString());
        Map<Integer, Integer> raidPoints = RaidBossPointsManager.getInstance().getList(player);

        return raidPoints != null && raidPoints.getOrDefault(bossId, 0) > 0;
    }

    @Override
    public String getDisplayValue() 
    {
        return "???"; // Hide the boss from public view
    }


    /**
     * Keep for internal use only
     * @return 
     */
    public String getBossName() 
    {
        return "Hidden";
    }
}
