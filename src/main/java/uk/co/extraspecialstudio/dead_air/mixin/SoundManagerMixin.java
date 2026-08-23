package uk.co.extraspecialstudio.dead_air.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.extraspecialstudio.dead_air.audio.AudioManager;
import uk.co.extraspecialstudio.dead_air.audio.RadioSoundInstance;

/**
 * When the sound engine stops a sound (either we called stop() or it finished naturally),
 * we get a callback. If it's our radio sound and still the active one for that station,
 * we mark the station so the next track starts (engine often doesn't report isActive=false for streaming OGGs).
 */
@Mixin(SoundManager.class)
public class SoundManagerMixin {

    @Inject(method = "stop(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("TAIL"), require = 1)
    private void onStop(SoundInstance sound, CallbackInfo ci) {
        if (sound instanceof RadioSoundInstance radio) {
            AudioManager.onRadioSoundRemovedFromEngine(radio);
        }
    }
}
