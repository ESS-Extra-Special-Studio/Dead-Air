package uk.co.extraspecialstudio.dead_air.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerType;
import uk.co.extraspecialstudio.dead_air.tower.RadioPanelManager;
import uk.co.extraspecialstudio.dead_air.tower.modules.TowerModuleStorage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all radio towers in the game world.
 */
@SuppressWarnings("null")
public class TowerManager {
    private static final Map<ResourceKey<Level>, Map<BlockPos, RadioTower>> TOWERS_BY_DIMENSION = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, List<RadioTower>> TOWERS_LIST = new ConcurrentHashMap<>();
    
    /**
     * Register a new Apocalypse Structures radio tower.
     * Power state is determined by tower type:
     * - STANDARD: Always broadcasting
     * - FENCED: 20% start broadcasting, 80% need activation
     * - OVERRUN: Always need activation
     */
    public static void registerTower(ServerLevel level, BlockPos pos, RadioStation station, 
                                    uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerType towerType) {
        ResourceKey<Level> dimension = level.dimension();
        
        // Check if tower is already registered - if so, update its power state based on panel activation
        if (isTowerRegistered(level, pos)) {
            RadioTower existingTower = TOWERS_BY_DIMENSION.get(dimension).get(pos);
            if (existingTower != null) {
                // Update power state based on Radio Panel activation (for world load persistence)
                // This ensures towers stay activated after world reload
                BlockPos radioPanelPos = existingTower.getRadioPanelPos();
                if (radioPanelPos != null) {
                    boolean panelActivated = uk.co.extraspecialstudio.dead_air.tower.RadioPanelManager.isPanelActivated(level, radioPanelPos);
                    
                    // Update power state based on tower type and panel activation
                    switch (towerType) {
                        case STANDARD:
                            existingTower.setPowered(true); // Always on
                            break;
                        case FENCED:
                            // Fenced: on if panel activated OR if it started powered
                            if (panelActivated) {
                                existingTower.setPowered(true);
                            } else if (existingTower.isPowered()) {
                                // If currently powered but panel not activated, it must have started powered
                                existingTower.setPowered(true); // Keep it on
                            } else {
                                existingTower.setPowered(false);
                            }
                            break;
                        case OVERRUN:
                            // Overrun: only on if panel activated
                            existingTower.setPowered(panelActivated);
                            break;
                        default:
                            // Player-built: only on if panel activated
                            existingTower.setPowered(panelActivated);
                            break;
                    }
                }
                return; // Tower already registered, just updated power state
            }
        }
        
        // Check minimum spacing
        if (!canPlaceTower(level, pos, station)) {
            Dead_air.LOGGER.warn("Cannot place tower at {} - too close to another tower of the same station type", pos);
            return;
        }
        
        // Find associated Radio Panel (pos might already be the Radio Panel position)
        BlockPos radioPanelPos = uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerDetector.findRadioPanel(level, pos);
        if (radioPanelPos == null && uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerDetector.isRadioPanel(level, pos)) {
            // pos is the Radio Panel itself
            radioPanelPos = pos;
        }
        
        RadioTower tower = new RadioTower(pos, dimension, station, towerType, radioPanelPos, true); // Official tower
        // Load module/capability state (keyed by panel pos when present, else tower pos)
        try {
            BlockPos keyPos = radioPanelPos != null ? radioPanelPos : pos;
            tower.setCapabilities(TowerModuleStorage.get(level).getCapabilities(keyPos));
        } catch (Exception ignored) {}
        
        // Set power state based on tower type
        // STANDARD towers are ALWAYS on (set in constructor, but ensure it stays true)
        if (towerType == uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerType.STANDARD) {
            tower.setPowered(true); // Standard towers always broadcast
        } else {
            // For OVERRUN and FENCED towers, check if Radio Panel is already activated (for world load persistence)
            // This ensures towers that were activated stay on after world reload
            if (radioPanelPos != null) {
                boolean panelActivated = uk.co.extraspecialstudio.dead_air.tower.RadioPanelManager.isPanelActivated(level, radioPanelPos);
                
                if (panelActivated) {
                    if (towerType == uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerType.OVERRUN) {
                        // Overrun towers: only on if panel is activated
                        tower.setPowered(true);
                    } else if (towerType == uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerType.FENCED) {
                        // Fenced towers: on if panel is activated OR if it started powered
                        // If panel is activated, definitely on
                        tower.setPowered(true);
                    }
                }
            }
        }
        
        TOWERS_BY_DIMENSION.computeIfAbsent(dimension, k -> new ConcurrentHashMap<>()).put(pos, tower);
        TOWERS_LIST.computeIfAbsent(dimension, k -> new ArrayList<>()).add(tower);
    }
    
    /**
     * Register a player-built tower (Radio Panel placed by player).
     * Player-built towers can broadcast any station the player chooses.
     * They always start off and need activation via the Radio Panel.
     */
    public static void registerPlayerTower(ServerLevel level, BlockPos panelPos, RadioStation station) {
        ResourceKey<Level> dimension = level.dimension();
        
        // Check if already registered
        if (isTowerRegistered(level, panelPos)) {
            return;
        }
        
        // Check minimum spacing
        if (!canPlaceTower(level, panelPos, station)) {
            Dead_air.LOGGER.warn("Cannot place player tower at {} - too close to another tower of the same station type", panelPos);
            return;
        }
        
        // Player-built towers use UNKNOWN type and are not official
        RadioTower tower = new RadioTower(panelPos, dimension, station, 
            uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerType.UNKNOWN, panelPos, false);
        try {
            tower.setCapabilities(TowerModuleStorage.get(level).getCapabilities(panelPos));
        } catch (Exception ignored) {}
        
        // Player-built towers always start off unless the panel is already activated.
        tower.setPowered(false);
        if (RadioPanelManager.isPanelActivated(level, panelPos)) {
            tower.setPowered(true);
        }
        
        TOWERS_BY_DIMENSION.computeIfAbsent(dimension, k -> new ConcurrentHashMap<>()).put(panelPos, tower);
        TOWERS_LIST.computeIfAbsent(dimension, k -> new ArrayList<>()).add(tower);
    }

    /**
     * Persist module/capability changes for a tower.
     */
    public static void saveCapabilities(ServerLevel level, BlockPos towerOrPanelPos, uk.co.extraspecialstudio.dead_air.tower.modules.TowerCapabilities caps) {
        if (level == null || towerOrPanelPos == null || caps == null) return;
        TowerModuleStorage.get(level).setCapabilities(towerOrPanelPos, caps);
        RadioTower tower = getTowerAt(level, towerOrPanelPos);
        if (tower != null) {
            tower.setCapabilities(caps);
        }
    }
    
    /**
     * Get the tower at the given position (Radio Panel position), or null.
     */
    public static RadioTower getTowerAt(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;
        Map<BlockPos, RadioTower> towers = TOWERS_BY_DIMENSION.get(level.dimension());
        return towers != null ? towers.get(pos) : null;
    }

    /**
     * Find the tower that uses this radio panel block: registered at {@code panelPos}, or structure tower whose
     * {@link RadioTower#getRadioPanelPos()} matches. Used when syncing known-tower storage from panel GUIs.
     */
    public static RadioTower findTowerForPanel(Level level, BlockPos panelPos) {
        if (level == null || panelPos == null) return null;
        RadioTower atKey = getTowerAt(level, panelPos);
        if (atKey != null) return atKey;
        for (RadioTower t : getAllTowers(level)) {
            if (t != null && t.getRadioPanelPos() != null && t.getRadioPanelPos().equals(panelPos)) {
                return t;
            }
        }
        Vec3 center = Vec3.atCenterOf(panelPos);
        for (RadioTower t : getTowersInRange(level, center)) {
            if (t != null && (t.getPosition().equals(panelPos)
                || (t.getRadioPanelPos() != null && t.getRadioPanelPos().equals(panelPos)))) {
                return t;
            }
        }
        return null;
    }

    /**
     * Remove a radio tower.
     */
    public static void removeTower(Level level, BlockPos pos) {
        ResourceKey<Level> dimension = level.dimension();
        
        Map<BlockPos, RadioTower> towers = TOWERS_BY_DIMENSION.get(dimension);
        if (towers != null) {
            towers.remove(pos);
            List<RadioTower> towerList = TOWERS_LIST.get(dimension);
            if (towerList != null) {
                towerList.removeIf(t -> t.getPosition().equals(pos)
                    || (t.getRadioPanelPos() != null && t.getRadioPanelPos().equals(pos)));
            }
        }
    }

    /** Remove whichever tower owns this radio panel (by panel pos or structure origin). */
    public static void removeTowerForPanel(Level level, BlockPos panelPos) {
        RadioTower tower = findTowerForPanel(level, panelPos);
        if (tower == null) {
            removeTower(level, panelPos);
            return;
        }
        removeTower(level, tower.getPosition());
        if (tower.getRadioPanelPos() != null) {
            removeTower(level, tower.getRadioPanelPos());
        }
    }

    /**
     * Whether any tower in this dimension still broadcasts the given station (used when a panel is removed to decide if we should remove the station from the GUI list).
     */
    public static boolean hasTowerBroadcasting(Level level, ResourceLocation stationId) {
        if (level == null || stationId == null) return false;
        List<RadioTower> list = TOWERS_LIST.get(level.dimension());
        if (list == null) return false;
        return list.stream().anyMatch(t -> stationId.equals(t.getStation().getId()));
    }
    
    /**
     * Check if a tower can be placed at the given position (respects minimum spacing).
     */
    public static boolean canPlaceTower(Level level, BlockPos pos, RadioStation station) {
        ResourceKey<Level> dimension = level.dimension();
        List<RadioTower> existingTowers = TOWERS_LIST.get(dimension);
        
        if (existingTowers == null || existingTowers.isEmpty()) {
            return true;
        }
        
        int minSpacing = station.getMinTowerSpacing();
        Vec3 newPos = Vec3.atCenterOf(pos);
        
        for (RadioTower tower : existingTowers) {
            if (tower.getStation().getId().equals(station.getId())) {
                double distance = tower.getDistanceTo(newPos);
                if (distance < minSpacing) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Get all towers in range of a position.
     */
    public static List<RadioTower> getTowersInRange(Level level, Vec3 pos) {
        ResourceKey<Level> dimension = level.dimension();
        List<RadioTower> towers = TOWERS_LIST.get(dimension);
        
        if (towers == null || towers.isEmpty()) {
            return Collections.emptyList();
        }
        // Copy before iterating: client and server share this map in singleplayer;
        // iterating while server may add causes CME or stale reads.
        List<RadioTower> snapshot = new ArrayList<>(towers);
        List<RadioTower> inRange = new ArrayList<>();
        for (RadioTower tower : snapshot) {
            if (tower != null && tower.isInRange(pos)) {
                inRange.add(tower);
            }
        }
        return inRange;
    }
    
    /**
     * Get the best signal tower for a given position and station.
     */
    public static RadioTower getBestTower(Level level, Vec3 pos, RadioStation station) {
        if (level == null || pos == null || station == null) {
            return null;
        }
        
        List<RadioTower> towers = getTowersInRange(level, pos);
        if (towers == null || towers.isEmpty()) {
            return null;
        }
        
        RadioTower bestTower = null;
        float bestSignal = 0.0f;
        
        for (RadioTower tower : towers) {
            if (tower != null && tower.getStation() != null && 
                tower.getStation().getId().equals(station.getId()) && tower.isPowered()) {
                try {
                    float signal = SignalStrength.getFinalSignalStrength(level, tower, pos);
                    if (signal > bestSignal) {
                        bestSignal = signal;
                        bestTower = tower;
                    }
                } catch (Exception e) {
                    Dead_air.LOGGER.warn("Error calculating signal strength for tower at {}", tower.getPosition(), e);
                }
            }
        }
        
        return bestTower;
    }
    
    /**
     * Update tower power states (called periodically).
     * Power is determined by tower type and Radio Panel activation:
     * - STANDARD: Always on
     * - FENCED: On if Radio Panel is activated (or started powered)
     * - OVERRUN: On only if Radio Panel is activated
     */
    public static void updateTowerPower(ServerLevel level) {
        ResourceKey<Level> dimension = level.dimension();
        List<RadioTower> towers = TOWERS_LIST.get(dimension);
        
        if (towers == null) {
            return;
        }
        
        long currentTick = level.getGameTime();
        
        for (RadioTower tower : towers) {
            if (tower.shouldCheckPower(currentTick)) {
                boolean powered = false;
                
                switch (tower.getTowerType()) {
                    case STANDARD:
                        // Standard towers always broadcast
                        powered = true;
                        break;
                        
                    case FENCED:
                        // Fenced towers: check if started powered OR Radio Panel is activated
                        // First check if it started powered (stored in initial state)
                        // We need to track if it started powered - for now, check if panel is activated
                        // OR if the tower was initially powered (we'll check the initial state)
                        if (tower.getRadioPanelPos() != null) {
                            // Check Radio Panel activation
                            powered = uk.co.extraspecialstudio.dead_air.tower.RadioPanelManager.isPanelActivated(
                                level, tower.getRadioPanelPos());
                            // Also check if tower started powered (20% chance)
                            // If tower is currently powered and panel is not activated, it must have started powered
                            if (!powered && tower.isPowered()) {
                                // Tower started powered, keep it on
                                powered = true;
                            }
                        } else {
                            // No panel found, but tower exists - assume it started powered if currently powered
                            powered = tower.isPowered();
                        }
                        break;
                        
                    case OVERRUN:
                        // Overrun towers: only on if Radio Panel is activated
                        if (tower.getRadioPanelPos() != null) {
                            powered = uk.co.extraspecialstudio.dead_air.tower.RadioPanelManager.isPanelActivated(
                                level, tower.getRadioPanelPos());
                        }
                        break;
                        
                    default:
                        // Player-built (UNKNOWN): only on if Radio Panel is activated (persists from backup)
                        if (tower.getRadioPanelPos() != null) {
                            powered = uk.co.extraspecialstudio.dead_air.tower.RadioPanelManager.isPanelActivated(
                                level, tower.getRadioPanelPos());
                        }
                        break;
                }
                
                tower.setPowered(powered);
                tower.updatePowerCheck(currentTick);
            }
        }
    }
    
    /**
     * Get all towers for a specific station.
     */
    public static List<RadioTower> getTowersForStation(Level level, RadioStation station) {
        ResourceKey<Level> dimension = level.dimension();
        List<RadioTower> towers = TOWERS_LIST.get(dimension);
        
        if (towers == null || towers.isEmpty()) {
            return Collections.emptyList();
        }
        List<RadioTower> snapshot = new ArrayList<>(towers);
        return snapshot.stream()
            .filter(t -> t != null && t.getStation() != null && t.getStation().getId().equals(station.getId()))
            .toList();
    }
    
    /**
     * Check if a tower is already registered at the given position.
     * Used to prevent duplicate registrations when scanning existing worlds.
     */
    public static boolean isTowerRegistered(Level level, BlockPos pos) {
        ResourceKey<Level> dimension = level.dimension();
        Map<BlockPos, RadioTower> towers = TOWERS_BY_DIMENSION.get(dimension);
        
        if (towers == null) {
            return false;
        }
        
        return towers.containsKey(pos);
    }
    
    /**
     * Get all towers in a dimension.
     */
    public static List<RadioTower> getAllTowers(Level level) {
        ResourceKey<Level> dimension = level.dimension();
        List<RadioTower> towers = TOWERS_LIST.get(dimension);
        
        if (towers == null || towers.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(towers);
    }
    
    /**
     * Stations that already have a tower within minTowerSpacing of pos (in-memory or from known-towers file).
     * Used so we don't assign the same station to two towers in overlapping range.
     */
    public static java.util.Set<ResourceLocation> getStationsAlreadyInRangeOf(ServerLevel level, BlockPos pos) {
        java.util.Set<ResourceLocation> out = new java.util.HashSet<>();
        if (level == null || pos == null) return out;
        Vec3 at = Vec3.atCenterOf(pos);

        // In-memory towers
        List<RadioTower> list = TOWERS_LIST.get(level.dimension());
        if (list != null) {
            for (RadioTower t : list) {
                if (t == null || t.getStation() == null) continue;
                if (t.getPosition().equals(pos)) continue; // skip self
                double dist = t.getDistanceTo(at);
                if (dist < t.getStation().getMinTowerSpacing()) {
                    out.add(t.getStation().getId());
                }
            }
        }

        // Known towers (persisted) so after restart we still avoid same-station overlap
        if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            uk.co.extraspecialstudio.dead_air.tower.KnownTowerStorage.loadBackupFileDirect(level);
            for (uk.co.extraspecialstudio.dead_air.tower.KnownTowerStorage.KnownTowerEntry e : uk.co.extraspecialstudio.dead_air.tower.KnownTowerStorage.getKnownTowers(level.dimension())) {
                if (e.towerPos.equals(pos)) continue;
                RadioStation st = StationRegistry.getStation(e.stationId);
                if (st == null) continue;
                double dist = Vec3.atCenterOf(e.towerPos).distanceTo(at);
                if (dist < st.getMinTowerSpacing()) {
                    out.add(e.stationId);
                }
            }
        }
        return out;
    }

    /**
     * Determine which station a tower should broadcast. Avoids stations that already have a tower
     * within minTowerSpacing (so two towers in signal range never share a station — each gets its own discovery).
     */
    public static RadioStation determineStationForTower(ServerLevel level, BlockPos pos, ApocalypseTowerType towerType) {
        java.util.Set<ResourceLocation> excluded = getStationsAlreadyInRangeOf(level, pos);
        return determineStationForTower(level, pos, towerType, excluded);
    }

    /**
     * Determine which station a tower should broadcast based on tower type.
     * Excludes any station in excludedStationIds (already in use by a tower within range).
     */
    public static RadioStation determineStationForTower(ServerLevel level, BlockPos pos, ApocalypseTowerType towerType,
                                                       java.util.Set<ResourceLocation> excludedStationIds) {
        Random random = new Random(pos.asLong()); // Use pos hash for deterministic randomness

        RadioStation emergencyStation = StationRegistry.getStation(StationRegistry.EMERGENCY_BROADCAST_ID);
        if (emergencyStation != null && random.nextDouble() < 0.1 && !excludedStationIds.contains(emergencyStation.getId())) {
            return emergencyStation;
        }

        List<RadioStation> musicAll = StationRegistry.getStationsByType(RadioStation.StationType.MUSIC);
        List<RadioStation> musicStations = musicAll.stream()
            .filter(s -> s != null && !StationRegistry.JUKEBOX_FM_ID.equals(s.getId()))
            .toList();
        if (musicStations.isEmpty()) {
            musicStations = musicAll;
        }
        if (musicStations.isEmpty()) {
            return emergencyStation != null ? emergencyStation : null;
        }

        // Prefer type-specific station if not excluded
        if (towerType == ApocalypseTowerType.STANDARD) {
            RadioStation bedrock = StationRegistry.getStation(StationRegistry.BEDROCK_RADIO_ID);
            if (bedrock != null && !excludedStationIds.contains(bedrock.getId()) && random.nextDouble() < 0.6) {
                return bedrock;
            }
        } else if (towerType == ApocalypseTowerType.OVERRUN) {
            RadioStation zombie = StationRegistry.getStation(StationRegistry.ZOMBIECRAFT_RADIO_ID);
            if (zombie != null && !excludedStationIds.contains(zombie.getId()) && random.nextDouble() < 0.6) {
                return zombie;
            }
        }

        // Pick from music stations that are NOT already in use in range (so new tower gets its own station)
        List<RadioStation> available = musicStations.stream()
            .filter(s -> s != null && !excludedStationIds.contains(s.getId()))
            .toList();
        if (available.isEmpty()) {
            available = musicStations.stream()
                .filter(s -> s != null && !StationRegistry.JUKEBOX_FM_ID.equals(s.getId()))
                .toList();
        }
        if (available.isEmpty()) {
            available = musicStations; // fallback: all taken, pick any
        }
        return available.get(random.nextInt(available.size()));
    }
}
