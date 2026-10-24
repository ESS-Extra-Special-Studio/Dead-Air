package uk.co.extraspecialstudio.dead_air.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;

import javax.annotation.Nullable;

/**
 * Soft Vic's Point Blank (modid {@code pointblank}) — gun item detection for survivor equip + loot tag.
 * Shoot AI is not mirrored (Point Blank NPC API varies); TaCZ remains the shoot path when present.
 */
public final class PointBlankCompat {
    private static final String[] CANDIDATE_MODS = {"pointblank", "point_blank", "vics_point_blank"};
    private static final String[] GUN_ITEM_CLASSES = {
        "com.vicmatskiv.pointblank.item.GunItem",
        "com.vicmatskiv.pointblank.item.FirearmItem",
        "com.pointblank.item.GunItem"
    };

    @Nullable private static String modId;
    @Nullable private static Class<?> gunItemClass;
    private static boolean probed;

    private PointBlankCompat() {}

    public static void probe() {
        if (probed) return;
        probed = true;
        for (String id : CANDIDATE_MODS) {
            if (ModList.get().isLoaded(id)) {
                modId = id;
                break;
            }
        }
        if (modId == null) return;
        for (String cn : GUN_ITEM_CLASSES) {
            try {
                gunItemClass = Class.forName(cn);
                Dead_air.LOGGER.info("[Dead Air] Point Blank gun class ready ({})", cn);
                return;
            } catch (Throwable ignored) {
            }
        }
        Dead_air.LOGGER.info("[Dead Air] Point Blank loaded ({}) — using item-tag / namespace gun detect", modId);
    }

    public static boolean isLoaded() {
        if (!probed) probe();
        return modId != null;
    }

    public static String detectedModId() {
        if (!probed) probe();
        return modId != null ? modId : "pointblank";
    }

    public static boolean enabled() {
        return Config.compatPointBlank && isLoaded();
    }

    public static boolean isGun(ItemStack stack) {
        if (!enabled() || stack == null || stack.isEmpty()) return false;
        try {
            if (stack.is(CompatTags.GUNS)) return true;
            if (gunItemClass != null && gunItemClass.isInstance(stack.getItem())) return true;
            var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            return key != null && modId != null && modId.equals(key.getNamespace());
        } catch (Throwable t) {
            return false;
        }
    }

    /** Resolve a known Point Blank item id if present in the registry. */
    public static ItemStack pickStarterGun(RandomSource random, String[] candidateIds) {
        if (!enabled() || candidateIds == null || candidateIds.length == 0) return ItemStack.EMPTY;
        for (int attempt = 0; attempt < 10; attempt++) {
            String id = candidateIds[random.nextInt(candidateIds.length)];
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) continue;
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item == null || item == Items.AIR) continue;
            ItemStack stack = new ItemStack(item);
            if (isGun(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }
}
