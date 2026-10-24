package uk.co.extraspecialstudio.dead_air.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Clothesline — two posts with a rope spanning three blocks wide (left / centre / right).
 * Model is authored north-facing with X from -16..32; clothes are player-scale.
 */
public class ClotheslineBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape[] OUTLINE = new VoxelShape[4];
    private static final VoxelShape[] COLLISION = new VoxelShape[4];

    static {
        Direction[] dirs = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int i = 0; i < 4; i++) {
            Direction d = dirs[i];
            // Full 3-block footprint for selection outline.
            OUTLINE[i] = rotBox(d, -16, 0, 6, 32, 28, 10);
            // Only the posts collide — walk under the laundry.
            VoxelShape posts = Shapes.or(
                rotBox(d, -15.5, 0, 6, -11.5, 28, 10),
                rotBox(d, 27.5, 0, 6, 31.5, 28, 10)
            );
            COLLISION[i] = posts;
        }
    }

    /** Box in the north-facing model frame (px), rotated to {@code facing}. Width may exceed 16. */
    private static VoxelShape rotBox(Direction facing, double x0, double y0, double z0,
                                     double x1, double y1, double z1) {
        double ax, az, bx, bz;
        if (facing == Direction.EAST) {
            ax = 16 - z0; az = x0; bx = 16 - z1; bz = x1;
        } else if (facing == Direction.SOUTH) {
            ax = 16 - x0; az = 16 - z0; bx = 16 - x1; bz = 16 - z1;
        } else if (facing == Direction.WEST) {
            ax = z0; az = 16 - x0; bx = z1; bz = 16 - x1;
        } else {
            ax = x0; az = z0; bx = x1; bz = z1;
        }
        return Shapes.box(Math.min(ax, bx) / 16.0, y0 / 16.0, Math.min(az, bz) / 16.0,
            Math.max(ax, bx) / 16.0, y1 / 16.0, Math.max(az, bz) / 16.0);
    }

    private static int idx(Direction d) {
        if (d == Direction.EAST) return 1;
        if (d == Direction.SOUTH) return 2;
        if (d == Direction.WEST) return 3;
        return 0;
    }

    public ClotheslineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos pos = context.getClickedPos();
        // Need free cells on both sides (3-block span centred on the placed block).
        BlockPos left = pos.relative(facing.getCounterClockWise());
        BlockPos right = pos.relative(facing.getClockWise());
        if (!context.getLevel().getBlockState(left).canBeReplaced()) return null;
        if (!context.getLevel().getBlockState(right).canBeReplaced()) return null;
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        // Clear the 3-block span so tall grass/snow do not clip the posts (matches EventSitePlacer).
        Direction facing = state.getValue(FACING);
        BlockPos left = pos.relative(facing.getCounterClockWise());
        BlockPos right = pos.relative(facing.getClockWise());
        if (level.getBlockState(left).canBeReplaced()) {
            level.setBlock(left, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        if (level.getBlockState(right).canBeReplaced()) {
            level.setBlock(right, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE[idx(state.getValue(FACING))];
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION[idx(state.getValue(FACING))];
    }
}
