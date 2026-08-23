package uk.co.extraspecialstudio.dead_air.item;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
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
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.client.RadioLiveDisplay;
import uk.co.extraspecialstudio.dead_air.client.T2RadioDisplayTexture;
import uk.co.extraspecialstudio.dead_air.client.WalkieTalkieTuningScreen;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Dead Air walkie (T1 or T2) with GeckoLib model/animations.
 * Right-click opens the Dead Air walkie tuning GUI (T2 Sync/Link controls live on that screen).
 */
public class WalkieItem extends Item implements GeoItem {
    public enum Tier { T1, T2 }

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.dead_air.walkie.idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Tier tier;

    public WalkieItem(Properties properties, Tier tier) {
        super(properties);
        this.tier = tier;
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public Tier getTier() {
        return tier;
    }

    public boolean isLinkCapable() {
        return tier == Tier.T2;
    }

    /** Asset base name for this tier under Dead Air's own namespace. */
    private String defaultAssetName() {
        return tier == Tier.T2 ? "walkie_t2" : "walkie_t1";
    }

    /**
     * GeckoLib mesh for this radio. Companion mods that add their own radio alongside the
     * built-in walkies override this instead of overwriting Dead Air's assets.
     */
    public ResourceLocation getGeoModelResource() {
        return ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "geo/item/" + defaultAssetName() + ".geo.json");
    }

    /** Skin for this radio. See {@link #getGeoModelResource()}. */
    public ResourceLocation getGeoTextureResource() {
        return ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "textures/item/" + defaultAssetName() + ".png");
    }

    /** Animation set for this radio. See {@link #getGeoModelResource()}. */
    public ResourceLocation getGeoAnimationResource() {
        return ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "animations/item/walkie.animation.json");
    }

    /**
     * Whether the on-item readout should be painted into this radio's texture. Only true for the
     * built-in T2 by default; companions override when they have a measured screen UV.
     */
    public boolean supportsLiveLcd() {
        return tier == Tier.T2;
    }

    /**
     * Live LCD paint target for this radio. Null when {@link #supportsLiveLcd()} is false.
     * Companions return their own atlas + rect.
     */
    @Nullable
    public RadioLiveDisplay.Spec getLiveLcdSpec() {
        return supportsLiveLcd() ? T2RadioDisplayTexture.SPEC : null;
    }

    /** True when the live painter successfully probed the current atlas for {@link #getLiveLcdSpec()}. */
    @OnlyIn(Dist.CLIENT)
    public boolean isLiveLcdSupported() {
        RadioLiveDisplay.Spec spec = getLiveLcdSpec();
        return spec != null && RadioLiveDisplay.get(spec).isSupported();
    }

    @Override
    public @Nonnull InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            openTuningScreen(hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /**
     * Opens the tuning GUI for this radio. Companions override to open a framed Pip-Boy screen, etc.
     * Safe to call from client keybinds / packets.
     */
    @OnlyIn(Dist.CLIENT)
    public void openTuningScreen(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        try {
            mc.setScreen(new WalkieTalkieTuningScreen(hand));
        } catch (NoClassDefFoundError | Exception e) {
            // A damaged install should not take the whole client down just because a screen failed to load.
            Dead_air.LOGGER.error("Could not open the walkie tuning screen", e);
        }
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
                        renderer = (BlockEntityWithoutLevelRenderer) c.getMethod("getWalkieRenderer").invoke(null);
                    } catch (ReflectiveOperationException e) {
                        throw new RuntimeException("Failed to create walkie renderer", e);
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

    private PlayState predicate(AnimationState<WalkieItem> state) {
        state.getController().setAnimation(IDLE);
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
