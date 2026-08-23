package uk.co.extraspecialstudio.dead_air.item;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/** Dimensional Relay — craft ingredient for T2.Radio (and future panel recipes). */
public class DimensionalRelayItem extends Item implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.dead_air.dimensional_relay.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public DimensionalRelayItem(Properties properties) {
        super(properties);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    try {
                        Class<?> c = Class.forName("uk.co.extraspecialstudio.dead_air.client.ClientItemRenderers");
                        renderer = (BlockEntityWithoutLevelRenderer) c.getMethod("getDimensionalRelayRenderer").invoke(null);
                    } catch (ReflectiveOperationException e) {
                        throw new RuntimeException("Failed to create dimensional relay renderer", e);
                    }
                }
                return renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<DimensionalRelayItem> state) {
        state.getController().setAnimation(IDLE);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
