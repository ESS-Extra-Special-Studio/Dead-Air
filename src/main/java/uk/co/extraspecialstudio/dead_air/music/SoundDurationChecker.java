package uk.co.extraspecialstudio.dead_air.music;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import uk.co.extraspecialstudio.dead_air.Dead_air;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side helper to check sound duration.
 * Filters out short sounds (< 20 seconds) to ensure only actual music tracks play.
 */
@OnlyIn(Dist.CLIENT)
@SuppressWarnings("null")
public class SoundDurationChecker {
    private static final Map<ResourceLocation, Float> DURATION_CACHE = new HashMap<>();
    private static final float MIN_DURATION_SECONDS = 20.0f; // Minimum 20 seconds for music tracks
    
    /**
     * Check if a sound is long enough to be considered a music track (>= 20 seconds).
     * Uses cached results to avoid repeated file I/O.
     * If duration cannot be determined, falls back to allowing the track (to avoid filtering out valid music).
     */
    public static boolean isLongEnough(ResourceLocation soundId) {
        // Check cache first
        Float cachedDuration = DURATION_CACHE.get(soundId);
        if (cachedDuration != null) {
            // If duration is 0, it means we couldn't determine it - allow it through
            if (cachedDuration == 0.0f) {
                return true; // Fallback: allow if we can't determine duration
            }
            return cachedDuration >= MIN_DURATION_SECONDS;
        }
        
        // Try to get duration
        float duration = getSoundDuration(soundId);
        DURATION_CACHE.put(soundId, duration);
        
        // If duration is 0, it means we couldn't determine it - allow it through as fallback
        if (duration == 0.0f) {
            return true; // Fallback: allow if we can't determine duration
        }
        
        return duration >= MIN_DURATION_SECONDS;
    }
    
    /**
     * Get the duration of a sound in seconds (public API for track advance).
     * Returns 0 if duration cannot be determined. Uses cache.
     */
    public static float getDurationSeconds(ResourceLocation soundId) {
        try {
            Float cached = DURATION_CACHE.get(soundId);
            if (cached != null && cached > 0.0f) {
                return cached;
            }
            float d = getSoundDuration(soundId);
            if (d > 0.0f) {
                DURATION_CACHE.put(soundId, d);
            }
            return d;
        } catch (Throwable t) {
            // LinkageError/NoClassDefFoundError must not crash the client on tune
            Dead_air.LOGGER.debug("Duration lookup failed for {}: {}", soundId, t.toString());
            return 0.0f;
        }
    }

    /**
     * Get the duration of a sound in seconds (internal, no cache write).
     * Returns 0 if duration cannot be determined.
     */
    private static float getSoundDuration(ResourceLocation soundId) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getSoundManager() == null || mc.getResourceManager() == null) {
                return 0.0f;
            }

            SoundEvent soundEvent = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(soundId);
            if (soundEvent == null) {
                return 0.0f;
            }

            SoundManager soundManager = mc.getSoundManager();
            ResourceLocation oggResource = resolveOggResource(soundManager, soundId);
            if (oggResource != null) {
                float duration = readDurationFromResource(mc, oggResource);
                if (duration > 0.0f) {
                    return duration;
                }
            }

            // Legacy fallback: direct path from event id (works for flat sound paths only)
            String soundPath = "sounds/" + soundId.getPath() + ".ogg";
            ResourceLocation legacyResource = ResourceLocation.fromNamespaceAndPath(soundId.getNamespace(), soundPath);
            return readDurationFromResource(mc, legacyResource);
        } catch (Throwable e) {
            return 0.0f;
        }
    }

    /** Resolve the OGG resource the sound engine uses (via sounds.json), not a guessed flat path. */
    private static ResourceLocation resolveOggResource(SoundManager soundManager, ResourceLocation soundEventId) {
        try {
            WeighedSoundEvents weighed = soundManager.getSoundEvent(soundEventId);
            if (weighed == null) {
                return null;
            }
            Sound sound = weighed.getSound(SoundInstance.createUnseededRandom());
            if (sound == null) {
                return null;
            }
            ResourceLocation path = sound.getLocation();
            if (path == null) {
                return null;
            }
            return ResourceLocation.fromNamespaceAndPath(path.getNamespace(), "sounds/" + path.getPath() + ".ogg");
        } catch (Exception e) {
            Dead_air.LOGGER.debug("Could not resolve OGG path for {}: {}", soundEventId, e.getMessage());
            return null;
        }
    }

    private static float readDurationFromResource(Minecraft mc, ResourceLocation soundResource) {
        try {
            var resourceOpt = mc.getResourceManager().getResource(soundResource);
            if (resourceOpt.isEmpty()) {
                return 0.0f;
            }
            var resource = resourceOpt.get();
            try (InputStream stream = resource.open()) {
                if (soundResource.getPath().endsWith(".ogg")) {
                    float oggDuration = OggDurationReader.readDurationSeconds(stream);
                    if (oggDuration > 0.0f) {
                        return oggDuration;
                    }
                }
            } catch (Throwable ignored) {
            }

            // Non-OGG fallback (unlikely for radio tracks)
            try (InputStream stream = resource.open()) {
                AudioInputStream audioStream = AudioSystem.getAudioInputStream(stream);
                AudioFileFormat format = AudioSystem.getAudioFileFormat(audioStream);
                long frameLength = format.getFrameLength();
                float frameRate = format.getFormat().getFrameRate();
                if (frameLength > 0 && frameRate > 0) {
                    return frameLength / frameRate;
                }
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {
        }
        return 0.0f;
    }
    
    /**
     * Clear the duration cache (useful when reloading resources).
     */
    public static void clearCache() {
        DURATION_CACHE.clear();
    }
}
