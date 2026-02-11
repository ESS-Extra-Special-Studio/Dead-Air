package uk.creatopia.unbound.dead_air.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import uk.creatopia.unbound.dead_air.Config;

/**
 * Calculates signal strength between a player position and a radio tower.
 */
@SuppressWarnings("null")
public class SignalStrength {
    
    /**
     * Calculate signal strength (0.0 to 1.0) based on distance to tower.
     */
    public static float calculateDistanceStrength(RadioTower tower, Vec3 playerPos) {
        double distance = tower.getDistanceTo(playerPos);
        double range = tower.getStation().getBroadcastRange();
        
        if (distance > range) {
            return 0.0f;
        }
        
        // Signal strength decreases with distance
        // At 0 distance: 1.0, at max range: ~0.1
        double normalizedDistance = distance / range;
        float strength = (float) (1.0 - (normalizedDistance * 0.9));
        return Math.max(0.0f, Math.min(1.0f, strength));
    }
    
    /**
     * Calculate line-of-sight penalty for signal strength.
     * Returns a multiplier (0.0 to 1.0) based on obstacles.
     */
    public static float calculateLineOfSightPenalty(Level level, Vec3 towerPos, Vec3 playerPos) {
        if (!Config.enableLineOfSight) {
            return 1.0f;
        }
        
        if (level.isClientSide) {
            return 1.0f; // Client can't do proper raycasting
        }
        
        // Simple check: count solid blocks between tower and player
        int obstacleCount = 0;
        Vec3 direction = playerPos.subtract(towerPos).normalize();
        double distance = towerPos.distanceTo(playerPos);
        int steps = (int) (distance / 2.0); // Check every 2 blocks
        
        for (int i = 1; i < steps; i++) {
            Vec3 checkPos = towerPos.add(direction.scale(i * 2.0));
            BlockPos blockPos = BlockPos.containing(checkPos);
            
            if (level.getBlockState(blockPos).canOcclude()) {
                obstacleCount++;
            }
        }
        
        // Each obstacle reduces signal by 5%
        float penalty = 1.0f - (obstacleCount * 0.05f);
        return Math.max(0.3f, Math.min(1.0f, penalty)); // Minimum 30% signal even with obstacles
    }
    
    /**
     * Calculate weather penalty (storms reduce signal).
     */
    public static float calculateWeatherPenalty(Level level) {
        if (!Config.enableWeatherEffects) {
            return 1.0f;
        }
        
        if (level.isRaining() || level.isThundering()) {
            return 0.7f; // 30% reduction in storms
        }
        return 1.0f;
    }
    
    /**
     * Get final signal strength combining all factors.
     */
    public static float getFinalSignalStrength(Level level, RadioTower tower, Vec3 playerPos) {
        if (!tower.isPowered() || !tower.isInRange(playerPos)) {
            return 0.0f;
        }
        
        float distanceStrength = calculateDistanceStrength(tower, playerPos);
        float losPenalty = calculateLineOfSightPenalty(level, tower.getPositionVec(), playerPos);
        float weatherPenalty = calculateWeatherPenalty(level);
        
        return distanceStrength * losPenalty * weatherPenalty;
    }
    
    /**
     * Get signal bars (0-5) for UI display.
     */
    public static int getSignalBars(float signalStrength) {
        if (signalStrength <= 0.0f) return 0;
        if (signalStrength < 0.2f) return 1;
        if (signalStrength < 0.4f) return 2;
        if (signalStrength < 0.6f) return 3;
        if (signalStrength < 0.8f) return 4;
        return 5;
    }
}
