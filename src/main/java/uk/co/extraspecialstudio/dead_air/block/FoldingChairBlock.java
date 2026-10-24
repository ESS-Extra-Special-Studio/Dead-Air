package uk.co.extraspecialstudio.dead_air.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import uk.co.extraspecialstudio.dead_air.entity.SeatEntity;

/** Camp folding chair ? right-click to sit (players); survivors use the same seat entity. */
public class FoldingChairBlock extends EbDecorBlock {
    /** Indexed by horizontal facing: N=0 E=1 S=2 W=3 ? no enum-switch synthetic class. */
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        // Matches folding_chair.bbmodel ~1.18? (seat ~y8, backrest ~y16).
        VoxelShape seat = Block.box(1.7, 0, 3.8, 14.3, 8.2, 13.2);
        SHAPES[0] = Shapes.or(seat, Block.box(2.9, 8.2, 10.9, 13.1, 16, 12.1)); // NORTH
        SHAPES[1] = Shapes.or(seat, Block.box(3.9, 8.2, 2.9, 5.1, 16, 13.1));   // EAST
        SHAPES[2] = Shapes.or(seat, Block.box(2.9, 8.2, 3.9, 13.1, 16, 5.1));   // SOUTH
        SHAPES[3] = Shapes.or(seat, Block.box(10.9, 8.2, 2.9, 12.1, 16, 13.1)); // WEST
    }

    public FoldingChairBlock(Properties properties) {
        super(properties, SHAPES[0]);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        if (facing == Direction.EAST) return SHAPES[1];
        if (facing == Direction.SOUTH) return SHAPES[2];
        if (facing == Direction.WEST) return SHAPES[3];
        return SHAPES[0];
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        // Sneak = place/break / pass through; otherwise sit (same seat used by survivors / villagers).
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return SeatEntity.trySit(level, pos, state, player)
            ? InteractionResult.CONSUME
            : InteractionResult.PASS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            SeatEntity.ejectAt(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
