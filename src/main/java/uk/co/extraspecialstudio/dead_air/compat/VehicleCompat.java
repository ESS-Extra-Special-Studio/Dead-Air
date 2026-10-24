package uk.co.extraspecialstudio.dead_air.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.ModList;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;

import javax.annotation.Nullable;

/**
 * Soft MrCrayfish Vehicle / Vehicle Mod Reborn — abandoned-vehicle flavour near crash sites.
 * Entity spawn is unsafe across versions; we place a parts chest + tag loot only.
 */
public final class VehicleCompat {
    private static final String[] CANDIDATES = {
        "vehicle", "vehicle_mod", "cvehicle", "cfmvehicle", "vehiclemod"
    };

    @Nullable private static String modId;
    private static boolean probed;

    private VehicleCompat() {}

    public static void probe() {
        if (probed) return;
        probed = true;
        for (String id : CANDIDATES) {
            if (ModList.get().isLoaded(id)) {
                modId = id;
                Dead_air.LOGGER.info("[Dead Air] Vehicle soft-compat: {}", id);
                return;
            }
        }
    }

    public static boolean isLoaded() {
        if (!probed) probe();
        return modId != null;
    }

    public static String detectedModId() {
        if (!probed) probe();
        return modId != null ? modId : "vehicle";
    }

    public static boolean enabled() {
        return Config.compatVehicle && isLoaded();
    }

    /** Near downed-plane / auction fallback: chest of vehicle parts from tag (or skip). */
    public static void decorateCrashSite(ServerLevel level, BlockPos center) {
        if (!enabled()) return;
        try {
            BlockPos at = CompatLoot.placePartsChest(level, center.offset(3, 0, -2),
                CompatTags.VEHICLE_PARTS, 3);
            if (at != null) {
                Dead_air.LOGGER.info("[Dead Air] Vehicle parts chest at {} (mod={})", at, modId);
            }
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] Vehicle decorate failed: {}", t.toString());
        }
    }
}
