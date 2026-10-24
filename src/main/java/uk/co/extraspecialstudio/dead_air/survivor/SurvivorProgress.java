package uk.co.extraspecialstudio.dead_air.survivor;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Slow per-survivor XP / level progression. Level-ups raise skills (cap 5) and
 * occasionally bump max health.
 */
public final class SurvivorProgress {
    public static final int MAX_LEVEL = 20;
    public static final int SKILL_MAX = 5;
    public static final float BASE_MAX_HEALTH = 24f;

    public enum SkillBias {
        SCAVENGE, MEDICINE, RADIO, COMBAT, ANY
    }

    private SurvivorProgress() {}

    public static int xpToNextLevel(int level) {
        int clamped = Math.max(1, Math.min(MAX_LEVEL, level));
        return 40 + clamped * 30;
    }

    /** Apply persisted bonus max HP from identity.level / bonusMaxHp. */
    public static void applyMaxHealth(SurvivorEntity survivor) {
        if (survivor.level().isClientSide) return;
        SurvivorIdentity id = survivor.getIdentity();
        if (id == null) return;
        AttributeInstance attr = survivor.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        float target = BASE_MAX_HEALTH + id.bonusMaxHp;
        if (Math.abs(attr.getBaseValue() - target) > 0.01) {
            float ratio = survivor.getHealth() / (float) Math.max(1.0, attr.getValue());
            attr.setBaseValue(target);
            survivor.setHealth(Math.min(survivor.getMaxHealth(), Math.max(1f, ratio * survivor.getMaxHealth())));
        }
    }

    public static void awardXp(SurvivorEntity survivor, int amount, SkillBias bias) {
        if (survivor.level().isClientSide || amount <= 0) return;
        SurvivorIdentity id = survivor.getIdentity();
        if (id == null) return;
        if (id.level >= MAX_LEVEL) {
            id.xp = 0;
            return;
        }
        id.xp += amount;
        boolean leveled = false;
        while (id.level < MAX_LEVEL && id.xp >= xpToNextLevel(id.level)) {
            id.xp -= xpToNextLevel(id.level);
            id.level++;
            leveled = true;
            onLevelUp(survivor, id, bias);
        }
        if (id.level >= MAX_LEVEL) {
            id.level = MAX_LEVEL;
            id.xp = 0;
        }
        if (leveled) {
            applyMaxHealth(survivor);
            notifyOwnerLevelUp(survivor, id);
        }
    }

    private static void onLevelUp(SurvivorEntity survivor, SurvivorIdentity id, SkillBias bias) {
        bumpSkill(id, preferredBias(survivor, bias), survivor.getRandom().nextInt(4));
        // Every 2 levels: +1 max HP.
        if (id.level % 2 == 0) {
            id.bonusMaxHp = Math.min(20, id.bonusMaxHp + 1);
        }
    }

    private static SkillBias preferredBias(SurvivorEntity survivor, SkillBias requested) {
        if (requested != null && requested != SkillBias.ANY) return requested;
        SurvivorEntity.Profession p = survivor.getProfession();
        if (p == SurvivorEntity.Profession.SCAVENGER || p == SurvivorEntity.Profession.CIVILIAN) {
            return SkillBias.SCAVENGE;
        }
        if (p == SurvivorEntity.Profession.MEDIC) return SkillBias.MEDICINE;
        if (p == SurvivorEntity.Profession.RADIO_OPERATOR || p == SurvivorEntity.Profession.PILOT) {
            return SkillBias.RADIO;
        }
        if (p == SurvivorEntity.Profession.MILITARY) return SkillBias.COMBAT;
        return SkillBias.ANY;
    }

    private static void bumpSkill(SurvivorIdentity id, SkillBias bias, int randomSlot) {
        if (tryBump(id, bias)) return;
        // Primary full — try others in random order.
        SkillBias[] order = {
            SkillBias.SCAVENGE, SkillBias.MEDICINE, SkillBias.RADIO, SkillBias.COMBAT
        };
        for (int i = 0; i < 4; i++) {
            if (tryBump(id, order[(randomSlot + i) % 4])) return;
        }
    }

    private static boolean tryBump(SurvivorIdentity id, SkillBias bias) {
        // if/else — avoid switch-on-enum synthetic SurvivorProgress$1 (Forge ModuleClassLoader CNF).
        if (bias == SkillBias.SCAVENGE) return bump(id, 0);
        if (bias == SkillBias.MEDICINE) return bump(id, 1);
        if (bias == SkillBias.RADIO) return bump(id, 2);
        if (bias == SkillBias.COMBAT) return bump(id, 3);
        return false;
    }

    private static boolean bump(SurvivorIdentity id, int slot) {
        return switch (slot) {
            case 0 -> {
                if (id.skillScavenge >= SKILL_MAX) yield false;
                id.skillScavenge++;
                yield true;
            }
            case 1 -> {
                if (id.skillMedicine >= SKILL_MAX) yield false;
                id.skillMedicine++;
                yield true;
            }
            case 2 -> {
                if (id.skillRadio >= SKILL_MAX) yield false;
                id.skillRadio++;
                yield true;
            }
            default -> {
                if (id.skillCombat >= SKILL_MAX) yield false;
                id.skillCombat++;
                yield true;
            }
        };
    }

    private static void notifyOwnerLevelUp(SurvivorEntity survivor, SurvivorIdentity id) {
        ServerPlayer owner = survivor.getOwnerPlayer();
        if (owner == null) return;
        String name = id.name != null ? id.name : survivor.getName().getString();
        owner.displayClientMessage(
            Component.translatable("message.dead_air.survivor.level_up", name, id.level), true);
    }

    /** Average of four skills scaled 0–1 (cap {@link #SKILL_MAX}). */
    public static float skillReadiness(SurvivorIdentity id) {
        if (id == null) return 0f;
        int sum = id.skillScavenge + id.skillMedicine + id.skillRadio + id.skillCombat;
        return Math.min(1f, sum / (float) (SKILL_MAX * 4));
    }
}
