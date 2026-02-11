package uk.creatopia.unbound.dead_air.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import uk.creatopia.unbound.dead_air.tower.ApocalypseTowerType;

/**
 * Represents a radio tower that broadcasts a station.
 * Can be either an official Apocalypse Structures tower or a player-built tower.
 */
@SuppressWarnings("null")
public class RadioTower {
    private final BlockPos position;
    private final ResourceKey<Level> dimension;
    private final RadioStation station;
    private final ApocalypseTowerType towerType;
    private final BlockPos radioPanelPos; // Position of associated Radio Panel (core unit)
    private final boolean isOfficial; // True for official towers, false for player-built
    /** Volatile so client thread sees server updates in singleplayer (shared tower list). */
    private volatile boolean isPowered;
    private long lastPowerCheck;
    private static final long POWER_CHECK_INTERVAL = 20; // Check every second
    
    public RadioTower(BlockPos position, ResourceKey<Level> dimension, RadioStation station, 
                     ApocalypseTowerType towerType, BlockPos radioPanelPos, boolean isOfficial) {
        this.position = position;
        this.dimension = dimension;
        this.station = station;
        this.towerType = towerType;
        this.radioPanelPos = radioPanelPos;
        this.isOfficial = isOfficial;
        
        // Set initial power state based on tower type
        // Player-built towers always start off (need activation)
        if (!isOfficial) {
            this.isPowered = false; // Player-built towers need activation
        } else {
            switch (towerType) {
                case STANDARD:
                    this.isPowered = true; // Standard towers always start on
                    break;
                case FENCED:
                    // 20% chance to start powered (deterministic based on position)
                    this.isPowered = new java.util.Random(position.asLong()).nextDouble() < 0.20;
                    break;
                case OVERRUN:
                    this.isPowered = false; // Overrun towers always start off
                    break;
                default:
                    this.isPowered = false;
            }
        }
        
        this.lastPowerCheck = 0;
    }
    
    /**
     * Legacy constructor for official towers (backwards compatibility).
     */
    public RadioTower(BlockPos position, ResourceKey<Level> dimension, RadioStation station, 
                     ApocalypseTowerType towerType, BlockPos radioPanelPos) {
        this(position, dimension, station, towerType, radioPanelPos, true);
    }
    
    /**
     * Check if this is an official tower (from Apocalypse Structures) or player-built.
     */
    public boolean isOfficial() {
        return isOfficial;
    }
    
    /**
     * Get the type of Apocalypse Structures tower.
     */
    public ApocalypseTowerType getTowerType() {
        return towerType;
    }
    
    /**
     * Get the position of the associated Radio Panel.
     */
    public BlockPos getRadioPanelPos() {
        return radioPanelPos;
    }
    
    public BlockPos getPosition() {
        return position;
    }
    
    public ResourceKey<Level> getDimension() {
        return dimension;
    }
    
    public RadioStation getStation() {
        return station;
    }
    
    public boolean isPowered() {
        return isPowered;
    }
    
    public void setPowered(boolean powered) {
        this.isPowered = powered;
    }
    
    public Vec3 getPositionVec() {
        return Vec3.atCenterOf(position);
    }
    
    public boolean shouldCheckPower(long currentTick) {
        return currentTick - lastPowerCheck >= POWER_CHECK_INTERVAL;
    }
    
    public void updatePowerCheck(long currentTick) {
        this.lastPowerCheck = currentTick;
    }
    
    public double getDistanceTo(Vec3 pos) {
        return getPositionVec().distanceTo(pos);
    }
    
    public boolean isInRange(Vec3 pos) {
        return getDistanceTo(pos) <= station.getBroadcastRange();
    }
}
