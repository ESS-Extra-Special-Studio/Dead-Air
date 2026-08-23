package uk.co.extraspecialstudio.dead_air.radio;

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
    /** When non-null/non-empty, this station plays an HTTP MP3 stream on the client instead of Minecraft SoundEvents. */
    private final String internetStreamUrl;
    
    public RadioStation(ResourceLocation id, String name, StationType type, String genre,
                       float frequency, int broadcastRange, int minTowerSpacing) {
        this(id, name, type, genre, frequency, broadcastRange, minTowerSpacing, null);
    }

    public RadioStation(ResourceLocation id, String name, StationType type, String genre,
                       float frequency, int broadcastRange, int minTowerSpacing, String internetStreamUrl) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.genre = genre;
        this.frequency = frequency;
        this.broadcastRange = broadcastRange;
        this.minTowerSpacing = minTowerSpacing;
        this.internetStreamUrl = (internetStreamUrl == null || internetStreamUrl.isEmpty()) ? null : internetStreamUrl;
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

    public String getInternetStreamUrl() {
        return internetStreamUrl;
    }

    public boolean isInternetStream() {
        return internetStreamUrl != null;
    }
    
    public enum StationType {
        EMERGENCY_BROADCAST,
        MUSIC,
        LORE,
        CORRUPTED
    }
}
