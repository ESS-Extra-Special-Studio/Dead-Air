package uk.creatopia.unbound.dead_air.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import uk.creatopia.unbound.dead_air.Config;

/**
 * Calculates signal strength between a player position and a radio tower.
 * Each tower has its own signal radius: 5/5 next to tower, drops 1 bar per 50 blocks. Rain: -1 bar at each level.
 */
@SuppressWarnings("null")
public class SignalStrength {
    /** 1 bar per 50 blocks: 0=5/5, 50=4/5, 100=3/5, 150=2/5, 200=1/5, 250=0/5 */
    private static final double SIGNAL_RADIUS_BLOCKS = 250.0;
    /** Rain reduces signal by 1 bar (0.2 in 0-1 scale) */
    private static final float RAIN_PENALTY = 0.2f;
    
    /**
     * Calculate signal strength (0.0 to 1.0) based on distance to tower.
     * 0 blocks = 1.0 (5/5), 50 blocks = 0.8 (4/5), 100 = 0.6, 150 = 0.4, 200 = 0.2, 250+ = 0.
     */
    public static float calculateDistanceStrength(RadioTower tower, Vec3 playerPos) {
        double distance = tower.getDistanceTo(playerPos);
        double range = tower.getStation().getBroadcastRange();
        
        if (distance > range) {
            return 0.0f;
        }
        
        // Linear falloff: 1 bar per 50 blocks, 0 at 250 blocks
        float strength = (float) Math.max(0.0, 1.0 - (distance / SIGNAL_RADIUS_BLOCKS));
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
     * Calculate weather penalty. Rain/thunder reduces signal by 1 bar (0.2) at each distance level.
     */
    public static float calculateWeatherPenalty(Level level) {
        if (!Config.enableWeatherEffects) {
            return 1.0f;
        }
        
        if (level.isRaining() || level.isThundering()) {
            return 1.0f - RAIN_PENALTY; // -1 bar
        }
        return 1.0f;
    }
    
    /** When player is within this many blocks of the tower, skip LOS/weather so "right next to" = full bars (5/5). Weather still affects signal when further away. */
    private static final double CLOSE_RANGE_BLOCKS = 6.0;

    /**
     * Get final signal strength combining all factors.
     * When very close to the tower, LOS and weather are skipped so you get 5/5 bars. As you move away, weather and LOS both apply.
     */
    public static float getFinalSignalStrength(Level level, RadioTower tower, Vec3 playerPos) {
        if (!tower.isPowered() || !tower.isInRange(playerPos)) {
            return 0.0f;
        }
        
        double distance = tower.getDistanceTo(playerPos);
        float distanceStrength = calculateDistanceStrength(tower, playerPos);
        // Right up close: no LOS or weather penalty. Further away: weather and LOS both apply.
        boolean veryClose = distance <= CLOSE_RANGE_BLOCKS;
        float losPenalty = veryClose ? 1.0f : calculateLineOfSightPenalty(level, tower.getPositionVec(), playerPos);
        float weatherPenalty = veryClose ? 1.0f : calculateWeatherPenalty(level);
        
        return Math.max(0.0f, Math.min(1.0f, distanceStrength * losPenalty * weatherPenalty));
    }

    /**
     * Compute signal from tower position + station (no RadioTower object).
     * Used when resolving from persisted known towers (no chunk scan).
     */
    public static float getFinalSignalStrengthFromPosition(Level level, Vec3 towerPos, RadioStation station, Vec3 playerPos) {
        if (station == null) return 0.0f;
        double distance = towerPos.distanceTo(playerPos);
        int range = station.getBroadcastRange();
        if (distance > range) return 0.0f;
        // Same formula: 1 bar per 50 blocks, 0 at 250
        float distanceStrength = (float) Math.max(0.0, 1.0 - (distance / SIGNAL_RADIUS_BLOCKS));
        distanceStrength = Math.max(0.0f, Math.min(1.0f, distanceStrength));
        boolean veryClose = distance <= CLOSE_RANGE_BLOCKS;
        float losPenalty = veryClose ? 1.0f : calculateLineOfSightPenalty(level, towerPos, playerPos);
        float weatherPenalty = veryClose ? 1.0f : calculateWeatherPenalty(level);
        return Math.max(0.0f, Math.min(1.0f, distanceStrength * losPenalty * weatherPenalty));
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
