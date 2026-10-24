package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import uk.co.extraspecialstudio.dead_air.item.RadioItem;

public class RadioItemModel extends GeoModel<RadioItem> {
    @Override
    public ResourceLocation getModelResource(RadioItem animatable) {
        return animatable.getGeoModelResource();
    }

    @Override
    public ResourceLocation getTextureResource(RadioItem animatable) {
        return animatable.getGeoTextureResource();
    }

    @Override
    public ResourceLocation getAnimationResource(RadioItem animatable) {
        return animatable.getGeoAnimationResource();
    }
}
