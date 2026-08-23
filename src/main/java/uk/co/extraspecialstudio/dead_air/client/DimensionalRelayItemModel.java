package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.item.DimensionalRelayItem;

public class DimensionalRelayItemModel extends GeoModel<DimensionalRelayItem> {
    @Override
    public ResourceLocation getModelResource(DimensionalRelayItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "geo/item/dimensional_relay.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(DimensionalRelayItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "textures/item/dimensional_relay.png");
    }

    @Override
    public ResourceLocation getAnimationResource(DimensionalRelayItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "animations/item/dimensional_relay.animation.json");
    }
}
