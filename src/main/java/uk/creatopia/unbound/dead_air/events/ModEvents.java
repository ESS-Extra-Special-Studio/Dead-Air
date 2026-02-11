package uk.creatopia.unbound.dead_air.events;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import uk.creatopia.unbound.dead_air.Dead_air;
import uk.creatopia.unbound.dead_air.radio.RadioStation;
import uk.creatopia.unbound.dead_air.radio.RadioTower;
import uk.creatopia.unbound.dead_air.radio.TowerManager;
import uk.creatopia.unbound.dead_air.station.StationUnlockManager;
import uk.creatopia.unbound.dead_air.walkie.WalkieTalkieManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event handlers for the mod.
 */
@Mod.EventBusSubscriber(modid = Dead_air.MODID)
@SuppressWarnings("null")
public class ModEvents {
    // Track which towers have already notified each player (to avoid spam)
    private static final Map<UUID, Set<net.minecraft.core.BlockPos>> NOTIFIED_TOWERS = new ConcurrentHashMap<>();

    private static boolean shutdownRequested() {
        return uk.creatopia.unbound.dead_air.Dead_air.isShutdownRequested();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (shutdownRequested()) return;
        var server = event.getServer();
        if (server == null || !server.isRunning() || server.isStopped()) return;
        if (server.getPlayerCount() == 0) {
            uk.creatopia.unbound.dead_air.Dead_air.setShutdownRequested(true);
            return;
        }
        try {
            if (server.isShutdown()) return;
        } catch (Exception e) {
            return;
        }

        try {
            // Delayed world load refreshes
            var delaysToProcess = new ArrayList<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>>();
            for (var entry : WORLD_LOAD_DELAYS.entrySet()) {
                int delay = entry.getValue();
                if (delay <= 0) {
                    delaysToProcess.add(entry.getKey());
                } else {
                    WORLD_LOAD_DELAYS.put(entry.getKey(), delay - 1);
                }
            }
            for (var dimensionKey : delaysToProcess) {
                WORLD_LOAD_DELAYS.remove(dimensionKey);
                ServerLevel level = server.getLevel(dimensionKey);
                if (level != null) {
                    // Load panel storage and scan chunks first so towers register with correct activation state
                    uk.creatopia.unbound.dead_air.events.ChunkEvents.markWorldReady(level);
                    refreshAllTowerPowerStates(level);
                }
            }

            uk.creatopia.unbound.dead_air.commands.SpawnTowerCommand.processPendingScans(server);

            var allLevels = server.getAllLevels();
            if (allLevels != null) {
                for (ServerLevel level : allLevels) {
                    if (level == null || level.isClientSide) continue;
                    try {
                        TowerManager.updateTowerPower(level);
                        uk.creatopia.unbound.dead_air.zombie.ZombieAttractionManager.update(level);
                    } catch (Exception e) {
                        // Ignore per-level errors (e.g. during shutdown)
                    }
                }
            }
        } catch (Exception e) {
            Dead_air.LOGGER.error("Error in server tick handler: {}", e.getMessage(), e);
        }
    }
    
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        
        // Check for new towers in range when player has powered-on walkie-talkie
        if (event.player instanceof ServerPlayer serverPlayer) {
            checkForNewTowers(serverPlayer);
        }
    }
    
    /**
     * Check if player with powered-on walkie-talkie has entered range of a new tower.
     */
    private static void checkForNewTowers(ServerPlayer player) {
        // Only check if player is holding walkie-talkie and it's powered on
        if (!WalkieTalkieManager.isHoldingWalkieTalkie(player)) {
            return;
        }
        
        WalkieTalkieManager.WalkieTalkieState state = WalkieTalkieManager.getState(player);
        if (!state.isOn()) {
            return;
        }
        
        // Only check every 20 ticks (1 second) to avoid spam
        if (player.tickCount % 20 != 0) {
            return;
        }
        
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        
        Vec3 playerPos = player.position();
        var towersInRange = TowerManager.getTowersInRange(level, playerPos);
        
        if (towersInRange.isEmpty()) {
            return;
        }
        
        // Get set of already-notified towers for this player
        Set<net.minecraft.core.BlockPos> notified = NOTIFIED_TOWERS.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet());
        
        for (RadioTower tower : towersInRange) {
            // Only check powered towers
            if (!tower.isPowered()) {
                continue;
            }
            
            // Emergency Broadcast is never "discovered" from towers - it's always available
            if (tower.getStation().getType() == RadioStation.StationType.EMERGENCY_BROADCAST) {
                notified.add(tower.getPosition());
                continue;
            }
            
            // Skip if we've already notified about this tower
            if (notified.contains(tower.getPosition())) {
                continue;
            }
            
            // Check if this station is new (not unlocked yet)
            boolean isUnlocked = StationUnlockManager.hasUnlocked(player, tower.getStation());
            
            if (!isUnlocked) {
                // Unlock the station
                StationUnlockManager.unlockStation(player, tower.getStation());
                
                // Mark this tower as notified
                notified.add(tower.getPosition());
                
                // Send chat message to player
                Component message = Component.literal("§6[Radio] §rDiscovered new station: §e" + 
                    tower.getStation().getName() + " §7(" + 
                    String.format("%.1f", tower.getStation().getFrequency()) + " MHz)");
                player.sendSystemMessage(message);
                
                Dead_air.LOGGER.info("Player {} discovered new station {} from tower at {}", 
                    player.getName().getString(), tower.getStation().getName(), tower.getPosition());
            } else {
                // Station already unlocked, but mark tower as notified to avoid future checks
                notified.add(tower.getPosition());
            }
        }
    }
    
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            uk.creatopia.unbound.dead_air.station.StationUnlockManager.restoreFromStorage(serverPlayer);
            // Sync known towers to client so it can compute signal locally (no per-tick packets needed)
            syncKnownTowersToPlayer(serverPlayer);
        }
    }

    /** Resync known towers to a player (e.g. after they add one via panel). Call from server only. */
    public static void syncKnownTowersToPlayer(ServerPlayer player) {
        if (player == null || player.level() == null) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        uk.creatopia.unbound.dead_air.events.ChunkEvents.ensureOverworldReadyForSignal(level);
        var entries = uk.creatopia.unbound.dead_air.tower.KnownTowerStorage.getKnownTowers(level.dimension());
        if (entries.isEmpty()) return;
        var list = new java.util.ArrayList<uk.creatopia.unbound.dead_air.net.KnownTowersSyncPacket.TowerEntry>();
        for (var e : entries) {
            list.add(new uk.creatopia.unbound.dead_air.net.KnownTowersSyncPacket.TowerEntry(
                e.towerPos, e.panelPos, e.stationId));
        }
        uk.creatopia.unbound.dead_air.net.DeadAirNet.CHANNEL.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new uk.creatopia.unbound.dead_air.net.KnownTowersSyncPacket(list));
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // In singleplayer, when the last player logs out we're about to save and exit. Stop all work immediately.
        if (event.getEntity().level().getServer() != null && event.getEntity().level().getServer().getPlayerCount() <= 1) {
            uk.creatopia.unbound.dead_air.Dead_air.setShutdownRequested(true);
        }
        WalkieTalkieManager.removePlayer(event.getEntity());
        uk.creatopia.unbound.dead_air.station.StationUnlockManager.removePlayer(event.getEntity());
        NOTIFIED_TOWERS.remove(event.getEntity().getUUID());
    }
    
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onWorldUnload(LevelEvent.Unload event) {
        uk.creatopia.unbound.dead_air.Dead_air.setShutdownRequested(true);
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            // Persist known towers when overworld unloads (exit to menu or full game exit) so they survive full restart
            if (serverLevel.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                uk.creatopia.unbound.dead_air.tower.KnownTowerStorage.persistToFile(serverLevel);
            }
            WORLD_LOAD_DELAYS.remove(serverLevel.dimension());
            uk.creatopia.unbound.dead_air.commands.SpawnTowerCommand.clearPendingScans(serverLevel.dimension());
            uk.creatopia.unbound.dead_air.events.ChunkEvents.clearWorldReady(serverLevel.dimension());
            uk.creatopia.unbound.dead_air.tower.RadioPanelManager.clearSessionForDimension(serverLevel.dimension());
        }
    }
    
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        uk.creatopia.unbound.dead_air.Dead_air.setShutdownRequested(true);
        WORLD_LOAD_DELAYS.clear();
        uk.creatopia.unbound.dead_air.commands.SpawnTowerCommand.clearAllPendingScans();
        NOTIFIED_TOWERS.clear();
        // Last-chance write of panel backup so activations persist after full game exit
        try {
            var server = event.getServer();
            if (server != null) {
                var overworld = server.overworld();
                if (overworld != null) {
                    var storage = uk.creatopia.unbound.dead_air.tower.PanelActivationStorage.getForSave(overworld);
                    if (storage != null) {
                        storage.setDirty();
                        storage.writeBackup(overworld);
                        Dead_air.LOGGER.info("Dead Air: wrote panel backup on server stop ({} panels)", storage.getActivatedCount());
                    }
                    uk.creatopia.unbound.dead_air.tower.KnownTowerStorage.persistToFile(overworld);
                }
            }
        } catch (Exception e) {
            Dead_air.LOGGER.warn("Dead Air: failed to write panel backup on stop: {}", e.getMessage());
        }
        Dead_air.LOGGER.debug("Dead Air: server stopping, cleared pending work");
    }
    
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onLevelSave(net.minecraftforge.event.level.LevelEvent.Save event) {
        // Do NOT set shutdown here: LevelEvent.Save fires on autosave too, which would
        // leave shutdown true and stop tower updates/music. Shutdown is set on Unload,
        // ServerStopping, and when last player logs out.
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            WORLD_LOAD_DELAYS.remove(serverLevel.dimension());
            uk.creatopia.unbound.dead_air.commands.SpawnTowerCommand.clearPendingScans(serverLevel.dimension());
            // Force panel activation storage to be saved when overworld saves (use getForSave so we're not skipped by shutdown flag)
            var server = serverLevel.getServer();
            if (server != null && serverLevel.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                var storage = uk.creatopia.unbound.dead_air.tower.PanelActivationStorage.getForSave(serverLevel);
                if (storage != null) {
                    storage.setDirty();
                    storage.writeBackup(serverLevel);
                }
                uk.creatopia.unbound.dead_air.tower.KnownTowerStorage.persistToFile(serverLevel);
            }
        }
    }
    
    
    @SubscribeEvent
    public static void onServerAboutToStart(net.minecraftforge.event.server.ServerAboutToStartEvent event) {
        uk.creatopia.unbound.dead_air.Dead_air.setShutdownRequested(false);
        WORLD_LOAD_DELAYS.clear();
        NOTIFIED_TOWERS.clear();
        Dead_air.LOGGER.debug("Cleared stale data on server about to start");
    }
    
    // Track world load delays per dimension to avoid blocking
    private static final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, Integer> WORLD_LOAD_DELAYS = new ConcurrentHashMap<>();
    
    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            Dead_air.LOGGER.info("World loaded in dimension: {} - panel init deferred to first chunk load", serverLevel.dimension().location());
            // Do NOT touch storage, getChunk, or SavedData here - any of that can block/deadlock during world load.
            // Panel backup load + READY_WORLDS flag happen in ChunkEvents when the first overworld chunk loads.
            WORLD_LOAD_DELAYS.put(serverLevel.dimension(), 5);
        }
    }
    
    /**
     * Refresh power states for all registered towers from persistent panel activation storage.
     * Called after world load so towers that were activated before save stay powered after reload.
     * For overworld, ensures backup is loaded first (fallback if LevelEvent.Load was too early).
     */
    private static void refreshAllTowerPowerStates(ServerLevel level) {
        try {
            if (level == null) return;
            if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                var storage = uk.creatopia.unbound.dead_air.tower.PanelActivationStorage.get(level);
                if (storage != null) {
                    uk.creatopia.unbound.dead_air.tower.PanelActivationStorage.loadBackupInto(level, storage);
                }
            }
            TowerManager.updateTowerPower(level);
            Dead_air.LOGGER.info("Refreshed tower power states from saved panel activations for dimension {}", level.dimension().location());
        } catch (Exception e) {
            Dead_air.LOGGER.error("Error refreshing tower power states: {}", e.getMessage(), e);
        }
    }
    
    // Removed scanExistingTowers - chunks are scanned as they load via ChunkEvents.onChunkLoad
    // This prevents blocking during world load initialization
}
