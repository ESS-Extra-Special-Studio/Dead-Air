package uk.creatopia.unbound.dead_air.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import uk.creatopia.unbound.dead_air.Config;
import uk.creatopia.unbound.dead_air.Dead_air;
import uk.creatopia.unbound.dead_air.music.MusicStationManager;
import uk.creatopia.unbound.dead_air.radio.RadioStation;
import uk.creatopia.unbound.dead_air.radio.RadioTower;
import uk.creatopia.unbound.dead_air.radio.SignalStrength;
import uk.creatopia.unbound.dead_air.radio.StationRegistry;
import uk.creatopia.unbound.dead_air.radio.TowerManager;

import java.util.*;

/**
 * Client-side audio manager for radio station playback.
 *
 * Design rules:
 * - Music plays only when the player is tuned to a station (walkie on, station selected).
 * - Music plays only when in range of a powered tower broadcasting that station with signal >= 0.1.
 * - Out of tower range or tower unpowered: no music. No fallback playback.
 * - Only one station plays at a time (the tuned station). Sound is non-positional (as if from the walkie).
 * - Each station uses pre-made playlists (see MusicStationManager); custom tracks are added later.
 */
@SuppressWarnings("null") // mc.level is checked for null before use
public class AudioManager {
    private static final Map<ResourceLocation, RadioSoundInstance> ACTIVE_SOUNDS = new java.util.concurrent.ConcurrentHashMap<>();
    private static RadioStation currentStation = null;
    private static float currentSignalStrength = 0.0f;
    /** Server-provided signal when client has no tower data. Thread-safe: updated from network thread. */
    private static final Map<ResourceLocation, Float> SERVER_SIGNAL_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    /** Throttle: log why music isn't playing at most every 5 seconds. */
    private static long lastNoPlayLogTime = 0;
    private static final long NO_PLAY_LOG_INTERVAL_MS = 5000;
    /** Log once when we get signal from cache (helps verify cache is used for music). */
    private static boolean loggedCacheSignal = false;
    
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
            Dead_air.LOGGER.warn("AudioManager.update called on server side - this should not happen!");
            return;
        }
        
        // Emergency Broadcast stations do not play music
        if (station.getType() == RadioStation.StationType.EMERGENCY_BROADCAST) {
            stopAll();
            return;
        }
        
        // If switching stations, stop the old one immediately and force new sound creation
        boolean isSwitchingStations = currentStation != null && !currentStation.getId().equals(station.getId());
        if (isSwitchingStations) {
            stopStation(currentStation);
            // Remove from map to force new sound creation with different track
            ACTIVE_SOUNDS.remove(currentStation.getId());
            currentStation = null;
        }
        
        try {
            // Local tower first (matches backup tag when music worked; singleplayer uses shared tower list)
            RadioTower primaryTower = TowerManager.getBestTower(mc.level, playerPos, station);
            float signal;
            if (primaryTower != null && primaryTower.isPowered()) {
                signal = SignalStrength.getFinalSignalStrength(mc.level, primaryTower, playerPos);
                if (signal < 0.1f) {
                    logWhyNotPlaying(station, true, true, signal, null, "local signal < 0.1");
                    stopStation(station);
                    ACTIVE_SOUNDS.remove(station.getId());
                    currentStation = null;
                    currentSignalStrength = 0.0f;
                    stopAllOtherStations(null);
                    return;
                }
            } else {
                Float serverSignal = SERVER_SIGNAL_CACHE.get(station.getId());
                if (serverSignal != null && serverSignal >= 0.1f) {
                    signal = serverSignal;
                } else if (uk.creatopia.unbound.dead_air.tower.KnownTowersClientCache.hasCachedTowers()) {
                    float cacheSignal = uk.creatopia.unbound.dead_air.tower.KnownTowersClientCache.getSignalFromCache(
                        station.getId(), playerPos, mc.level);
                    if (cacheSignal >= 0.1f) {
                        signal = cacheSignal;
                        if (!loggedCacheSignal) {
                            loggedCacheSignal = true;
                            Dead_air.LOGGER.info("[Dead Air MUSIC] Using KnownTowersClientCache signal={} for station {}", String.format("%.2f", signal), station.getId());
                        }
                    } else {
                        float localSig = (primaryTower != null && primaryTower.isPowered())
                            ? SignalStrength.getFinalSignalStrength(mc.level, primaryTower, playerPos) : 0f;
                        logWhyNotPlaying(station, primaryTower != null, primaryTower != null && primaryTower.isPowered(), localSig, serverSignal, "no usable signal");
                        stopStation(station);
                        ACTIVE_SOUNDS.remove(station.getId());
                        currentStation = null;
                        currentSignalStrength = 0.0f;
                        stopAllOtherStations(null);
                        return;
                    }
                } else {
                    float localSig = (primaryTower != null && primaryTower.isPowered())
                        ? SignalStrength.getFinalSignalStrength(mc.level, primaryTower, playerPos) : 0f;
                    logWhyNotPlaying(station, primaryTower != null, primaryTower != null && primaryTower.isPowered(), localSig, serverSignal, "no usable signal (need tower data or server signal)");
                    stopStation(station);
                    ACTIVE_SOUNDS.remove(station.getId());
                    currentStation = null;
                    currentSignalStrength = 0.0f;
                    stopAllOtherStations(null);
                    return;
                }
            }

            currentStation = station;
            currentSignalStrength = signal;
            
            RadioSoundInstance sound = ACTIVE_SOUNDS.get(station.getId());
            boolean needNewSound = sound == null || isSwitchingStations || (sound.isStopped() && signal >= 0.1f);
            if (needNewSound) {
                try {
                    int trackCount = Config.simplifiedMusicDebug ? 1 : MusicStationManager.getTracksForStation(station.getId()).size();
                    Dead_air.LOGGER.info("[Dead Air MUSIC] Attempting to start: station={} tracks={} signal={} simplified={}", station.getId(), trackCount, String.format("%.2f", signal), Config.simplifiedMusicDebug);
                    if (sound != null) {
                        stopStation(station);
                        ACTIVE_SOUNDS.remove(station.getId());
                    }
                    sound = createSoundForStation(station, signal);
                    if (sound != null && mc.getSoundManager() != null) {
                        ACTIVE_SOUNDS.put(station.getId(), sound);
                        mc.getSoundManager().play(sound);
                        ResourceLocation trackId = Config.simplifiedMusicDebug ? ResourceLocation.withDefaultNamespace("music_disc.13") : MusicStationManager.getCurrentTrackId(station.getId());
                        String trackName = trackId != null ? MusicStationManager.formatTrackDisplayName(trackId) : "?";
                        Dead_air.LOGGER.info("[Dead Air MUSIC] Started playing: station={} track={} signal={}", station.getId(), trackId, String.format("%.2f", signal));
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(Component.literal("§6[Radio] §rNow Playing: §e" + station.getName() + " §7- §f" + trackName));
                        }
                    } else if (sound == null) {
                        Dead_air.LOGGER.warn("[Dead Air MUSIC] Could not create sound for station {} (tracks: {})", station.getId(), trackCount);
                    } else {
                        Dead_air.LOGGER.warn("[Dead Air MUSIC] SoundManager null, cannot play station {}", station.getId());
                    }
                } catch (Exception e) {
                    Dead_air.LOGGER.error("Error creating sound for station {}", station.getId(), e);
                }
            } else {
                try {
                    // If sound is stopped, restart it immediately (for track cycling)
                    if (sound.isStopped()) {
                        ACTIVE_SOUNDS.remove(station.getId());
                        sound = createSoundForStation(station, signal);
                        if (sound != null && mc.getSoundManager() != null) {
                            ACTIVE_SOUNDS.put(station.getId(), sound);
                            mc.getSoundManager().play(sound);
                            ResourceLocation trackId = Config.simplifiedMusicDebug ? ResourceLocation.withDefaultNamespace("music_disc.13") : MusicStationManager.getCurrentTrackId(station.getId());
                            String trackName = trackId != null ? MusicStationManager.formatTrackDisplayName(trackId) : "?";
                            Dead_air.LOGGER.info("[Dead Air MUSIC] Restarted playback: station={} track={} signal={}", station.getId(), trackId, String.format("%.2f", signal));
                            if (mc.player != null) {
                                mc.player.sendSystemMessage(Component.literal("§6[Radio] §rNow Playing: §e" + station.getName() + " §7- §f" + trackName));
                            }
                        }
                    } else {
                        // Sound is still playing - just update signal strength
                        // This allows mid-song tuning (don't restart if already playing)
                        sound.updateSignalStrength(signal);
                    }
                } catch (Exception e) {
                    Dead_air.LOGGER.error("Error updating signal strength for station {}", station.getId(), e);
                }
            }
            
            // Audio blending disabled - only play the tuned station
            // Stop any other stations that might be playing
            stopAllOtherStations(station.getId());
        } catch (Exception e) {
            Dead_air.LOGGER.error("Error in AudioManager.update for station {}", station != null ? station.getId() : "null", e);
            // Don't crash - just stop audio
            stopAll();
        }
    }
    
    /**
     * Log why music is not playing (throttled to every 5s) to help debug "music not playing" issues.
     */
    private static void logWhyNotPlaying(RadioStation station, boolean hasLocalTower, boolean towerPowered,
                                        float localSignal, Float serverSignal, String reason) {
        long now = System.currentTimeMillis();
        if (now - lastNoPlayLogTime < NO_PLAY_LOG_INTERVAL_MS) return;
        lastNoPlayLogTime = now;
        String serverStr = serverSignal != null ? String.format("%.2f", serverSignal) : "none";
        Dead_air.LOGGER.info("[Dead Air MUSIC] Not playing: station={} | localTower={} powered={} localSignal={} serverSignal={} | reason: {}",
            station.getId(), hasLocalTower, towerPowered, String.format("%.2f", localSignal), serverStr, reason);
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
        } catch (Exception e) {
            Dead_air.LOGGER.error("Error stopping other stations", e);
        }
    }
    
    /**
     * Stop playback for a specific station.
     */
    public static void stopStation(RadioStation station) {
        RadioSoundInstance sound = ACTIVE_SOUNDS.remove(station.getId());
        if (sound != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().stop(sound);
            }
        }
    }
    
    /**
     * Stop all radio playback.
     * Safe to call during shutdown - checks for null to prevent hangs.
     */
    public static void stopAll() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                // Create a copy of the sounds list to avoid ConcurrentModificationException
                var soundsToStop = new java.util.ArrayList<>(ACTIVE_SOUNDS.values());
                for (RadioSoundInstance sound : soundsToStop) {
                    if (sound != null) {
                        try {
                            mc.getSoundManager().stop(sound);
                        } catch (Exception e) {
                            // Ignore errors during shutdown
                            Dead_air.LOGGER.debug("Error stopping sound during shutdown (non-critical): {}", e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore all errors during shutdown - this prevents hangs
            Dead_air.LOGGER.debug("Error in AudioManager.stopAll during shutdown (non-critical): {}", e.getMessage());
        } finally {
            ACTIVE_SOUNDS.clear();
            currentStation = null;
            currentSignalStrength = 0.0f;
            loggedCacheSignal = false;
        }
    }
    
    /**
     * Update volume for all currently playing sounds.
     * Called when the volume slider is adjusted.
     */
    public static void updateVolumeForAllSounds() {
        for (RadioSoundInstance sound : ACTIVE_SOUNDS.values()) {
            if (sound != null) {
                // Force volume update by calling updateSignalStrength with current value
                sound.updateSignalStrength(sound.getSignalStrength());
            }
        }
    }
    
    /**
     * Create a sound instance for a radio station.
     */
    private static RadioSoundInstance createSoundForStation(RadioStation station, float signalStrength) {
        if (station == null) {
            return null;
        }
        
        try {
            // Create sound instance - RadioSoundInstance will handle getting a valid sound event
            return new RadioSoundInstance(station, signalStrength);
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
     * Used for both overlay display and music playback (single source of truth).
     */
    public static void setServerSignal(ResourceLocation stationId, float signal) {
        if (signal >= 0.1f) {
            SERVER_SIGNAL_CACHE.put(stationId, signal);
        } else {
            SERVER_SIGNAL_CACHE.remove(stationId);
        }
    }

    /**
     * Get the last server-provided signal for a station (for overlay/GUI display).
     * Returns null if no value or signal was below threshold.
     */
    public static Float getServerSignal(ResourceLocation stationId) {
        return SERVER_SIGNAL_CACHE.get(stationId);
    }

    /**
     * Called when we receive a server signal response. Immediately tries to start playback
     * if the player is tuned to this station, so we don't rely on the next tick seeing the cache.
     */
    public static void tryPlayFromServerSignal(ResourceLocation stationId, float signal) {
        if (signal < 0.1f) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || !mc.level.isClientSide) return;
        uk.creatopia.unbound.dead_air.walkie.WalkieTalkieManager.WalkieTalkieState state =
            uk.creatopia.unbound.dead_air.walkie.WalkieTalkieManager.getState(mc.player);
        if (!state.isOn() || state.getCurrentStation() == null) return;
        if (!state.getCurrentStation().getId().equals(stationId)) return;
        if (state.getCurrentStation().getType() == RadioStation.StationType.EMERGENCY_BROADCAST) return;
        RadioStation station = StationRegistry.getStation(stationId);
        if (station == null) return;
        update(mc, station, mc.player.position());
    }
}
