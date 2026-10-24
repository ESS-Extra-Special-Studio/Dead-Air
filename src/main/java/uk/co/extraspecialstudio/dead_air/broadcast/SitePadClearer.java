package uk.co.extraspecialstudio.dead_air.broadcast;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import uk.co.extraspecialstudio.dead_air.Config;

/**
 * Flattens a circular-ish pad and strips trees/foliage before procedural EB kits place.
 */
public final class SitePadClearer {
    private static final int CLEAR_UP = 28;

    private SitePadClearer() {}

    /**
     * Clears the pad and returns the walk-layer Y kits must place on.
     * Hard clear uses median surface Y (can differ from the center heightmap);
     * soft clear keeps {@code center.getY()}.
     */
    public static int prepare(ServerLevel level, BlockPos center, int radius) {
        if (level == null || center == null || radius < 1) return center == null ? 0 : center.getY();
        if (!Config.ebSiteClearTrees) {
            softClear(level, center, radius);
            return center.getY();
        }
        int r = radius;
        int targetY = medianSurfaceY(level, center, r);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > (r + 0.5) * (r + 0.5)) continue;
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                // Strip canopy / junk above the pad plane.
                for (int y = targetY; y <= targetY + CLEAR_UP; y++) {
                    m.set(x, y, z);
                    BlockState s = level.getBlockState(m);
                    if (s.isAir()) continue;
                    if (shouldStrip(s)) {
                        level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
                // Ensure solid footing under the pad.
                m.set(x, targetY - 1, z);
                BlockState ground = level.getBlockState(m);
                if (ground.isAir() || ground.canBeReplaced() || !ground.getFluidState().isEmpty()) {
                    level.setBlock(m, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                // Clear the walk layer itself if something solid was left.
                m.set(x, targetY, z);
                BlockState walk = level.getBlockState(m);
                if (!walk.isAir() && shouldStrip(walk)) {
                    level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        return targetY;
    }

    /** Legacy soft clear (plants only) when hard clear is disabled. */
    public static void softClear(ServerLevel level, BlockPos center, int r) {
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = 0; y <= 5; y++) {
                    BlockPos p = center.offset(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (s.is(Blocks.CACTUS) || s.is(Blocks.DEAD_BUSH) || s.is(Blocks.SHORT_GRASS)
                        || s.is(Blocks.TALL_GRASS) || s.is(Blocks.SNOW) || s.is(Blocks.FERN)
                        || s.is(Blocks.LARGE_FERN) || s.canBeReplaced()) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
                BlockPos ground = center.offset(x, -1, z);
                if (level.getBlockState(ground).isAir() || level.getBlockState(ground).canBeReplaced()) {
                    level.setBlock(ground, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static int medianSurfaceY(ServerLevel level, BlockPos center, int r) {
        int[] samples = new int[32];
        int n = 0;
        for (int dx = -r; dx <= r && n < samples.length; dx += Math.max(1, r / 2)) {
            for (int dz = -r; dz <= r && n < samples.length; dz += Math.max(1, r / 2)) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                samples[n++] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            }
        }
        if (n == 0) {
            return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ());
        }
        java.util.Arrays.sort(samples, 0, n);
        return samples[n / 2];
    }

    private static boolean shouldStrip(BlockState s) {
        if (s.isAir()) return false;
        if (s.is(Blocks.BEDROCK) || s.is(Blocks.BARRIER) || s.is(Blocks.COMMAND_BLOCK)
            || s.is(Blocks.CHAIN_COMMAND_BLOCK) || s.is(Blocks.REPEATING_COMMAND_BLOCK)) {
            return false;
        }
        if (s.is(Blocks.CHEST) || s.is(Blocks.BARREL) || s.is(Blocks.SPAWNER)
            || s.is(Blocks.ENDER_CHEST) || s.is(Blocks.TRAPPED_CHEST)) {
            return false;
        }
        // Trees, leaves, plants, soft terrain junk, and replaceable.
        if (s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(BlockTags.REPLACEABLE)
            || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS)
            || s.canBeReplaced()) {
            return true;
        }
        // Dirt/grass columns sitting above the pad (odd terrain / roots)
        if (s.is(BlockTags.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL)
            || s.is(Blocks.MYCELIUM) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.SNOW_BLOCK)
            || s.is(Blocks.POWDER_SNOW)) {
            return true;
        }
        // Thin scrap that kits will rebuild over.
        if (s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE)
            || s.is(Blocks.OAK_FENCE) || s.is(Blocks.OAK_PLANKS)
            || s.is(Blocks.STONE) || s.is(Blocks.ANDESITE) || s.is(Blocks.GRANITE)
            || s.is(Blocks.DIORITE)) {
            return true;
        }
        return s.canBeReplaced();
    }
}
