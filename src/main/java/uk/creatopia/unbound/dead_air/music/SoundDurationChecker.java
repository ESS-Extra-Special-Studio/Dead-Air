package uk.creatopia.unbound.dead_air.music;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import uk.creatopia.unbound.dead_air.Dead_air;

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
            Dead_air.LOGGER.debug("Could not determine duration for {}, allowing through as fallback", soundId);
            return true; // Fallback: allow if we can't determine duration
        }
        
        return duration >= MIN_DURATION_SECONDS;
    }
    
    /**
     * Get the duration of a sound in seconds.
     * Returns 0 if duration cannot be determined.
     */
    private static float getSoundDuration(ResourceLocation soundId) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getSoundManager() == null) {
                return 0.0f;
            }
            
            SoundEvent soundEvent = ForgeRegistries.SOUND_EVENTS.getValue(soundId);
            if (soundEvent == null) {
                return 0.0f;
            }
            
            SoundManager soundManager = mc.getSoundManager();
            
            // Try to get the Sound object from the SoundEvent
            // This requires accessing the sound registry
            try {
                // Get the sound location - Minecraft sounds are typically at:
                // assets/{namespace}/sounds/{path}.ogg
                String soundPath = "sounds/" + soundId.getPath() + ".ogg";
                ResourceLocation soundResource = ResourceLocation.fromNamespaceAndPath(
                    soundId.getNamespace(), soundPath);
                
                // Try to load the audio file from resources
                if (mc.getResourceManager() != null) {
                    var resourceOpt = mc.getResourceManager().getResource(soundResource);
                    if (resourceOpt.isPresent()) {
                        var resource = resourceOpt.get();
                        InputStream stream = null;
                        AudioInputStream audioStream = null;
                        try {
                            stream = resource.open();
                            audioStream = AudioSystem.getAudioInputStream(stream);
                            
                            AudioFileFormat format = AudioSystem.getAudioFileFormat(audioStream);
                            
                            // Get duration from frame length and frame rate
                            long frameLength = format.getFrameLength();
                            float frameRate = format.getFormat().getFrameRate();
                            
                            if (frameLength > 0 && frameRate > 0) {
                                float duration = frameLength / frameRate;
                                Dead_air.LOGGER.debug("Sound {} duration: {} seconds", soundId, duration);
                                return duration;
                            }
                        } catch (Exception e) {
                            // Could not read audio file
                        } finally {
                            try {
                                if (audioStream != null) audioStream.close();
                                if (stream != null) stream.close();
                            } catch (Exception e) {
                                // Ignore close errors
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Could not determine duration - return 0 (will be filtered out)
                Dead_air.LOGGER.debug("Could not determine duration for sound {}: {}", soundId, e.getMessage());
            }
        } catch (Exception e) {
            Dead_air.LOGGER.debug("Error checking sound duration for {}: {}", soundId, e.getMessage());
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
