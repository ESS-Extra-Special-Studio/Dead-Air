package uk.co.extraspecialstudio.dead_air.item;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/**
 * Reflection bridge to client GeoItem BEWLRs. Never throws — creative-tab icon
 * rendering must not hard-crash if a renderer class failed to load (e.g. mid-session
 * jar replace, incomplete deploy).
 */
final class ClientGeoRenderers {
    private ClientGeoRenderers() {}

    static BlockEntityWithoutLevelRenderer load(String getterMethod) {
        try {
            Class<?> c = Class.forName("uk.co.extraspecialstudio.dead_air.client.ClientItemRenderers");
            Object renderer = c.getMethod(getterMethod).invoke(null);
            if (renderer instanceof BlockEntityWithoutLevelRenderer bewlr) {
                return bewlr;
            }
        } catch (Throwable t) {
            Dead_air.LOGGER.error("Failed to create GeoItem renderer via {} — falling back to no-op", getterMethod, t);
        }
        try {
            Class<?> c = Class.forName("uk.co.extraspecialstudio.dead_air.client.ClientItemRenderers");
            Object noop = c.getMethod("getNoopRenderer").invoke(null);
            if (noop instanceof BlockEntityWithoutLevelRenderer bewlr) {
                return bewlr;
            }
        } catch (Throwable t) {
            Dead_air.LOGGER.error("Failed to load no-op GeoItem renderer", t);
        }
        // Absolute last resort: empty anonymous BEWLR (still client-only call path).
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            return new BlockEntityWithoutLevelRenderer(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels()) {
                @Override
                public void renderByItem(net.minecraft.world.item.ItemStack stack,
                                         net.minecraft.world.item.ItemDisplayContext displayContext,
                                         com.mojang.blaze3d.vertex.PoseStack poseStack,
                                         net.minecraft.client.renderer.MultiBufferSource buffer,
                                         int packedLight, int packedOverlay) {
                    // no-op
                }
            };
        } catch (Throwable t) {
            Dead_air.LOGGER.error("Failed to construct emergency no-op GeoItem renderer", t);
            return null;
        }
    }
}
