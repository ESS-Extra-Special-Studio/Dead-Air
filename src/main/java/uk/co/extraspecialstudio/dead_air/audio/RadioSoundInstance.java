package uk.co.extraspecialstudio.dead_air.audio;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.music.MusicStationManager;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.SignalStrength;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Custom sound instance for radio station playback with signal strength effects.
 * Extends AbstractSoundInstance (not tickable) so the engine does not remove us every tick
 * based on isDonePlaying() - which was causing ~47ms stop and tapping.
 */
@SuppressWarnings("null")
public class RadioSoundInstance extends AbstractSoundInstance {
    private final RadioStation station;
    private float signalStrength;
    private long resumeOffsetMs = 0L;
    private volatile boolean streamRequested = false;
    private volatile boolean streamDelivered = false;

    /** Chunk used when discarding decoded audio to reach a resume point. */
    private static final int SKIP_CHUNK_BYTES = 64 * 1024;
    /** Anything past this is treated as a bad resume point rather than a very long skip. */
    private static final long MAX_SKIP_MS = 20 * 60_000L;

    /**
     * Start or cycle radio playback for a station (client). Uses the local player for Remix/Jukebox track gating.
     *
     * @param startRandom true when tuning in (random first track); false when advancing to next track
     */
    public static RadioSoundInstance create(RadioStation station, float signalStrength, boolean startRandom, Vec3 playerPos) {
        if (station == null) {
            return null;
        }
        if (station.isInternetStream()) {
            return new RadioSoundInstance(station, signalStrength, getPlaceholderSound(), playerPos);
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc != null ? mc.player : null;
        List<ResourceLocation> tracks = MusicStationManager.getPlaybackTracks(station.getId(), player);
        if (tracks.isEmpty()) {
            Dead_air.LOGGER.warn("No tracks found for station {}", station.getId());
            return new RadioSoundInstance(station, signalStrength, getPlaceholderSound(), playerPos);
        }
        ResourceLocation trackId = startRandom
            ? MusicStationManager.getRandomTrack(station.getId(), player)
            : MusicStationManager.getNextTrack(station.getId(), player);
        if (trackId == null) {
            trackId = tracks.get(0);
        }
        SoundEvent event = getSoundEventForTrackId(trackId);
        return new RadioSoundInstance(station, signalStrength, event, playerPos);
    }

    /**
     * When true, position is relative to the listener so sound follows the player.
     */
    @Override
    public boolean isRelative() {
        return true;
    }

    /**
     * Start this track part-way through. Must be set before the sound is handed to the sound manager.
     */
    public void setResumeOffsetMs(long offsetMs) {
        this.resumeOffsetMs = offsetMs > 0L ? offsetMs : 0L;
    }

    /**
     * True while the engine is still decoding this sound's stream. Skipping to a resume point can take
     * seconds, so callers watching for a start that never happened must not count this as a failure.
     */
    public boolean isStreamPending() {
        return streamRequested && !streamDelivered;
    }

    /**
     * Radio tracks are streamed, so only a couple of seconds are ever queued on the OpenAL source and
     * AL_SEC_OFFSET cannot move playback. Reaching a resume point means discarding decoded audio from the
     * stream before it is attached to a channel.
     */
    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        this.streamRequested = true;
        CompletableFuture<AudioStream> base = soundBuffers.getStream(sound.getPath(), looping);
        long skipMs = this.resumeOffsetMs;
        if (skipMs >= 1000L && skipMs <= MAX_SKIP_MS) {
            base = base.thenApplyAsync(stream -> skipForward(stream, skipMs), Util.backgroundExecutor());
        }
        return base.whenComplete((stream, error) -> this.streamDelivered = true);
    }

    private static AudioStream skipForward(AudioStream stream, long skipMs) {
        try {
            AudioFormat format = stream.getFormat();
            long bytesPerSecond = (long) format.getSampleRate() * format.getChannels() * (format.getSampleSizeInBits() / 8);
            if (bytesPerSecond <= 0L) {
                return stream;
            }
            long startedAt = System.currentTimeMillis();
            long target = bytesPerSecond * skipMs / 1000L;
            long skipped = 0L;
            while (skipped < target) {
                int want = (int) Math.min(SKIP_CHUNK_BYTES, target - skipped);
                ByteBuffer chunk = stream.read(want);
                if (chunk == null || !chunk.hasRemaining()) {
                    break;
                }
                skipped += chunk.remaining();
            }
            if (AudioManager.LOG_PLAYBACK) {
                Dead_air.LOGGER.info("[radio] skipped {}ms of {}ms requested in {}ms",
                    skipped * 1000L / bytesPerSecond, skipMs, System.currentTimeMillis() - startedAt);
            }
        } catch (Throwable t) {
            Dead_air.LOGGER.warn("Failed to skip {}ms into radio track, starting from the beginning", skipMs, t);
        }
        return stream;
    }

    /**
     * Create a sound that plays a specific track (used when restarting for volume change so we do not advance the playlist).
     */
    public static RadioSoundInstance createForTrack(RadioStation station, float signalStrength, ResourceLocation trackId, Vec3 playerPos) {
        SoundEvent event = getSoundEventForTrackId(trackId);
        return new RadioSoundInstance(station, signalStrength, event, playerPos);
    }

    private static SoundEvent getSoundEventForTrackId(ResourceLocation trackId) {
        if (trackId == null) return getPlaceholderSound();
        SoundEvent event = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(trackId);
        return event != null ? event : getPlaceholderSound();
    }

    /** Constructor for createForTrack: same track, new volume (does not advance playlist). */
    private RadioSoundInstance(RadioStation station, float signalStrength, SoundEvent soundEvent, Vec3 playerPos) {
        super(soundEvent, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.station = station;
        this.signalStrength = signalStrength;
        // Non-looping: stream plays once, then the engine stops us so the next track can start.
        // AbstractSoundInstance (non-tickable) + MIN_VOLUME_FOR_ENGINE avoids the old ~47ms tickable cut-off.
        this.looping = false;
        this.attenuation = Attenuation.NONE;
        if (playerPos != null) {
            this.x = playerPos.x;
            this.y = playerPos.y;
            this.z = playerPos.z;
        }
        updateVolume();
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
                SoundEvent placeholder = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(placeholderId);
                if (placeholder != null) {
                    return placeholder;
                }
            } catch (Exception e) {
                // Continue to next fallback
            }
        }
        
        // Last resort: try to get any sound event from registry
        try {
            var sounds = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT;
            SoundEvent firstSound = sounds.iterator().next();
            if (firstSound != null) {
                return firstSound;
            }
        } catch (Exception e) {
            Dead_air.LOGGER.error("Could not get any sound event for placeholder", e);
        }
        
        // Absolute last resort: try hardcoded fallback
        SoundEvent fallback = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("minecraft:block.note_block.pling"));
        if (fallback != null) {
            return fallback;
        }
        
        // If we still can't get a sound, something is very wrong
        // But we can't return null, so we'll throw an exception
        throw new IllegalStateException("CRITICAL: No sound events available in registry! This should never happen.");
    }
    
    /** Volume steps with signal bars: 5/5 = loudest, then clearly quieter per bar so distance is audible. 0/5 = off. Uses current Config.maxVolume so GUI slider takes effect. */
    private void updateVolume() {
        int bars = SignalStrength.getSignalBars(signalStrength);
        if (bars == 0) {
            this.volume = 0f;
            return;
        }
        float minVol = (float) Config.minVolume;
        float maxVol = (float) (Config.maxVolume >= 0.01 ? Config.maxVolume : 0.7f);
        float barMultiplier = switch (bars) {
            case 5 -> 1.0f;
            case 4 -> 0.7f;
            case 3 -> 0.45f;
            case 2 -> 0.25f;
            case 1 -> 0.12f;
            default -> 0f;
        };
        this.volume = Math.min(maxVol, minVol + barMultiplier * (maxVol - minVol));
    }

    /** Minimum volume we return so the engine does not remove us (many engines stop "silent" sounds). Actual stop is done by AudioManager when no signal. */
    private static final float MIN_VOLUME_FOR_ENGINE = 0.01f;

    @Override
    public float getVolume() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level != null && mc.player != null && mc.level.isClientSide && station != null) {
            float live = uk.co.extraspecialstudio.dead_air.client.WalkieTalkieOverlay.calculateSignalStrengthForStation(mc, station);
            this.signalStrength = live;
        }
        updateVolume();
        // Never return 0: engine may stop sounds it considers "silent", causing ~47ms cut-off. We stop ourselves in AudioManager when no signal.
        return this.volume <= 0f ? MIN_VOLUME_FOR_ENGINE : this.volume;
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

    /** AbstractSoundInstance does not have isStopped(); we are non-tickable so only the engine removes us when the track ends. */
    public boolean isStopped() {
        return false;
    }
}
