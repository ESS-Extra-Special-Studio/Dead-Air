package uk.creatopia.unbound.dead_air.radio;

import net.minecraft.resources.ResourceLocation;

/**
 * Represents a radio station that can be broadcast from towers.
 */
@SuppressWarnings("null")
public class RadioStation {
    private final ResourceLocation id;
    private final String name;
    private final StationType type;
    private final String genre;
    private final float frequency;
    private final int broadcastRange;
    private final int minTowerSpacing;
    
    public RadioStation(ResourceLocation id, String name, StationType type, String genre, 
                       float frequency, int broadcastRange, int minTowerSpacing) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.genre = genre;
        this.frequency = frequency;
        this.broadcastRange = broadcastRange;
        this.minTowerSpacing = minTowerSpacing;
    }
    
    public ResourceLocation getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public StationType getType() {
        return type;
    }
    
    public String getGenre() {
        return genre;
    }
    
    public float getFrequency() {
        return frequency;
    }
    
    public int getBroadcastRange() {
        return broadcastRange;
    }
    
    public int getMinTowerSpacing() {
        return minTowerSpacing;
    }
    
    public enum StationType {
        EMERGENCY_BROADCAST,
        MUSIC,
        LORE,
        CORRUPTED
    }
}
