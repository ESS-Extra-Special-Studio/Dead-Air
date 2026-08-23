package uk.co.extraspecialstudio.dead_air.item;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
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

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/** Resonant Chord — crafted by piling 9 Static Notes on the ground. Right-click to place as decor. */
public class ResonantChordItem extends Item implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.dead_air.resonant_chord.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ResonantChordItem(Properties properties) {
        super(properties);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    @Override
    public @Nonnull InteractionResult useOn(@Nonnull UseOnContext context) {
        InteractionResult placed = PlaceableDecorations.tryPlace(context);
        return placed.consumesAction() ? placed : super.useOn(context);
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
                        renderer = (BlockEntityWithoutLevelRenderer) c.getMethod("getResonantChordRenderer").invoke(null);
                    } catch (ReflectiveOperationException e) {
                        throw new RuntimeException("Failed to create resonant chord renderer", e);
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

    private PlayState predicate(AnimationState<ResonantChordItem> state) {
        state.getController().setAnimation(IDLE);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
