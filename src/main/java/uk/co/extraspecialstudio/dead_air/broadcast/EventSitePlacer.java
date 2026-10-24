package uk.co.extraspecialstudio.dead_air.broadcast;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.block.ClotheslineBlock;
import uk.co.extraspecialstudio.dead_air.block.DeadAirBlocks;
import uk.co.extraspecialstudio.dead_air.block.EbDecorBlock;
import uk.co.extraspecialstudio.dead_air.block.SurvivorTentBlock;

import javax.annotation.Nullable;

/**
 * Procedural EB site kits — distinct wasteland silhouettes for camp / auction / convoy / broken relay.
 */
public final class EventSitePlacer {
    private EventSitePlacer() {}

    @Nullable
    public static BlockPos place(ServerLevel level, PendingSites.Site site) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, site.center.getX(), site.center.getZ());
        BlockPos center = new BlockPos(site.center.getX(), y, site.center.getZ());
        return switch (site.kind) {
            case PendingSites.KIND_AUCTION -> placeAuction(level, center);
            case PendingSites.KIND_CONVOY -> placeConvoy(level, center);
            case PendingSites.KIND_RELAY -> placeRelay(level, center, site.template);
            default -> placeCamp(level, center);
        };
    }

    /** Stranded camp: packed dirt ring, firepit, tents facing in, crates, scrap fence. */
    public static BlockPos placeCamp(ServerLevel level, BlockPos center) {
        RandomSource random = level.getRandom();
        center = clearPad(level, center, 6);
        layCampFloor(level, center, 4, Blocks.COARSE_DIRT.defaultBlockState(), Blocks.PODZOL.defaultBlockState());
        BlockPos fire = center;
        set(level, fire, DeadAirBlocks.SIGNAL_FIREPIT.get().defaultBlockState());
        // Log seats around the pit
        set(level, fire.offset(2, 0, 0), Blocks.OAK_LOG.defaultBlockState());
        set(level, fire.offset(-2, 0, 1), Blocks.SPRUCE_LOG.defaultBlockState());
        set(level, fire.offset(0, 0, 2), Blocks.OAK_LOG.defaultBlockState());

        placeTent(level, fire.offset(4, 0, 1), Direction.WEST);
        placeTent(level, fire.offset(-3, 0, 2), Direction.EAST);
        if (random.nextBoolean()) {
            placeTent(level, fire.offset(1, 0, -4), Direction.SOUTH);
        }

        placeChair(level, fire.offset(2, 0, 2), Direction.WEST);
        if (random.nextBoolean()) {
            placeChair(level, fire.offset(-2, 0, 2), Direction.EAST);
        }
        if (random.nextFloat() < 0.7f) {
            placeBackpack(level, fire.offset(4, 0, -1), Direction.WEST);
        }
        placeCampTable(level, fire.offset(3, 0, -1), Direction.WEST);
        if (random.nextFloat() < 0.85f) {
            placeCampLantern(level, fire.offset(-2, 0, -1), Direction.SOUTH);
        }
        if (random.nextFloat() < 0.65f) {
            placeJerrycan(level, fire.offset(1, 0, 3), Direction.NORTH);
        }
        placeCookingPot(level, fire.offset(0, 0, -3), Direction.SOUTH);
        if (random.nextFloat() < 0.55f) {
            placeMedicalCase(level, fire.offset(-3, 0, -2), Direction.EAST);
        }
        placeLootChest(level, fire.offset(3, 0, 2), "chests/survivor_camp");
        if (random.nextFloat() < 0.45f) {
            placeLootChest(level, fire.offset(-4, 0, -1), "chests/survivor_camp");
        }
        fenceScrap(level, fire, random, 7);
        if (random.nextFloat() < 0.7f) {
            // West edge — 3-block span; after scrap so fence junk can't sit in the posts.
            placeClothesline(level, fire.offset(-5, 0, 0), Direction.EAST);
        }
        pathDots(level, fire, Direction.SOUTH, 5);
        return standNear(level, fire);
    }

    /** Auction stall: canvas awning, stall counters, goods crates, side tent + firepit. */
    public static BlockPos placeAuction(ServerLevel level, BlockPos center) {
        RandomSource random = level.getRandom();
        center = clearPad(level, center, 7);
        layCampFloor(level, center, 5, Blocks.SMOOTH_STONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState());
        BlockPos stall = center;

        // Corner posts
        int[][] posts = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
        for (int[] p : posts) {
            for (int y = 0; y <= 3; y++) {
                set(level, stall.offset(p[0], y, p[1]), Blocks.OAK_FENCE.defaultBlockState());
            }
        }
        // Awning roof
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (Math.abs(x) == 3 || Math.abs(z) == 3 || (Math.abs(x) + Math.abs(z)) % 2 == 0) {
                    Block roof = Math.abs(x) <= 1 && Math.abs(z) <= 1
                        ? Blocks.WHITE_WOOL : Blocks.LIGHT_GRAY_WOOL;
                    set(level, stall.offset(x, 4, z), roof.defaultBlockState());
                }
            }
        }
        // Counters
        for (int x = -2; x <= 2; x++) {
            set(level, stall.offset(x, 0, -1), Blocks.OAK_SLAB.defaultBlockState());
            set(level, stall.offset(x, 1, -1), Blocks.OAK_TRAPDOOR.defaultBlockState());
        }
        set(level, stall.offset(0, 0, 0), Blocks.BARREL.defaultBlockState());
        placeLootChest(level, stall.offset(1, 0, 1), "chests/survivor_camp");
        placeLootChest(level, stall.offset(-1, 0, 1), "chests/survivor_camp");
        placeCampTable(level, stall.offset(2, 0, 0), Direction.WEST);
        set(level, stall.offset(-2, 0, 0), Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        set(level, stall.offset(0, 0, -3), Blocks.OAK_SIGN.defaultBlockState());
        // Lantern on the ground by the sign — not floating above it.
        placeCampLantern(level, stall.offset(1, 0, -3), Direction.NORTH);

        BlockPos fire = stall.offset(5, 0, 2);
        set(level, fire, DeadAirBlocks.SIGNAL_FIREPIT.get().defaultBlockState());
        placeTent(level, stall.offset(-5, 0, 1), Direction.EAST);
        placeMedicalCase(level, stall.offset(-3, 0, 0), Direction.EAST);
        placeChair(level, stall.offset(-1, 0, 3), Direction.NORTH);
        if (random.nextFloat() < 0.6f) {
            placeBackpack(level, stall.offset(-4, 0, 2), Direction.EAST);
        }
        pathDots(level, stall, Direction.NORTH, 4);
        return standNear(level, stall);
    }

    /** Refugee convoy: three tents, central fire, packed chests, trail markers. */
    public static BlockPos placeConvoy(ServerLevel level, BlockPos center) {
        RandomSource random = level.getRandom();
        center = clearPad(level, center, 8);
        layCampFloor(level, center, 5, Blocks.DIRT_PATH.defaultBlockState(), Blocks.COARSE_DIRT.defaultBlockState());
        BlockPos fire = center;
        set(level, fire, DeadAirBlocks.SIGNAL_FIREPIT.get().defaultBlockState());
        set(level, fire.offset(2, 0, 0), Blocks.CAMPFIRE.defaultBlockState().setValue(
            net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, false));

        placeTent(level, fire.offset(4, 0, 2), Direction.WEST);
        placeTent(level, fire.offset(-4, 0, 1), Direction.EAST);
        placeTent(level, fire.offset(0, 0, -5), Direction.SOUTH);
        if (random.nextFloat() < 0.5f) {
            placeTent(level, fire.offset(3, 0, -3), Direction.WEST);
        }

        placeLootChest(level, fire.offset(2, 0, -1), "chests/survivor_camp");
        placeLootChest(level, fire.offset(-2, 0, -2), "chests/survivor_camp");
        set(level, fire.offset(0, 0, 3), Blocks.HAY_BLOCK.defaultBlockState());
        placeChair(level, fire.offset(3, 0, 1), Direction.WEST);
        placeBackpack(level, fire.offset(-3, 0, 2), Direction.EAST);
        placeCampTable(level, fire.offset(4, 0, -1), Direction.SOUTH);
        placeCookingPot(level, fire.offset(-1, 0, 2), Direction.EAST);
        placeJerrycan(level, fire.offset(-4, 0, -2), Direction.EAST);
        if (random.nextFloat() < 0.5f) {
            placeJerrycan(level, fire.offset(5, 0, 0), Direction.WEST);
        }
        // Road cones on the south approach (keep clear of clothesline / trail posts).
        placeTrafficCone(level, fire.offset(2, 0, 5), Direction.NORTH);
        placeTrafficCone(level, fire.offset(-2, 0, 5), Direction.NORTH);

        // Trail markers south — start past the cone line
        for (int i = 1; i <= 6; i++) {
            BlockPos m = fire.offset((i % 2 == 0) ? 1 : -1, 0, 6 + i);
            set(level, m, Blocks.OAK_FENCE.defaultBlockState());
            if (i % 2 == 0) set(level, m.above(), Blocks.TORCH.defaultBlockState());
            if (i % 3 == 0) set(level, m.offset(0, -1, 0), Blocks.DIRT_PATH.defaultBlockState());
        }
        fenceScrap(level, fire, random, 8);
        // West edge laundry — after scrap/trail so nothing overwrites the 3-block span
        placeClothesline(level, fire.offset(-6, 0, 0), Direction.EAST);
        return standNear(level, fire);
    }

    /** Broken radio mast: stone platform, copper/iron mast, Broken Relay cabinet, debris. */
    public static BlockPos placeRelay(ServerLevel level, BlockPos center, String template) {
        int scale = 0;
        if (template != null) {
            if (template.contains("large")) scale = 2;
            else if (template.contains("medium")) scale = 1;
        }
        center = clearPad(level, center, 5 + scale);
        BlockPos base = center;
        int pr = 2 + scale;
        for (int x = -pr; x <= pr; x++) {
            for (int z = -pr; z <= pr; z++) {
                Block floor = (Math.abs(x) == pr || Math.abs(z) == pr)
                    ? Blocks.STONE_BRICKS : Blocks.SMOOTH_STONE;
                set(level, base.offset(x, -1, z), floor.defaultBlockState());
                if ((Math.abs(x) == pr || Math.abs(z) == pr) && (x + z) % 2 == 0) {
                    set(level, base.offset(x, 0, z), Blocks.IRON_BARS.defaultBlockState());
                }
            }
        }
        // Mast
        int height = 6 + scale * 2;
        for (int y = 0; y < height; y++) {
            Block mast;
            if (y >= height - 1) mast = Blocks.LIGHTNING_ROD;
            else if (y % 3 == 0) mast = Blocks.COPPER_BLOCK;
            else if (y % 2 == 0) mast = Blocks.OXIDIZED_COPPER;
            else mast = Blocks.IRON_BARS;
            set(level, base.offset(0, y, 0), mast.defaultBlockState());
        }
        // Cross-arms
        int armY = height - 2;
        set(level, base.offset(-2, armY, 0), Blocks.IRON_BARS.defaultBlockState());
        set(level, base.offset(-1, armY, 0), Blocks.IRON_BARS.defaultBlockState());
        set(level, base.offset(1, armY, 0), Blocks.IRON_BARS.defaultBlockState());
        set(level, base.offset(2, armY, 0), Blocks.IRON_BARS.defaultBlockState());
        if (scale >= 1) {
            set(level, base.offset(0, armY, -2), Blocks.IRON_BARS.defaultBlockState());
            set(level, base.offset(0, armY, -1), Blocks.IRON_BARS.defaultBlockState());
            set(level, base.offset(0, armY, 1), Blocks.IRON_BARS.defaultBlockState());
            set(level, base.offset(0, armY, 2), Blocks.IRON_BARS.defaultBlockState());
        }
        // Fallen mast section
        for (int i = 1; i <= 3 + scale; i++) {
            set(level, base.offset(i, 0, 2),
                (i % 2 == 0 ? Blocks.WEATHERED_COPPER : Blocks.EXPOSED_COPPER).defaultBlockState());
        }
        BlockPos relay = base.offset(-3, 0, 0);
        set(level, relay, DeadAirBlocks.BROKEN_RELAY.get().defaultBlockState()
            .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        placeLootChest(level, base.offset(2, 0, -2), "chests/broken_relay");
        set(level, base.offset(-1, 0, 3), Blocks.REDSTONE_TORCH.defaultBlockState());
        set(level, base.offset(1, 0, -1), Blocks.DAYLIGHT_DETECTOR.defaultBlockState());
        RandomSource random = level.getRandom();
        placeJerrycan(level, base.offset(3, 0, -1), Direction.WEST);
        placeCampLantern(level, base.offset(-3, 0, -2), Direction.EAST);
        if (scale >= 1) {
            set(level, base.offset(3, 0, 1), Blocks.BARREL.defaultBlockState());
            placeCampLantern(level, base.offset(-2, 0, -2), Direction.SOUTH);
        }
        if (scale >= 2) {
            set(level, base.offset(4, 0, 0), Blocks.COPPER_BLOCK.defaultBlockState());
            set(level, base.offset(-4, 0, 1), Blocks.BLAST_FURNACE.defaultBlockState());
            placeTent(level, base.offset(0, 0, 5), Direction.NORTH);
        }
        return relay;
    }

    private static void placeTent(ServerLevel level, BlockPos pos, Direction facing) {
        // Canopy runs two blocks deep opposite the opening — clear the rear cell.
        BlockPos rear = pos.relative(facing.getOpposite());
        if (!level.getBlockState(pos).canBeReplaced()) return;
        if (!level.getBlockState(rear).canBeReplaced()) return;
        set(level, rear, Blocks.AIR.defaultBlockState());
        BlockState tent = DeadAirBlocks.SURVIVOR_TENT.get().defaultBlockState()
            .setValue(SurvivorTentBlock.FACING, facing);
        set(level, pos, tent);
    }

    private static void placeChair(ServerLevel level, BlockPos pos, Direction facing) {
        set(level, pos, DeadAirBlocks.FOLDING_CHAIR.get().defaultBlockState()
            .setValue(EbDecorBlock.FACING, facing));
    }

    private static void placeBackpack(ServerLevel level, BlockPos pos, Direction facing) {
        set(level, pos, DeadAirBlocks.SURVIVOR_BACKPACK.get().defaultBlockState()
            .setValue(EbDecorBlock.FACING, facing));
    }

    private static void placeCampTable(ServerLevel level, BlockPos pos, Direction facing) {
        set(level, pos, DeadAirBlocks.CAMP_TABLE.get().defaultBlockState()
            .setValue(EbDecorBlock.FACING, facing));
    }

    private static void placeCampLantern(ServerLevel level, BlockPos pos, Direction facing) {
        set(level, pos, DeadAirBlocks.CAMP_LANTERN.get().defaultBlockState()
            .setValue(EbDecorBlock.FACING, facing));
    }

    private static void placeJerrycan(ServerLevel level, BlockPos pos, Direction facing) {
        set(level, pos, DeadAirBlocks.JERRYCAN.get().defaultBlockState()
            .setValue(EbDecorBlock.FACING, facing));
    }

    private static void placeCookingPot(ServerLevel level, BlockPos pos, Direction facing) {
        set(level, pos, DeadAirBlocks.COOKING_POT.get().defaultBlockState()
            .setValue(EbDecorBlock.FACING, facing));
    }

    private static void placeMedicalCase(ServerLevel level, BlockPos pos, Direction facing) {
        set(level, pos, DeadAirBlocks.MEDICAL_CASE.get().defaultBlockState()
            .setValue(EbDecorBlock.FACING, facing));
    }

    private static void placeTrafficCone(ServerLevel level, BlockPos pos, Direction facing) {
        set(level, pos, DeadAirBlocks.TRAFFIC_CONE.get().defaultBlockState()
            .setValue(EbDecorBlock.FACING, facing));
    }

    private static void placeClothesline(ServerLevel level, BlockPos pos, Direction facing) {
        // Model spans three blocks left–right of facing; clear sides so scrap/fences don't clip posts.
        BlockPos left = pos.relative(facing.getCounterClockWise());
        BlockPos right = pos.relative(facing.getClockWise());
        if (!level.getBlockState(pos).canBeReplaced()) return;
        if (!level.getBlockState(left).canBeReplaced()) return;
        if (!level.getBlockState(right).canBeReplaced()) return;
        set(level, left, Blocks.AIR.defaultBlockState());
        set(level, right, Blocks.AIR.defaultBlockState());
        set(level, pos, DeadAirBlocks.CLOTHESLINE.get().defaultBlockState()
            .setValue(ClotheslineBlock.FACING, facing));
    }

    private static void layCampFloor(ServerLevel level, BlockPos center, int r, BlockState a, BlockState b) {
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > (r + 0.5) * (r + 0.5)) continue;
                BlockState floor = ((x + z) & 1) == 0 ? a : b;
                set(level, center.offset(x, -1, z), floor);
            }
        }
    }

    private static void cratePile(ServerLevel level, BlockPos origin, RandomSource random) {
        placeLootChest(level, origin, "chests/survivor_camp");
        set(level, origin.above(), random.nextBoolean()
            ? Blocks.BARREL.defaultBlockState()
            : Blocks.OAK_SLAB.defaultBlockState());
        if (random.nextBoolean()) {
            set(level, origin.offset(1, 0, 0), Blocks.BARREL.defaultBlockState());
        }
    }

    /** Place a chest with Dead Air loot table + soft apocalypse food/med/gear inject. */
    private static void placeLootChest(ServerLevel level, BlockPos pos, String lootPath) {
        if (!level.getBlockState(pos).canBeReplaced() && !level.getBlockState(pos).isAir()) {
            return;
        }
        set(level, pos, Blocks.CHEST.defaultBlockState());
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ChestBlockEntity chest) {
            chest.setLootTable(
                ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, lootPath),
                level.getRandom().nextLong());
            chest.setChanged();
        }
        // Soft-compat food/med/gear comes from loot-table tag pools — do not also inject
        // into empty slots or open-time generation doubles the haul.
    }

    private static void pathDots(ServerLevel level, BlockPos origin, Direction dir, int len) {
        for (int i = 1; i <= len; i++) {
            BlockPos p = origin.relative(dir, i);
            set(level, p.below(), Blocks.DIRT_PATH.defaultBlockState());
        }
    }

    private static void fenceScrap(ServerLevel level, BlockPos origin, RandomSource random) {
        fenceScrap(level, origin, random, 6);
    }

    private static void fenceScrap(ServerLevel level, BlockPos origin, RandomSource random, int count) {
        for (int i = 0; i < count; i++) {
            BlockPos p = origin.offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4);
            if (p.closerThan(origin, 1.5)) continue;
            if (!level.getBlockState(p).isAir()) continue;
            BlockState scrap = switch (random.nextInt(4)) {
                case 0 -> Blocks.OAK_FENCE.defaultBlockState();
                case 1 -> Blocks.COBBLESTONE.defaultBlockState();
                case 2 -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                default -> Blocks.OAK_PLANKS.defaultBlockState();
            };
            set(level, p, scrap);
        }
    }

    private static BlockPos standNear(ServerLevel level, BlockPos fire) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            for (int d = 2; d <= 5; d++) {
                BlockPos c = fire.relative(dir, d);
                if (level.getBlockState(c).isAir() && level.getBlockState(c.above()).isAir()
                    && !level.getBlockState(c.below()).isAir()) {
                    return c;
                }
            }
        }
        // Last resort: clear a cell north of the fire so survivors/traders never spawn in scrap.
        BlockPos fallback = fire.north(3);
        if (!level.getBlockState(fallback).isAir()) {
            set(level, fallback, Blocks.AIR.defaultBlockState());
        }
        if (!level.getBlockState(fallback.above()).isAir()) {
            set(level, fallback.above(), Blocks.AIR.defaultBlockState());
        }
        return fallback;
    }

    /** Clears the pad and returns center snapped to the walk-layer Y the clearer used. */
    private static BlockPos clearPad(ServerLevel level, BlockPos center, int r) {
        int padY = SitePadClearer.prepare(level, center, r);
        if (padY == center.getY()) return center;
        return new BlockPos(center.getX(), padY, center.getZ());
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
}
