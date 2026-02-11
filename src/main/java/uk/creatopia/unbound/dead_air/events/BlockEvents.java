package uk.creatopia.unbound.dead_air.events;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import uk.creatopia.unbound.dead_air.Dead_air;
import uk.creatopia.unbound.dead_air.radio.RadioStation;
import uk.creatopia.unbound.dead_air.radio.StationRegistry;
import uk.creatopia.unbound.dead_air.radio.TowerManager;
import uk.creatopia.unbound.dead_air.tower.ApocalypseTowerDetector;
import uk.creatopia.unbound.dead_air.tower.ApocalypseTowerType;

/**
 * Event handlers for block placement and removal.
 * When a Radio Panel block is placed we register the tower (structure or player-built).
 * This is the only way to detect panels placed by structure blocks or players; chunk load
 * handles worldgen towers (no "structure placed" event in Forge).
 */
@Mod.EventBusSubscriber(modid = Dead_air.MODID)
@SuppressWarnings("null")
public class BlockEvents {
    
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        
        BlockPos pos = event.getPos();
        
        // Check if placed block is a Radio Panel (towers are identified by their Radio Panels)
        if (ApocalypseTowerDetector.isRadioPanel(level, pos)) {
            // Found a Radio Panel - check if it's an official tower or player-built
            ApocalypseTowerType towerType = ApocalypseTowerDetector.getTowerType(level, pos);
            
            if (towerType != ApocalypseTowerType.UNKNOWN) {
                // Official tower - register it (TowerManager avoids same station within range)
                RadioStation station = TowerManager.determineStationForTower(level, pos, towerType);
                if (station != null) {
                    // Register tower at the Radio Panel position
                    TowerManager.registerTower(level, pos, station, towerType);
                    Dead_air.LOGGER.info("RadioTowers {} tower detected at {} (Radio Panel) broadcasting station {}", 
                        towerType, pos, station.getName());
                }
            } else {
                // Player-built tower - register with default station (Emergency Broadcast)
                // Player can change station later if we add that feature
                RadioStation defaultStation = StationRegistry.getStation(StationRegistry.EMERGENCY_BROADCAST_ID);
                if (defaultStation != null) {
                    TowerManager.registerPlayerTower(level, pos, defaultStation);
                    Dead_air.LOGGER.info("Player-built tower detected at {} (Radio Panel) - registered as player tower", pos);
                }
            }
        }
    }
    
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        
        BlockPos pos = event.getPos();
        
        // Check if broken block is a Radio Panel (towers are identified by Radio Panels)
        if (ApocalypseTowerDetector.isRadioPanel(level, pos)) {
            // Remove tower associated with this Radio Panel
            TowerManager.removeTower(level, pos);
            uk.creatopia.unbound.dead_air.tower.RadioPanelManager.clearPanel(pos);
            Dead_air.LOGGER.info("RadioTowers tower removed at {} (Radio Panel broken)", pos);
        }
    }
    
}
