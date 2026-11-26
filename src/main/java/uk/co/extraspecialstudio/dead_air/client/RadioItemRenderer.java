package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import uk.co.extraspecialstudio.dead_air.item.RadioItem;

public class RadioItemRenderer extends GeoItemRenderer<RadioItem> {
    public RadioItemRenderer() {
        super(new RadioItemModel());
    }

    @Override
    public ResourceLocation getTextureLocation(RadioItem animatable) {
        if (animatable != null && animatable.supportsLiveLcd()) {
            RadioLiveDisplay.Spec spec = animatable.getLiveLcdSpec();
            if (spec != null) {
                return RadioLiveDisplay.get(spec).getTexture(getCurrentItemStack());
            }
        }
        return super.getTextureLocation(animatable);
    }
}
