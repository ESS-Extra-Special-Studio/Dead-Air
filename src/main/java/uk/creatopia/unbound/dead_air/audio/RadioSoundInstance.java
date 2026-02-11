package uk.creatopia.unbound.dead_air.audio;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.registries.ForgeRegistries;
import uk.creatopia.unbound.dead_air.Config;
import uk.creatopia.unbound.dead_air.Dead_air;
import uk.creatopia.unbound.dead_air.music.MusicStationManager;
import uk.creatopia.unbound.dead_air.radio.RadioStation;

import java.util.List;

/**
 * Custom sound instance for radio station playback with signal strength effects.
 */
@SuppressWarnings("null")
public class RadioSoundInstance extends AbstractTickableSoundInstance {
    private final RadioStation station;
    private float signalStrength;
    private final float baseVolume;
    private List<ResourceLocation> tracks;
    private int currentTrackIndex = 0;
    private int ticksSinceStart = 0;
    private static final int TICKS_PER_TRACK = 20 * 60 * 3; // 3 minutes per track (approximate)

    public RadioSoundInstance(RadioStation station, float signalStrength) {
        // Always use MUSIC - radio replaces vanilla music entirely (MusicManagerMixin disables vanilla music).
        // Never use AMBIENT or RECORDS - music only plays through radio stations.
        super(getValidSoundEventForStation(station),
            SoundSource.MUSIC,
            SoundInstance.createUnseededRandom());
        this.station = station;
        this.signalStrength = signalStrength;
        // Use config volume; if config not loaded yet (e.g. 0), fall back to 0.7 so music can play
        float configMax = (float) Config.maxVolume;
        this.baseVolume = configMax >= 0.01f ? configMax : 0.7f;
        this.looping = false;
        this.attenuation = Attenuation.NONE;
        this.tracks = Config.simplifiedMusicDebug ? List.of(SIMPLE_DEBUG_TRACK) : MusicStationManager.getTracksForStation(station.getId());
        updateVolume();
        
        Dead_air.LOGGER.info("Created RadioSoundInstance for station {} with {} tracks (simplified={})", 
            station.getId(), tracks != null ? tracks.size() : 0, Config.simplifiedMusicDebug);
        if (tracks != null && !tracks.isEmpty()) {
            Dead_air.LOGGER.info("Playing track: {}", getCurrentTrackId());
        }
    }
    
    /** Single known-good track for simplified debug mode (bypasses playlist/track logic). */
    private static final ResourceLocation SIMPLE_DEBUG_TRACK = ResourceLocation.withDefaultNamespace("music_disc.13");

    /**
     * Get a valid SoundEvent for the station.
     * When simplifiedMusicDebug is true: always plays music_disc.13 (no playlist/track logic).
     * Otherwise: cycles through the station's playlist.
     */
    private static SoundEvent getValidSoundEventForStation(RadioStation station) {
        if (station == null) {
            return getPlaceholderSound();
        }

        // DEBUG: Bypass playlist/track logic - just play any known-good track
        if (Config.simplifiedMusicDebug) {
            SoundEvent simple = ForgeRegistries.SOUND_EVENTS.getValue(SIMPLE_DEBUG_TRACK);
            if (simple != null) {
                Dead_air.LOGGER.info("[Dead Air MUSIC] Simplified debug mode: playing {} (no playlist)", SIMPLE_DEBUG_TRACK);
                return simple;
            }
        }

        // Get tracks for this station
        List<ResourceLocation> stationTracks = MusicStationManager.getTracksForStation(station.getId());
        if (stationTracks != null && !stationTracks.isEmpty()) {
            // Use getNextTrack to cycle through tracks in order
            ResourceLocation trackId = MusicStationManager.getNextTrack(station.getId());
            if (trackId != null) {
                SoundEvent soundEvent = ForgeRegistries.SOUND_EVENTS.getValue(trackId);
                if (soundEvent != null) {
                    Dead_air.LOGGER.debug("Selected track {} for station {} (cycling through playlist)", 
                        trackId, station.getId());
                    return soundEvent;
                } else {
                    Dead_air.LOGGER.warn("Track {} not found in registry for station {}", trackId, station.getId());
                }
            }
        } else {
            Dead_air.LOGGER.warn("No tracks found for station {}", station.getId());
        }
        
        // Fallback to placeholder sound
        return getPlaceholderSound();
    }
    
    /**
     * Get the current track ID being played (for logging).
     */
    private ResourceLocation getCurrentTrackId() {
        if (tracks != null && !tracks.isEmpty() && currentTrackIndex >= 0 && currentTrackIndex < tracks.size()) {
            return tracks.get(currentTrackIndex);
        }
        return null;
    }
    
    /**
     * Get a placeholder sound event that won't cause crashes.
     * NEVER returns null - always returns a valid sound event.
     */
    @SuppressWarnings("null")
    private static SoundEvent getPlaceholderSound() {
        // Try multiple common Minecraft sounds as fallbacks
        String[] fallbackSounds = {
            "minecraft:ambient.cave",
            "minecraft:block.note_block.pling",
            "minecraft:entity.experience_orb.pickup",
            "minecraft:ui.button.click",
            "minecraft:block.note_block.note"
        };
        
        for (String soundId : fallbackSounds) {
            try {
                ResourceLocation placeholderId = ResourceLocation.parse(soundId);
                SoundEvent placeholder = ForgeRegistries.SOUND_EVENTS.getValue(placeholderId);
                if (placeholder != null) {
                    Dead_air.LOGGER.debug("Using placeholder sound: {}", soundId);
                    return placeholder;
                }
            } catch (Exception e) {
                // Continue to next fallback
            }
        }
        
        // Last resort: try to get any sound event from registry
        try {
            var sounds = ForgeRegistries.SOUND_EVENTS.getValues();
            if (!sounds.isEmpty()) {
                SoundEvent firstSound = sounds.iterator().next();
                if (firstSound != null) {
                    Dead_air.LOGGER.debug("Using first available sound from registry as placeholder");
                    return firstSound;
                }
            }
        } catch (Exception e) {
            Dead_air.LOGGER.error("Could not get any sound event for placeholder", e);
        }
        
        // Absolute last resort: This should never happen, but we need to return something
        Dead_air.LOGGER.error("CRITICAL: Could not find ANY sound event! Using hardcoded fallback.");
        // Try one more time with a hardcoded ID
        SoundEvent fallback = ForgeRegistries.SOUND_EVENTS.getValue(ResourceLocation.parse("minecraft:block.note_block.pling"));
        if (fallback != null) {
            return fallback;
        }
        
        // If we still can't get a sound, something is very wrong
        // But we can't return null, so we'll throw an exception
        throw new IllegalStateException("CRITICAL: No sound events available in registry! This should never happen.");
    }
    
    @Override
    public void tick() {
        updateVolume();
        ticksSinceStart++;
        if (ticksSinceStart >= TICKS_PER_TRACK) {
            ticksSinceStart = 0;
        }
        if (this.isStopped() && tracks != null && !tracks.isEmpty()) {
            currentTrackIndex = (currentTrackIndex + 1) % tracks.size();
            ResourceLocation nextTrackId = tracks.get(currentTrackIndex);
            if (ForgeRegistries.SOUND_EVENTS.getValue(nextTrackId) != null) {
                Dead_air.LOGGER.debug("Track finished, should move to next track: {}", nextTrackId);
            }
        }
    }
    
    private void updateVolume() {
        float minVol = (float) Config.minVolume;
        float maxVol = baseVolume;
        float volume = minVol + (maxVol - minVol) * signalStrength;
        
        // Add static effect for low signal - more pronounced static when far
        if (signalStrength < 0.3f) {
            // Very weak signal - heavy static, low volume
            volume *= (0.3f + signalStrength * 0.5f); // Volume reduced significantly
        } else if (signalStrength < 0.5f) {
            // Weak signal - moderate static
            volume *= (0.6f + signalStrength * 0.4f); // Volume reduced moderately
        } else if (signalStrength < 0.7f) {
            // Moderate signal - light static
            volume *= (0.8f + signalStrength * 0.2f); // Slight volume reduction
        }
        // Strong signal (>= 0.7) - clear audio, no static
        
        this.volume = Math.max(0.15f, Math.max(minVol, Math.min(maxVol, volume))); // 0.15 min so radio is clearly audible
        
        if (this.volume < 0.01f) {
            Dead_air.LOGGER.warn("Volume very low for station {}: {} (signal: {}, minVol: {}, maxVol: {})", 
                station.getId(), this.volume, signalStrength, minVol, maxVol);
        }
    }
    
    public void updateSignalStrength(float newStrength) {
        this.signalStrength = newStrength;
        updateVolume();
    }
    
    public RadioStation getStation() {
        return station;
    }
    
    public float getSignalStrength() {
        return signalStrength;
    }
    
    @Override
    public boolean canStartSilent() {
        return false; // We need to be audible so the sound manager actually plays us
    }
}
