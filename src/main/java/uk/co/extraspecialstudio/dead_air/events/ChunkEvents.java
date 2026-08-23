package uk.co.extraspecialstudio.dead_air.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerDetector;
import uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerType;
import uk.co.extraspecialstudio.dead_air.tower.KnownTowerStorage;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.TowerManager;
import net.minecraft.core.BlockPos;

/**
 * Event handlers for chunk loading to scan for existing towers.
 * We need to scan chunks because Forge does not fire a "structure placed" event when
 * worldgen or structure blocks place structures—we only get chunk load. When we ourselves
 * place a tower (e.g. /dead_air spawntower), we use origin+offset instead (see SpawnTowerCommand).
 */
@EventBusSubscriber(modid = Dead_air.MODID)
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
        // Eager-load panel activations and known towers from backup so signal resolution works immediately after world load (no panel click needed)
        if (dimension == net.minecraft.world.level.Level.OVERWORLD) {
            var storage = uk.co.extraspecialstudio.dead_air.tower.PanelActivationStorage.get(level);
            if (storage != null) {
                uk.co.extraspecialstudio.dead_air.tower.PanelActivationStorage.loadBackupInto(level, storage);
            }
            uk.co.extraspecialstudio.dead_air.tower.KnownTowerStorage.loadBackupFileDirect(level);
        }
        scanLoadedChunks(level);
    }
    
    /** Clear world ready state when dimension unloads. */
    public static void clearWorldReady(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        READY_WORLDS.remove(dimension);
    }

    /**
     * Call from packet handler only (never during world/chunk load). Loads backup into cache and sets
     * READY_WORLDS so scanChunkIfReady can run. Safe because it runs when player already in world.
     * Also loads known towers if cache is empty (fallback if markWorldReady ran before overworld was ready).
     */
    public static void ensureOverworldReadyForSignal(ServerLevel level) {
        if (level == null || level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        boolean alreadyReady = READY_WORLDS.containsKey(level.dimension());
        if (!alreadyReady) {
            READY_WORLDS.put(level.dimension(), true);
            uk.co.extraspecialstudio.dead_air.tower.PanelActivationStorage.loadBackupFileDirect(level);
        }
        // Always ensure known towers are loaded (in case packet arrives before markWorldReady, or markWorldReady skipped overworld)
        uk.co.extraspecialstudio.dead_air.tower.KnownTowerStorage.loadBackupFileDirect(level);
    }

    /**
     * Scan a single chunk for towers (e.g. when handling signal request and tower not found).
     * Safe to call from network thread; only scans if world is ready.
     */
    public static void scanChunkIfReady(ServerLevel level, int chunkX, int chunkZ) {
        if (level == null || Dead_air.isShutdownRequested()) return;
        if (!READY_WORLDS.containsKey(level.dimension())) return;
        if (!level.hasChunk(chunkX, chunkZ)) return;
        try {
            var chunk = level.getChunk(chunkX, chunkZ);
            if (chunk instanceof LevelChunk) {
                scanChunk(level, (LevelChunk) chunk);
            }
        } catch (Exception e) {
            // Chunk may be unloaded or invalid
        }
    }
    
    /**
     * Scan chunks for radio towers: spawn area plus chunks around each player.
     * This ensures we find towers near the player even when they're far from world spawn.
     */
    private static void scanLoadedChunks(ServerLevel level) {
        try {
            java.util.Set<net.minecraft.world.level.ChunkPos> toScan = new java.util.HashSet<>();
            // 1) Spawn area
            var spawnPos = level.getSharedSpawnPos();
            int spawnChunkX = spawnPos.getX() >> 4;
            int spawnChunkZ = spawnPos.getZ() >> 4;
            int radius = 12;
            for (int cx = spawnChunkX - radius; cx <= spawnChunkX + radius; cx++) {
                for (int cz = spawnChunkZ - radius; cz <= spawnChunkZ + radius; cz++) {
                    if (level.hasChunk(cx, cz))
                        toScan.add(new net.minecraft.world.level.ChunkPos(cx, cz));
                }
            }
            // 2) Chunks around each player (so towers near player are found even far from spawn)
            for (net.minecraft.server.level.ServerPlayer player : level.players()) {
                if (player == null) continue;
                int pcx = player.getBlockX() >> 4;
                int pcz = player.getBlockZ() >> 4;
                int pr = 4;
                for (int cx = pcx - pr; cx <= pcx + pr; cx++) {
                    for (int cz = pcz - pr; cz <= pcz + pr; cz++) {
                        if (level.hasChunk(cx, cz))
                            toScan.add(new net.minecraft.world.level.ChunkPos(cx, cz));
                    }
                }
            }
            int scanned = 0;
            for (net.minecraft.world.level.ChunkPos cp : toScan) {
                var chunk = level.getChunk(cp.x, cp.z);
                if (chunk instanceof LevelChunk) {
                    scanChunk(level, (LevelChunk) chunk);
                    scanned++;
                }
            }
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
                            RadioStation station = TowerManager.determineStationForTower(level, panelPos, towerType);
                            if (station != null) {
                                TowerManager.registerTower(level, panelPos, station, towerType);
                                processedPanels.add(panelPos);
                                // Persist detection so walkie can point even after chunk unload (no unlock/music)
                                BlockPos towerPos = panelPos;
                                uk.co.extraspecialstudio.dead_air.radio.RadioTower reg =
                                    TowerManager.findTowerForPanel(level, panelPos);
                                if (reg != null) towerPos = reg.getPosition();
                                if (KnownTowerStorage.addDetectedTower(level, towerPos, panelPos, station.getId())) {
                                    for (net.minecraft.server.level.ServerPlayer p : level.players()) {
                                        uk.co.extraspecialstudio.dead_air.events.ModEvents.syncKnownTowersToPlayer(p);
                                    }
                                }
                            }
                        } else {
                            // Already in TowerManager — still ensure detection is persisted (e.g. after upgrade)
                            BlockPos towerPos = panelPos;
                            uk.co.extraspecialstudio.dead_air.radio.RadioTower reg =
                                TowerManager.findTowerForPanel(level, panelPos);
                            if (reg != null) {
                                towerPos = reg.getPosition();
                                RadioStation st = reg.getStation();
                                if (st != null && KnownTowerStorage.addDetectedTower(level, towerPos, panelPos, st.getId())) {
                                    for (net.minecraft.server.level.ServerPlayer p : level.players()) {
                                        uk.co.extraspecialstudio.dead_air.events.ModEvents.syncKnownTowersToPlayer(p);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        // Do nothing here. Forge: "Do not perform direct level interactions in ChunkEvent.Load"
        // (can deadlock). Panel backup load + tower scan happen only on first signal request (lazy init).
        if (Dead_air.isShutdownRequested()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!READY_WORLDS.containsKey(level.dimension())) return;

        if (event.getChunk() instanceof LevelChunk chunk) {
            try {
                scanChunk(level, chunk);
            } catch (Exception e) {
                Dead_air.LOGGER.error("Error scanning chunk for radio towers at ({}, {})", 
                    chunk.getPos().x, chunk.getPos().z, e);
            }
        }
    }
    
}
