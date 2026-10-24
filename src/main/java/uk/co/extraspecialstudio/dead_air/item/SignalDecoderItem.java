package uk.co.extraspecialstudio.dead_air.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import uk.co.extraspecialstudio.dead_air.walkie.WalkieNbt;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Crafting upgrade that turns a T2 Radio into a T3 Radio.
 * Right-click while holding a T2 in the other hand installs it in place (NBT preserved).
 */
public class SignalDecoderItem extends Item {
    public SignalDecoderItem(Properties properties) {
        super(properties);
    }

    @Override
    public @Nonnull InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack decoder = player.getItemInHand(hand);
        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack walkie = player.getItemInHand(other);
        if (!(walkie.getItem() instanceof WalkieItem w) || w.getTier() != WalkieItem.Tier.T2) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.dead_air.decoder.need_t2")
                    .withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.fail(decoder);
        }
        if (!level.isClientSide) {
            ItemStack upgraded = new ItemStack(DeadAirItems.WALKIE_T3.get());
            CompoundTag tag = WalkieNbt.get(walkie);
            if (tag != null) WalkieNbt.set(upgraded, tag.copy());
            player.setItemInHand(other, upgraded);
            if (!player.getAbilities().instabuild) decoder.shrink(1);
            level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.5f, 1.4f);
            player.displayClientMessage(Component.translatable("message.dead_air.decoder.upgraded")
                .withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResultHolder.sidedSuccess(decoder, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
