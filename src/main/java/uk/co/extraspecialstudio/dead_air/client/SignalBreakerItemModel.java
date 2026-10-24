package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.item.SignalBreakerItem;

public class SignalBreakerItemModel extends GeoModel<SignalBreakerItem> {
    @Override
    public ResourceLocation getModelResource(SignalBreakerItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "geo/item/signal_breaker.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(SignalBreakerItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "textures/item/signal_breaker_3d.png");
    }

    @Override
    public ResourceLocation getAnimationResource(SignalBreakerItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "animations/item/signal_breaker.animation.json");
    }

    /**
     * Solid (not cutout/translucent): hilt cubes must write depth so sky does not show through
     * overlapping guard/grip faces. Foil glint still composites on top via GeoItemRenderer.
     */
    @Override
    public RenderType getRenderType(SignalBreakerItem animatable, ResourceLocation texture) {
        return RenderType.entitySolid(texture);
    }
}
