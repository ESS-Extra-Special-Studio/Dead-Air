package uk.co.extraspecialstudio.dead_air.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Wasteland tent — right-click from outside to sleep (bed-style). Wake/cancel exits outside the tent.
 */
public class SurvivorTentBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<SurvivorTentBlock> CODEC = simpleCodec(SurvivorTentBlock::new);
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public SurvivorTentBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
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
        return SHAPE;
    }

    public boolean isBed(BlockState state, BlockGetter level, BlockPos pos, @Nullable Entity player) {
        return true;
    }

    public Direction getBedDirection(BlockState state, LevelReader level, BlockPos pos) {
        return state.getValue(FACING);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.CONSUME;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;

        Direction facing = state.getValue(FACING);
        BlockPos exit = pos.relative(facing.getOpposite());
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
