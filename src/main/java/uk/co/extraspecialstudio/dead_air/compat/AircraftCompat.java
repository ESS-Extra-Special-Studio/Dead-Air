package uk.co.extraspecialstudio.dead_air.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.fml.ModList;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/**
 * Soft Immersive Aircraft â€” alternate wreck loot flavour when RadioTowers plane is absent.
 */
public final class AircraftCompat {
    private static final boolean LOADED = ModList.get().isLoaded("immersive_aircraft");

    private AircraftCompat() {}

    public static void probe() {
        if (LOADED) {
            Dead_air.LOGGER.info("[Dead Air] Immersive Aircraft soft-compat ready");
        }
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static boolean enabled() {
        return Config.compatImmersiveAircraft && LOADED;
    }

    /**
     * Inject aircraft-related loot into an existing crash chest, or place a small parts chest.
     * @param usedRtPlane true if RadioTowers plane wreck was spawned (skip alternate flavour)
     */
    public static void decorateCrashSite(ServerLevel level, BlockPos center, boolean usedRtPlane) {
        if (!enabled() || usedRtPlane) return;
        try {
            // Prefer filling the main crash chest if present nearby
            for (BlockPos p : BlockPos.betweenClosed(center.offset(-3, -1, -3), center.offset(3, 2, 3))) {
                BlockEntity be = level.getBlockEntity(p);
                if (be instanceof ChestBlockEntity) {
                    CompatLoot.injectIntoChest(level, p.immutable(), CompatTags.AIRCRAFT_PARTS, 2);
                    Dead_air.LOGGER.info("[Dead Air] Immersive Aircraft loot injected at {}", p);
                    return;
                }
            }
            CompatLoot.placePartsChest(level, center.offset(-2, 0, 2), CompatTags.AIRCRAFT_PARTS, 2);
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] Aircraft decorate failed: {}", t.toString());
        }
    }
}
