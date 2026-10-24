package uk.co.extraspecialstudio.dead_air.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.survivor.SurvivorEntity;

/**
 * Soft-equip mid-tier apparel from Apocalypse Now / Survival Instinct / Zombie Survival Kit.
 * Never rolls exo, juggernaut, bombsquad, or full high-end kits — survivors stay scrappy.
 */
public final class SurvivorApocalypseGearCompat {

    private static final boolean AN = ModList.get().isLoaded("apocalypsenow");
    private static final boolean SI = ModList.get().isLoaded("survival_instinct");
    private static final boolean ZSK = ModList.get().isLoaded("zombiekit");
    private static final boolean CHOVY = ModList.get().isLoaded("chovys_apocalypse_mod");

    /** Military — iron/chain equivalent vibe. */
    private static final String[] MILITARY = {
        "apocalypsenow:soldier_helmet", "apocalypsenow:soldier_chestplate",
        "apocalypsenow:soldier_leggings", "apocalypsenow:soldier_boots",
        "apocalypsenow:army_helmet", "apocalypsenow:army_chestplate",
        "apocalypsenow:army_leggings", "apocalypsenow:army_boots",
        "apocalypsenow:military_urban_helmet", "apocalypsenow:military_urban_chestplate",
        "apocalypsenow:military_urban_leggings", "apocalypsenow:military_urban_boots",
        "apocalypsenow:bulletproof_vest_chestplate",
        "survival_instinct:military_helmet", "survival_instinct:military_chestplate",
        "survival_instinct:military_leggings", "survival_instinct:military_boots",
        "zombiekit:standard_tactical_helmet", "zombiekit:standard_tactical_chestplate",
        "zombiekit:standard_tactical_leggings", "zombiekit:standard_tactical_boots",
        "zombiekit:forest_tactical_helmet", "zombiekit:forest_tactical_chestplate"
    };

    private static final String[] SCAVENGER = {
        "apocalypsenow:rusty_helmet", "apocalypsenow:rusty_chestplate",
        "apocalypsenow:rusty_leggings", "apocalypsenow:rusty_boots",
        "apocalypsenow:police_helmet", "apocalypsenow:police_chestplate",
        "apocalypsenow:construction_helmet", "apocalypsenow:construction_chestplate",
        "apocalypsenow:gasmask_helmet", "apocalypsenow:facemask",
        "apocalypsenow:civilian_body_vest_chestplate",
        "survival_instinct:police_helmet", "survival_instinct:police_chestplate",
        "survival_instinct:motorcycle_helmet", "survival_instinct:wool_balaclava_helmet",
        "survival_instinct:gas_mask_helmet",
        "zombiekit:standard_riot_helmet", "zombiekit:skiing_helmet",
        "zombiekit:forest_riot_chestplate",
        "chovys_apocalypse_mod:policevest_chestplate"
    };

    private static final String[] MEDIC = {
        "apocalypsenow:nurse_chestplate", "apocalypsenow:nurse_leggings", "apocalypsenow:nurse_boots",
        "apocalypsenow:facemask", "apocalypsenow:gasmask_helmet",
        "survival_instinct:hazmat_helmet", "survival_instinct:hazmat_chestplate"
    };

    private static final String[] PILOT = {
        "apocalypsenow:aviator_hat_helmet", "apocalypsenow:jet_pilot_helmet",
        "apocalypsenow:gentex_hgu_56_p_helmet", "apocalypsenow:helicopter_helmet",
        "apocalypsenow:jet_pilot_chestplate", "apocalypsenow:military_pilot_headset_chestplate"
    };

    private static final String[] RADIO = {
        "apocalypsenow:military_beret_helmet", "apocalypsenow:military_cap_helmet",
        "apocalypsenow:officer_beret_helmet", "apocalypsenow:military_pilot_headset_chestplate",
        "apocalypsenow:facemask"
    };

    private static final String[] CIVILIAN = {
        "apocalypsenow:baseball_cap_green_helmet", "apocalypsenow:baseball_cap_blue_helmet",
        "apocalypsenow:facemask", "apocalypsenow:strawhat_helmet",
        "apocalypsenow:cowboy_hat_helmet", "apocalypsenow:high_visibility_jacket_chestplate",
        "survival_instinct:wool_balaclava_helmet", "zombiekit:skiing_boots",
        "chovys_apocalypse_mod:policevest_chestplate"
    };

    private SurvivorApocalypseGearCompat() {
    }

    public static boolean anyLoaded() {
        return AN || SI || ZSK || CHOVY || ModList.get().isLoaded("zmodfood");
    }

    public static void apply(SurvivorEntity survivor, RandomSource random) {
        if (!Config.compatApocalypseGear || !anyLoaded()) return;
        try {
            String[] pool = poolFor(survivor.getProfession());
            if (pool.length == 0) return;

            float chance = switch (survivor.getProfession()) {
                case MILITARY -> 0.85f;
                case SCAVENGER -> 0.70f;
                case MEDIC, PILOT -> 0.55f;
                case RADIO_OPERATOR -> 0.45f;
                default -> 0.35f;
            };
            if (random.nextFloat() > chance) return;

            int pieces = switch (survivor.getProfession()) {
                case MILITARY -> 2 + random.nextInt(3); // 2–4
                case SCAVENGER -> 1 + random.nextInt(3); // 1–3
                default -> 1 + random.nextInt(2); // 1–2
            };

            for (int i = 0; i < pieces; i++) {
                String id = pool[random.nextInt(pool.length)];
                ItemStack stack = stackFromId(id);
                if (stack.isEmpty()) continue;
                if (!(stack.getItem() instanceof ArmorItem armor)) continue;
                EquipmentSlot slot = armor.getEquipmentSlot();
                ItemStack existing = survivor.getItemBySlot(slot);
                // Prefer filling empties; occasionally upgrade vanilla leather/iron scrap
                if (existing.isEmpty() || random.nextFloat() < 0.55f) {
                    survivor.setItemSlot(slot, stack);
                }
            }
        } catch (LinkageError | Exception e) {
            Dead_air.LOGGER.debug("[Dead Air] Apocalypse gear equip failed: {}", e.toString());
        }
    }

    private static String[] poolFor(SurvivorEntity.Profession profession) {
        return switch (profession) {
            case MILITARY -> MILITARY;
            case SCAVENGER -> SCAVENGER;
            case MEDIC -> MEDIC;
            case PILOT -> PILOT;
            case RADIO_OPERATOR -> RADIO;
            case CIVILIAN -> CIVILIAN;
        };
    }

    private static ItemStack stackFromId(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item);
    }
}
