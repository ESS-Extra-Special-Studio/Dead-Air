package uk.creatopia.unbound.dead_air.api;

import net.minecraft.resources.ResourceLocation;
import uk.creatopia.unbound.dead_air.music.MusicStationManager;
import uk.creatopia.unbound.dead_air.radio.RadioStation;
import uk.creatopia.unbound.dead_air.radio.StationRegistry;

import java.util.List;

/**
 * Public API for Dead Air mod.
 * Allows other mods and data packs to register custom stations and music tracks.
 */
@SuppressWarnings("null")
public class DeadAirAPI {
    
    /**
     * Register a custom radio station.
     * Can be called from mod initialization or data pack loading.
     * 
     * @param station The radio station to register
     * @return true if registration was successful, false if a station with this ID already exists
     */
    public static boolean registerStation(RadioStation station) {
        if (StationRegistry.getStation(station.getId()) != null) {
            return false; // Station already exists
        }
        StationRegistry.registerStation(station);
        return true;
    }
    
    /**
     * Register a music track to an existing station.
     * If the station doesn't exist, it will be created automatically.
     * 
     * @param stationId The ID of the station to add the track to
     * @param trackId The resource location of the sound event to play
     * @return true if the track was added successfully
     */
    public static boolean addTrackToStation(ResourceLocation stationId, ResourceLocation trackId) {
        RadioStation station = StationRegistry.getStation(stationId);
        
        // If station doesn't exist, create a default one
        if (station == null) {
            station = new RadioStation(
                stationId,
                stationId.getPath().replace("_", " "), // Auto-generate name from ID
                RadioStation.StationType.MUSIC,
                "Custom",
                generateFrequency(stationId),
                1500, // Default range
                400    // Default spacing
            );
            StationRegistry.registerStation(station);
        }
        
        // Add track to the station
        List<ResourceLocation> tracks = MusicStationManager.getTracksForStation(stationId);
        if (!tracks.contains(trackId)) {
            // Access the internal track map (we'll need to make this public or add a method)
            // For now, we'll need to add a method to MusicStationManager
            return MusicStationManager.addTrack(stationId, trackId);
        }
        
        return true;
    }
    
    /**
     * Create and register a custom music station with tracks.
     * 
     * @param stationId Unique ID for the station
     * @param name Display name of the station
     * @param genre Genre/category of the station
     * @param frequency Radio frequency (88.0 - 108.0 MHz)
     * @param tracks List of sound event resource locations to play
     * @return The created station, or null if a station with this ID already exists
     */
    public static RadioStation createMusicStation(ResourceLocation stationId, String name, 
                                                  String genre, float frequency, 
                                                  List<ResourceLocation> tracks) {
        // Check if station already exists
        if (StationRegistry.getStation(stationId) != null) {
            return null;
        }
        
        // Create station
        RadioStation station = new RadioStation(
            stationId,
            name,
            RadioStation.StationType.MUSIC,
            genre,
            frequency,
            1500, // Default range
            400    // Default spacing
        );
        
        // Register station
        StationRegistry.registerStation(station);
        
        // Add tracks
        for (ResourceLocation trackId : tracks) {
            MusicStationManager.addTrack(stationId, trackId);
        }
        
        return station;
    }
    
    /**
     * Create and register a custom lore station.
     * 
     * @param stationId Unique ID for the station
     * @param name Display name of the station
     * @param genre Genre/category of the station
     * @param frequency Radio frequency (88.0 - 108.0 MHz)
     * @param tracks List of sound event resource locations (story recordings)
     * @return The created station, or null if a station with this ID already exists
     */
    public static RadioStation createLoreStation(ResourceLocation stationId, String name,
                                                String genre, float frequency,
                                                List<ResourceLocation> tracks) {
        if (StationRegistry.getStation(stationId) != null) {
            return null;
        }
        
        RadioStation station = new RadioStation(
            stationId,
            name,
            RadioStation.StationType.LORE,
            genre,
            frequency,
            1200, // Slightly shorter range for lore stations
            350    // Closer spacing
        );
        
        StationRegistry.registerStation(station);
        
        for (ResourceLocation trackId : tracks) {
            MusicStationManager.addTrack(stationId, trackId);
        }
        
        return station;
    }
    
    /**
     * Get all registered stations.
     * 
     * @return Unmodifiable collection of all stations
     */
    public static java.util.Collection<RadioStation> getAllStations() {
        return StationRegistry.getAllStations();
    }
    
    /**
     * Get a station by ID.
     * 
     * @param stationId The station ID
     * @return The station, or null if not found
     */
    public static RadioStation getStation(ResourceLocation stationId) {
        return StationRegistry.getStation(stationId);
    }
    
    /**
     * Get all tracks for a station.
     * 
     * @param stationId The station ID
     * @return List of track resource locations
     */
    public static List<ResourceLocation> getStationTracks(ResourceLocation stationId) {
        return MusicStationManager.getTracksForStation(stationId);
    }
    
    /**
     * Generate a unique frequency for a station ID.
     */
    private static float generateFrequency(ResourceLocation stationId) {
        int hash = stationId.hashCode();
        float frequency = 88.0f + (Math.abs(hash % 200) / 10.0f);
        return Math.round(frequency * 10.0f) / 10.0f;
    }
}
