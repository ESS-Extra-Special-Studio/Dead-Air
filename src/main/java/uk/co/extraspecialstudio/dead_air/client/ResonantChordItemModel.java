package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.item.ResonantChordItem;

/**
 * Cycles flipbook frames on the Geo texture. Entity textures ignore .mcmeta,
 * so we swap ResourceLocations by game time (8 frames @ 2 ticks each).
 */
public class ResonantChordItemModel extends GeoModel<ResonantChordItem> {
    private static final int FRAME_COUNT = 8;
    private static final int TICKS_PER_FRAME = 2;

    @Override
    public ResourceLocation getModelResource(ResonantChordItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "geo/item/resonant_chord.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ResonantChordItem animatable) {
        int frame = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            frame = (int) ((mc.level.getGameTime() / TICKS_PER_FRAME) % FRAME_COUNT);
        } else {
            frame = (int) ((System.currentTimeMillis() / 50L / TICKS_PER_FRAME) % FRAME_COUNT);
        }
        return ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "textures/item/resonant_chord_" + frame + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(ResonantChordItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "animations/item/resonant_chord.animation.json");
    }
}
