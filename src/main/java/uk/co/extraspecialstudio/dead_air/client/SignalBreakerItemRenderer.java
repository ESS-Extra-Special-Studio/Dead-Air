package uk.co.extraspecialstudio.dead_air.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import uk.co.extraspecialstudio.dead_air.item.SignalBreakerItem;

/**
 * Hand / GUI poses from {@code models/item/signal_breaker.json}.
 * Blade fragments ({@code frag_*} / {@code core_glow}) pulse translucency like The Listener;
 * hilt / guard stay solid so depth sorting stays clean.
 */
public class SignalBreakerItemRenderer extends GeoItemRenderer<SignalBreakerItem> {
    private static final float PULSE_SPEED = 0.045f;
    private static final float ALPHA_MIN = 0.38f;
    private static final float ALPHA_MAX = 0.92f;

    public SignalBreakerItemRenderer() {
        super(new SignalBreakerItemModel());
    }

    @Override
    public void renderRecursively(PoseStack poseStack, SignalBreakerItem animatable, GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        if (isBladeBone(bone.getName())) {
            RenderType translucent = RenderType.entityTranslucent(
                getGeoModel().getTextureResource(animatable));
            VertexConsumer bladeBuffer = bufferSource.getBuffer(translucent);
            super.renderRecursively(poseStack, animatable, bone, translucent, bufferSource, bladeBuffer,
                isReRender, partialTick, packedLight, packedOverlay,
                red, green, blue, pulseAlpha(partialTick));
            return;
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer,
            isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    private static boolean isBladeBone(String name) {
        return name != null && (name.startsWith("frag_") || "core_glow".equals(name));
    }

    private static float pulseAlpha(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        float tick = (mc.level != null ? mc.level.getGameTime() : 0L) + partialTick;
        float t = tick * PULSE_SPEED;
        float wave = 0.5f + 0.5f * Mth.sin(t);
        wave = 0.85f * wave + 0.15f * (0.5f + 0.5f * Mth.sin(t * 1.7f + 1.1f));
        return Mth.lerp(wave, ALPHA_MIN, ALPHA_MAX);
    }
}
