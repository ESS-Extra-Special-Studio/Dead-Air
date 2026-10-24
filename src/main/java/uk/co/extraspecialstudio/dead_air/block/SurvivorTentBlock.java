package uk.co.extraspecialstudio.dead_air.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * One-man wasteland tent — 1 block wide × 2 deep × 2 tall (opens on the facing side).
 * Right-click from outside to sleep; wake/cancel exits outside the tent.
 */
public class SurvivorTentBlock extends HorizontalDirectionalBlock {
    // Model (north-facing frame): opening toward -Z, A-frame canopy runs +Z for 2 blocks.
    // Outline = solid stepped A-frame silhouette; collision = hollow stepped walls + floor so players can step in.
    private static final double LEAN_TAN = Math.tan(Math.toRadians(22.5));
    private static final VoxelShape[] OUTLINE = new VoxelShape[4];
    private static final VoxelShape[] COLLISION = new VoxelShape[4];

    static {
        Direction[] dirs = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int i = 0; i < 4; i++) {
            Direction d = dirs[i];
            VoxelShape outline = Shapes.empty();
            VoxelShape collision = rotBox(d, 0.8, 0, 1, 15.2, 0.4, 31);
            for (int k = 0; k < 9; k++) {
                double y0 = 0.35 + k * 2.0;
                double y1 = y0 + 2.0;
                double lx0 = 0.5 + (y0 - 0.35) * LEAN_TAN;
                double lx1 = Math.min(8.0, lx0 + 2.2);
                outline = Shapes.or(outline, rotBox(d, lx0, y0, 1, 16 - lx0, y1, 31));
                collision = Shapes.or(collision, rotBox(d, lx0, y0, 1, lx1, y1, 31));
                collision = Shapes.or(collision, rotBox(d, 16 - lx1, y0, 1, 16 - lx0, y1, 31));
            }
            OUTLINE[i] = outline;
            COLLISION[i] = collision;
        }
    }

    /** Box in the north-facing model frame (px), rotated to {@code facing}. Depth may exceed 16. */
    private static VoxelShape rotBox(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
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

    public SurvivorTentBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        // Canopy runs two blocks deep behind the opening ? that rear cell must be free.
        BlockPos rear = context.getClickedPos().relative(facing.getOpposite());
        if (!context.getLevel().getBlockState(rear).canBeReplaced()) return null;
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        // Clear the rear canopy cell so grass/snow do not clip the A-frame (matches EventSitePlacer).
        Direction facing = state.getValue(FACING);
        BlockPos rear = pos.relative(facing.getOpposite());
        if (level.getBlockState(rear).canBeReplaced()) {
            level.setBlock(rear, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
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

    @Override
    public boolean isBed(BlockState state, BlockGetter level, BlockPos pos, @Nullable Entity player) {
        return true;
    }

    @Override
    public Direction getBedDirection(BlockState state, LevelReader level, BlockPos pos) {
        return state.getValue(FACING);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.CONSUME;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;

        Direction facing = state.getValue(FACING);
        BlockPos exit = pos.relative(facing);
        TentSleepTracker.mark(sp.getUUID(), exit);

        var result = sp.startSleepInBed(pos);
        if (result.left().isPresent()) {
            TentSleepTracker.clear(sp.getUUID());
            Player.BedSleepingProblem problem = result.left().get();
            Component msg = problem.getMessage();
            if (msg != null) sp.displayClientMessage(msg, true);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.SUCCESS;
    }
}
