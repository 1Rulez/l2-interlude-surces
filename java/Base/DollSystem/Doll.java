package Base.DollSystem;
/**
 * L2Coba Dolls.
 */
public class Doll {
    private int id;
    private int skillId;
    private int skillLvl;

    public Doll(int id, int skillId, int skillLvl) {
        this.id = id;
        this.skillId = skillId;
        this.skillLvl = skillLvl;
    }

    public int getId() {
        return id;
    }

    public int getSkillId() {
        return skillId;
    }

    public int getSkillLvl() {
        return skillLvl;
    }
}
