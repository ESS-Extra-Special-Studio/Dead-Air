package uk.creatopia.unbound.dead_air.station;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import uk.creatopia.unbound.dead_air.radio.RadioStation;
import uk.creatopia.unbound.dead_air.radio.StationRegistry;
import uk.creatopia.unbound.dead_air.walkie.WalkieTalkieManager;

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
     */
    public static void restoreFromStorage(net.minecraft.server.level.ServerPlayer player) {
        var storage = UnlockedStationsStorage.get(player.serverLevel());
        if (storage == null) return;
        Set<ResourceLocation> stations = storage.getStations(player.getUUID());
        if (stations.isEmpty()) return;
        Set<ResourceLocation> unlocked = UNLOCKED_STATIONS.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet());
        unlocked.addAll(stations);
    }
    
    /**
     * Check if a player has unlocked a station.
     */
    public static boolean hasUnlocked(Player player, RadioStation station) {
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
     * Remove player data when they disconnect.
     */
    public static void removePlayer(Player player) {
        UNLOCKED_STATIONS.remove(player.getUUID());
    }
}
