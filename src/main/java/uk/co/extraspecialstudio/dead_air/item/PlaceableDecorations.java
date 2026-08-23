package uk.co.extraspecialstudio.dead_air.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Right-click places notes / chords as single non-merging world decorations.
 * One decoration per block face. Never auto-picked up — punch / right-click to retrieve.
 */
public final class PlaceableDecorations {
    public static final String NBT_DECOR = "DeadAirDecor";
    public static final String NBT_BLOCK_X = "DeadAirDecorX";
    public static final String NBT_BLOCK_Y = "DeadAirDecorY";
    public static final String NBT_BLOCK_Z = "DeadAirDecorZ";
    public static final String NBT_FACE = "DeadAirDecorFace";

    private static final int RETRIEVE_SETTLE_TICKS = 10;

    private PlaceableDecorations() {}

    public static boolean isDecorStack(ItemStack stack) {
        if (stack.isEmpty()) return false;
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(NBT_DECOR);
    }

    public static boolean isPlaceableDecorItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(DeadAirItems.STATIC_NOTE.get())
            || stack.is(DeadAirItems.RESONANT_CHORD.get());
    }

    public static void stripDecorTag(ItemStack stack) {
        if (stack.isEmpty()) return;
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove(NBT_DECOR);
        tag.remove(NBT_BLOCK_X);
        tag.remove(NBT_BLOCK_Y);
        tag.remove(NBT_BLOCK_Z);
        tag.remove(NBT_FACE);
        if (tag.isEmpty()) stack.setTag(null);
    }

    public static InteractionResult tryPlace(UseOnContext ctx) {
        ItemStack held = ctx.getItemInHand();
        if (!isPlaceableDecorItem(held)) return InteractionResult.PASS;

        Player player = ctx.getPlayer();
        Level level = ctx.getLevel();
        BlockPos clicked = ctx.getClickedPos();
        Direction face = ctx.getClickedFace();
        BlockState state = level.getBlockState(clicked);

        if (player != null && shouldDeferToBlock(player, level, clicked, state)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) return InteractionResult.SUCCESS;

        if (isFaceOccupied(level, clicked, face)) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.dead_air.decor.face_occupied"), true);
            }
            level.playSound(null, clicked, SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.35f, 1.2f);
            return InteractionResult.FAIL;
        }

        Vec3 at = placementPos(ctx);
        ItemStack one = held.copy();
        one.setCount(1);
        stripDecorTag(one);
        CompoundTag tag = one.getOrCreateTag();
        tag.putUUID(NBT_DECOR, UUID.randomUUID());
        tag.putInt(NBT_BLOCK_X, clicked.getX());
        tag.putInt(NBT_BLOCK_Y, clicked.getY());
        tag.putInt(NBT_BLOCK_Z, clicked.getZ());
        tag.putString(NBT_FACE, face.getSerializedName());

        ItemEntity entity = new ItemEntity(level, at.x, at.y, at.z, one);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setNoGravity(true);
        // Delay 0 so punches work; walk-pickup is cancelled in DecorRetrieveEvents for decor stacks.
        entity.setPickUpDelay(0);
        entity.setUnlimitedLifetime();
        level.addFreshEntity(entity);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.PLAYERS, 0.6f, 1.1f);

        if (player != null && !player.getAbilities().instabuild) {
            held.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    /** True if a placed decor already claims this block face. */
    public static boolean isFaceOccupied(Level level, BlockPos block, Direction face) {
        // Cover the clicked block and the cell on that face (where the item sits)
        AABB search = new AABB(block).minmax(new AABB(block.relative(face))).inflate(0.35);
        List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, search,
            e -> !e.isRemoved() && isDecorStack(e.getItem()));
        for (ItemEntity entity : nearby) {
            CompoundTag tag = entity.getItem().getTag();
            if (tag == null || !tag.contains(NBT_FACE)) continue;
            if (tag.getInt(NBT_BLOCK_X) != block.getX()) continue;
            if (tag.getInt(NBT_BLOCK_Y) != block.getY()) continue;
            if (tag.getInt(NBT_BLOCK_Z) != block.getZ()) continue;
            if (!face.getSerializedName().equals(tag.getString(NBT_FACE))) continue;
            return true;
        }
        return false;
    }

    private static Vec3 placementPos(UseOnContext ctx) {
        Vec3 hit = ctx.getClickLocation();
        Direction face = ctx.getClickedFace();
        double inset = 0.02;
        return hit.add(face.getStepX() * inset, face.getStepY() * inset, face.getStepZ() * inset);
    }

    private static boolean shouldDeferToBlock(Player player, Level level, BlockPos pos, BlockState state) {
        if (player.isSecondaryUseActive()) return false; // sneak = always place
        if (state.getMenuProvider(level, pos) != null) return true;
        var block = state.getBlock();
        return block instanceof DoorBlock
            || block instanceof TrapDoorBlock
            || block instanceof FenceGateBlock
            || block instanceof ButtonBlock
            || block instanceof LeverBlock;
    }

    /** Remove the decoration and drop it back as a normal, pick-up-able item. */
    public static boolean tryRetrieve(@Nullable Player player, ItemEntity entity) {
        if (player == null || entity == null || entity.isRemoved()) return false;
        // Placing swings the arm, which would otherwise retrieve the decoration on the same tick.
        if (entity.tickCount < RETRIEVE_SETTLE_TICKS) return false;
        ItemStack stack = entity.getItem();
        if (!isDecorStack(stack) || !isPlaceableDecorItem(stack)) return false;

        ItemStack give = stack.copy();
        stripDecorTag(give);

        Level level = player.level();
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        entity.discard();

        // Always drop it back so decorations are never lost, creative included.
        ItemEntity drop = new ItemEntity(level, x, y, z, give);
        drop.setDefaultPickUpDelay();
        drop.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(drop);

        level.playSound(null, x, y, z,
            SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.PLAYERS, 0.6f, 1.0f);
        return true;
    }
}
