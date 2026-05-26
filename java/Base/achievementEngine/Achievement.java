 package Base.achievementEngine;
 
 import java.util.logging.Logger;

import com.l2jmega.gameserver.model.actor.instance.Player;

import java.util.ArrayList;
 import java.util.HashMap;

 /**
  * @author L2Fol
  * @version v1
  */
 public class Achievement
 {
         private int _id;
         private String _name;
         private String _reward;
         private String _description = "No Description!";
         private boolean _repeatable;
       
         private HashMap<Integer, Long> _rewardList;
         private ArrayList<Condition> _conditions;
         
         private static Logger _log = Logger.getLogger(Achievement.class.getName());
         
         public Achievement(int id, String name, String description, String reward, boolean repeatable, ArrayList<Condition> conditions)
         {
                 _rewardList = new HashMap<>();
                 _id = id;
                 _name = name;
                 _description = description;
                 _reward = reward;
                 _conditions = conditions;
                 _repeatable = repeatable;
 
                 createRewardList();
         }
         
         private void createRewardList()
         {
                 for (String s : _reward.split(";"))
                 {
                         if (s == null || s.isEmpty())
                                 continue;
                         
                        String[] split = s.split(",");
                        Integer item = 0;
                        Long count = 0L;
                         try
                         {
                                 item = Integer.valueOf(split[0]);
                                 count = Long.valueOf(split[1]);
                         }
                         catch(NumberFormatException nfe)
                         {
                                 _log.warning("[AchievementsEngine] Error: Wrong reward " + nfe);
                        }
                         _rewardList.put(item, count);
                 }
         }
         
         public boolean meetAchievementRequirements(Player player)
         {
                 for (Condition c: getConditions())
                 {
                         if (!c.meetConditionRequirements(player))
                        {
                                 return false;
                         }
                 }
                 return true;
         }
         
         public int getID()
         {
                 return _id;
         }
         
         public String getName()
         {
                 return _name;
         }
         
         public String getDescription()
         {
                 return _description;
         }
         
         public String getReward()
         {
                 return _reward;
         }
         
         public boolean isRepeatable()
         {
                 return _repeatable;
         }
         
         public HashMap<Integer, Long> getRewardList()
         {
                 return _rewardList;
         }
         
         public ArrayList<Condition> getConditions()
         {
                 return _conditions;
         }
 }