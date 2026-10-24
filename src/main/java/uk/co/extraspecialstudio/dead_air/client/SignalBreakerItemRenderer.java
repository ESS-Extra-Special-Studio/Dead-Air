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

public class SignalBreakerItemRenderer extends GeoItemRenderer<SignalBreakerItem> {
    private static final float PULSE_SPEED = 0.045f;
    private static final float ALPHA_MIN = 0.38f;
    private static final float ALPHA_MAX = 0.92f;

    public SignalBreakerItemRenderer() {
        super(new SignalBreakerItemModel());
    }

    // GeckoLib 4.x renderRecursively signature varies — pulse via model/texture for now.
}
