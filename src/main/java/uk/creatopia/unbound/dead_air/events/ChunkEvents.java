package uk.creatopia.unbound.dead_air.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import uk.creatopia.unbound.dead_air.Dead_air;
import uk.creatopia.unbound.dead_air.tower.ApocalypseTowerDetector;
import uk.creatopia.unbound.dead_air.tower.ApocalypseTowerType;
import uk.creatopia.unbound.dead_air.radio.RadioStation;
import uk.creatopia.unbound.dead_air.radio.StationRegistry;
import uk.creatopia.unbound.dead_air.radio.TowerManager;
import net.minecraft.core.BlockPos;

/**
 * Event handlers for chunk loading to scan for existing towers.
 * We need to scan chunks because Forge does not fire a "structure placed" event when
 * worldgen or structure blocks place structures—we only get chunk load. When we ourselves
 * place a tower (e.g. /dead_air spawntower), we use origin+offset instead (see SpawnTowerCommand).
 */
@Mod.EventBusSubscriber(modid = Dead_air.MODID)
@SuppressWarnings("null")
public class ChunkEvents {
    // Track when worlds finished loading to avoid scanning during world load
    // Maps dimension to true when world is ready (set by ModEvents after delay)
    private static final java.util.concurrent.ConcurrentHashMap<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, Boolean> READY_WORLDS = new java.util.concurrent.ConcurrentHashMap<>();
    
    /**
     * Mark a world as ready for chunk scanning (called by ModEvents after delay).
     * Loads panel activation storage from disk first so towers see correct activation state, then scans chunks.
     */
    public static void markWorldReady(ServerLevel level) {
        if (level == null || Dead_air.isShutdownRequested()) return;
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension = level.dimension();
        READY_WORLDS.put(dimension, true);
        // Eager-load panel activations from disk so isPanelActivated() sees saved state when we register towers
        uk.creatopia.unbound.dead_air.tower.PanelActivationStorage.get(level);
        scanLoadedChunks(level);
    }
    
    /** Clear world ready state when dimension unloads. */
    public static void clearWorldReady(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        READY_WORLDS.remove(dimension);
    }
    
    /**
     * Scan chunks around spawn for radio towers.
     * Called when world becomes ready to catch chunks that loaded during the initial delay.
     */
    private static void scanLoadedChunks(ServerLevel level) {
        try {
            var spawnPos = level.getSharedSpawnPos();
            int spawnChunkX = spawnPos.getX() >> 4;
            int spawnChunkZ = spawnPos.getZ() >> 4;
            int radius = 12; // 12 chunks (192 blocks) each direction from spawn
            int scanned = 0;
            
            for (int cx = spawnChunkX - radius; cx <= spawnChunkX + radius; cx++) {
                for (int cz = spawnChunkZ - radius; cz <= spawnChunkZ + radius; cz++) {
                    if (level.hasChunk(cx, cz)) {
                        var chunk = level.getChunk(cx, cz);
                        if (chunk instanceof LevelChunk) {
                            scanChunk(level, (LevelChunk) chunk);
                            scanned++;
                        }
                    }
                }
            }
            Dead_air.LOGGER.info("Scanned {} chunks around spawn for radio towers in {}", scanned, level.dimension().location());
        } catch (Exception e) {
            Dead_air.LOGGER.error("Error scanning chunks for towers", e);
        }
    }
    
    private static void scanChunk(ServerLevel level, LevelChunk chunk) {
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        java.util.Set<BlockPos> processedPanels = new java.util.HashSet<>();
        
        for (int x = chunkX * 16; x < (chunkX + 1) * 16; x += 4) {
            for (int z = chunkZ * 16; z < (chunkZ + 1) * 16; z += 4) {
                int minY = Math.max(level.getMinBuildHeight(), level.getSeaLevel() - 20);
                int maxY = Math.min(level.getMaxBuildHeight(), level.getSeaLevel() + 100);
                for (int y = minY; y < maxY; y += 4) {
                    pos.set(x, y, z);
                    if (ApocalypseTowerDetector.isRadioPanel(level, pos)) {
                        BlockPos panelPos = pos.immutable();
                        if (processedPanels.contains(panelPos)) continue;
                        boolean alreadyRegistered = false;
                        for (int checkX = -5; checkX <= 5; checkX += 2) {
                            for (int checkZ = -5; checkZ <= 5; checkZ += 2) {
                                if (TowerManager.isTowerRegistered(level, panelPos.offset(checkX, 0, checkZ))) {
                                    alreadyRegistered = true;
                                    break;
                                }
                            }
                            if (alreadyRegistered) break;
                        }
                        if (!alreadyRegistered) {
                            ApocalypseTowerType towerType = ApocalypseTowerDetector.getTowerType(level, panelPos);
                            RadioStation station = determineStationForTower(level, panelPos);
                            if (station != null) {
                                TowerManager.registerTower(level, panelPos, station, towerType);
                                processedPanels.add(panelPos);
                            }
                        }
                    }
                }
            }
        }
    }
    
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (Dead_air.isShutdownRequested()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        
        // Don't scan chunks during world load - wait until world is ready
        if (!READY_WORLDS.containsKey(level.dimension())) {
            return;
        }
        
        if (event.getChunk() instanceof LevelChunk chunk) {
            try {
                scanChunk(level, chunk);
            } catch (Exception e) {
                Dead_air.LOGGER.error("Error scanning chunk for radio towers at ({}, {})", 
                    chunk.getPos().x, chunk.getPos().z, e);
            }
        }
    }
    
    /**
     * Determine which station a tower should broadcast.
     * Takes tower type into account for preferred stations.
     */
    private static RadioStation determineStationForTower(ServerLevel level, BlockPos pos) {
        // Get tower type to determine preferred station
        ApocalypseTowerType towerType = ApocalypseTowerDetector.getTowerType(level, pos);
        
        // All towers can broadcast Emergency Broadcast (no restriction)
        // But we'll assign music stations more often for variety
        
        java.util.Random random = new java.util.Random(pos.asLong());
        
        // Determine station based on tower type
        if (towerType == ApocalypseTowerType.STANDARD) {
            // Standard towers: Prefer Bedrock Radio (vanilla music)
            if (random.nextDouble() < 0.6) { // 60% chance for Bedrock Radio
                RadioStation bedrockRadio = StationRegistry.getStation(StationRegistry.BEDROCK_RADIO_ID);
                if (bedrockRadio != null) {
                    return bedrockRadio;
                }
            }
        } else if (towerType == ApocalypseTowerType.OVERRUN) {
            // Overrun towers: Prefer Zombiecraft Radio
            if (random.nextDouble() < 0.6) { // 60% chance for Zombiecraft Radio
                RadioStation zombiecraftRadio = StationRegistry.getStation(StationRegistry.ZOMBIECRAFT_RADIO_ID);
                if (zombiecraftRadio != null) {
                    return zombiecraftRadio;
                }
            }
        }
        
        // Otherwise, randomly assign from all music stations
        var musicStations = StationRegistry.getStationsByType(RadioStation.StationType.MUSIC);
        if (!musicStations.isEmpty()) {
            return musicStations.get(random.nextInt(musicStations.size()));
        }
        
        // Fallback to Emergency Broadcast
        return StationRegistry.getStation(StationRegistry.EMERGENCY_BROADCAST_ID);
    }
}
