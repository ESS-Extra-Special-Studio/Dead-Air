package uk.co.extraspecialstudio.dead_air.events;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.net.DeadAirNet;
import uk.co.extraspecialstudio.dead_air.net.StationRemovedFromListPacket;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.RadioTower;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;
import uk.co.extraspecialstudio.dead_air.radio.TowerManager;
import uk.co.extraspecialstudio.dead_air.station.StationUnlockManager;
import uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerDetector;
import uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerType;

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
                }
            } else {
                // Player-built tower - register with default station (Emergency Broadcast)
                // Player can change station later if we add that feature
                RadioStation defaultStation = StationRegistry.getStation(StationRegistry.EMERGENCY_BROADCAST_ID);
                if (defaultStation != null) {
                    TowerManager.registerPlayerTower(level, pos, defaultStation);
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
            RadioTower tower = TowerManager.getTowerAt(level, pos);
            RadioStation station = tower != null ? tower.getStation() : null;
            TowerManager.removeTower(level, pos);
            uk.co.extraspecialstudio.dead_air.tower.RadioPanelManager.clearPanel(pos);
            // If no other tower in this dimension broadcasts this station, remove it from the GUI station list for all players
            if (station != null && !TowerManager.hasTowerBroadcasting(level, station.getId())) {
                StationUnlockManager.removeStationForAllPlayers(level, station.getId());
                DeadAirNet.CHANNEL.send(PacketDistributor.ALL.noArg(), new StationRemovedFromListPacket(station.getId()));
            }
        }
    }
    
}
