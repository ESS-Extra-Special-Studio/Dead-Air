package uk.creatopia.unbound.dead_air.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to disable vanilla Minecraft background music.
 * All music should only play through the Dead Air radio system.
 * Ambient sounds continue playing naturally.
 */
@Mixin(MusicManager.class)
@SuppressWarnings("null")
public class MusicManagerMixin {
    
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void disableVanillaMusic(CallbackInfo ci) {
        // Disable vanilla music completely - all music should come from radio stations
        // This prevents the MusicManager from starting any new music tracks
        // Ambient sounds are not affected as they use a different system
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            // Always cancel vanilla music - radio system handles all music
            ci.cancel();
        }
    }
}
