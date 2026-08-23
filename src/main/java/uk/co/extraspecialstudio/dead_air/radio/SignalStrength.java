package uk.co.extraspecialstudio.dead_air.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import uk.co.extraspecialstudio.dead_air.Config;

/**
 * Calculates signal strength between a player position and a radio tower.
 * Bars use 75-block increments: 5/5 within 75, 4/5 within 150, 3/5 within 225, 2/5 within 300, 1/5 within range (default 375). 0/5 beyond. Rain: -1 bar.
 */
@SuppressWarnings("null")
public class SignalStrength {
    /** Bar increments in blocks: 5/5 = 75, 4/5 = 150, 3/5 = 225, 2/5 = 300, 1/5 at 375, 0 beyond. */
    private static final int BAR_INCREMENT_BLOCKS = 75;
    /** Max range for bars so we never get "1 bar for hundreds of blocks" when config range is large. */
    private static final int MAX_BAR_RANGE_BLOCKS = BAR_INCREMENT_BLOCKS * 5; // 375
    /** Rain reduces signal by 1 bar (0.2 in 0-1 scale) */
    private static final float RAIN_PENALTY = 0.2f;

    /**
     * Distance-to-signal (0.0 to 1.0) with 75-block bar steps. Effective range is capped at 375 so bars always drop to 0 at 375 blocks.
     */
    private static float distanceToSignal(double distance, double range) {
        double effectiveRange = Math.min(range, MAX_BAR_RANGE_BLOCKS);
        if (distance > effectiveRange) return 0.0f;
        double t5 = Math.min(BAR_INCREMENT_BLOCKS * 1, effectiveRange);
        double t4 = Math.min(BAR_INCREMENT_BLOCKS * 2, effectiveRange);
        double t3 = Math.min(BAR_INCREMENT_BLOCKS * 3, effectiveRange);
        double t2 = Math.min(BAR_INCREMENT_BLOCKS * 4, effectiveRange);
        if (distance <= t5) {
            return (float) (t5 <= 0 ? 1.0 : (1.0 - (distance / t5) * 0.2));
        }
        if (distance <= t4) {
            double band = t4 - t5;
            return (float) (band <= 0 ? 0.8 : (0.8 - (distance - t5) / band * 0.2));
        }
        if (distance <= t3) {
            double band = t3 - t4;
            return (float) (band <= 0 ? 0.6 : (0.6 - (distance - t4) / band * 0.2));
        }
        if (distance <= t2) {
            double band = t2 - t3;
            return (float) (band <= 0 ? 0.4 : (0.4 - (distance - t3) / band * 0.2));
        }
        double band = effectiveRange - t2;
        return (float) (band <= 0 ? 0.2 : (0.2 - (distance - t2) / band * 0.15)); // 0.2 down to 0.05 at effectiveRange
    }
    
    /**
     * Calculate signal strength (0.0 to 1.0) based on distance to tower.
     * 5/5 within 75 blocks, 4/5 at 150, 3/5 at 225, 2/5 at 300, 1/5 at range (e.g. 375), 0 beyond.
     */
    public static float calculateDistanceStrength(RadioTower tower, Vec3 playerPos) {
        double distance = tower.getDistanceTo(playerPos);
        double range = tower.getStation().getBroadcastRange();
        float strength = distanceToSignal(distance, range);
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
    
    /** When player is within this many blocks of the tower, skip LOS/weather so we get full 5/5 bars. Matches BAR_INCREMENT (75) so signal doesn't drop until 75 blocks out. */
    private static final double CLOSE_RANGE_BLOCKS = BAR_INCREMENT_BLOCKS; // 75

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
     * Uses same 75-block bar steps: 5/5 within 75, 4/5 at 150, etc.
     */
    public static float getFinalSignalStrengthFromPosition(Level level, Vec3 towerPos, RadioStation station, Vec3 playerPos) {
        if (station == null) return 0.0f;
        double distance = towerPos.distanceTo(playerPos);
        int range = station.getBroadcastRange();
        int effectiveRange = Math.min(range, MAX_BAR_RANGE_BLOCKS);
        if (distance > effectiveRange) return 0.0f;
        float distanceStrength = distanceToSignal(distance, range);
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
