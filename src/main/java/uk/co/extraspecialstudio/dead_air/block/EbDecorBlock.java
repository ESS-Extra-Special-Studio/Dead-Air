package uk.co.extraspecialstudio.dead_air.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Simple horizontal-facing EB site decor (chair, crates, etc.). */
public class EbDecorBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<EbDecorBlock> CODEC = simpleCodec(p -> new EbDecorBlock(p, Block.box(0, 0, 0, 16, 16, 16)));

    /** Shapes for N/E/S/W — collision matches the rotated model. */
    private final VoxelShape[] shapes = new VoxelShape[4];

    public EbDecorBlock(Properties properties, VoxelShape northShape) {
        super(properties);
        shapes[0] = northShape;
        shapes[1] = rotateY(northShape, Direction.EAST);
        shapes[2] = rotateY(northShape, Direction.SOUTH);
        shapes[3] = rotateY(northShape, Direction.WEST);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public static EbDecorBlock box(Properties properties, double x0, double y0, double z0,
                                   double x1, double y1, double z1) {
        return new EbDecorBlock(properties, Block.box(x0, y0, z0, x1, y1, z1));
    }

    /** Rotate a north-authored shape around Y to {@code facing}. No enum-switch synthetics. */
    static VoxelShape rotateY(VoxelShape shape, Direction facing) {
        if (facing == Direction.NORTH) return shape;
        VoxelShape out = Shapes.empty();
        for (AABB box : shape.toAabbs()) {
            double x0 = box.minX;
            double y0 = box.minY;
            double z0 = box.minZ;
            double x1 = box.maxX;
            double y1 = box.maxY;
            double z1 = box.maxZ;
            double nx0, nz0, nx1, nz1;
            if (facing == Direction.EAST) {
                nx0 = 1.0 - z1; nz0 = x0; nx1 = 1.0 - z0; nz1 = x1;
            } else if (facing == Direction.SOUTH) {
                nx0 = 1.0 - x1; nz0 = 1.0 - z1; nx1 = 1.0 - x0; nz1 = 1.0 - z0;
            } else { // WEST
                nx0 = z0; nz0 = 1.0 - x1; nx1 = z1; nz1 = 1.0 - x0;
            }
            out = Shapes.joinUnoptimized(out,
                Shapes.box(Math.min(nx0, nx1), y0, Math.min(nz0, nz1),
                    Math.max(nx0, nx1), y1, Math.max(nz0, nz1)),
                BooleanOp.OR);
        }
        return out.optimize();
    }

    private static int facingIndex(Direction facing) {
        if (facing == Direction.EAST) return 1;
        if (facing == Direction.SOUTH) return 2;
        if (facing == Direction.WEST) return 3;
        return 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes[facingIndex(state.getValue(FACING))];
    }
}
