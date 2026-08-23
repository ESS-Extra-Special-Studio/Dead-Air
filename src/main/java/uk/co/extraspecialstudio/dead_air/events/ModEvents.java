package uk.co.extraspecialstudio.dead_air.events;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import uk.co.extraspecialstudio.dead_air.api.DeadAirAPI;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.item.DeadAirItems;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.RadioTower;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;
import uk.co.extraspecialstudio.dead_air.radio.TowerManager;
import uk.co.extraspecialstudio.dead_air.station.FieldGuideGrantStorage;
import uk.co.extraspecialstudio.dead_air.station.StationUnlockManager;
import uk.co.extraspecialstudio.dead_air.tower.modules.TowerModuleStorage;
import uk.co.extraspecialstudio.dead_air.walkie.WalkieTalkieManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event handlers for the mod.
 */
@EventBusSubscriber(modid = Dead_air.MODID)
@SuppressWarnings("null")
public class ModEvents {
    private static final String FIELD_GUIDE_GRANTED_TAG = Dead_air.MODID + ".field_guide_granted";
    // Track which towers have already notified each player (to avoid spam)
    private static final Map<UUID, Set<net.minecraft.core.BlockPos>> NOTIFIED_TOWERS = new ConcurrentHashMap<>();

    private static boolean shutdownRequested() {
        return uk.co.extraspecialstudio.dead_air.Dead_air.isShutdownRequested();
    }

    // Walkie GUI opens from WalkieItem.use() on the client (Dead Air owns the items now).

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (shutdownRequested()) return;
        var server = event.getServer();
        if (server == null || !server.isRunning() || server.isStopped()) return;
        if (server.getPlayerCount() == 0) {
            uk.co.extraspecialstudio.dead_air.Dead_air.setShutdownRequested(true);
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
                    uk.co.extraspecialstudio.dead_air.events.ChunkEvents.markWorldReady(level);
                    refreshAllTowerPowerStates(level);
                }
            }

            uk.co.extraspecialstudio.dead_air.commands.SpawnTowerCommand.processPendingScans(server);

            var allLevels = server.getAllLevels();
            if (allLevels != null) {
                for (ServerLevel level : allLevels) {
                    if (level == null || level.isClientSide) continue;
                    try {
                        TowerManager.updateTowerPower(level);
                        // Zombie attraction (to towers / players with radio) is handled by RadioTowers mod when present
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
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
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

            if (tower.getStation().getId().equals(StationRegistry.JUKEBOX_FM_ID)) {
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
                player.sendSystemMessage(
                    Component.literal("[Radio] ").withStyle(net.minecraft.ChatFormatting.GOLD)
                        .append(Component.translatable("message.dead_air.radio.new_station_discovered").withStyle(net.minecraft.ChatFormatting.WHITE))
                );
            } else {
                // Station already unlocked, but mark tower as notified to avoid future checks
                notified.add(tower.getPosition());
            }
        }
    }
    
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            uk.co.extraspecialstudio.dead_air.station.StationUnlockManager.restoreFromStorage(serverPlayer);
            // Sync known towers to client so it can compute signal locally (no per-tick packets needed)
            syncKnownTowersToPlayer(serverPlayer);
            grantFieldGuideIfFirstJoin(serverPlayer);
        }
    }

    private static void grantFieldGuideIfFirstJoin(ServerPlayer player) {
        FieldGuideGrantStorage storage = FieldGuideGrantStorage.get(player.serverLevel());
        if (storage == null) {
            return;
        }

        UUID id = player.getUUID();
        // Legacy: some builds only set entity persistent data, which did not always survive dedicated-server logins
        if (player.getPersistentData().getBoolean(FIELD_GUIDE_GRANTED_TAG)) {
            storage.markReceived(id);
            return;
        }
        if (storage.hasReceived(id)) {
            return;
        }

        ItemStack guide = new ItemStack(DeadAirItems.DEAD_AIR_FIELD_GUIDE.get());
        boolean added = player.getInventory().add(guide);
        if (!added) {
            player.drop(guide, false);
        }

        storage.markReceived(id);
        player.getPersistentData().putBoolean(FIELD_GUIDE_GRANTED_TAG, true);
        player.sendSystemMessage(
            Component.literal("[Dead Air] ").withStyle(net.minecraft.ChatFormatting.GOLD)
                .append(Component.translatable("message.dead_air.field_guide_received").withStyle(net.minecraft.ChatFormatting.WHITE))
        );
    }

    /** Resync known towers to a player (e.g. after they add one via panel). Call from server only. */
    public static void syncKnownTowersToPlayer(ServerPlayer player) {
        if (player == null || player.level() == null) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        uk.co.extraspecialstudio.dead_air.events.ChunkEvents.ensureOverworldReadyForSignal(level);
        var entries = uk.co.extraspecialstudio.dead_air.tower.KnownTowerStorage.getKnownTowers(level.dimension());
        java.util.ArrayList<BlockPos> repairPanels = new java.util.ArrayList<>();
        for (var e : entries) {
            if (e.activated && StationRegistry.JUKEBOX_FM_ID.equals(e.stationId)
                && !TowerModuleStorage.get(level).getCapabilities(e.panelPos).isJukeboxModuleInstalled()) {
                repairPanels.add(e.panelPos);
            }
        }
        for (BlockPos panelPos : repairPanels) {
            DeadAirAPI.activatePanelAt(level, panelPos, StationRegistry.BEDROCK_RADIO_ID);
        }
        if (!repairPanels.isEmpty()) {
            entries = uk.co.extraspecialstudio.dead_air.tower.KnownTowerStorage.getKnownTowers(level.dimension());
        }
        var list = new java.util.ArrayList<uk.co.extraspecialstudio.dead_air.net.KnownTowersSyncPacket.TowerEntry>();
        for (var e : entries) {
            var caps = TowerModuleStorage.get(level).getCapabilities(e.panelPos);
            list.add(new uk.co.extraspecialstudio.dead_air.net.KnownTowersSyncPacket.TowerEntry(
                e.towerPos, e.panelPos, e.stationId,
                caps.isJukeboxModuleInstalled(), caps.isSignalBoostInstalled(), e.activated));
        }
        uk.co.extraspecialstudio.dead_air.net.DeadAirNet.sendToPlayer(player,
            new uk.co.extraspecialstudio.dead_air.net.KnownTowersSyncPacket(list));
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // In singleplayer, when the last player logs out we're about to save and exit. Stop all work immediately.
        if (event.getEntity().level().getServer() != null && event.getEntity().level().getServer().getPlayerCount() <= 1) {
            uk.co.extraspecialstudio.dead_air.Dead_air.setShutdownRequested(true);
        }
        WalkieTalkieManager.removePlayer(event.getEntity());
        uk.co.extraspecialstudio.dead_air.station.StationUnlockManager.removePlayer(event.getEntity());
        NOTIFIED_TOWERS.remove(event.getEntity().getUUID());
    }
    
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void onWorldUnload(LevelEvent.Unload event) {
        uk.co.extraspecialstudio.dead_air.Dead_air.setShutdownRequested(true);
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            // Clear unlocked stations so a new world doesn't show the previous world's station list
            StationUnlockManager.clearAll();
            // Persist known towers when overworld unloads (exit to menu or full game exit) so they survive full restart
            if (serverLevel.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                uk.co.extraspecialstudio.dead_air.tower.KnownTowerStorage.persistToFile(serverLevel);
            }
            WORLD_LOAD_DELAYS.remove(serverLevel.dimension());
            uk.co.extraspecialstudio.dead_air.commands.SpawnTowerCommand.clearPendingScans(serverLevel.dimension());
            uk.co.extraspecialstudio.dead_air.events.ChunkEvents.clearWorldReady(serverLevel.dimension());
            uk.co.extraspecialstudio.dead_air.tower.RadioPanelManager.clearSessionForDimension(serverLevel.dimension());
        }
    }
    
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        uk.co.extraspecialstudio.dead_air.Dead_air.setShutdownRequested(true);
        WORLD_LOAD_DELAYS.clear();
        uk.co.extraspecialstudio.dead_air.commands.SpawnTowerCommand.clearAllPendingScans();
        NOTIFIED_TOWERS.clear();
        // Last-chance write of panel backup so activations persist after full game exit
        try {
            var server = event.getServer();
            if (server != null) {
                var overworld = server.overworld();
                if (overworld != null) {
                    var storage = uk.co.extraspecialstudio.dead_air.tower.PanelActivationStorage.getForSave(overworld);
                    if (storage != null) {
                        storage.setDirty();
                        storage.writeBackup(overworld);
                    }
                    uk.co.extraspecialstudio.dead_air.tower.KnownTowerStorage.persistToFile(overworld);
                }
            }
        } catch (Exception e) {
            Dead_air.LOGGER.warn("Dead Air: failed to write panel backup on stop: {}", e.getMessage());
        }
    }
    
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void onLevelSave(net.neoforged.neoforge.event.level.LevelEvent.Save event) {
        // Do NOT set shutdown here: LevelEvent.Save fires on autosave too, which would
        // leave shutdown true and stop tower updates/music. Shutdown is set on Unload,
        // ServerStopping, and when last player logs out.
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            WORLD_LOAD_DELAYS.remove(serverLevel.dimension());
            uk.co.extraspecialstudio.dead_air.commands.SpawnTowerCommand.clearPendingScans(serverLevel.dimension());
            // Force panel activation storage to be saved when overworld saves (use getForSave so we're not skipped by shutdown flag)
            var server = serverLevel.getServer();
            if (server != null && serverLevel.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                var storage = uk.co.extraspecialstudio.dead_air.tower.PanelActivationStorage.getForSave(serverLevel);
                if (storage != null) {
                    storage.setDirty();
                    storage.writeBackup(serverLevel);
                }
                uk.co.extraspecialstudio.dead_air.tower.KnownTowerStorage.persistToFile(serverLevel);
            }
        }
    }
    
    
    @SubscribeEvent
    public static void onServerAboutToStart(net.neoforged.neoforge.event.server.ServerAboutToStartEvent event) {
        uk.co.extraspecialstudio.dead_air.Dead_air.setShutdownRequested(false);
        WORLD_LOAD_DELAYS.clear();
        NOTIFIED_TOWERS.clear();
    }
    
    // Track world load delays per dimension to avoid blocking
    private static final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, Integer> WORLD_LOAD_DELAYS = new ConcurrentHashMap<>();
    
    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
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
                var storage = uk.co.extraspecialstudio.dead_air.tower.PanelActivationStorage.get(level);
                if (storage != null) {
                    uk.co.extraspecialstudio.dead_air.tower.PanelActivationStorage.loadBackupInto(level, storage);
                }
            }
            TowerManager.updateTowerPower(level);
        } catch (Exception e) {
            Dead_air.LOGGER.error("Error refreshing tower power states: {}", e.getMessage(), e);
        }
    }
    
    // Removed scanExistingTowers - chunks are scanned as they load via ChunkEvents.onChunkLoad
    // This prevents blocking during world load initialization
}
