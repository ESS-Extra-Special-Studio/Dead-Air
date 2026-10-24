package uk.co.extraspecialstudio.dead_air.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.survivor.SurvivorEntity;
import uk.co.extraspecialstudio.dead_air.survivor.SurvivorIdentity;

/**
 * Unified soft gun + modded gear facade for survivors: TaCZ (equip + shoot) and Point Blank (equip + tag).
 * Also accepts anything in {@link CompatTags#GUNS}.
 */
public final class SurvivorGunCompat {
    private static final String[] POINT_BLANK_STARTERS = {
        "pointblank:glock17", "pointblank:m1911", "pointblank:p320",
        "pointblank:mp5", "pointblank:ump45", "pointblank:m4a1",
        "pointblank:ak47", "pointblank:m870", "pointblank:spas12"
    };

    private static final String[] COLD_SWEAT_ARMOR = {
        "cold_sweat:fur_helmet", "cold_sweat:fur_parka",
        "cold_sweat:fur_pants", "cold_sweat:fur_boots"
    };

    private SurvivorGunCompat() {}

    public static boolean isGun(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        // Prefer live gun APIs — tag alone includes ammo and bare modern_kinetic_gun
        if (Config.compatTacz && SurvivorTaczCompat.isGun(stack)) return true;
        if (PointBlankCompat.isGun(stack)) return true;
        return false;
    }

    public static boolean holdingGun(LivingEntity entity) {
        return isGun(entity.getMainHandItem());
    }

    /**
     * True when the survivor can actually fire (TaCZ NPC shoot path).
     * Point Blank stacks count as guns for equip/loot but have no NPC shoot AI —
     * treat them as non-ranged so melee is not blocked forever.
     */
    public static boolean holdingUsableGun(LivingEntity entity) {
        if (!Config.compatTacz) return false;
        return SurvivorTaczCompat.isGun(entity.getMainHandItem());
    }

    public static void prepareGunForNpc(ItemStack gun) {
        if (Config.compatTacz) SurvivorTaczCompat.prepareGunForNpc(gun);
    }

    public static void draw(LivingEntity entity, ItemStack gun) {
        if (Config.compatTacz) SurvivorTaczCompat.draw(entity, gun);
    }

    public static boolean tryShoot(Mob shooter, LivingEntity target) {
        if (Config.compatTacz && SurvivorTaczCompat.isGun(shooter.getMainHandItem())) {
            return SurvivorTaczCompat.tryShoot(shooter, target);
        }
        return false;
    }

    /**
     * Overlay modded weapons/armor onto a freshly geared survivor.
     * Replaces main-hand with a TaCZ/Point Blank gun when the roll succeeds.
     */
    public static void applyModdedStarterLoadout(SurvivorEntity survivor, RandomSource random) {
        try {
            tryEquipModdedArmor(survivor, random);
            SurvivorApocalypseGearCompat.apply(survivor, random);
            tryEquipStarterGun(survivor, random);
        } catch (LinkageError | Exception e) {
            // ModuleClassLoader can CNF compat helpers mid-spawn — never crash egg place.
            Dead_air.LOGGER.error("Dead Air: modded starter loadout failed for {}", survivor.getName().getString(), e);
        }
    }

    public static void tryEquipStarterGun(SurvivorEntity survivor, RandomSource random) {
        // Only Military (always) and Scavenger (decent chance) spawn with TaCZ/PB guns.
        float chance = switch (survivor.getProfession()) {
            case MILITARY -> 1.0f;
            case SCAVENGER -> 0.60f;
            default -> 0f;
        };
        if (chance <= 0f || random.nextFloat() > chance) return;

        ItemStack gun = pickStarterGun(random, survivor.getProfession(), survivor.getIdentity().orientation);
        if (gun.isEmpty()) return;

        survivor.setItemSlot(EquipmentSlot.MAINHAND, gun);
        prepareGunForNpc(gun);
        // Guns don't need offhand arrows; clear bow packs if we replaced a bow
        ItemStack off = survivor.getOffhandItem();
        if (off.is(net.minecraft.world.item.Items.ARROW)) {
            survivor.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
    }

    public static ItemStack pickStarterGun(RandomSource random,
                                           SurvivorEntity.Profession profession,
                                           SurvivorIdentity.CombatOrientation orientation) {
        SurvivorTaczCompat.StarterTier tier = tierFor(profession, orientation);

        // Survivors only shoot via TaCZ. Do not equip unusable Point Blank starters
        // (they would block melee through holdingGun without ever firing).
        if (Config.compatTacz && SurvivorTaczCompat.isLoaded()) {
            return SurvivorTaczCompat.pickStarterGun(random, tier);
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack pickLootGun(RandomSource random) {
        if (Config.compatTacz && SurvivorTaczCompat.isLoaded()) {
            ItemStack tacz = SurvivorTaczCompat.pickLootGun(random);
            if (!tacz.isEmpty()) return tacz;
        }
        // Loot can still roll PB for the player to use.
        if (Config.compatPointBlank && PointBlankCompat.isLoaded()) {
            return PointBlankCompat.pickStarterGun(random, POINT_BLANK_STARTERS);
        }
        return ItemStack.EMPTY;
    }

    private static SurvivorTaczCompat.StarterTier tierFor(SurvivorEntity.Profession profession,
                                                          SurvivorIdentity.CombatOrientation orientation) {
        if (profession == SurvivorEntity.Profession.MILITARY) {
            return SurvivorTaczCompat.StarterTier.PRIMARY;
        }
        if (profession == SurvivorEntity.Profession.SCAVENGER) {
            return SurvivorTaczCompat.StarterTier.MIXED;
        }
        return SurvivorTaczCompat.StarterTier.SIDEARM;
    }

    private static void tryEquipModdedArmor(SurvivorEntity survivor, RandomSource random) {
        if (!Config.compatColdSweat) return;
        // Chance scales with scavenger / cautious cold-prep vibe
        float chance = survivor.getProfession() == SurvivorEntity.Profession.SCAVENGER ? 0.45f : 0.28f;
        if (random.nextFloat() > chance) return;

        int pieces = 1 + random.nextInt(3);
        for (int i = 0; i < pieces; i++) {
            String id = COLD_SWEAT_ARMOR[random.nextInt(COLD_SWEAT_ARMOR.length)];
            // Resolve inline — do not touch CompatLoot (ModuleClassLoader CNF on spawn).
            ItemStack stack = stackFromId(id);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof ArmorItem armor) {
                EquipmentSlot slot = armor.getEquipmentSlot();
                // Don't overwrite already-filled slots every time — prefer empty, else 50% replace
                ItemStack existing = survivor.getItemBySlot(slot);
                if (existing.isEmpty() || random.nextBoolean()) {
                    survivor.setItemSlot(slot, stack);
                }
            }
        }
    }

    private static ItemStack stackFromId(String id) {
        if (id == null || id.isEmpty()) return ItemStack.EMPTY;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item);
    }
}
