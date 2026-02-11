package uk.creatopia.unbound.dead_air.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import uk.creatopia.unbound.dead_air.Config;

/**
 * Manages power integration for radio towers using Forge Energy (FE).
 */
@SuppressWarnings("null")
public class PowerManager {
    
    /**
     * Check if a tower at the given position has sufficient power.
     */
    public static boolean isTowerPowered(ServerLevel level, BlockPos pos) {
        // Check the block entity at the position
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            // Try checking adjacent blocks for power sources
            return checkAdjacentPower(level, pos);
        }
        
        // Check if the block entity has energy capability
        return be.getCapability(ForgeCapabilities.ENERGY).map(energy -> {
            return energy.getEnergyStored() >= Config.powerConsumption;
        }).orElse(false);
    }
    
    /**
     * Consume power from a tower.
     */
    public static boolean consumePower(ServerLevel level, BlockPos pos, int amount) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return false;
        }
        
        return be.getCapability(ForgeCapabilities.ENERGY).map(energy -> {
            if (energy.getEnergyStored() >= amount) {
                energy.extractEnergy(amount, false);
                return true;
            }
            return false;
        }).orElse(false);
    }
    
    /**
     * Check adjacent blocks for power sources (for towers that don't have BE at exact position).
     */
    private static boolean checkAdjacentPower(ServerLevel level, BlockPos pos) {
        // Check all 6 adjacent blocks
        BlockPos[] adjacent = {
            pos.above(),
            pos.below(),
            pos.north(),
            pos.south(),
            pos.east(),
            pos.west()
        };
        
        for (BlockPos adj : adjacent) {
            BlockEntity be = level.getBlockEntity(adj);
            if (be != null) {
                boolean hasPower = be.getCapability(ForgeCapabilities.ENERGY).map(energy -> {
                    return energy.getEnergyStored() >= Config.powerConsumption;
                }).orElse(false);
                
                if (hasPower) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Get power consumption rate per tick.
     */
    public static int getRequiredPower() {
        return Config.powerConsumption;
    }
}
