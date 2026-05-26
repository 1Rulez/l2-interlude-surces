package Base.DollSystem;

import Base.Data.XMLDocument;

import com.l2jmega.gameserver.data.SkillTable;
import com.l2jmega.gameserver.model.L2Skill;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.item.instance.ItemInstance;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
/**
 * L2Coba Dolls.
 */
public class DollsData extends XMLDocument {
    private Map<Integer, Doll> dolls;

    public DollsData() {
        dolls = new HashMap<>();
        load();
    }

    public void reload() {
        dolls.clear();
        load();
    }

    public static DollsData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final DollsData INSTANCE = new DollsData();
    }

    @Override
    protected void load() {
        loadDocument("./data/xml/Dolls.xml");
        LOG.info("DollsData: Loaded " + dolls.size() + " dolls.");
    }

    @Override
    protected void parseDocument(Document doc, File file) {
        try {
            final Node root = doc.getFirstChild();

            for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (!"Doll".equalsIgnoreCase(node.getNodeName())) {
                    continue;
                }

                NamedNodeMap attrs = node.getAttributes();
                int id = Integer.parseInt(attrs.getNamedItem("Id").getNodeValue());
                int skillId = Integer.parseInt(attrs.getNamedItem("SkillId").getNodeValue());
                int skillLvl = Integer.parseInt(attrs.getNamedItem("SkillLvl").getNodeValue());

                Doll doll = new Doll(id, skillId, skillLvl);
                dolls.put(id, doll);
            }
        } catch (Exception e) {
            // LOG.warning("DollsData: Error while loading dolls: " + e);
            e.printStackTrace();
        }
    }

    public Map<Integer, Doll> getDolls() {
        return dolls;
    }

    public Doll getDollById(int id) {
        return dolls.get(id);
    }
    public boolean isDollById(int id) {
        return dolls.containsKey(id);
    }
    
    
    public static Doll getDoll(Player player)
	{
    	List<ItemInstance> collect = player.getInventory().getItems().stream().filter(x -> DollsData.getInstance().isDollById(x.getItemId())).collect(Collectors.toList());
    	int skillLv = 0;
    	int itemId = 0;
    	System.out.println(collect.isEmpty());
    	if(!collect.isEmpty()){
    		for (ItemInstance y : collect)
    		{
    			int skillLvl = DollsData.getInstance().getDollById(y.getItemId()).getSkillLvl();
    			if (skillLvl > skillLv)
    			{
    				skillLv = skillLvl;
    				itemId = y.getItemId();
    			}
    		}
    	}
    	if(itemId == 0)
    		return null;
    	return DollsData.getInstance().getDollById(itemId);
	}
    
    public static void setSkillForDoll(Player player, int dollItemId) {
        Doll doll = DollsData.getInstance().getDollById(dollItemId);

        if (doll == null) {
            return; 
        }

        int skillId = doll.getSkillId();
        int skillLvl = doll.getSkillLvl();

        L2Skill skill = SkillTable.getInstance().getInfo(skillId, skillLvl);

        if (skill != null) {
            int currentSkillLvl = player.getSkillLevel(skillId);

            if (currentSkillLvl > 0) {
              
                player.removeSkill(skillId);
            }

            if (player.getInventory().getItemByItemId(dollItemId) == null) {
                
            	refreshAllDollSkills(player);
            } else {
                
                player.addSkill(skill);
            }

            player.sendSkillList();
        }
    }

    public static void refreshAllDollSkills(Player player) {
        Map<Integer, Integer> highestSkillLevels = new HashMap<>();

        List<ItemInstance> collect = player.getInventory().getItems()
                .stream()
                .filter(x -> DollsData.getInstance().isDollById(x.getItemId()))
                .collect(Collectors.toList());

        for (ItemInstance dollItem : collect) {
            int skillId = DollsData.getInstance().getDollById(dollItem.getItemId()).getSkillId();
            int skillLvl = DollsData.getInstance().getDollById(dollItem.getItemId()).getSkillLvl();

            if (!highestSkillLevels.containsKey(skillId) || skillLvl > highestSkillLevels.get(skillId)) {
                highestSkillLevels.put(skillId, skillLvl);
            }
        }

        for (Map.Entry<Integer, Integer> entry : highestSkillLevels.entrySet()) {
            L2Skill skill = SkillTable.getInstance().getInfo(entry.getKey(), entry.getValue());

            if (skill != null) {
                player.addSkill(skill);
            }
        }

        player.sendSkillList();
    }




    
    
    
    
 /*   public static void setSkillDoll(Player player)
    {
    	Doll doll = getDoll(player);
    	if(doll == null)
    	{
    		Map<Integer, Doll> dolls2 = DollsData.getInstance().getDolls();
    		for (Entry<Integer, Doll> mapEntry : dolls2.entrySet()){
    			int skillId = mapEntry.getValue().getSkillId();
    			int skillLvl = mapEntry.getValue().getSkillLvl();
    			L2Skill skill = SkillTable.getInstance().getInfo(skillId, skillLvl);
    			if (skill != null) {
    				if (player.getSkillLevel(skillId) != skillLvl) {
    					player.removeSkill(skill);
    					player.sendSkillList();
    				}
    			}
    		}
    	}
    	else{
    		int skillId = doll.getSkillId();
    		int skillLvl = doll.getSkillLvl();
    		L2Skill skill = SkillTable.getInstance().getInfo(skillId, skillLvl);
            if (skill != null) {
                if (player.getSkillLevel(skillId) != skillLvl) {
                	player.addSkill(skill);
                	player.sendSkillList();
                }
            }
    	}
    }*/
    
    



    
    
    
    public static void getSkillDoll(Player player, ItemInstance item) {
        if (item != null) {
            if (DollsData.getInstance().isDollById(item.getItemId())) {
                setSkillForDoll(player, item.getItemId());
                refreshAllDollSkills(player);
            }
        } else {
 
        }
    }

}