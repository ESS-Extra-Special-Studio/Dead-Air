package uk.co.extraspecialstudio.dead_air.broadcast;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.compat.AweRoadsCompat;
import uk.co.extraspecialstudio.dead_air.compat.FtbChunksCompat;

/**
 * Shared gates for EB site create / drift / place: claims, structures, density, AWE roads.
 * Terrain flatness / water stay in {@link PendingSites#isValidBuildSite}.
 */
public final class SitePlacementRules {

    /** Keep camps / relays / auctions / crash off active + paved AWE corridors. */
    public static final int AWE_ROAD_AVOID_RADIUS = 40;
    /** Convoys may sit on roads — only avoid while a carve is actively pending through the pad. */
    public static final int AWE_ROAD_CONVOY_PENDING_AVOID = 12;

    private SitePlacementRules() {}

    /**
     * Full soft gate for a candidate center (loaded checks when chunks exist).
     * @param clearRadius approximate kit clear footprint for claim/structure sampling
     * @param exclude site being placed (ignored in density); null on create
     */
    public static boolean allows(ServerLevel level, BlockPos center, int clearRadius,
                                 PendingSites.Site exclude) {
        if (level == null || center == null) return false;
        if (Config.ebSiteAvoidClaims && isClaimedFootprint(level, center, clearRadius)) return false;
        if (Config.ebSiteAvoidStructures && nearStructure(level, center, clearRadius)) return false;
        if (!densityOk(level, center, exclude)) return false;
        if (!aweRoadOk(level, center, exclude)) return false;
        return true;
    }

    /**
     * Most EB kits avoid paved/pending AWE roads so carves and camps do not fight.
     * Convoys are allowed on finished roads (only reject if a pending carve is right on top).
     */
    public static boolean aweRoadOk(ServerLevel level, BlockPos center, PendingSites.Site exclude) {
        if (!AweRoadsCompat.isLoaded()) return true;
        boolean convoy = exclude != null && PendingSites.KIND_CONVOY.equals(exclude.kind);
        if (convoy) {
            // Convoys sit on finished roads — only dodge an in-progress carve through the pad
            return !AweRoadsCompat.isPendingCarveNear(level, center.getX(), center.getZ(),
                AWE_ROAD_CONVOY_PENDING_AVOID);
        }
        // Camps / relays / auctions / crash: stay off paved + pending corridors
        return !AweRoadsCompat.isRoadBusyNear(level, center.getX(), center.getZ(), AWE_ROAD_AVOID_RADIUS);
    }

    /** Any FTB-claimed chunk overlapping the clear disk. Soft-false if FTB absent. */
    public static boolean isClaimedFootprint(ServerLevel level, BlockPos center, int clearRadius) {
        int r = Math.max(0, clearRadius);
        int minCx = (center.getX() - r) >> 4;
        int maxCx = (center.getX() + r) >> 4;
        int minCz = (center.getZ() - r) >> 4;
        int maxCz = (center.getZ() + r) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (FtbChunksCompat.isChunkClaimed(level, cx, cz)) return true;
            }
        }
        return false;
    }

    /** Village tag or any structure piece intersecting sampled points in the footprint. */
    public static boolean nearStructure(ServerLevel level, BlockPos center, int clearRadius) {
        if (!level.hasChunk(center.getX() >> 4, center.getZ() >> 4)) {
            // Unloaded: cannot query structure starts reliably — defer to place-time recheck.
            return false;
        }
        int r = Math.max(0, clearRadius);
        BlockPos[] samples = {
            center,
            center.offset(r, 0, 0),
            center.offset(-r, 0, 0),
            center.offset(0, 0, r),
            center.offset(0, 0, -r),
            center.offset(r, 0, r),
            center.offset(-r, 0, -r)
        };
        for (BlockPos sample : samples) {
            if (!level.hasChunk(sample.getX() >> 4, sample.getZ() >> 4)) continue;
            // Villages only — getAllStructuresAt rejects mineshafts/ruins/etc. and soft-locks
            // sticky relays in structure-heavy packs.
            try {
                StructureStart village = level.structureManager()
                    .getStructureWithPieceAt(sample, StructureTags.VILLAGE);
                if (village != null && village.isValid()) return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    /** Max per chunk + min separation vs other Dead Air sites. */
    public static boolean densityOk(ServerLevel level, BlockPos center, PendingSites.Site exclude) {
        PendingSites data = PendingSites.get(level.getServer());
        if (data == null) return true;
        ChunkPos chunk = new ChunkPos(center);
        int inChunk = 0;
        long minSepSq = (long) Config.ebSiteMinSeparation * (long) Config.ebSiteMinSeparation;
        for (PendingSites.Site s : data.getSites()) {
            if (exclude != null && s == exclude) continue;
            if (exclude != null && exclude.siteId != null && exclude.siteId.equals(s.siteId)) continue;
            if (s.center == null) continue;
            ChunkPos sc = new ChunkPos(s.center);
            if (sc.x == chunk.x && sc.z == chunk.z) {
                inChunk++;
                if (inChunk >= Config.ebSiteMaxPerChunk) return false;
            }
            double dx = (double) s.center.getX() - center.getX();
            double dz = (double) s.center.getZ() - center.getZ();
            if (dx * dx + dz * dz < minSepSq) return false;
        }
        return true;
    }
}
