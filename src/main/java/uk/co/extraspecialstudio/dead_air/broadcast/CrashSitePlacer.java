package uk.co.extraspecialstudio.dead_air.broadcast;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/** Procedural downed-plane wreck (fallback when RadioTowers plane is unavailable) + shared loot/hostiles. */
public final class CrashSitePlacer {
    private CrashSitePlacer() {}

    public static void placeDebrisWreck(ServerLevel level, BlockPos center) {
        RandomSource random = level.getRandom();
        // Long fuselage spine â€” readable from 30+ blocks
        for (int z = -6; z <= 6; z++) {
            BlockPos p = center.offset(0, 0, z);
            setSolid(level, p, z % 3 == 0 ? Blocks.IRON_BLOCK.defaultBlockState() : Blocks.GRAY_CONCRETE.defaultBlockState());
            if (Math.abs(z) < 5) {
                setSolid(level, p.above(), Blocks.IRON_BARS.defaultBlockState());
            }
        }
        // Wings
        for (int x = -5; x <= 5; x++) {
            if (x == 0) continue;
            BlockPos wing = center.offset(x, 0, 0);
            setSolid(level, wing, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
            if (Math.abs(x) >= 3) {
                setSolid(level, wing.above(), Blocks.IRON_TRAPDOOR.defaultBlockState());
            }
        }
        // Cockpit / tail blobs
        setSolid(level, center.offset(0, 1, -6), Blocks.BLAST_FURNACE.defaultBlockState());
        setSolid(level, center.offset(0, 0, 7), Blocks.COAL_BLOCK.defaultBlockState());
        setSolid(level, center.offset(0, 1, 7), Blocks.COAL_BLOCK.defaultBlockState());
        setSolid(level, center.offset(1, 0, 7), Blocks.IRON_BARS.defaultBlockState());
        setSolid(level, center.offset(-1, 0, 7), Blocks.IRON_BARS.defaultBlockState());

        // Scattered debris ring
        for (int i = 0; i < 18; i++) {
            int dx = random.nextInt(11) - 5;
            int dz = random.nextInt(13) - 6;
            BlockPos p = center.offset(dx, 0, dz);
            BlockState state = switch (random.nextInt(6)) {
                case 0, 1 -> Blocks.IRON_BLOCK.defaultBlockState();
                case 2 -> Blocks.BLAST_FURNACE.defaultBlockState();
                case 3 -> Blocks.COAL_BLOCK.defaultBlockState();
                case 4 -> Blocks.IRON_BARS.defaultBlockState();
                default -> Blocks.GRAY_CONCRETE.defaultBlockState();
            };
            setSolid(level, p, state);
            if (random.nextFloat() < 0.25f) {
                BlockPos above = p.above();
                if (level.getBlockState(above).isAir()) {
                    level.setBlock(above, random.nextBoolean()
                        ? Blocks.CAMPFIRE.defaultBlockState()
                        : Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        // Scorch trail
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            for (int d = 2; d <= 5; d++) {
                BlockPos trail = center.relative(dir, d);
                if (level.getBlockState(trail).isAir() && random.nextFloat() < 0.45f) {
                    level.setBlock(trail, Blocks.FIRE.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        // Scorched sand / dirt ring
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (x * x + z * z > 10) continue;
                BlockPos under = center.offset(x, -1, z);
                if (!level.getBlockState(under).isAir()) {
                    level.setBlock(under, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        // Signal firepit off the wing â€” readable camp-at-wreck silhouette
        BlockPos pit = center.offset(4, 0, 3);
        if (level.getBlockState(pit).canBeReplaced() || level.getBlockState(pit).isAir()) {
            setSolid(level, pit, uk.co.extraspecialstudio.dead_air.block.DeadAirBlocks.SIGNAL_FIREPIT.get().defaultBlockState());
        }
        placeLootAndScars(level, center);
    }

    public static void placeLootAndScars(ServerLevel level, BlockPos center) {
        BlockPos chestPos = center.offset(2, 0, 1);
        if (!level.getBlockState(chestPos).canBeReplaced() && !level.getBlockState(chestPos).isAir()) {
            chestPos = center.offset(-2, 0, 2);
        }
        if (!level.getBlockState(chestPos).canBeReplaced() && !level.getBlockState(chestPos).isAir()) {
            chestPos = center.above(2);
        }
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS);
        BlockEntity be = level.getBlockEntity(chestPos);
        if (be instanceof ChestBlockEntity chest) {
            chest.setLootTable(ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "chests/downed_plane")));
        }
        try {
            uk.co.extraspecialstudio.dead_air.compat.GunLootCompat.injectCrashChest(level, chestPos);
        } catch (Throwable ignored) {
        }
    }

    /** True when enough solid debris exists that the wreck is obvious in-world. */
    public static boolean hasVisibleWreck(ServerLevel level, BlockPos center) {
        int solids = 0;
        boolean chest = false;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-8, 0, -8), center.offset(8, 3, 8))) {
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.CHEST)) chest = true;
            if (s.is(Blocks.IRON_BLOCK) || s.is(Blocks.GRAY_CONCRETE) || s.is(Blocks.LIGHT_GRAY_CONCRETE)
                || s.is(Blocks.COAL_BLOCK) || s.is(Blocks.BLAST_FURNACE) || s.is(Blocks.IRON_BARS)) {
                solids++;
            }
        }
        return chest && solids >= 8;
    }

    public static void spawnLightHostiles(ServerLevel level, BlockPos center) {
        RandomSource random = level.getRandom();
        int count = 2 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            BlockPos at = center.offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4);
            EntityType<?> type = switch (random.nextInt(3)) {
                case 0 -> EntityType.HUSK;
                case 1 -> EntityType.SKELETON;
                default -> EntityType.ZOMBIE;
            };
            type.spawn(level, at, MobSpawnType.EVENT);
        }
    }

    private static void setSolid(ServerLevel level, BlockPos p, BlockState state) {
        BlockState cur = level.getBlockState(p);
        if (cur.is(Blocks.BEDROCK)) return;
        if (cur.isAir() || cur.canBeReplaced() || cur.getDestroySpeed(level, p) >= 0) {
            level.setBlock(p, state, Block.UPDATE_CLIENTS);
        }
    }
}
