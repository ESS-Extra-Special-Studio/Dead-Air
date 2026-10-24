package uk.co.extraspecialstudio.dead_air.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Key bindings for Dead Air mod.
 */
@SuppressWarnings("null")
public class KeyBindings {
    public static final KeyMapping TUNE_RADIO = new KeyMapping(
        "key.dead_air.tune_radio",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_N,  // N = tune radio (H = mute, G = other menu, B = radio on/off)
        "key.categories.dead_air"
    );

    /** Hold to open squad order radial wheel. */
    public static final KeyMapping PARTY_ORDERS = new KeyMapping(
        "key.dead_air.party_orders",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_C,
        "key.categories.dead_air"
    );
    
    public static void register() {
        // Key bindings are registered automatically by Forge
        // Just need to check them in client tick
    }
}
