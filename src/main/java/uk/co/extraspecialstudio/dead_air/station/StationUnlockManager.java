package uk.co.extraspecialstudio.dead_air.station;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;
import uk.co.extraspecialstudio.dead_air.walkie.WalkieTalkieManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages which stations each player has unlocked.
 * Stations unlock when players discover/interact with towers.
 */
@SuppressWarnings("null")
public class StationUnlockManager {
    private static final Map<UUID, Set<ResourceLocation>> UNLOCKED_STATIONS = new ConcurrentHashMap<>();
    
    /**
     * Unlock a station for a player. Persists to world storage so it survives restart.
     */
    public static void unlockStation(Player player, RadioStation station) {
        UUID playerId = player.getUUID();
        Set<ResourceLocation> unlocked = UNLOCKED_STATIONS.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());
        unlocked.add(station.getId());
        // Persist to world so discovered stations are remembered
        if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            var storage = UnlockedStationsStorage.get(level);
            if (storage != null) storage.addStation(playerId, station.getId());
        }
    }

    /**
     * Restore a player's unlocked stations from world storage (call on login).
     * Clears any previous in-memory list first so a new world never shows another world's stations.
     */
    public static void restoreFromStorage(net.minecraft.server.level.ServerPlayer player) {
        Set<ResourceLocation> unlocked = UNLOCKED_STATIONS.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet());
        unlocked.clear();
        var storage = UnlockedStationsStorage.get(player.serverLevel());
        if (storage == null) return;
        Set<ResourceLocation> stations = storage.getStations(player.getUUID());
        if (stations.isEmpty()) return;
        unlocked.addAll(stations);
    }
    
    /**
     * Check if a player has unlocked a station.
     * Emergency Broadcast is always considered unlocked (always on, full signal).
     */
    public static boolean hasUnlocked(Player player, RadioStation station) {
        if (station != null && station.getId().equals(StationRegistry.EMERGENCY_BROADCAST_ID)) {
            return true;
        }
        Set<ResourceLocation> unlocked = UNLOCKED_STATIONS.get(player.getUUID());
        if (unlocked == null) {
            return false;
        }
        return unlocked.contains(station.getId());
    }
    
    /**
     * Get all unlocked stations for a player.
     */
    public static List<RadioStation> getUnlockedStations(Player player) {
        Set<ResourceLocation> unlocked = UNLOCKED_STATIONS.get(player.getUUID());
        if (unlocked == null) {
            return Collections.emptyList();
        }
        
        return StationRegistry.getAllStations().stream()
            .filter(s -> unlocked.contains(s.getId()))
            .toList();
    }
    
    /**
     * Get all available stations (Emergency Broadcast always first, then unlocked, then current tuned so it's always visible).
     */
    public static List<RadioStation> getAvailableStations(Player player) {
        List<RadioStation> available = new ArrayList<>();
        var emergencyId = StationRegistry.EMERGENCY_BROADCAST_ID;
        
        // Emergency Broadcast is always available (once - never duplicated)
        RadioStation emergency = StationRegistry.getStation(emergencyId);
        if (emergency != null) {
            available.add(emergency);
        }
        
        // Add unlocked stations (excluding Emergency so we don't duplicate)
        for (RadioStation s : getUnlockedStations(player)) {
            if (s == null || s.getId().equals(emergencyId)) continue;
            available.add(s);
        }
        
        // Always include current tuned station so overlay and GUI match (e.g. if tuned from NBT but not "discovered")
        RadioStation tuned = WalkieTalkieManager.getState(player).getCurrentStation();
        if (tuned != null && !available.stream().anyMatch(s -> s.getId().equals(tuned.getId()))) {
            available.add(tuned);
        }
        
        return available;
    }
    
    /**
     * Remove a station from all players' unlocked lists and from world storage.
     * Call when the last tower broadcasting that station is removed (e.g. Radio Panel broken) so the station disappears from the GUI list.
     */
    public static void removeStationForAllPlayers(net.minecraft.server.level.ServerLevel level, ResourceLocation stationId) {
        if (level == null || stationId == null) return;
        for (Set<ResourceLocation> unlocked : UNLOCKED_STATIONS.values()) {
            unlocked.remove(stationId);
        }
        var storage = UnlockedStationsStorage.get(level);
        if (storage != null) storage.removeStationFromAllPlayers(stationId);
    }

    /**
     * Remove player data when they disconnect.
     */
    public static void removePlayer(Player player) {
        UNLOCKED_STATIONS.remove(player.getUUID());
    }

    /**
     * Clear all unlocked stations (e.g. when world unloads so a fresh world doesn't show previous world's list).
     * After this, the next player login will repopulate from that world's storage via restoreFromStorage.
     */
    public static void clearAll() {
        UNLOCKED_STATIONS.clear();
    }

    /**
     * Clear unlocked stations for one player (e.g. client disconnect so GUI doesn't show stale list).
     */
    public static void clearForPlayer(java.util.UUID playerId) {
        UNLOCKED_STATIONS.remove(playerId);
    }

    /**
     * Remove a station from one player's in-memory unlocked set (e.g. when client receives StationRemovedFromListPacket).
     */
    public static void removeStationForPlayer(UUID playerId, ResourceLocation stationId) {
        Set<ResourceLocation> unlocked = UNLOCKED_STATIONS.get(playerId);
        if (unlocked != null) unlocked.remove(stationId);
    }
}
