package uk.co.extraspecialstudio.dead_air.mixin;

import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.extraspecialstudio.dead_air.audio.AudioManager;
import uk.co.extraspecialstudio.dead_air.audio.RadioChannelTracker;
import uk.co.extraspecialstudio.dead_air.audio.RadioSoundInstance;

/**
 * When a streaming Channel is ticked (updateStream), update volume for radio sounds.
 * When the stream finishes (non-looping), advance to the next playlist track.
 */
@Mixin(Channel.class)
public class ChannelMixin {

    @Inject(method = "updateStream", at = @At("HEAD"), require = 1)
    private void dead_air$updateRadioVolume(CallbackInfo ci) {
        Channel self = (Channel) (Object) this;
        if (!RadioChannelTracker.isRadioChannel(self)) return;
        try {
            float vol = RadioChannelTracker.getVolumeForChannel(self);
            if (vol >= 0f) {
                self.setVolume(vol);
            }
        } catch (Throwable ignored) {
            // Channel may already be dying during SoundEngine.stopAll / dimension change.
            RadioChannelTracker.unregister(RadioChannelTracker.getSoundForChannel(self));
        }
    }

    @Inject(method = "updateStream", at = @At("TAIL"), require = 1)
    private void dead_air$onRadioStreamEnded(CallbackInfo ci) {
        Channel self = (Channel) (Object) this;
        if (!RadioChannelTracker.isRadioChannel(self) || !self.stopped()) {
            return;
        }
        RadioSoundInstance sound = RadioChannelTracker.getSoundForChannel(self);
        if (sound == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> {
                try {
                    AudioManager.onRadioStreamEnded(sound);
                } catch (Throwable ignored) {
                }
            });
        }
    }
}
