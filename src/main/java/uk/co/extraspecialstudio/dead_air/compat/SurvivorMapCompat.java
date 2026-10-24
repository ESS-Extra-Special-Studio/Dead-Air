package uk.co.extraspecialstudio.dead_air.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import uk.co.extraspecialstudio.dead_air.base.PlayerBaseStorage;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/**
 * Soft map-mod markers for player bases (JourneyMap / Xaero waypoints + FTB Chunks log/claim best-effort).
 */
public final class SurvivorMapCompat {
    private SurvivorMapCompat() {}

    public static void addOrUpdateBaseMarker(ServerPlayer player, ResourceKey<Level> dim, BlockPos panel, int range) {
        try {
            MapWaypointCompat.markEbSite(player, panel, "Dead Air Base");
            String ftb = FtbChunksCompat.onBaseSet(player, dim, panel, range);
            Dead_air.LOGGER.debug("[Dead Air] Base map integrate ftb={}", ftb);
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] Base map marker failed: {}", t.toString());
        }
    }

    public static void removeBaseMarker(PlayerBaseStorage.Base base) {
        // Temporary waypoints expire on the map-mod side; FTB claims are not auto-unclaimed.
    }

    public static String status() {
        return DeadAirCompat.shortSummary();
    }
}
