package uk.co.extraspecialstudio.dead_air.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import uk.co.extraspecialstudio.dead_air.broadcast.RelayInvestigation;

/** Glowing relay cabinet at a Broken Relay site. Right-click to recover its log. */
public class BrokenRelayBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    /** Cabinet body + stub mast — matches multi-element block model. */
    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(2, 0, 3, 14, 12, 13),
        Block.box(7, 12, 7, 9, 25, 9)
    );

    public BrokenRelayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, true));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            RelayInvestigation.investigate(sp, pos, state);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (state.getValue(ACTIVE)) return 0f;
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ACTIVE) || random.nextInt(3) != 0) return;
        level.addParticle(ParticleTypes.ELECTRIC_SPARK,
            pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.6 + random.nextDouble() * 0.5,
            pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.02, 0);
    }
}
