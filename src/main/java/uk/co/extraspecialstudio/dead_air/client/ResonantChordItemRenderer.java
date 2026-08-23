package uk.co.extraspecialstudio.dead_air.client;

import software.bernie.geckolib.renderer.GeoItemRenderer;
import uk.co.extraspecialstudio.dead_air.item.ResonantChordItem;

public class ResonantChordItemRenderer extends GeoItemRenderer<ResonantChordItem> {
    public ResonantChordItemRenderer() {
        super(new ResonantChordItemModel());
    }
}
