package Base.achievementEngine;
 
 import com.l2jmega.L2DatabaseFactory;
import com.l2jmega.gameserver.model.actor.instance.Player;

import java.io.File;
 
 import java.sql.Connection;
 import java.sql.PreparedStatement;
 import java.sql.ResultSet;
 import java.sql.SQLException;
 import java.sql.Statement;
 import java.util.Map;
 import java.util.logging.Logger;
 
 import javax.xml.parsers.DocumentBuilderFactory;
 
 import java.util.ArrayList;
import java.util.HashMap;
 
 import org.w3c.dom.Document;
 import org.w3c.dom.NamedNodeMap;
 import org.w3c.dom.Node;
 
 
 /**
  * @author L2Fol
  * @version v1
  */
 public class AchievementsManager
 {
         private Map<Integer, Achievement> _achievementList = new HashMap<>();
         
         
         private static Logger _log = Logger.getLogger(AchievementsManager.class.getName());
         
         public AchievementsManager()
         {
                ensureAchievementsTable();
                loadAchievements();
         }
         
         private void loadAchievements()
         {
                 DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                 factory.setValidating(false);
                factory.setIgnoringComments(true);
                 
                 File file = new File("./data/xml/achievements.xml");
                 if (!file.exists())
                 {
                         _log.warning("[AchievementsEngine] Error: achievements xml file does not exist, check directory!");
                 }
                 try
                 {
                         Document doc = factory.newDocumentBuilder().parse(file);
                         
                         for (Node list = doc.getFirstChild(); list != null; list = list.getNextSibling())
                         {
                                        if("list".equalsIgnoreCase(list.getNodeName()))
                                 {
                                         for (Node achievement = list.getFirstChild(); achievement != null; achievement = achievement.getNextSibling())
                                        {
                                                                if("achievement".equalsIgnoreCase(achievement.getNodeName()))
                                                 {
                                                         int id = checkInt(achievement, "id");
                                                         
                                                         String name = String.valueOf(achievement.getAttributes().getNamedItem("name").getNodeValue());
                                                         String description = String.valueOf(achievement.getAttributes().getNamedItem("description").getNodeValue());
                                                         String reward = String.valueOf(achievement.getAttributes().getNamedItem("reward").getNodeValue());
                                                         boolean repeat = checkBoolean(achievement, "repeatable");
                                                         
                                                         ArrayList<Condition> conditions = conditionList(achievement.getAttributes());
                                                         
                                                         _achievementList.put(id, new Achievement(id, name, description, reward, repeat, conditions));
                                                         alterTable(id);
                                                 }
                                         }
                                 }
                         }
                         _log.info("--------------------------------------------------------------------");
                         _log.info("                                                                    ");
                         _log.info("[AchievementsEngine] Successfully loaded: " + getAchievementList().size() + " achievements from xml!");
                         _log.info("                      AchievementsEngine                            ");
                         _log.info("                        by L2Fol                                    ");
                         _log.info("                                                                    ");
                         _log.info("--------------------------------------------------------------------");
                 }
                 catch (Exception e)
                 {
                         _log.warning("[AchievementsEngine] Error: " + e);
                         e.printStackTrace();
                 }
         }
         
         public void rewardForAchievement(int achievementID, Player player) 
         {
        	    Achievement achievement = _achievementList.get(achievementID);
        	    if (achievement == null) return;

        	    for (int itemId : achievement.getRewardList().keySet()) 
        	    {
        	        long itemCount = achievement.getRewardList().get(itemId);
        	        String achievementName = achievement.getName();

        	        for (Condition cond : achievement.getConditions()) 
        	        {
        	            // Show display value (masked if RaidKill)
        	            player.sendMessage("Condition: " + cond.getDisplayValue());
        	        }

        	        player.addItem(achievementName, itemId, itemCount, player, true);
        	    }
        	}
         
         private static boolean checkBoolean(Node d, String nodename)
         {
                 boolean b = false;
                 
                 try
                 {
                         b = Boolean.valueOf(d.getAttributes().getNamedItem(nodename).getNodeValue());
                 }
                 catch (Exception e)
                 {
                         
                 }
                 return b;
         }
         
         private static int checkInt(Node d, String nodename)
         {
                 int i = 0;
                 
                 try
                 {
                         i = Integer.valueOf(d.getAttributes().getNamedItem(nodename).getNodeValue());
                 }
                 catch (Exception e)
                 {
                         
                 }
                 return i;
         }
         
         /**
          * Alter table, catch exception if already exist.
          * @param fieldID
          */
        
		private static void alterTable(int fieldID)
        {
                try (Connection con = L2DatabaseFactory.getInstance().getConnection())
                {
                        if (!columnExists(con, "achievements", "a" + fieldID))
                        {
                                try (Statement st = con.createStatement())
                                {
                                        st.executeUpdate("ALTER TABLE achievements ADD COLUMN a" + fieldID + " INT DEFAULT 0");
                                }
                        }
                }
                catch (SQLException e)
                {
                        // Silent: schema will be validated by Player code on demand
                }
        }

         /**
          * Ensure base achievements table exists with primary key.
          */
         private static void ensureAchievementsTable()
         {
                 try (Connection con = L2DatabaseFactory.getInstance().getConnection())
                 {
                         if (!tableExists(con, "achievements"))
                         {
                                 try (Statement st = con.createStatement())
                                 {
                                         st.executeUpdate("CREATE TABLE achievements (owner_id INT NOT NULL, PRIMARY KEY (owner_id))");
                                 }
                         }
                 }
                 catch (SQLException e)
                 {
                         // Silent to avoid noisy logs; Player code will handle missing table
                 }
         }

         private static boolean tableExists(Connection con, String tableName) throws SQLException
         {
                 try (PreparedStatement ps = con.prepareStatement(
                         "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?"))
                 {
                         ps.setString(1, tableName);
                         try (ResultSet rs = ps.executeQuery())
                         {
                                 return rs.next() && rs.getInt(1) > 0;
                         }
                 }
         }

         private static boolean columnExists(Connection con, String tableName, String columnName) throws SQLException
         {
                 try (PreparedStatement ps = con.prepareStatement(
                         "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?"))
                 {
                         ps.setString(1, tableName);
                         ps.setString(2, columnName);
                         try (ResultSet rs = ps.executeQuery())
                         {
                                 return rs.next() && rs.getInt(1) > 0;
                         }
                 }
         }
         
         public ArrayList<Condition> conditionList(NamedNodeMap attributesList)
         {
        	 ArrayList<Condition> conditions = new ArrayList<>();
                 
            for (int j = 0; j < attributesList.getLength(); j++)
             {
                 addToConditionList(attributesList.item(j).getNodeName(), attributesList.item(j).getNodeValue(), conditions);
             }
                 
                 return conditions;
         }
         
         public Map<Integer, Achievement> getAchievementList()
         {
                 return _achievementList;
         }
         
         public static AchievementsManager getInstance()
         {
                 return SingletonHolder._instance;
         }
                 
         private static class SingletonHolder
         {
                 protected static final AchievementsManager _instance = new AchievementsManager();
        }
         
         private static void addToConditionList(String nodeName, Object value, ArrayList<Condition> conditions)
         {
              if (nodeName.equals("minLevel"))
                 conditions.add(new Level(value));
              
         else if (nodeName.equals("minPvPCount"))
                conditions.add(new Pvp(value));
              
         else if (nodeName.equals("minPkCount"))
                 conditions.add(new Pk(value));
              
         else if (nodeName.equals("minClanLevel"))
                 conditions.add(new ClanLevel(value));
              
         else if (nodeName.equals("mustBeHero"))
                 conditions.add(new Hero(value));
              
         else if (nodeName.equals("mustBeNoble"))
                 conditions.add(new Noble(value));
              
         else if (nodeName.equals("mustBeClanLeader"))
                 conditions.add(new ClanLeader(value));
              
         else if (nodeName.equals("minWeaponEnchant"))
                 conditions.add(new WeaponEnchant(value));
              
         else if (nodeName.equals("minKarmaCount"))
                 conditions.add(new Karma(value));
              
         else if (nodeName.equals("minAdenaCount"))
                 conditions.add(new Adena(value));
              
         else if (nodeName.equals("minClanMembersCount"))
                 conditions.add(new MinCMcount(value));

         else if (nodeName.equals("maxHP"))
                 conditions.add(new Maxhp(value));
              
         else if (nodeName.equals("maxMP"))
                 conditions.add(new Maxmp(value));
              
         else if (nodeName.equals("maxCP"))
                 conditions.add(new Maxcp(value));
              
         else if (nodeName.equals("mustBeMarried"))
                 conditions.add(new Marry(value));
              
         else if (nodeName.equals("itemAmmount"))
                 conditions.add(new ItemsCount(value));
              
         else if (nodeName.equals("crpAmmount"))
                  conditions.add(new Crp(value));
              
         else if (nodeName.equals("lordOfCastle"))
                  conditions.add(new Castle(value));
              
         else if (nodeName.equals("mustBeMageClass"))
                  conditions.add(new Mageclass(value));
              
         else if (nodeName.equals("mustBeVip"))
                  conditions.add(new Noble(value));
              
         else if (nodeName.equals("raidToKill"))
                  conditions.add(new RaidKill(value));
         
         else if (nodeName.equals("CompleteAchievements"))
                  conditions.add(new CompleteAchievements(value));
              
         else if (nodeName.equals("minSubclassCount"))
                  conditions.add(new Sub(value));
              
         else if (nodeName.equals("minSkillEnchant"))
                  conditions.add(new SkillEnchant(value));
              
         else if (nodeName.equals("minOnlineTime"))
                  conditions.add(new OnlineTime(value));
              
         else if (nodeName.equals("Cursedweapon"))
                  conditions.add(new CursedWeapon(value));
              
         else if (nodeName.equals("minHeadEnchant"))
                  conditions.add(new Head(value));
              
         else if (nodeName.equals("minChestEnchant"))
                  conditions.add(new Chest(value));
              
         else if (nodeName.equals("minFeetEnchant"))
                  conditions.add(new Feet(value));
              
         else if (nodeName.equals("minLegsEnchant"))
                  conditions.add(new Legs(value));
              
         else if (nodeName.equals("minGlovestEnchant"))
                  conditions.add(new Gloves(value));
            
              
      }
  }
 
 
