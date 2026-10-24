package uk.co.extraspecialstudio.dead_air.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Signal firepit — campfire-scale light; Cold Sweat heat via soft block_temp datapack when lit. */
public class SignalFirepitBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** Matches stone-ring firepit model. */
    private static final VoxelShape SHAPE = Block.box(1.5, 0, 1.5, 14.5, 4.5, 14.5);

    public SignalFirepitBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(LIT, true);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (state.getValue(LIT) && stack.is(Items.WATER_BUCKET)) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(LIT, false), 3);
                level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1f, 1f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!state.getValue(LIT) && (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE))) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(LIT, true), 3);
                level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1f, 1f);
                if (stack.is(Items.FLINT_AND_STEEL)) {
                    stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
                } else if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        if (random.nextInt(6) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.45f + random.nextFloat() * 0.25f,
                0.85f + random.nextFloat() * 0.25f, false);
        }
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.35;
        double cz = pos.getZ() + 0.5;
        level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
            cx + (random.nextDouble() - 0.5) * 0.2, cy + 0.25, cz + (random.nextDouble() - 0.5) * 0.2,
            0, 0.045 + random.nextDouble() * 0.02, 0);
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.LARGE_SMOKE,
                cx + (random.nextDouble() - 0.5) * 0.15, cy + 0.3, cz + (random.nextDouble() - 0.5) * 0.15,
                0, 0.02, 0);
        }
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.FLAME,
                cx + (random.nextDouble() - 0.5) * 0.35,
                cy + random.nextDouble() * 0.15,
                cz + (random.nextDouble() - 0.5) * 0.35,
                0, 0.012, 0);
        }
        if (random.nextBoolean()) {
            level.addParticle(ParticleTypes.LAVA,
                cx + (random.nextDouble() - 0.5) * 0.25, cy, cz + (random.nextDouble() - 0.5) * 0.25,
                0, 0, 0);
        }
    }
}
