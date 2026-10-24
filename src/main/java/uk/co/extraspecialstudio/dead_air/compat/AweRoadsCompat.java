package uk.co.extraspecialstudio.dead_air.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.neoforged.fml.ModList;
import uk.co.extraspecialstudio.dead_air.Dead_air;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Soft hooks into AWE density roads (no hard dep).
 * EB camps/relays/auctions/crash avoid active + paved corridors; convoys prefer them.
 */
public final class AweRoadsCompat {

    private static final boolean LOADED = ModList.get().isLoaded("apocalyptic_world_engine");
    private static Method isBusyNear;
    private static Method isPendingNear;
    private static Method pickAlongRoad;
    private static Method nearestRoad;
    private static boolean probed;

    private AweRoadsCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static void probe() {
        if (!LOADED || probed) return;
        probed = true;
        try {
            Class<?> cl = Class.forName(
                "uk.co.extraspecialstudio.apocalyptic_world_engine.worldgen.RoadNetworkQueries");
            isBusyNear = cl.getMethod("isRoadBusyNear", ServerLevel.class, int.class, int.class, int.class);
            isPendingNear = cl.getMethod("isPendingCarveNear", ServerLevel.class, int.class, int.class, int.class);
            pickAlongRoad = cl.getMethod("pickAlongFinishedRoad",
                ServerLevel.class, int.class, int.class, int.class, int.class, RandomSource.class);
            nearestRoad = cl.getMethod("nearestRoadPoint",
                ServerLevel.class, int.class, int.class, int.class);
            Dead_air.LOGGER.info("[Dead Air] AWE roads soft-compat ready (EB avoid / convoy snap)");
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] AWE roads API unavailable: {}", t.toString());
            isBusyNear = null;
            isPendingNear = null;
            pickAlongRoad = null;
            nearestRoad = null;
        }
    }

    /** True when AWE has a pending carve or paved sample near this XZ. */
    public static boolean isRoadBusyNear(ServerLevel level, int x, int z, int radius) {
        if (!LOADED) return false;
        probe();
        if (isBusyNear == null) return false;
        try {
            Object r = isBusyNear.invoke(null, level, x, z, radius);
            return r instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }

    /** True only for an in-progress carve (finished roads are fine for convoys). */
    public static boolean isPendingCarveNear(ServerLevel level, int x, int z, int radius) {
        if (!LOADED) return false;
        probe();
        if (isPendingNear == null) return false;
        try {
            Object r = isPendingNear.invoke(null, level, x, z, radius);
            return r instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Prefer a point on an already-paved / finished AWE road corridor for refugee convoys.
     * Falls back to null so {@link uk.co.extraspecialstudio.dead_air.broadcast.PendingSites} uses normal scatter.
     */
    @Nullable
    public static BlockPos pickConvoyOnRoad(ServerLevel level, int ox, int oz,
                                            int minDist, int maxDist, RandomSource random) {
        if (!LOADED) return null;
        probe();
        try {
            if (pickAlongRoad != null) {
                Object r = pickAlongRoad.invoke(null, level, ox, oz, minDist, maxDist, random);
                if (r instanceof BlockPos pos) return pos;
            }
            // Early game / sparse samples: nearest carved road within a wider bubble
            if (nearestRoad != null) {
                Object n = nearestRoad.invoke(null, level, ox, oz, Math.max(maxDist, minDist * 2));
                if (n instanceof BlockPos pos) {
                    int dx = pos.getX() - ox;
                    int dz = pos.getZ() - oz;
                    int d2 = dx * dx + dz * dz;
                    if (d2 >= (long) minDist * minDist) return pos;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
