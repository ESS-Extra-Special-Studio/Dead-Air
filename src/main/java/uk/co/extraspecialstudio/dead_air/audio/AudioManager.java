package uk.co.extraspecialstudio.dead_air.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.music.MusicStationManager;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.RadioTower;
import uk.co.extraspecialstudio.dead_air.radio.SignalStrength;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;
import uk.co.extraspecialstudio.dead_air.radio.TowerManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Client-side audio manager for radio station playback.
 *
 * Design rules:
 * - Music plays only when the player is tuned to a station (walkie on, station selected).
 * - Music plays only when in range of a powered tower broadcasting that station with signal >= 0.1.
 * - Out of tower range or tower unpowered: no music. No fallback playback.
 * - Only one station plays at a time (the tuned station). Music plays only when walkie is on the player (in hand, hotbar, or inventory).
 * - Each station uses pre-made playlists (see MusicStationManager); custom tracks are added later.
 */
@SuppressWarnings("null") // mc.level is checked for null before use
public class AudioManager {
    private static final Map<ResourceLocation, RadioSoundInstance> ACTIVE_SOUNDS = new java.util.concurrent.ConcurrentHashMap<>();
    private static RadioStation currentStation = null;
    private static float currentSignalStrength = 0.0f;
    /** Last signal strength posted per station (client-side). */
    private static final Map<ResourceLocation, Float> LAST_POSTED_SIGNAL = new java.util.concurrent.ConcurrentHashMap<>();
    /** Server-provided signal when client has no tower data. Thread-safe: updated from network thread. */
    private static final Map<ResourceLocation, Float> SERVER_SIGNAL_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    /** Client tick when we last received a server signal (so we can treat stale as 0 when no cache and stop out of range). */
    private static final Map<ResourceLocation, Integer> SERVER_SIGNAL_TICK = new java.util.concurrent.ConcurrentHashMap<>();
    /** If we have no cache and no server response in this many ticks, treat as 0 (stop music when out of range). */
    private static final int SERVER_SIGNAL_STALE_TICKS = 10;
    /** Tick when we last started a sound per station; used so we don't treat "not active yet" as "finished" right after play(). */
    private static final Map<ResourceLocation, Integer> SOUND_START_TICK = new java.util.concurrent.ConcurrentHashMap<>();
    /** Stations whose sound was removed by the sound engine this tick (set from mixin). Unused in new flow; mixin now sets PENDING_NEXT_TRACK or RESTART_SAME_STATION. */
    private static final Set<ResourceLocation> STATIONS_SOUND_REMOVED_THIS_TICK = java.util.Collections.synchronizedSet(new HashSet<>());
    /** Set by mixin when a track ended after playing long enough: next update() will start the next track in playlist. */
    private static final Set<ResourceLocation> PENDING_NEXT_TRACK = java.util.Collections.synchronizedSet(new HashSet<>());
    /** Set by mixin when a track was removed too soon (e.g. engine quirk): next update() will restart the same track. */
    private static final Set<ResourceLocation> RESTART_SAME_STATION = java.util.Collections.synchronizedSet(new HashSet<>());
    /**
     * Stations interrupted mid-track (dimension change / sound-engine wipe). Values are milliseconds already played
     * so we can resume the same track near the same position instead of picking a new random song.
     */
    private static final Map<ResourceLocation, Long> RESUME_OFFSET_MS = new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * Durable resume snapshot, kept separately from {@link #RESUME_OFFSET_MS} because a dimension change
     * drops signal to 0 (tower cache cleared) and that path clears the live resume state before playback restarts.
     */
    private static final Map<ResourceLocation, ResourceLocation> RESUME_TRACK = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Long> RESUME_SNAPSHOT_MS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Long> RESUME_SNAPSHOT_AT_MS = new java.util.concurrent.ConcurrentHashMap<>();
    /** Older snapshots are ignored so a long absence starts a fresh track. */
    private static final long RESUME_SNAPSHOT_VALID_MS = 120_000L;
    /**
     * Stations whose snapshot may be used to resume. The snapshot itself is refreshed every tick so it
     * survives teardown in any order; arming is what distinguishes an interrupt from the player stopping.
     */
    private static final Set<ResourceLocation> RESUME_ARMED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Verbose resume/playback logging. Flip to true when diagnosing cross-dimension / resume issues. */
    public static final boolean LOG_PLAYBACK = false;
    /** Stations whose stream reported a natural end this cycle (playlist advance), not an interrupt. */
    private static final Set<ResourceLocation> NATURAL_FINISH = java.util.Collections.synchronizedSet(new HashSet<>());
    /** Last signal bar level (0-5); used for updateSignalStrength. No restart on bar change - track keeps playing. */
    private static int lastBarLevel = -1;
    /** When we already have sound: only stop when signal drops below this (hysteresis prevents restart on boundary jitter). */
    private static final float STOP_THRESHOLD_WHEN_PLAYING = 0.02f;
    /** Last game tick we ran track start/advance logic per station (unreliable across Render vs game thread). */
    private static final Map<ResourceLocation, Integer> LAST_TICK_TRACK_LOGIC_RAN = new java.util.concurrent.ConcurrentHashMap<>();
    /** Last wall-clock ms we ran track start/advance logic per station. Throttles to ~20/sec regardless of which thread calls update(). */
    private static final Map<ResourceLocation, Long> LAST_TRACK_LOGIC_TIME_MS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long MIN_MS_BETWEEN_TRACK_LOGIC = 50L;
    /** Last wall-clock ms we called play() per station. Hard cap: never start a new track within 100ms of the previous start. */
    private static final Map<ResourceLocation, Long> LAST_PLAY_TIME_MS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long MIN_MS_BETWEEN_PLAY = 100L;
    /** Real wall-clock time of the last play() call. Unlike LAST_PLAY_TIME_MS this is never back-dated for resumes. */
    private static final Map<ResourceLocation, Long> LAST_PLAY_ISSUED_MS = new java.util.concurrent.ConcurrentHashMap<>();
    /** How long a started sound may go without being bound to a channel before we treat the start as failed. */
    private static final long START_CONFIRM_MS = 4000L;
    /** Consecutive failed starts per station; stops us looping forever if a sound never binds (e.g. placeholder sounds). */
    private static final Map<ResourceLocation, Integer> FAILED_START_RETRIES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_FAILED_START_RETRIES = 8;
    /** Minimum ms after play() before we consider track "finished" when we do not know real duration. */
    private static final long MIN_MS_PLAYED_BEFORE_FINISHED = 30_000L;
    /** One gate: run track start/advance logic at most once per 2 sec per station. Prevents any path from spamming play(). */
    private static final Map<ResourceLocation, Long> LAST_FULL_UPDATE_MS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long MIN_MS_BETWEEN_FULL_UPDATE = 2000L;
    /** Cooldown: never start a new track within this many ms of last start (stops engine-from-stopping-immediately loop -> tapping). */
    private static final long MIN_MS_BETWEEN_START = 2000L;
    /** When the engine stops us very early, pause briefly before retrying to avoid rapid start/stop loops. */
    private static final long EARLY_STOP_THRESHOLD_MS = 5000L;
    /** Short retry delay so tracks resume quickly while still preventing spam loops. */
    private static final long EARLY_STOP_BACKOFF_MS = 1500L;
    /** Per-station: time (ms) when we last got an early stop from the engine; used to enforce backoff. */
    private static final Map<ResourceLocation, Long> LAST_EARLY_STOP_MS = new java.util.concurrent.ConcurrentHashMap<>();
    /** When true, mixin should not add PENDING_NEXT_TRACK or early-stop backoff (we explicitly called stopAll/stopStation for switching). */
    private static volatile boolean EXPLICIT_STOP_IN_PROGRESS = false;
    /** Fallback when a track duration cannot be read from resources. */
    private static final long UNKNOWN_TRACK_FALLBACK_MS = 4 * 60_000L; // 4 minutes
    /** Small buffer so we don't cut right at metadata boundary. */
    private static final long TRACK_END_BUFFER_MS = 750L;
    /** How far short of a track's end a stop must be before we treat it as an interrupt rather than a finish. */
    private static final long PREMATURE_STOP_SLACK_MS = 3000L;

    /**
     * Update audio playback based on player position and tuned station.
     * Now supports audio blending for overlapping stations.
     * Music starts immediately and allows mid-song tuning.
     */
    public static void update(Minecraft mc, RadioStation station, Vec3 playerPos) {
        // Safety check: ensure we're on client side
        if (mc == null || mc.level == null || mc.player == null || station == null) {
            stopAll();
            return;
        }
        // Additional safety: ensure level is client-side
        if (mc.level.isClientSide == false) {
            return;
        }
        
        // Emergency Broadcast stations do not play music
        if (station.getType() == RadioStation.StationType.EMERGENCY_BROADCAST) {
            stopAll();
            return;
        }
        
        // We no longer poll engine.isActive() - for streaming/OGG it often returns false while playing and caused track skipping.
        // We only advance when the engine actually removes the sound (SoundEngineMixin.onRadioSoundRemovedFromEngine).
        
        // If switching stations, stop ALL radio sounds so the previous track definitely stops, then force new sound
        boolean isSwitchingStations = currentStation != null && !currentStation.getId().equals(station.getId());
        if (isSwitchingStations) {
            stopAll();
        }
        
        // Set currentStation early so overlay shows "Now Playing" - only clear on explicit stop/return
        currentStation = station;
        
        try {
            // Signal to decide "can we start": overlay (server → TowerManager → cache) so if HUD shows bars, music can start
            float signalForStart = uk.co.extraspecialstudio.dead_air.client.WalkieTalkieOverlay.calculateSignalStrengthForStation(mc, station);
            // When musicPlaysWithoutTower is true, boost weak signal so music can play when no tower in world - but never boost 0 (out of range)
            // so music still stops dynamically when leaving tower range and starts when re-entering.
            if (uk.co.extraspecialstudio.dead_air.Config.musicPlaysWithoutTower && signalForStart > 0f && signalForStart < 0.2f) {
                signalForStart = 0.5f;
            }

            // Stop if no signal: 0 bars or very weak so music stops when leaving tower range
            int bars = SignalStrength.getSignalBars(signalForStart);
            boolean noSignal = signalForStart < 0.05f || (bars == 0 && !uk.co.extraspecialstudio.dead_air.Config.musicPlaysWithoutTower);
            if (noSignal) {
                if (signalForStart < 0.1f) SERVER_SIGNAL_CACHE.remove(station.getId());
                PENDING_NEXT_TRACK.remove(station.getId());
                RESTART_SAME_STATION.remove(station.getId());
                RESUME_OFFSET_MS.remove(station.getId());
                LAST_PLAY_TIME_MS.remove(station.getId()); // allow immediate restart when back in range
                lastBarLevel = -1;
                stopStation(station);
                ACTIVE_SOUNDS.remove(station.getId());
                currentStation = null;
                currentSignalStrength = 0.0f;
                stopAllOtherStations(null);
                return;
            }

            float signal = signalForStart;
            currentSignalStrength = signal;

            // Emit signal change event (client) when signal changes meaningfully.
            float prev = LAST_POSTED_SIGNAL.getOrDefault(station.getId(), -1f);
            if (prev < 0f || Math.abs(prev - signal) >= 0.02f) {
                LAST_POSTED_SIGNAL.put(station.getId(), signal);
                uk.co.extraspecialstudio.dead_air.api.events.DeadAirEvents.post(
                    new uk.co.extraspecialstudio.dead_air.api.events.types.DeadAirSignalStrengthChangedEvent(station.getId(), prev < 0f ? signal : prev, signal)
                );
            }
            stopAllOtherStations(station.getId());

            if (station.isInternetStream()) {
                if (signal < STOP_THRESHOLD_WHEN_PLAYING && InternetStreamManager.isPlaying(station.getId())) {
                    lastBarLevel = -1;
                    SERVER_SIGNAL_CACHE.remove(station.getId());
                    LAST_PLAY_TIME_MS.remove(station.getId());
                    InternetStreamManager.stopAll();
                    currentStation = null;
                    currentSignalStrength = 0.0f;
                    stopAllOtherStations(null);
                    return;
                }
                InternetStreamManager.updateSignal(signal);
                boolean started = InternetStreamManager.ensurePlaying(station, signal);
                if (started) {
                    ResourceLocation trackId = MusicStationManager.getCurrentTrackId(station.getId(), mc.player);
                    uk.co.extraspecialstudio.dead_air.api.events.DeadAirEvents.post(
                        new uk.co.extraspecialstudio.dead_air.api.events.types.DeadAirBroadcastStartedEvent(station.getId(), trackId, signal, playerPos)
                    );
                }
                int streamBars = SignalStrength.getSignalBars(signal);
                lastBarLevel = streamBars;
                return;
            }

            RadioSoundInstance sound = ACTIVE_SOUNDS.get(station.getId());

            // If we already have a sound for this station, update volume. Timer is a fallback if stream-end callbacks are missed.
            if (sound != null) {
                if (PENDING_NEXT_TRACK.remove(station.getId())) {
                    advanceToNextTrack(mc, station, signal, playerPos);
                    return;
                }
                long playedMs = System.currentTimeMillis() - LAST_PLAY_TIME_MS.getOrDefault(station.getId(), 0L);
                // A start issued while the sound engine is still reloading (dimension change) never reaches a channel.
                // Without this it would sit silent until the duration timer expired and skipped to another track.
                long sinceIssued = System.currentTimeMillis() - LAST_PLAY_ISSUED_MS.getOrDefault(station.getId(), 0L);
                if (RadioChannelTracker.hasChannel(sound)) {
                    if (FAILED_START_RETRIES.remove(station.getId()) != null && LOG_PLAYBACK) {
                        Dead_air.LOGGER.info("[radio] channel bound station={} track={}", station.getId(), sound.getLocation());
                    }
                    // Keep the resume point current every tick: teardown paths run in an unpredictable order
                    // and any of them may clear the live state before it can be recorded.
                    snapshotResumePoint(station.getId(), playedMs, sound.getLocation());
                } else if (sinceIssued > START_CONFIRM_MS && !sound.isStreamPending() && isTrackedAsStreaming(sound)) {
                    int attempts = FAILED_START_RETRIES.merge(station.getId(), 1, Integer::sum);
                    boolean giveUp = attempts > MAX_FAILED_START_RETRIES;
                    // Once out of retries, start a fresh track rather than leaving the player in silence.
                    retryFailedStart(station, sound, giveUp ? 0L : playedMs);
                    if (giveUp) {
                        FAILED_START_RETRIES.remove(station.getId());
                        Dead_air.LOGGER.warn("Radio for {} could not resume after {} attempts; starting a new track", station.getId(), attempts);
                    }
                    return;
                }
                ResourceLocation trackId = MusicStationManager.getCurrentTrackId(station.getId(), mc.player);
                float trackDurationSec = 0f;
                if (trackId != null) {
                    try {
                        trackDurationSec = uk.co.extraspecialstudio.dead_air.music.SoundDurationChecker.getDurationSeconds(trackId);
                    } catch (Throwable ignored) {
                        // Duration helpers must never crash playback (ModuleClassLoader NCDFE)
                    }
                }
                long advanceAfterMs = trackDurationSec > 0
                    ? (long) (trackDurationSec * 1000L) + TRACK_END_BUFFER_MS
                    : UNKNOWN_TRACK_FALLBACK_MS;
                if (playedMs >= advanceAfterMs && !mc.getSoundManager().isActive(sound)) {
                    advanceToNextTrack(mc, station, signal, playerPos);
                    return;
                }
                int currentBars = SignalStrength.getSignalBars(signal);
                // Use lower stop threshold when already playing (hysteresis) - prevents restart when crossing bar boundaries
                if (signal < STOP_THRESHOLD_WHEN_PLAYING) {
                    if (LOG_PLAYBACK) {
                        Dead_air.LOGGER.info("[radio] stop station={} reason=signal {} below {} played={}ms",
                            station.getId(), signal, STOP_THRESHOLD_WHEN_PLAYING, playedMs);
                    }
                    // Losing signal mid-track (dimension change clears the tower cache) must not lose the position.
                    armResume(station.getId(), "signal lost");
                    lastBarLevel = -1;
                    SERVER_SIGNAL_CACHE.remove(station.getId());
                    LAST_PLAY_TIME_MS.remove(station.getId()); // allow restart when back in range
                    stopStation(station);
                    ACTIVE_SOUNDS.remove(station.getId());
                    currentStation = null;
                    currentSignalStrength = 0.0f;
                    stopAllOtherStations(null);
                    return;
                }
                // Don't restart on bar change - track keeps playing. RadioSoundInstance.getVolume() fetches live signal.
                lastBarLevel = currentBars;
                sound.updateSignalStrength(signal);
                return;
            }

            // No sound for this station: start one. Cooldown + early-stop backoff prevent restart loop.
            try {
                long nowStart = System.currentTimeMillis();
                long earlyStopAt = LAST_EARLY_STOP_MS.getOrDefault(station.getId(), 0L);
                long sinceEarlyStop = earlyStopAt > 0 ? (nowStart - earlyStopAt) : Long.MAX_VALUE;
                if (sinceEarlyStop < EARLY_STOP_BACKOFF_MS) {
                    stopAllOtherStations(station.getId());
                    return;
                }
                long sinceLastStart = nowStart - LAST_PLAY_TIME_MS.getOrDefault(station.getId(), 0L);
                if (sinceLastStart < MIN_MS_BETWEEN_START) {
                    stopAllOtherStations(station.getId());
                    return;
                }
                boolean restartSame = RESTART_SAME_STATION.remove(station.getId());
                boolean playNext = PENDING_NEXT_TRACK.contains(station.getId());
                Long resumeOffset = RESUME_OFFSET_MS.remove(station.getId());
                ResourceLocation resumeTrack = null;
                if (!playNext) {
                    // Dimension change drops signal to 0 first, which clears the live resume state, so fall back
                    // to the durable snapshot and pick the same track up where it stopped.
                    ResumePoint point = takeResumePoint(station.getId(), nowStart);
                    if (point != null) {
                        restartSame = true;
                        resumeTrack = point.trackId();
                        if (resumeOffset == null) resumeOffset = point.offsetMs();
                    }
                }
                boolean startRandom = !restartSame && (isSwitchingStations || !playNext);

                RadioSoundInstance newSound = restartSame
                    ? RadioSoundInstance.createForTrack(station, signal,
                        resumeTrack != null ? resumeTrack : MusicStationManager.getCurrentTrackId(station.getId(), mc.player), playerPos)
                    : createSoundForStation(station, signal, startRandom, playerPos);
                if (newSound != null && mc.getSoundManager() != null) {
                    if (playNext) {
                        PENDING_NEXT_TRACK.remove(station.getId());
                    }
                    ACTIVE_SOUNDS.put(station.getId(), newSound);
                    long startMs = System.currentTimeMillis();
                    if (resumeOffset != null && resumeOffset > 0L) {
                        // Pretend we started earlier so duration / advance math stays correct after the skip.
                        startMs -= resumeOffset;
                        newSound.setResumeOffsetMs(resumeOffset);
                    }
                    if (mc.player != null) SOUND_START_TICK.put(station.getId(), mc.player.tickCount);
                    LAST_PLAY_TIME_MS.put(station.getId(), startMs);
                    LAST_PLAY_ISSUED_MS.put(station.getId(), System.currentTimeMillis());
                    int currentBars = SignalStrength.getSignalBars(signal);
                    lastBarLevel = currentBars;
                    if (LOG_PLAYBACK) {
                        Dead_air.LOGGER.info("[radio] start station={} track={} resumeOffset={}ms restartSame={} playNext={} signal={}",
                            station.getId(), newSound.getLocation(), resumeOffset == null ? 0L : resumeOffset, restartSame, playNext, signal);
                    }
                    mc.getSoundManager().play(newSound);

                    // Emit broadcast started event (client) with station + currently selected track.
                    ResourceLocation trackId = MusicStationManager.getCurrentTrackId(station.getId(), mc.player);
                    uk.co.extraspecialstudio.dead_air.api.events.DeadAirEvents.post(
                        new uk.co.extraspecialstudio.dead_air.api.events.types.DeadAirBroadcastStartedEvent(station.getId(), trackId, signal, playerPos)
                    );
                }
            } catch (Exception e) {
                Dead_air.LOGGER.error("Error creating sound for station {}", station.getId(), e);
            }
            stopAllOtherStations(station.getId());
        } catch (Exception e) {
            Dead_air.LOGGER.error("Error in AudioManager.update for station {}", station != null ? station.getId() : "null", e);
            // Don't clear currentStation on exception - overlay may still show "Now Playing"; only stop active sounds
            try {
                if (station != null) {
                    stopStation(station);
                    ACTIVE_SOUNDS.remove(station.getId());
                }
            } catch (Exception ignored) {}
        }
    }
    
    /**
     * Stop all stations except the primary one.
     * Ensures only one track plays at a time.
     */
    private static void stopAllOtherStations(ResourceLocation primaryStationId) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getSoundManager() == null) {
                return;
            }
            
            var stationsToStop = new ArrayList<ResourceLocation>();
            for (var entry : ACTIVE_SOUNDS.entrySet()) {
                // Keep the primary station, stop all others
                if (primaryStationId == null || !entry.getKey().equals(primaryStationId)) {
                    stationsToStop.add(entry.getKey());
                }
            }
            
            for (var stationId : stationsToStop) {
                RadioStation station = StationRegistry.getStation(stationId);
                if (station != null) {
                    stopStation(station);
                }
            }

            RadioStation primary = primaryStationId != null ? StationRegistry.getStation(primaryStationId) : null;
            if (primaryStationId == null) {
                InternetStreamManager.stopAll();
            } else if (primary != null && !primary.isInternetStream()) {
                InternetStreamManager.stopAll();
            }
        } catch (Exception e) {
            Dead_air.LOGGER.error("Error stopping other stations", e);
        }
    }
    
    /**
     * Stop playback for a specific station.
     */
    public static void stopStation(RadioStation station) {
        InternetStreamManager.stopIfStation(station.getId());
        RadioSoundInstance sound = ACTIVE_SOUNDS.remove(station.getId());
        if (sound != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().stop(sound);
            }
        }
    }
    
    /**
     * Stop all radio (music) playback but do not stop static.
     * Use when switching to Emergency Broadcast so static can keep playing.
     */
    public static void stopAllRadioSounds() {
        EXPLICIT_STOP_IN_PROGRESS = true;
        try {
            InternetStreamManager.stopAll();
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                var soundsToStop = new java.util.ArrayList<>(ACTIVE_SOUNDS.values());
                for (RadioSoundInstance sound : soundsToStop) {
                    if (sound != null) {
                        try {
                            mc.getSoundManager().stop(sound);
                        } catch (Exception e) {
                            // Ignore errors during shutdown
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore all errors during shutdown
        } finally {
            ACTIVE_SOUNDS.clear();
            SOUND_START_TICK.clear();
            STATIONS_SOUND_REMOVED_THIS_TICK.clear();
            PENDING_NEXT_TRACK.clear();
            RESTART_SAME_STATION.clear();
            RESUME_OFFSET_MS.clear();
            NATURAL_FINISH.clear();
            LAST_EARLY_STOP_MS.clear();
            LAST_TICK_TRACK_LOGIC_RAN.clear();
            LAST_TRACK_LOGIC_TIME_MS.clear();
            LAST_PLAY_ISSUED_MS.clear();
            FAILED_START_RETRIES.clear();
            lastBarLevel = -1;
            currentStation = null;
            currentSignalStrength = 0.0f;
            // Do NOT stop static - overlay will play it for Emergency Broadcast
            EXPLICIT_STOP_IN_PROGRESS = false;
        }
    }

    /**
     * Stop all radio playback and static.
     * Safe to call during shutdown - checks for null to prevent hangs.
     */
    public static void stopAll() {
        stopAllRadioSounds();
        StaticSoundManager.stopStatic(Minecraft.getInstance());
    }
    
    /** True when we have an active sound for this station (for overlay "Now Playing" display). */
    public static boolean hasActiveSoundForStation(ResourceLocation stationId) {
        return ACTIVE_SOUNDS.get(stationId) != null || InternetStreamManager.isPlaying(stationId);
    }
    
    /**
     * Update volume for all currently playing sounds.
     * Called when the volume slider is adjusted.
     */
    public static void updateVolumeForAllSounds() {
        InternetStreamManager.updateSignal(currentSignalStrength);
        for (RadioSoundInstance sound : ACTIVE_SOUNDS.values()) {
            if (sound != null) {
                // Force volume update by calling updateSignalStrength with current value
                sound.updateSignalStrength(sound.getSignalStrength());
            }
        }
    }
    
    /** Ticks after starting before we consider "finished". Align with MIN_MS_PLAYED_BEFORE_FINISHED (30s) so we don't advance on early engine callbacks. */
    private static final int GRACE_TICKS_AFTER_START = 20 * 30; // 30 sec
    /** Max ticks one track can be "playing" before we force-advance to next (in case engine never reports finished). ~4 minutes. */
    private static final int MAX_TRACK_TICKS = 20 * 60 * 4;
    /** Fallback: after this many ticks consider track finished so next starts (engine often doesn't report isActive=false for OGG). 90 sec. */
    private static final int FORCE_NEXT_TRACK_TICKS = 20 * 60 + 20 * 30;
    /** Custom tracks: require at least 5 seconds before considering "finished" (prevents rapid skip when sound fails to load). */
    private static final int CUSTOM_TRACK_MIN_TICKS = 20 * 5;

    /**
     * True when the sound has finished playing (so we can start the next track in the playlist).
     * We do NOT use engine.isActive() - for streaming/OGG the engine often reports false even while playing, which caused immediate skipping.
     * Only consider finished when: explicitly stopped, engine removed (mixin callback), or safety timeouts.
     */
    private static boolean isSoundFinished(Minecraft mc, RadioSoundInstance sound) {
        if (sound == null) return true;
        if (mc == null || mc.player == null || mc.getSoundManager() == null) return false;
        ResourceLocation stationId = sound.getStation().getId();
        long nowMs = System.currentTimeMillis();
        long playedMs = nowMs - LAST_PLAY_TIME_MS.getOrDefault(stationId, 0L);
        if (playedMs < MIN_MS_PLAYED_BEFORE_FINISHED) return false;
        int startTick = SOUND_START_TICK.getOrDefault(stationId, 0);
        int elapsed = mc.player.tickCount - startTick;
        if (elapsed < GRACE_TICKS_AFTER_START) return false;
        ResourceLocation currentTrack = MusicStationManager.getCurrentTrackId(stationId, mc.player);
        boolean isCustomTrack = currentTrack != null && currentTrack.getNamespace().equals(uk.co.extraspecialstudio.dead_air.Dead_air.MODID) && currentTrack.getPath().startsWith("music.custom.");
        if (isCustomTrack && elapsed < CUSTOM_TRACK_MIN_TICKS) return false;
        if (sound.isStopped()) return true;
        if (STATIONS_SOUND_REMOVED_THIS_TICK.remove(stationId)) return true;  // Engine actually removed (mixin)
        if (elapsed >= MAX_TRACK_TICKS) return true;
        if (elapsed >= FORCE_NEXT_TRACK_TICKS) return true;
        return false;
    }

    /**
     * Snapshot current playback so a dimension change / sound-engine wipe resumes the same track
     * near the same position instead of advancing or picking a random song.
     */
    public static void prepareInterruptResume() {
        RadioStation station = currentStation;
        if (station == null) return;
        ResourceLocation stationId = station.getId();
        RadioSoundInstance active = ACTIVE_SOUNDS.get(stationId);
        if (active != null) {
            long playedMs = System.currentTimeMillis() - LAST_PLAY_TIME_MS.getOrDefault(stationId, System.currentTimeMillis());
            if (playedMs >= 250L) {
                RESUME_OFFSET_MS.put(stationId, playedMs);
                RESTART_SAME_STATION.add(stationId);
                PENDING_NEXT_TRACK.remove(stationId);
                NATURAL_FINISH.remove(stationId);
                snapshotResumePoint(stationId, playedMs, active.getLocation());
            }
        }
        // Playback may already have been torn down before this ran; the per-tick snapshot still holds the position.
        armResume(stationId, "level unload");
    }

    /** Allow the recorded snapshot to be used for the next start of this station. */
    private static void armResume(ResourceLocation stationId, String reason) {
        if (stationId == null) return;
        RESUME_ARMED.add(stationId);
        if (LOG_PLAYBACK) {
            Dead_air.LOGGER.info("[radio] resume armed station={} track={} offset={}ms reason={}",
                stationId, RESUME_TRACK.get(stationId), RESUME_SNAPSHOT_MS.get(stationId), reason);
        }
    }

    /** True when the channel stopped with a meaningful amount of the track still left to play. */
    private static boolean stoppedBeforeTrackEnd(RadioSoundInstance sound, long playedMs) {
        try {
            float durationSec = uk.co.extraspecialstudio.dead_air.music.SoundDurationChecker.getDurationSeconds(sound.getLocation());
            if (durationSec <= 0f) {
                return false;
            }
            return playedMs < (long) (durationSec * 1000L) - PREMATURE_STOP_SLACK_MS;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Only streamed sounds are bound to a tracked channel, so a static fallback sound must never be
     * treated as a start that failed. An unresolved sound means the engine never got to it, which counts.
     */
    private static boolean isTrackedAsStreaming(RadioSoundInstance sound) {
        try {
            var resolved = sound.getSound();
            return resolved == null || resolved.shouldStream();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * Tear down a sound the engine never actually started and snapshot where it should have been,
     * so the next update retries the same track at the same position.
     */
    private static void retryFailedStart(RadioStation station, RadioSoundInstance sound, long intendedPlayedMs) {
        ResourceLocation stationId = station.getId();
        EXPLICIT_STOP_IN_PROGRESS = true;
        try {
            stopStation(station);
        } catch (Exception ignored) {
        } finally {
            EXPLICIT_STOP_IN_PROGRESS = false;
        }
        ACTIVE_SOUNDS.remove(stationId);
        LAST_PLAY_TIME_MS.remove(stationId);
        LAST_PLAY_ISSUED_MS.remove(stationId);
        PENDING_NEXT_TRACK.remove(stationId);
        NATURAL_FINISH.remove(stationId);
        clearResumePoint(stationId);
        if (intendedPlayedMs < 250L) {
            RESUME_OFFSET_MS.remove(stationId);
            RESTART_SAME_STATION.remove(stationId);
            return;
        }
        snapshotResumePoint(stationId, intendedPlayedMs, sound != null ? sound.getLocation() : null);
        armResume(stationId, "start never reached a channel");
    }

    /** Record track + position so playback can resume even if signal drops to 0 in between (dimension change). */
    private static void snapshotResumePoint(ResourceLocation stationId, long playedMs) {
        snapshotResumePoint(stationId, playedMs, null);
    }

    /**
     * Prefer the track taken straight off the playing sound: mid-transition the playlist lookup needs a
     * player, and losing the track id means the resume is dropped and a random song starts instead.
     */
    private static void snapshotResumePoint(ResourceLocation stationId, long playedMs, ResourceLocation knownTrackId) {
        if (stationId == null || playedMs < 250L) return;
        ResourceLocation trackId = knownTrackId;
        if (trackId == null) {
            try {
                Minecraft mc = Minecraft.getInstance();
                trackId = MusicStationManager.getCurrentTrackId(stationId, mc != null ? mc.player : null);
            } catch (Throwable ignored) {
            }
        }
        if (trackId != null) {
            RESUME_TRACK.put(stationId, trackId);
        }
        RESUME_SNAPSHOT_MS.put(stationId, playedMs);
        RESUME_SNAPSHOT_AT_MS.put(stationId, System.currentTimeMillis());
    }

    private static void clearResumePoint(ResourceLocation stationId) {
        RESUME_TRACK.remove(stationId);
        RESUME_SNAPSHOT_MS.remove(stationId);
        RESUME_SNAPSHOT_AT_MS.remove(stationId);
        RESUME_ARMED.remove(stationId);
    }

    /** Drop all resume snapshots. Call on disconnect only — dimension changes rely on them surviving. */
    public static void clearResumeSnapshots() {
        RESUME_TRACK.clear();
        RESUME_SNAPSHOT_MS.clear();
        RESUME_SNAPSHOT_AT_MS.clear();
        RESUME_ARMED.clear();
    }

    /** Consume the snapshot for this station, or null when missing, expired, or past the end of the track. */
    private static ResumePoint takeResumePoint(ResourceLocation stationId, long nowMs) {
        if (!RESUME_ARMED.remove(stationId)) {
            return null;
        }
        Long snapshotAt = RESUME_SNAPSHOT_AT_MS.get(stationId);
        Long playedMs = RESUME_SNAPSHOT_MS.get(stationId);
        ResourceLocation trackId = RESUME_TRACK.get(stationId);
        clearResumePoint(stationId);
        String rejected = null;
        if (snapshotAt == null || playedMs == null || trackId == null) {
            rejected = "no snapshot";
        } else if (nowMs - snapshotAt > RESUME_SNAPSHOT_VALID_MS) {
            rejected = "snapshot expired after " + (nowMs - snapshotAt) + "ms";
        } else {
            long durationMs = 0L;
            try {
                float durationSec = uk.co.extraspecialstudio.dead_air.music.SoundDurationChecker.getDurationSeconds(trackId);
                if (durationSec > 0f) durationMs = (long) (durationSec * 1000L);
            } catch (Throwable ignored) {
            }
            if (durationMs > 0L && playedMs >= durationMs - TRACK_END_BUFFER_MS) {
                rejected = "past end of track (" + playedMs + "/" + durationMs + "ms)";
            }
        }
        if (rejected != null) {
            if (LOG_PLAYBACK) Dead_air.LOGGER.info("[radio] resume rejected station={} reason={}", stationId, rejected);
            return null;
        }
        return new ResumePoint(trackId, playedMs);
    }

    private record ResumePoint(ResourceLocation trackId, long offsetMs) {
    }

    /**
     * Called from mixin when our radio sound is removed from the engine (track ended or we called stop).
     * Dimension / engine wipes resume the same track; natural finishes advance via {@link #onRadioStreamEnded}.
     */
    public static void onRadioSoundRemovedFromEngine(RadioSoundInstance sound) {
        if (sound == null) return;
        ResourceLocation stationId = sound.getStation().getId();
        RadioSoundInstance active = ACTIVE_SOUNDS.get(stationId);
        long playedMs = System.currentTimeMillis() - LAST_PLAY_TIME_MS.getOrDefault(stationId, 0L);
        if (active != sound) return;
        ACTIVE_SOUNDS.remove(stationId);
        if (EXPLICIT_STOP_IN_PROGRESS) {
            if (LOG_PLAYBACK) {
                Dead_air.LOGGER.info("[radio] teardown station={} played={}ms reason=explicit stop", stationId, playedMs);
            }
            return;
        }
        // Natural playlist finish is handled exclusively by onRadioStreamEnded.
        if (NATURAL_FINISH.remove(stationId)) {
            return;
        }
        // Do not auto-restart if we are no longer tuned to this station (prevents ghost overlap).
        if (currentStation == null || !currentStation.getId().equals(stationId)) {
            RESUME_OFFSET_MS.remove(stationId);
            RESTART_SAME_STATION.remove(stationId);
            PENDING_NEXT_TRACK.remove(stationId);
            return;
        }
        // Mid-play teardown (dim change, resource reload, engine stop): resume same track.
        if (playedMs >= 250L) {
            LAST_EARLY_STOP_MS.remove(stationId);
            RESUME_OFFSET_MS.putIfAbsent(stationId, playedMs);
            RESTART_SAME_STATION.add(stationId);
            PENDING_NEXT_TRACK.remove(stationId);
            snapshotResumePoint(stationId, playedMs, sound.getLocation());
            armResume(stationId, "engine teardown at " + playedMs + "ms");
            return;
        }
        LAST_EARLY_STOP_MS.put(stationId, System.currentTimeMillis());
    }

    /**
     * The sound engine stopped everything at once (dimension change, resource reload, disconnect). No per-sound
     * callback runs for that, so drop our playing state here and arm a resume from the last recorded position.
     */
    public static void onSoundEngineWiped() {
        if (ACTIVE_SOUNDS.isEmpty()) {
            return;
        }
        for (var entry : new ArrayList<>(ACTIVE_SOUNDS.entrySet())) {
            ResourceLocation stationId = entry.getKey();
            RadioSoundInstance sound = entry.getValue();
            long playedMs = System.currentTimeMillis() - LAST_PLAY_TIME_MS.getOrDefault(stationId, 0L);
            RadioChannelTracker.unregister(sound);
            ACTIVE_SOUNDS.remove(stationId);
            LAST_PLAY_TIME_MS.remove(stationId);
            LAST_PLAY_ISSUED_MS.remove(stationId);
            PENDING_NEXT_TRACK.remove(stationId);
            NATURAL_FINISH.remove(stationId);
            FAILED_START_RETRIES.remove(stationId);
            if (LOG_PLAYBACK) {
                Dead_air.LOGGER.info("[radio] sound engine wiped station={} track={} played={}ms",
                    stationId, sound.getLocation(), playedMs);
            }
            if (EXPLICIT_STOP_IN_PROGRESS || playedMs < 250L) {
                continue;
            }
            snapshotResumePoint(stationId, playedMs, sound.getLocation());
            armResume(stationId, "sound engine wiped");
        }
    }

    /**
     * Called when a radio streaming channel stops (track finished). Runs on the main thread.
     */
    public static void onRadioStreamEnded(RadioSoundInstance sound) {
        if (sound == null) {
            return;
        }
        ResourceLocation stationId = sound.getStation().getId();
        if (ACTIVE_SOUNDS.get(stationId) != sound) {
            return;
        }
        long playedMs = System.currentTimeMillis() - LAST_PLAY_TIME_MS.getOrDefault(stationId, 0L);
        // A dimension change or a starved stream also stops the channel. That is an interrupt to resume,
        // not the end of the song, so only advance when we really are at the end of the track.
        if (RESUME_OFFSET_MS.containsKey(stationId) || RESTART_SAME_STATION.contains(stationId)
            || stoppedBeforeTrackEnd(sound, playedMs)) {
            onRadioSoundRemovedFromEngine(sound);
            return;
        }
        if (LOG_PLAYBACK) {
            Dead_air.LOGGER.info("[radio] stream ended station={} track={} played={}ms -> advancing", stationId, sound.getLocation(), playedMs);
        }
        NATURAL_FINISH.add(stationId);
        ACTIVE_SOUNDS.remove(stationId);
        if (EXPLICIT_STOP_IN_PROGRESS) {
            NATURAL_FINISH.remove(stationId);
            return;
        }
        if (playedMs >= MIN_MS_PLAYED_BEFORE_FINISHED) {
            LAST_EARLY_STOP_MS.remove(stationId);
            PENDING_NEXT_TRACK.add(stationId);
        } else {
            NATURAL_FINISH.remove(stationId);
            LAST_EARLY_STOP_MS.put(stationId, System.currentTimeMillis());
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || !mc.level.isClientSide) {
            return;
        }
        RadioStation station = sound.getStation();
        if (currentStation == null || !currentStation.getId().equals(stationId)) {
            return;
        }
        float signal = uk.co.extraspecialstudio.dead_air.client.WalkieTalkieOverlay.calculateSignalStrengthForStation(mc, station);
        advanceToNextTrack(mc, station, signal, mc.player.position());
    }

    /**
     * Track advance is driven by stream-end callbacks (ChannelMixin + stop mixins). Kept as hook for SoundEngine tick.
     */
    public static void checkSoundsRemovedAfterTick(net.minecraft.client.sounds.SoundEngine engine) {
        // Intentionally no-op: isActive() is unreliable for streaming OGG while playing.
    }

    /**
     * Stop current track and start the next playlist entry immediately (timer-driven advance).
     */
    private static void advanceToNextTrack(Minecraft mc, RadioStation station, float signal, Vec3 playerPos) {
        if (station == null || mc == null || mc.getSoundManager() == null) {
            return;
        }
        EXPLICIT_STOP_IN_PROGRESS = true;
        try {
            PENDING_NEXT_TRACK.remove(station.getId());
            stopStation(station);
            ACTIVE_SOUNDS.remove(station.getId());
            LAST_PLAY_TIME_MS.put(station.getId(), 0L);
            RadioSoundInstance newSound = createSoundForStation(station, signal, false, playerPos);
            if (newSound == null) {
                PENDING_NEXT_TRACK.add(station.getId());
                return;
            }
            ACTIVE_SOUNDS.put(station.getId(), newSound);
            if (mc.player != null) {
                SOUND_START_TICK.put(station.getId(), mc.player.tickCount);
            }
            LAST_PLAY_TIME_MS.put(station.getId(), System.currentTimeMillis());
            LAST_PLAY_ISSUED_MS.put(station.getId(), System.currentTimeMillis());
            clearResumePoint(station.getId());
            if (LOG_PLAYBACK) {
                Dead_air.LOGGER.info("[radio] advance station={} track={} signal={}", station.getId(), newSound.getLocation(), signal);
            }
            mc.getSoundManager().play(newSound);
            ResourceLocation trackId = MusicStationManager.getCurrentTrackId(station.getId(), mc.player);
            uk.co.extraspecialstudio.dead_air.api.events.DeadAirEvents.post(
                new uk.co.extraspecialstudio.dead_air.api.events.types.DeadAirBroadcastStartedEvent(station.getId(), trackId, signal, playerPos)
            );
        } catch (Exception e) {
            Dead_air.LOGGER.error("Error advancing to next track for station {}", station.getId(), e);
            PENDING_NEXT_TRACK.add(station.getId());
        } finally {
            EXPLICIT_STOP_IN_PROGRESS = false;
        }
    }

    /**
     * Create a sound instance for a radio station.
     * Music plays from the player (walkie must be in hand, hotbar, or inventory).
     * @param startRandom true when tuning in (start at random track); false when cycling to next track
     */
    private static RadioSoundInstance createSoundForStation(RadioStation station, float signalStrength, boolean startRandom, Vec3 playerPos) {
        if (station == null) {
            return null;
        }
        
        try {
            return RadioSoundInstance.create(station, signalStrength, startRandom, playerPos);
        } catch (Exception e) {
            Dead_air.LOGGER.error("Failed to create sound instance for station {}", station.getId(), e);
            return null;
        }
    }
    
    /**
     * Get current station being played.
     */
    public static RadioStation getCurrentStation() {
        return currentStation;
    }
    
    /**
     * Get current signal strength.
     */
    public static float getCurrentSignalStrength() {
        return currentSignalStrength;
    }

    /**
     * Set server-provided signal for a station (called when response packet arrives).
     * @param receivedTick client tick when the response was received (for staleness check when no cache).
     */
    public static void setServerSignal(ResourceLocation stationId, float signal, int receivedTick) {
        SERVER_SIGNAL_CACHE.put(stationId, signal);
        SERVER_SIGNAL_TICK.put(stationId, receivedTick);
    }

    /** Call from overlay: when we have no tower cache, treat server signal as 0 if we haven't received a response in a while (so music stops when out of range). */
    public static boolean isServerSignalStale(ResourceLocation stationId, int currentTick) {
        Integer last = SERVER_SIGNAL_TICK.get(stationId);
        if (last == null) return true;
        return currentTick - last > SERVER_SIGNAL_STALE_TICKS;
    }

    /**
     * Get the last server-provided signal for a station (for overlay/GUI display).
     * Returns null if no response yet; otherwise the value (including 0 when out of range).
     */
    public static Float getServerSignal(ResourceLocation stationId) {
        return SERVER_SIGNAL_CACHE.get(stationId);
    }

    /**
     * Called when we receive a server signal response. Immediately tries to start playback
     * if the player is tuned to this station (signal >= 0.05 so 1-bar in range starts too).
     */
    public static void tryPlayFromServerSignal(ResourceLocation stationId, float signal) {
        if (signal < 0.05f) return;
        tryStartMusicIfTuned(stationId);
    }

    /**
     * If the player is tuned to the given station (or any station when stationId is null) with walkie on,
     * run update() so music can start. Call this when we receive tower list or signal so music starts on world load without right-click.
     */
    public static void tryStartMusicIfTuned(ResourceLocation stationId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || !mc.level.isClientSide) return;
        uk.co.extraspecialstudio.dead_air.walkie.WalkieTalkieManager.WalkieTalkieState state =
            uk.co.extraspecialstudio.dead_air.walkie.WalkieTalkieManager.getState(mc.player);
        if (!state.isOn() || state.getCurrentStation() == null) return;
        RadioStation station = state.getCurrentStation();
        if (station.getType() == RadioStation.StationType.EMERGENCY_BROADCAST) return;
        if (stationId != null && !station.getId().equals(stationId)) return;
        update(mc, station, mc.player.position());
    }
}
