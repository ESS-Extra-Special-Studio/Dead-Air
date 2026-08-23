package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/**
 * Built-in T2 walkie live LCD. Delegates to {@link RadioLiveDisplay} with the shipped 1024 atlas rect.
 */
public final class T2RadioDisplayTexture {
    private static final ResourceLocation BASE_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "textures/item/walkie_t2.png");
    private static final ResourceLocation LIVE_ID =
        ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "dynamic/walkie_t2_live");

    public static final RadioLiveDisplay.Spec SPEC = new RadioLiveDisplay.Spec(
        BASE_TEXTURE,
        LIVE_ID,
        new RadioLiveDisplay.LcdRect(18, 18, 110, 74),
        1024
    );

    private T2RadioDisplayTexture() {}

    public static boolean isLiveLcdSupported() {
        return RadioLiveDisplay.get(SPEC).isSupported();
    }

    public static void reset() {
        RadioLiveDisplay.get(SPEC).reset();
    }

    public static ResourceLocation getTexture(ItemStack stack) {
        return RadioLiveDisplay.get(SPEC).getTexture(stack);
    }
}
