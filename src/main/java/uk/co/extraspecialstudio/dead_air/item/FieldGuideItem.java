package uk.co.extraspecialstudio.dead_air.item;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import uk.co.extraspecialstudio.dead_air.client.DeadAirFieldGuideScreen;

import javax.annotation.Nonnull;

/**
 * Opens the in-game Dead Air Field Guide on right-click.
 */
@SuppressWarnings("null")
public class FieldGuideItem extends Item {

    public FieldGuideItem(Properties properties) {
        super(properties);
    }

    @Override
    public @Nonnull InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            openGuideClient();
        }
        @SuppressWarnings("null")
        InteractionResultHolder<ItemStack> result = InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        return result;
    }

    @OnlyIn(Dist.CLIENT)
    private static void openGuideClient() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(new DeadAirFieldGuideScreen());
        }
    }
}
