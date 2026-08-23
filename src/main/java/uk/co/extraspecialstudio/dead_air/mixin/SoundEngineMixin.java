package uk.co.extraspecialstudio.dead_air.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.extraspecialstudio.dead_air.audio.AudioManager;
import uk.co.extraspecialstudio.dead_air.audio.RadioSoundInstance;

/**
 * When the sound engine stops a sound we get a callback (onStop).
 * The engine may also remove finished sounds in tick() without calling stop(); afterTick checks isActive so we advance.
 * We update radio sound volume each tick so it gets quieter/louder seamlessly when moving between signal bands.
 */
@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    @Inject(method = "stop(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("TAIL"), require = 1)
    private void onStop(SoundInstance sound, CallbackInfo ci) {
        if (sound instanceof RadioSoundInstance radio) {
            uk.co.extraspecialstudio.dead_air.audio.RadioChannelTracker.unregister(radio);
            AudioManager.onRadioSoundRemovedFromEngine(radio);
        }
    }

    @Inject(method = "tick(Z)V", at = @At("TAIL"), require = 1)
    private void afterTick(boolean paused, CallbackInfo ci) {
        AudioManager.checkSoundsRemovedAfterTick((SoundEngine) (Object) this);
    }

    /**
     * A dimension change stops every channel in bulk via Minecraft.setLevel -> SoundManager.stop().
     * That path never calls stop(SoundInstance), so without this our radio state believes it is still playing.
     */
    @Inject(method = "stopAll", at = @At("HEAD"), require = 1)
    private void onStopAll(CallbackInfo ci) {
        AudioManager.onSoundEngineWiped();
    }
}
