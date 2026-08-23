package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import uk.co.extraspecialstudio.dead_air.item.WalkieItem;

public class WalkieItemModel extends GeoModel<WalkieItem> {
    @Override
    public ResourceLocation getModelResource(WalkieItem animatable) {
        return animatable.getGeoModelResource();
    }

    @Override
    public ResourceLocation getTextureResource(WalkieItem animatable) {
        return animatable.getGeoTextureResource();
    }

    @Override
    public ResourceLocation getAnimationResource(WalkieItem animatable) {
        return animatable.getGeoAnimationResource();
    }
}
