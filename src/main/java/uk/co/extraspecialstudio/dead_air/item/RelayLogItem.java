package uk.co.extraspecialstudio.dead_air.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** A page recovered from a Broken Relay. Right-click to read it. */
public class RelayLogItem extends Item {
    private static final String TAG_LINES = "LogLines";
    private static final String TAG_INDEX = "LogIndex";

    public RelayLogItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(int index, List<String> lines) {
        ItemStack stack = new ItemStack(DeadAirItems.RELAY_LOG.get());
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_INDEX, index);
        ListTag list = new ListTag();
        for (String l : lines) list.add(StringTag.valueOf(l));
        tag.put(TAG_LINES, list);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    @Nullable
    private static CompoundTag getTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            CompoundTag tag = getTag(stack);
            player.sendSystemMessage(Component.translatable("item.dead_air.relay_log.header",
                tag != null ? tag.getInt(TAG_INDEX) + 1 : 1).withStyle(ChatFormatting.AQUA));
            if (tag != null) {
                ListTag list = tag.getList(TAG_LINES, Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) {
                    player.sendSystemMessage(Component.literal(list.getString(i)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                }
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = getTag(stack);
        int index = tag != null ? tag.getInt(TAG_INDEX) + 1 : 1;
        tooltip.add(Component.translatable("item.dead_air.relay_log.tooltip", index).withStyle(ChatFormatting.GRAY));
    }
}
