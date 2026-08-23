package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientItemRenderers {
    private static WalkieItemRenderer walkieRenderer;
    private static ResonantChordItemRenderer resonantChordRenderer;
    private static DimensionalRelayItemRenderer dimensionalRelayRenderer;

    private ClientItemRenderers() {}

    public static BlockEntityWithoutLevelRenderer getWalkieRenderer() {
        if (walkieRenderer == null) {
            walkieRenderer = new WalkieItemRenderer();
        }
        return walkieRenderer;
    }

    public static BlockEntityWithoutLevelRenderer getResonantChordRenderer() {
        if (resonantChordRenderer == null) {
            resonantChordRenderer = new ResonantChordItemRenderer();
        }
        return resonantChordRenderer;
    }

    public static BlockEntityWithoutLevelRenderer getDimensionalRelayRenderer() {
        if (dimensionalRelayRenderer == null) {
            dimensionalRelayRenderer = new DimensionalRelayItemRenderer();
        }
        return dimensionalRelayRenderer;
    }
}
