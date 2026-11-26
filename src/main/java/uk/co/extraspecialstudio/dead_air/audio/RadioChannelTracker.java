package uk.co.extraspecialstudio.dead_air.audio;

import com.mojang.blaze3d.audio.Channel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which Channels are playing RadioSoundInstances so we can update volume
 * when the Channel is ticked (on the audio thread). The mixin approach of
 * handle.execute() from the game thread may not work - OpenAL requires
 * alSourcef from the thread that owns the context.
 */
public final class RadioChannelTracker {
    private static final Map<Channel, RadioSoundInstance> CHANNEL_TO_SOUND = new ConcurrentHashMap<>();
    private static final Map<RadioSoundInstance, Channel> SOUND_TO_CHANNEL = new ConcurrentHashMap<>();

    public static void register(Channel channel, RadioSoundInstance sound) {
        if (channel != null && sound != null) {
            CHANNEL_TO_SOUND.put(channel, sound);
            SOUND_TO_CHANNEL.put(sound, channel);
        }
    }

    public static void unregister(RadioSoundInstance sound) {
        if (sound != null) {
            Channel ch = SOUND_TO_CHANNEL.remove(sound);
            if (ch != null) {
                CHANNEL_TO_SOUND.remove(ch);
            }
        }
    }

    /** Drop every tracked channel (after stopAll / failed-start thrash). */
    public static void clearAll() {
        CHANNEL_TO_SOUND.clear();
        SOUND_TO_CHANNEL.clear();
    }

    /** True once the sound engine has actually bound this sound to a channel and started streaming it. */
    public static boolean hasChannel(RadioSoundInstance sound) {
        return sound != null && SOUND_TO_CHANNEL.containsKey(sound);
    }

    /**
     * Called from ChannelMixin.updateStream on the OpenAL audio thread.
     * Uses cached volume only — never Level/player (dimension teardowns race this thread).
     * Returns volume to set, or -1 if not our channel.
     */
    public static float getVolumeForChannel(Channel channel) {
        RadioSoundInstance sound = CHANNEL_TO_SOUND.get(channel);
        if (sound == null) return -1f;
        try {
            float baseVol = sound.getCachedVolume();
            return baseVol <= 0f ? 0.01f : baseVol;
        } catch (Throwable ignored) {
            return 0.01f;
        }
    }

    public static boolean isRadioChannel(Channel channel) {
        return CHANNEL_TO_SOUND.containsKey(channel);
    }

    public static RadioSoundInstance getSoundForChannel(Channel channel) {
        return CHANNEL_TO_SOUND.get(channel);
    }
}
