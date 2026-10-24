package uk.co.extraspecialstudio.dead_air.survivor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

/**
 * Rolled once on spawn, persisted on the entity. Feeds idle chat, combat bias, and skin/name gender.
 */
public final class SurvivorIdentity {
    public enum Gender {
        MALE, FEMALE;

        static Gender byIndex(int i) {
            return i == 1 ? FEMALE : MALE;
        }
    }

    public enum Personality {
        CHEERFUL, GRIM, PARANOID, LOYAL, SARCASTIC;

        static Personality byIndex(int i) {
            Personality[] v = values();
            return v[Math.floorMod(i, v.length)];
        }
    }

    public enum CombatOrientation {
        AGGRESSIVE, DEFENSIVE, SUPPORT, CAUTIOUS;

        static CombatOrientation byIndex(int i) {
            CombatOrientation[] v = values();
            return v[Math.floorMod(i, v.length)];
        }
    }

    public enum Hope {
        FIND_FAMILY, SAFE_HOME, FIX_THE_SIGNAL, SEE_THE_OCEAN, STOCK_A_CACHE, QUIET_DAYS;

        static Hope byIndex(int i) {
            Hope[] v = values();
            return v[Math.floorMod(i, v.length)];
        }
    }

    /** Male display names — paired only with male visual variants. */
    private static final String[] MALE_NAMES = {
        "Eli", "Tobias", "Dmitri", "Callum", "Owen", "Marek", "Jonah", "Felix", "Wes", "Kofi",
        "Teo", "Bram", "Niko", "Ash", "Pavel", "Glen", "Idris", "Orin", "Jace", "Rook",
        "Finn", "Cole", "Percy", "Remy", "Quinn"
    };

    /** Female display names — paired only with female visual variants. */
    private static final String[] FEMALE_NAMES = {
        "Mara", "June", "Rosa", "Ines", "Priya", "Hana", "Lena", "Sade", "Noor", "Agnes",
        "Ruth", "Ivy", "Cass", "Vera", "Yara", "Suri", "Zara", "Bess", "Maeve", "Talia",
        "Sable", "Asha", "Nyx", "Nora", "Kira"
    };

    /** Two distinct looks per profession × gender. */
    public static final int SKIN_VARIANT_COUNT = 2;

    public String name = "Survivor";
    public Gender gender = Gender.MALE;
    /** 0–1 visual skin index within profession × gender. */
    public int skinVariant;
    public Personality personality = Personality.LOYAL;
    public CombatOrientation orientation = CombatOrientation.DEFENSIVE;
    public Hope hope = Hope.SAFE_HOME;
    /** 0–{@link SurvivorProgress#SKILL_MAX} each. */
    public int skillScavenge;
    public int skillMedicine;
    public int skillRadio;
    public int skillCombat;
    /** 1–{@link SurvivorProgress#MAX_LEVEL}. */
    public int level = 1;
    /** Progress toward next level. */
    public int xp;
    /** Extra max HP from even level-ups (persisted). */
    public int bonusMaxHp;
    private int idleChatIndex;

    public void roll(RandomSource random) {
        gender = random.nextBoolean() ? Gender.MALE : Gender.FEMALE;
        skinVariant = random.nextInt(SKIN_VARIANT_COUNT);
        String[] pool = gender == Gender.FEMALE ? FEMALE_NAMES : MALE_NAMES;
        name = pool[random.nextInt(pool.length)];
        personality = Personality.byIndex(random.nextInt(Personality.values().length));
        orientation = CombatOrientation.byIndex(random.nextInt(CombatOrientation.values().length));
        hope = Hope.byIndex(random.nextInt(Hope.values().length));
        skillScavenge = random.nextInt(4);
        skillMedicine = random.nextInt(4);
        skillRadio = random.nextInt(4);
        skillCombat = random.nextInt(4);
        level = 1;
        xp = 0;
        bonusMaxHp = 0;
        idleChatIndex = random.nextInt(4);
    }

    public boolean isFemale() {
        return gender == Gender.FEMALE;
    }

    /** Texture path segment: {@code {profession}_{male|female}_{0|1}}. */
    public String skinTextureKey(String professionId) {
        String g = isFemale() ? "female" : "male";
        int v = Math.floorMod(skinVariant, SKIN_VARIANT_COUNT);
        return professionId + "_" + g + "_" + v;
    }

    public void biasSkillsForProfession(SurvivorEntity.Profession profession, RandomSource random) {
        // if/else — avoid switch-on-enum synthetic SurvivorIdentity$1 (Forge ModuleClassLoader CNF).
        if (profession == SurvivorEntity.Profession.SCAVENGER) {
            skillScavenge = Math.max(skillScavenge, 2 + random.nextInt(2));
        } else if (profession == SurvivorEntity.Profession.MEDIC) {
            skillMedicine = Math.max(skillMedicine, 2 + random.nextInt(2));
        } else if (profession == SurvivorEntity.Profession.RADIO_OPERATOR) {
            skillRadio = Math.max(skillRadio, 2 + random.nextInt(2));
        } else if (profession == SurvivorEntity.Profession.PILOT) {
            skillRadio = Math.max(skillRadio, 2 + random.nextInt(2));
        } else if (profession == SurvivorEntity.Profession.MILITARY) {
            skillCombat = Math.max(skillCombat, 2 + random.nextInt(2));
        } else if (profession == SurvivorEntity.Profession.CIVILIAN) {
            skillScavenge = Math.max(skillScavenge, 1 + random.nextInt(2));
        }
    }

    public String nextIdleLineKey(String professionId) {
        int i = idleChatIndex++ % 4;
        return "survivor.dead_air.idle." + professionId + "." + i;
    }

    public String hopeLineKey() {
        Hope h = hope != null ? hope : Hope.SAFE_HOME;
        return "survivor.dead_air.hope." + h.name().toLowerCase();
    }

    public String whoLineKey(String professionId) {
        return "survivor.dead_air.who." + professionId;
    }

    public void save(CompoundTag tag) {
        tag.putString("Name", name);
        tag.putInt("Gender", gender.ordinal());
        tag.putInt("SkinVariant", skinVariant);
        tag.putInt("Personality", personality.ordinal());
        tag.putInt("Orientation", orientation.ordinal());
        tag.putInt("Hope", hope.ordinal());
        tag.putInt("SkScav", skillScavenge);
        tag.putInt("SkMed", skillMedicine);
        tag.putInt("SkRad", skillRadio);
        tag.putInt("SkCbt", skillCombat);
        tag.putInt("Level", level);
        tag.putInt("Xp", xp);
        tag.putInt("BonusMaxHp", bonusMaxHp);
        tag.putInt("IdleIdx", idleChatIndex);
    }

    public void load(CompoundTag tag) {
        if (tag.contains("Name")) name = tag.getString("Name");
        gender = Gender.byIndex(tag.contains("Gender") ? tag.getInt("Gender") : 0);
        skinVariant = Math.floorMod(tag.contains("SkinVariant") ? tag.getInt("SkinVariant") : 0, SKIN_VARIANT_COUNT);
        personality = Personality.byIndex(tag.getInt("Personality"));
        orientation = CombatOrientation.byIndex(tag.getInt("Orientation"));
        hope = Hope.byIndex(tag.getInt("Hope"));
        skillScavenge = clampSkill(tag.getInt("SkScav"));
        skillMedicine = clampSkill(tag.getInt("SkMed"));
        skillRadio = clampSkill(tag.getInt("SkRad"));
        skillCombat = clampSkill(tag.getInt("SkCbt"));
        level = tag.contains("Level")
            ? Math.max(1, Math.min(SurvivorProgress.MAX_LEVEL, tag.getInt("Level")))
            : 1;
        xp = Math.max(0, tag.contains("Xp") ? tag.getInt("Xp") : 0);
        bonusMaxHp = Math.max(0, Math.min(20, tag.contains("BonusMaxHp") ? tag.getInt("BonusMaxHp") : 0));
        idleChatIndex = tag.getInt("IdleIdx");
        // Legacy saves: if name looks female but gender missing was male, leave as-is —
        // Gender tag above defaults to MALE when absent.
    }

    private static int clampSkill(int v) {
        return Math.max(0, Math.min(SurvivorProgress.SKILL_MAX, v));
    }
}
