package uk.co.extraspecialstudio.dead_air.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.RadioTower;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;
import uk.co.extraspecialstudio.dead_air.radio.TowerManager;
import uk.co.extraspecialstudio.dead_air.station.StationUnlockManager;

/**
 * Block that represents a radio tower control panel.
 * Players interact with this to unlock stations.
 */
@SuppressWarnings("null") // BlockState/Level from vanilla API
public class RadioTowerBlock extends Block {
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    
    public RadioTowerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }
    
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }
    
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(POWERED, false);
    }
    
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, 
                                InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        
        // Check if there's a registered tower at this position
        RadioTower tower = findTowerAt(level, pos);
        
        if (tower == null) {
            // Try to register this as a new tower
            // Note: RadioTowerBlock is deprecated - towers are now detected via RadioTowers mod
            // This code is kept for backwards compatibility but may not work correctly
            RadioStation station = determineStationForTower(level, pos);
            if (station != null && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                // Use UNKNOWN type for legacy blocks
                TowerManager.registerTower(serverLevel, pos, station, 
                    uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerType.UNKNOWN);
                tower = findTowerAt(level, pos);
            }
        }
        
        final RadioTower finalTower = tower;
        if (finalTower != null && player instanceof ServerPlayer serverPlayer) {
            // Open tower interaction GUI
            NetworkHooks.openScreen(serverPlayer, new TowerMenuProvider(finalTower, pos), 
                buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeResourceLocation(finalTower.getStation().getId());
                });
            
            if (!finalTower.getStation().getId().equals(StationRegistry.JUKEBOX_FM_ID)) {
                StationUnlockManager.unlockStation(player, finalTower.getStation());
            }
            return InteractionResult.CONSUME;
        }
        
        return InteractionResult.PASS;
    }
    
    private RadioTower findTowerAt(Level level, BlockPos pos) {
        var towers = TowerManager.getTowersInRange(level, net.minecraft.world.phys.Vec3.atCenterOf(pos));
        return towers.stream()
            .filter(t -> t.getPosition().equals(pos))
            .findFirst()
            .orElse(null);
    }
    
    private RadioStation determineStationForTower(Level level, BlockPos pos) {
        RadioStation emergency = StationRegistry.getStation(StationRegistry.EMERGENCY_BROADCAST_ID);
        if (emergency != null && TowerManager.getTowersForStation(level, emergency).isEmpty()) {
            return emergency;
        }
        
        var musicStations = StationRegistry.getStationsByType(RadioStation.StationType.MUSIC);
        if (!musicStations.isEmpty()) {
            java.util.Random random = new java.util.Random(pos.asLong());
            return musicStations.get(random.nextInt(musicStations.size()));
        }
        
        return emergency;
    }
    
    /**
     * Menu provider for tower interaction GUI.
     */
    private static class TowerMenuProvider implements MenuProvider {
        private final RadioTower tower;
        private final BlockPos pos;
        
        public TowerMenuProvider(RadioTower tower, BlockPos pos) {
            this.tower = tower;
            this.pos = pos;
        }
        
        @Override
        public Component getDisplayName() {
            return Component.translatable("block.dead_air.radio_tower.title", tower.getStation().getName());
        }
        
        @Override
        public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
            // Return a simple menu - in full implementation, this would be a proper GUI
            // For now, we'll use a placeholder
            return new TowerMenu(containerId, playerInventory, pos, tower);
        }
    }
}
