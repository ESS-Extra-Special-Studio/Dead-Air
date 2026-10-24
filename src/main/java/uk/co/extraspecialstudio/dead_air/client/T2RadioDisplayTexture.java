package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/**
 * Built-in T2 radio live LCD. Delegates to {@link RadioLiveDisplay} with the shipped 1024 atlas rect.
 */
public final class T2RadioDisplayTexture {
    private static final ResourceLocation BASE_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "textures/item/radio_t2.png");
    private static final ResourceLocation LIVE_ID =
        ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "dynamic/radio_t2_live");

    /**
     * Live LCD paint rect on radio_t2 / radio_t3 atlas (1024).
     * Radio geo maps the screen face to UV 0,0 size 4×3 on a 32 atlas → pixels 0–128 × 0–96.
     * Thin inset keeps a bezel frame without the large black gap players reported.
     */
    private static final RadioLiveDisplay.LcdRect LCD =
        new RadioLiveDisplay.LcdRect(4, 4, 124, 92);

    public static final RadioLiveDisplay.Spec SPEC = new RadioLiveDisplay.Spec(
        BASE_TEXTURE,
        LIVE_ID,
        LCD,
        1024,
        true,
        0,
        RadioLiveDisplay.LcdPalette.GREEN
    );

    /** T3 shares the T2 mesh and LCD rect with a blue screen palette. */
    public static final RadioLiveDisplay.Spec T3_SPEC = new RadioLiveDisplay.Spec(
        ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "textures/item/radio_t3.png"),
        ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "dynamic/radio_t3_live"),
        LCD,
        1024,
        true,
        0,
        RadioLiveDisplay.LcdPalette.BLUE
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
