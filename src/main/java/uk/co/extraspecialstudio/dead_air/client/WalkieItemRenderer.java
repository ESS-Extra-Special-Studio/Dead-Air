package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import uk.co.extraspecialstudio.dead_air.item.WalkieItem;

public class WalkieItemRenderer extends GeoItemRenderer<WalkieItem> {
    public WalkieItemRenderer() {
        super(new WalkieItemModel());
    }

    @Override
    public ResourceLocation getTextureLocation(WalkieItem animatable) {
        if (animatable != null && animatable.supportsLiveLcd()) {
            RadioLiveDisplay.Spec spec = animatable.getLiveLcdSpec();
            if (spec != null) {
                return RadioLiveDisplay.get(spec).getTexture(getCurrentItemStack());
            }
        }
        return super.getTextureLocation(animatable);
    }
}
