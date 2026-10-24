package uk.co.extraspecialstudio.dead_air.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import uk.co.extraspecialstudio.dead_air.Dead_air;

@OnlyIn(Dist.CLIENT)
public final class ClientItemRenderers {
    private static BlockEntityWithoutLevelRenderer radioRenderer;
    private static BlockEntityWithoutLevelRenderer resonantChordRenderer;
    private static BlockEntityWithoutLevelRenderer dimensionalRelayRenderer;
    private static BlockEntityWithoutLevelRenderer signalBreakerRenderer;
    private static BlockEntityWithoutLevelRenderer noopRenderer;

    private ClientItemRenderers() {}

    /** Empty BEWLR so creative-tab / GUI icon renders never hard-crash on a broken Geo setup. */
    public static BlockEntityWithoutLevelRenderer getNoopRenderer() {
        if (noopRenderer == null) {
            Minecraft mc = Minecraft.getInstance();
            noopRenderer = new BlockEntityWithoutLevelRenderer(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels()) {
                @Override
                public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                                         MultiBufferSource buffer, int packedLight, int packedOverlay) {
                    // no-op
                }
            };
        }
        return noopRenderer;
    }

    public static BlockEntityWithoutLevelRenderer getRadioRenderer() {
        if (radioRenderer == null) {
            try {
                radioRenderer = new RadioItemRenderer();
            } catch (Throwable t) {
                Dead_air.LOGGER.error("Failed to create radio GeoItem renderer; using no-op", t);
                radioRenderer = getNoopRenderer();
            }
        }
        return radioRenderer;
    }

    public static BlockEntityWithoutLevelRenderer getResonantChordRenderer() {
        if (resonantChordRenderer == null) {
            try {
                resonantChordRenderer = new ResonantChordItemRenderer();
            } catch (Throwable t) {
                Dead_air.LOGGER.error("Failed to create resonant chord GeoItem renderer; using no-op", t);
                resonantChordRenderer = getNoopRenderer();
            }
        }
        return resonantChordRenderer;
    }

    public static BlockEntityWithoutLevelRenderer getDimensionalRelayRenderer() {
        if (dimensionalRelayRenderer == null) {
            try {
                dimensionalRelayRenderer = new DimensionalRelayItemRenderer();
            } catch (Throwable t) {
                Dead_air.LOGGER.error("Failed to create dimensional relay GeoItem renderer; using no-op", t);
                dimensionalRelayRenderer = getNoopRenderer();
            }
        }
        return dimensionalRelayRenderer;
    }

    public static BlockEntityWithoutLevelRenderer getSignalBreakerRenderer() {
        if (signalBreakerRenderer == null) {
            try {
                signalBreakerRenderer = new SignalBreakerItemRenderer();
            } catch (Throwable t) {
                Dead_air.LOGGER.error("Failed to create signal breaker GeoItem renderer; using no-op", t);
                signalBreakerRenderer = getNoopRenderer();
            }
        }
        return signalBreakerRenderer;
    }
}
