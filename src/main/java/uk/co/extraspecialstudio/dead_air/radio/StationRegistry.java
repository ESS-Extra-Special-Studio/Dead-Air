package uk.co.extraspecialstudio.dead_air.radio;

import net.minecraft.resources.ResourceLocation;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;

import java.util.*;

/**
 * Registry for all radio stations in the game.
 */
@SuppressWarnings("null")
public class StationRegistry {
    private static final Map<ResourceLocation, RadioStation> STATIONS = new HashMap<>();
    private static final List<RadioStation> STATION_LIST = new ArrayList<>();
    
    // Default stations
    public static final ResourceLocation EMERGENCY_BROADCAST_ID = ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "emergency_broadcast");
    public static final ResourceLocation BEDROCK_RADIO_ID = ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "bedrock_radio");
    public static final ResourceLocation ZOMBIECRAFT_RADIO_ID = ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "zombiecraft_radio");
    public static final ResourceLocation MEDIEVAL_FM_ID = ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "medieval_fm");
    public static final ResourceLocation JUKEBOX_FM_ID = ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "jukebox_fm");
    public static final ResourceLocation REMIX_RADIO_ID = ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "remix_radio");
    /** Custom Creatopia tracks only; same tracks also play on Bedrock Radio and Remix Radio. */
    public static final ResourceLocation CREATOPIA_RADIO_ID = ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "creatopia_radio");
    /** Live 24/7 MP3 stream from music.creatopia.uk (CR1). */
    public static final ResourceLocation INTERNET_CR1_ID = ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "internet_cr1");
    /** Live 24/7 MP3 stream from music.creatopia.uk (Ambiance FM). */
    public static final ResourceLocation INTERNET_AMBIANCE_FM_ID = ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "internet_ambiance_fm");

    public static void registerDefaultStations() {
        // Clear any old dynamic stations from previous sessions
        // Keep only the default stations (Emergency Broadcast, Bedrock Radio, etc.)
        clearDynamicStations();
        
        // Fire event to allow other mods to register stations
        uk.co.extraspecialstudio.dead_air.events.StationRegistrationEvent event = 
            new uk.co.extraspecialstudio.dead_air.events.StationRegistrationEvent();
        
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        
        // Register stations from event
        for (RadioStation station : event.getStationsToRegister()) {
            registerStation(station);
        }
        
        int emergencyRange = Config.emergencyBroadcastRange > 0 ? Config.emergencyBroadcastRange : 2000;
        int musicRange = Config.musicStationRange > 0 ? Config.musicStationRange : 375;
        int minSpacing = Config.minTowerSpacing > 0 ? Config.minTowerSpacing : 400;

        // Emergency Broadcast Station - All towers can broadcast this
        registerStation(new RadioStation(
            EMERGENCY_BROADCAST_ID,
            "Emergency Broadcast",
            RadioStation.StationType.EMERGENCY_BROADCAST,
            "Emergency",
            88.5f,
            emergencyRange,
            minSpacing
        ));

        // Bedrock Radio - Vanilla Minecraft music (common for standard towers)
        registerStation(new RadioStation(
            BEDROCK_RADIO_ID,
            "Bedrock Radio",
            RadioStation.StationType.MUSIC,
            "Ambient",
            95.5f,
            musicRange,
            minSpacing
        ));

        registerStation(new RadioStation(
            ZOMBIECRAFT_RADIO_ID,
            "Zombiecraft Radio",
            RadioStation.StationType.MUSIC,
            "Horror",
            98.3f,
            musicRange,
            minSpacing
        ));

        registerStation(new RadioStation(
            MEDIEVAL_FM_ID,
            "Medieval FM",
            RadioStation.StationType.MUSIC,
            "Medieval",
            102.7f,
            musicRange,
            minSpacing
        ));

        // Jukebox FM - Jukebox tracks
        registerStation(new RadioStation(
            JUKEBOX_FM_ID,
            "Jukebox FM",
            RadioStation.StationType.MUSIC,
            "Variety",
            105.9f,
            musicRange,
            minSpacing
        ));

        // Remix Radio - Mix of all music
        registerStation(new RadioStation(
            REMIX_RADIO_ID,
            "Remix Radio",
            RadioStation.StationType.MUSIC,
            "Mix",
            99.1f,
            musicRange,
            minSpacing
        ));

        // Creatopia Radio - Custom Dead Air tracks only (same tracks also in Bedrock and Remix)
        registerStation(new RadioStation(
            CREATOPIA_RADIO_ID,
            "Creatopia Radio",
            RadioStation.StationType.MUSIC,
            "Creatopia",
            97.5f,
            musicRange,
            minSpacing
        ));

        registerStation(new RadioStation(
            INTERNET_CR1_ID,
            "Creatopia Radio 1 (CR1)",
            RadioStation.StationType.MUSIC,
            "Live stream",
            108.1f,
            musicRange,
            minSpacing,
            "https://music.creatopia.uk/listen/cr1/mp3/radio.mp3"
        ));

        registerStation(new RadioStation(
            INTERNET_AMBIANCE_FM_ID,
            "Ambiance FM",
            RadioStation.StationType.MUSIC,
            "Live stream",
            108.5f,
            musicRange,
            minSpacing,
            "https://music.creatopia.uk/listen/ambiance-fm/radio.mp3"
        ));
    }
    
    public static void registerStation(RadioStation station) {
        STATIONS.put(station.getId(), station);
        STATION_LIST.add(station);
    }

    public static void replaceStation(RadioStation station) {
        STATIONS.put(station.getId(), station);
        for (int i = 0; i < STATION_LIST.size(); i++) {
            if (STATION_LIST.get(i).getId().equals(station.getId())) {
                STATION_LIST.set(i, station);
                return;
            }
        }
        STATION_LIST.add(station);
    }
    
    public static RadioStation getStation(ResourceLocation id) {
        return STATIONS.get(id);
    }
    
    public static Collection<RadioStation> getAllStations() {
        return Collections.unmodifiableCollection(STATION_LIST);
    }
    
    public static List<RadioStation> getStationsByType(RadioStation.StationType type) {
        return STATION_LIST.stream()
            .filter(s -> s.getType() == type)
            .toList();
    }
    
    /**
     * Find a station at a specific frequency (exact match within 0.1 MHz).
     */
    public static RadioStation findStationByFrequency(float frequency) {
        return STATION_LIST.stream()
            .filter(s -> Math.abs(s.getFrequency() - frequency) < 0.1f)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Find the nearest station to a given frequency.
     * Returns the station closest to the frequency, or null if no stations exist.
     */
    public static RadioStation findNearestStationByFrequency(float frequency) {
        if (STATION_LIST.isEmpty()) {
            return null;
        }
        
        RadioStation nearest = null;
        float nearestDistance = Float.MAX_VALUE;
        
        for (RadioStation station : STATION_LIST) {
            float distance = Math.abs(station.getFrequency() - frequency);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = station;
            }
        }
        
        return nearest;
    }
    
    /**
     * Check if a frequency is available (not too close to existing stations).
     * Minimum spacing is 0.2 MHz to prevent interference.
     */
    public static boolean isFrequencyAvailable(float frequency, float minSpacing) {
        return STATION_LIST.stream()
            .noneMatch(s -> Math.abs(s.getFrequency() - frequency) < minSpacing);
    }
    
    /**
     * Get the minimum frequency spacing required between stations.
     */
    public static float getMinFrequencySpacing() {
        return 0.2f; // 0.2 MHz minimum spacing (like real FM radio)
    }
    
    /**
     * Get the maximum number of stations that can fit in the frequency range.
     */
    public static int getMaxStationsInRange() {
        float range = 108.0f - 88.0f; // 20 MHz range
        float minSpacing = getMinFrequencySpacing();
        return (int) (range / minSpacing); // ~100 stations max
    }
    
    /**
     * Clear all dynamic stations (stations created from mod music scanning).
     * Keeps only the default stations (Emergency Broadcast, Bedrock Radio, etc.).
     * This prevents old stations from previous sessions from persisting.
     */
    private static void clearDynamicStations() {
        // List of default station IDs that should always be kept
        Set<ResourceLocation> defaultStationIds = Set.of(
            EMERGENCY_BROADCAST_ID,
            BEDROCK_RADIO_ID,
            JUKEBOX_FM_ID,
            REMIX_RADIO_ID,
            CREATOPIA_RADIO_ID,
            INTERNET_CR1_ID,
            INTERNET_AMBIANCE_FM_ID
        );
        
        // Remove all stations that are not in the default list
        STATION_LIST.removeIf(station -> !defaultStationIds.contains(station.getId()));
        STATIONS.entrySet().removeIf(entry -> !defaultStationIds.contains(entry.getKey()));
        
    }
}
