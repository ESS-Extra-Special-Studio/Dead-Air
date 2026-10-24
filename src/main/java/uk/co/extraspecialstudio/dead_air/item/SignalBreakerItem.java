package uk.co.extraspecialstudio.dead_air.item;

import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Improvised mast-spike. Hits convert to {@code dead_air:acoustic} damage — the only type
 * that hurts The Listener (any mode). Heavy / slow — radio scrap, not a fantasy sword.
 */
public class SignalBreakerItem extends SwordItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SignalBreakerItem(Properties properties) {
        // Netherite (+4) +12 +1 = 17 attack; -2.8 speed keeps the heavy mast-weapon feel
        super(Tiers.NETHERITE, 12, -2.8f, properties);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = ClientGeoRenderers.load("getSignalBreakerRenderer");
                }
                return renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new software.bernie.geckolib.core.animation.AnimationController<>(this, "idle", 0, state -> {
            state.getController().setAnimation(
                software.bernie.geckolib.core.animation.RawAnimation.begin()
                    .thenLoop("animation.signal_breaker.idle"));
            return software.bernie.geckolib.core.object.PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.dead_air.signal_breaker.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.dead_air.signal_breaker.tooltip2").withStyle(ChatFormatting.DARK_AQUA));
        // Explicit line so inventory hover always shows the intended hit (Netherite base +12 → 17)
        tooltip.add(Component.literal("17 Attack Damage").withStyle(ChatFormatting.DARK_GREEN));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Foil glint on solid geo made the guard/grip look see-through against the sky.
        // Sheen comes from the echo-crystal blade colors instead.
        return false;
    }
}
