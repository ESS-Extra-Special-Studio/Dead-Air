package uk.creatopia.unbound.dead_air.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import uk.creatopia.unbound.dead_air.Dead_air;
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
    private static final Map<ResourceLocation, RadioSoundInstance> ACTIVE_SOUNDS = new HashMap<>();
    private static RadioStation currentStation = null;
    private static float currentSignalStrength = 0.0f;
    /** Server-provided signal when client has no tower data (e.g. dedicated server or stale state). */
    private static final Map<ResourceLocation, Float> SERVER_SIGNAL_CACHE = new HashMap<>();
    
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
            RadioTower primaryTower = TowerManager.getBestTower(mc.level, playerPos, station);
            float signal;

            if (primaryTower != null && primaryTower.isPowered()) {
                signal = SignalStrength.getFinalSignalStrength(mc.level, primaryTower, playerPos);
                if (signal < 0.1f) {
                    if (currentStation != null && currentStation.getId().equals(station.getId())) {
                        Dead_air.LOGGER.info("Radio: signal too weak for {} ({}), need >= 0.1", station.getId(), signal);
                    }
                    stopStation(station);
                    ACTIVE_SOUNDS.remove(station.getId());
                    currentStation = null;
                    currentSignalStrength = 0.0f;
                    stopAllOtherStations(null);
                    return;
                }
            } else {
                // No local tower (client may have no tower data) - use server-provided signal if present
                Float serverSignal = SERVER_SIGNAL_CACHE.get(station.getId());
                if (serverSignal != null && serverSignal >= 0.1f) {
                    signal = serverSignal;
                    Dead_air.LOGGER.info("Radio: using server signal {} for {} (playing)", signal, station.getId());
                } else {
                    if (primaryTower == null) {
                        Dead_air.LOGGER.info("Radio: no tower in range for station {} (requesting from server if needed)", station.getId());
                    } else {
                        Dead_air.LOGGER.info("Radio: tower at {} for station {} not powered", primaryTower.getPosition(), station.getId());
                    }
                    stopStation(station);
                    ACTIVE_SOUNDS.remove(station.getId());
                    currentStation = null;
                    currentSignalStrength = 0.0f;
                    stopAllOtherStations(null);
                    return;
                }
            }

            // Update primary station - only when in tower range with valid signal (local or server)
            currentStation = station;
            currentSignalStrength = signal;
            
            RadioSoundInstance sound = ACTIVE_SOUNDS.get(station.getId());
            // If switching stations or sound doesn't exist, create a new one (ensures different track)
            if (sound == null || isSwitchingStations) {
                try {
                    // Stop and remove old sound if it exists
                    if (sound != null) {
                        stopStation(station);
                        ACTIVE_SOUNDS.remove(station.getId());
                    }
                    
                    // Create new sound - this will pick a track for this station
                    sound = createSoundForStation(station, signal);
                    if (sound != null && mc.getSoundManager() != null) {
                        ACTIVE_SOUNDS.put(station.getId(), sound);
                        mc.getSoundManager().play(sound);
                        Dead_air.LOGGER.info("Started playing sound for station {} with signal strength {} (switching: {})", 
                            station.getId(), signal, isSwitchingStations);
                    } else if (sound == null) {
                        Dead_air.LOGGER.warn("Could not create sound for station {}", station.getId());
                    }
                } catch (Exception e) {
                    Dead_air.LOGGER.error("Error creating sound for station {}", station.getId(), e);
                }
            } else {
                try {
                    // If sound is stopped, restart it immediately (for track cycling)
                    if (sound.isStopped()) {
                        Dead_air.LOGGER.debug("Sound stopped for station {}, restarting with new track", station.getId());
                        ACTIVE_SOUNDS.remove(station.getId());
                        sound = createSoundForStation(station, signal);
                        if (sound != null && mc.getSoundManager() != null) {
                            ACTIVE_SOUNDS.put(station.getId(), sound);
                            mc.getSoundManager().play(sound);
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
     * Used when client has no tower data so music can still play.
     */
    public static void setServerSignal(ResourceLocation stationId, float signal) {
        if (signal >= 0.1f) {
            SERVER_SIGNAL_CACHE.put(stationId, signal);
        } else {
            SERVER_SIGNAL_CACHE.remove(stationId);
        }
    }
}
