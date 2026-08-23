package uk.co.extraspecialstudio.dead_air.client;

import software.bernie.geckolib.renderer.GeoItemRenderer;
import uk.co.extraspecialstudio.dead_air.item.DimensionalRelayItem;

public class DimensionalRelayItemRenderer extends GeoItemRenderer<DimensionalRelayItem> {
    public DimensionalRelayItemRenderer() {
        super(new DimensionalRelayItemModel());
    }
}
