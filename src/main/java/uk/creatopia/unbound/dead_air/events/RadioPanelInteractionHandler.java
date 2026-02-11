package uk.creatopia.unbound.dead_air.events;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import uk.creatopia.unbound.dead_air.Dead_air;
import uk.creatopia.unbound.dead_air.radio.RadioTower;
import uk.creatopia.unbound.dead_air.radio.TowerManager;
import uk.creatopia.unbound.dead_air.station.StationUnlockManager;
import uk.creatopia.unbound.dead_air.tower.ApocalypseTowerDetector;
import uk.creatopia.unbound.dead_air.tower.ApocalypseTowerType;
import uk.creatopia.unbound.dead_air.tower.RadioPanelManager;

/**
 * Handles player interactions with Radio Panels from RadioTowers mod.
 * Integrates with the mod's Radio Panel system for tower activation.
 */
@Mod.EventBusSubscriber(modid = Dead_air.MODID)
@SuppressWarnings("null")
public class RadioPanelInteractionHandler {
    
    /**
     * Handle right-click on Radio Panel blocks.
     * This hooks into the Radio Panel's existing right-click behavior.
     * Uses LOW priority to ensure we don't interfere with other mods' handlers.
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOW)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // CRITICAL: Only handle BLOCK right-clicks, not item right-clicks
        // This ensures we don't interfere with walkie-talkie mod's item right-click
        if (event.getLevel().isClientSide) {
            return;
        }
        
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        
        // Don't process if player is holding an item (item right-clicks should be handled by item mods)
        // This prevents interference with walkie-talkie mod's right-click functionality
        if (event.getItemStack() != null && !event.getItemStack().isEmpty()) {
            // Player is holding an item - let item mods handle this
            // Only process if they're right-clicking a block (not using the item)
            // Check if the interaction was actually on a block (not air)
            if (event.getHitVec() == null || event.getPos() == null) {
                return; // Not a block interaction
            }
        }
        
        BlockPos pos = event.getPos();
        
        // Check if this is a Radio Panel from RadioTowers mod
        if (ApocalypseTowerDetector.isRadioPanel(level, pos)) {
            // Activate the Radio Panel (this integrates with RadioTowers mod's system)
            RadioPanelManager.activatePanel(level, pos);
            
            // Find the tower associated with this Radio Panel
            // The tower position is the Radio Panel position
            RadioTower tower = findTowerAtPanel(level, pos);
            
            if (tower == null) {
                // No tower registered yet - check if it's an official tower or player-built
                ApocalypseTowerType towerType = ApocalypseTowerDetector.getTowerType(level, pos);
                
                if (towerType != ApocalypseTowerType.UNKNOWN) {
                    // Official tower - register it
                    uk.creatopia.unbound.dead_air.radio.RadioStation station = 
                        uk.creatopia.unbound.dead_air.radio.TowerManager.determineStationForTower(level, pos, towerType);
                    
                    if (station != null) {
                        TowerManager.registerTower(level, pos, station, towerType);
                        tower = findTowerAtPanel(level, pos);
                        Dead_air.LOGGER.info("Registered official tower at {} after Radio Panel interaction", pos);
                    }
                } else {
                    // Player-built tower - register with a default station (player can change later)
                    // For now, use Emergency Broadcast as default for player-built towers
                    uk.creatopia.unbound.dead_air.radio.RadioStation defaultStation = 
                        uk.creatopia.unbound.dead_air.radio.StationRegistry.getStation(
                            uk.creatopia.unbound.dead_air.radio.StationRegistry.EMERGENCY_BROADCAST_ID);
                    if (defaultStation != null) {
                        TowerManager.registerPlayerTower(level, pos, defaultStation);
                        tower = findTowerAtPanel(level, pos);
                        Dead_air.LOGGER.info("Registered player-built tower at {} after Radio Panel interaction", pos);
                    }
                }
            }
            
            if (tower != null) {
                // Immediately update tower power state based on panel activation
                // This ensures discovery happens immediately, not after a delay
                updateTowerPowerImmediately(level, tower);
                
                Dead_air.LOGGER.info("Player {} interacted with Radio Panel at {}, tower power updated", 
                    event.getEntity().getName().getString(), pos);
                
                // Trigger station discovery for nearby players
                // This ensures players discover the station when they activate a panel
                if (tower.isPowered()) {
                    triggerStationDiscovery(level, tower, event.getEntity());
                }
            }
        }
    }
    
    /**
     * Find the tower associated with a Radio Panel position.
     */
    private static RadioTower findTowerAtPanel(ServerLevel level, BlockPos panelPos) {
        // Towers are registered at the Radio Panel position
        // Check if there's a tower registered at this exact position
        if (TowerManager.isTowerRegistered(level, panelPos)) {
            // Get all towers and find the one at this position
            var allTowers = TowerManager.getAllTowers(level);
            return allTowers.stream()
                .filter(t -> t.getPosition().equals(panelPos) || 
                           (t.getRadioPanelPos() != null && t.getRadioPanelPos().equals(panelPos)))
                .findFirst()
                .orElse(null);
        }
        
        // Fallback: check towers in range
        var towers = TowerManager.getTowersInRange(level, net.minecraft.world.phys.Vec3.atCenterOf(panelPos));
        
        return towers.stream()
            .filter(t -> t.getPosition().equals(panelPos) || 
                       (t.getRadioPanelPos() != null && t.getRadioPanelPos().equals(panelPos)))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Immediately update tower power state based on Radio Panel activation.
     * This is called right after panel activation to ensure immediate discovery.
     */
    private static void updateTowerPowerImmediately(ServerLevel level, RadioTower tower) {
        boolean powered = false;
        
        switch (tower.getTowerType()) {
            case STANDARD:
                // Standard towers always broadcast
                powered = true;
                break;
                
            case FENCED:
                // Fenced towers: check if Radio Panel is activated OR if it started powered
                if (tower.getRadioPanelPos() != null) {
                    boolean panelActivated = RadioPanelManager.isPanelActivated(level, tower.getRadioPanelPos());
                    powered = panelActivated;
                    // If panel is not activated but tower is currently powered, it must have started powered
                    if (!powered && tower.isPowered()) {
                        powered = true; // Keep it on if it started powered
                    }
                } else {
                    // No panel found, but tower exists - assume it started powered if currently powered
                    powered = tower.isPowered();
                }
                break;
                
            case OVERRUN:
                // Overrun towers: only on if Radio Panel is activated
                if (tower.getRadioPanelPos() != null) {
                    powered = RadioPanelManager.isPanelActivated(level, tower.getRadioPanelPos());
                }
                break;
                
            default:
                // Player-built towers: only on if Radio Panel is activated
                if (tower.getRadioPanelPos() != null) {
                    powered = RadioPanelManager.isPanelActivated(level, tower.getRadioPanelPos());
                }
                break;
        }
        
        tower.setPowered(powered);
        Dead_air.LOGGER.info("Updated tower power at {} to {} (panel activated: {})", 
            tower.getPosition(), powered, 
            tower.getRadioPanelPos() != null ? RadioPanelManager.isPanelActivated(level, tower.getRadioPanelPos()) : "N/A");
    }
    
    /**
     * Trigger station discovery for players near the activated tower.
     */
    private static void triggerStationDiscovery(ServerLevel level, RadioTower tower, net.minecraft.world.entity.player.Player activatingPlayer) {
        if (!tower.isPowered()) {
            return;
        }
        
        // Check all players in range of the tower
        var players = level.players();
        for (net.minecraft.world.entity.player.Player player : players) {
            if (player instanceof ServerPlayer) {
                ServerPlayer serverPlayer = (ServerPlayer) player;
                // Check if player is in range and has walkie-talkie
                double distance = player.position().distanceTo(tower.getPositionVec());
                if (distance <= tower.getStation().getBroadcastRange()) {
                    // Check if player has unlocked this station
                    boolean isUnlocked = StationUnlockManager.hasUnlocked(serverPlayer, tower.getStation());
                    
                    if (!isUnlocked) {
                        // Unlock the station
                        StationUnlockManager.unlockStation(serverPlayer, tower.getStation());
                        
                        // Send discovery message
                        Component message = Component.literal("§6[Radio] §rDiscovered new station: §e" + 
                            tower.getStation().getName() + " §7(" + 
                            String.format("%.1f", tower.getStation().getFrequency()) + " MHz)");
                        serverPlayer.sendSystemMessage(message);
                        
                        Dead_air.LOGGER.info("Player {} discovered station {} from tower at {} after panel activation", 
                            serverPlayer.getName().getString(), tower.getStation().getName(), tower.getPosition());
                    }
                }
            }
        }
    }
}
