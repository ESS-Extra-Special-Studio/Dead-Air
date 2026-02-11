package uk.creatopia.unbound.dead_air.music;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.ForgeRegistries;
import uk.creatopia.unbound.dead_air.Dead_air;
import uk.creatopia.unbound.dead_air.radio.RadioStation;
import uk.creatopia.unbound.dead_air.radio.StationRegistry;

import java.util.*;

/**
 * Manages music and lore tracks for radio stations.
 * Each station has a pre-made playlist based on original design (Bedrock, Zombiecraft, Medieval FM, etc.).
 * Custom tracks can be added later via the API or data.
 */
@SuppressWarnings("null")
public class MusicStationManager {
    private static final Map<ResourceLocation, List<ResourceLocation>> STATION_TRACKS = new HashMap<>();
    private static final Map<ResourceLocation, Integer> CURRENT_TRACK_INDEX = new HashMap<>();
    /** Global fallback so every station has at least one playable track (never empty). */
    private static List<ResourceLocation> FALLBACK_TRACKS = new ArrayList<>();

    /**
     * Initialize music tracks for stations.
     */
    public static void initialize() {
        // Clear old tracks and track indices from previous sessions
        STATION_TRACKS.clear();
        CURRENT_TRACK_INDEX.clear();
        
        // Bedrock Radio - Vanilla Minecraft music tracks
        List<ResourceLocation> bedrockTracks = new ArrayList<>();
        scanForVanillaMusic(bedrockTracks);
        STATION_TRACKS.put(StationRegistry.BEDROCK_RADIO_ID, bedrockTracks);
        
        // Zombiecraft Radio - Zombiecraft mod music
        List<ResourceLocation> zombiecraftTracks = new ArrayList<>();
        scanForZombiecraftMusic(zombiecraftTracks);
        STATION_TRACKS.put(StationRegistry.ZOMBIECRAFT_RADIO_ID, zombiecraftTracks);
        
        // Medieval FM - Medieval themed music
        List<ResourceLocation> medievalTracks = new ArrayList<>();
        scanForMedievalMusic(medievalTracks);
        STATION_TRACKS.put(StationRegistry.MEDIEVAL_FM_ID, medievalTracks);
        
        // Jukebox FM - Jukebox/music disc tracks
        List<ResourceLocation> jukeboxTracks = new ArrayList<>();
        scanForJukeboxMusic(jukeboxTracks);
        STATION_TRACKS.put(StationRegistry.JUKEBOX_FM_ID, jukeboxTracks);
        
        // Remix Radio - Mix of all music tracks (ONLY station that should share tracks)
        // Create deep copies to ensure tracks are unique instances
        Set<ResourceLocation> remixTracksSet = new LinkedHashSet<>();
        remixTracksSet.addAll(bedrockTracks);
        remixTracksSet.addAll(zombiecraftTracks);
        remixTracksSet.addAll(medievalTracks);
        remixTracksSet.addAll(jukeboxTracks);
        // Final safety check: remove any ambient sounds that may have been added
        remixTracksSet.removeIf(MusicStationManager::isAmbientSound);
        // Remove duplicates
        List<ResourceLocation> remixTracks = new ArrayList<>(remixTracksSet);
        STATION_TRACKS.put(StationRegistry.REMIX_RADIO_ID, remixTracks);
        
        if (uk.creatopia.unbound.dead_air.Config.autoDiscoverModMusic) {
            scanForModMusic();
        }
        
        // Build global fallback from jukebox (music discs) so getTracksForStation never returns empty
        List<ResourceLocation> jukebox = STATION_TRACKS.get(StationRegistry.JUKEBOX_FM_ID);
        if (jukebox != null && !jukebox.isEmpty()) {
            FALLBACK_TRACKS = new ArrayList<>(jukebox);
        } else {
            FALLBACK_TRACKS = new ArrayList<>();
            for (SoundEvent ev : ForgeRegistries.SOUND_EVENTS.getValues()) {
                ResourceLocation id = ForgeRegistries.SOUND_EVENTS.getKey(ev);
                if (id != null && id.getPath().startsWith("music_disc")) {
                    FALLBACK_TRACKS.add(id);
                }
            }
            if (FALLBACK_TRACKS.isEmpty()) {
                FALLBACK_TRACKS.add(ResourceLocation.withDefaultNamespace("music_disc.13"));
            }
            Dead_air.LOGGER.info("Built fallback track list: {} tracks", FALLBACK_TRACKS.size());
        }

        // Any station with 0 tracks gets music disc fallback (including dynamic stations)
        for (var entry : new HashMap<>(STATION_TRACKS).entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                STATION_TRACKS.put(entry.getKey(), new ArrayList<>(FALLBACK_TRACKS));
                Dead_air.LOGGER.info("Station {} had no tracks - using {} fallback tracks", entry.getKey(), FALLBACK_TRACKS.size());
            }
        }
    }
    
    /**
     * Scan for vanilla Minecraft music tracks.
     * Only includes actual music tracks (songs), not ambient sounds.
     */
    private static void scanForVanillaMusic(List<ResourceLocation> tracks) {
        for (SoundEvent soundEvent : ForgeRegistries.SOUND_EVENTS.getValues()) {
            ResourceLocation id = ForgeRegistries.SOUND_EVENTS.getKey(soundEvent);
            if (id != null && id.getNamespace().equals("minecraft")) {
                String path = id.getPath();
                // Vanilla music tracks (only actual music, not ambient)
                if ((path.startsWith("music.game") || path.startsWith("music.menu") || 
                     path.startsWith("music.creative") || path.startsWith("music.credits") ||
                     path.startsWith("music.dragon") || path.startsWith("music.end") ||
                     path.startsWith("music.nether") || path.startsWith("music.under_water")) &&
                    !isAmbientSound(id)) {
                    tracks.add(id);
                }
            }
        }
        Dead_air.LOGGER.info("Found {} vanilla Minecraft music tracks for Bedrock Radio", tracks.size());
    }
    
    /**
     * Scan for Zombiecraft mod music tracks.
     * Only includes actual music tracks (songs), not ambient sounds.
     */
    private static void scanForZombiecraftMusic(List<ResourceLocation> tracks) {
        for (SoundEvent soundEvent : ForgeRegistries.SOUND_EVENTS.getValues()) {
            ResourceLocation id = ForgeRegistries.SOUND_EVENTS.getKey(soundEvent);
            if (id != null && (id.getNamespace().equals("zombiecraft") || 
                               id.getNamespace().contains("zombie") ||
                               id.getPath().contains("zombie"))) {
                String path = id.getPath().toLowerCase();
                // Only include actual music tracks, exclude ambient sounds
                if (path.contains("music") && !path.contains("ambient")) {
                    // Double-check: exclude common ambient sound patterns
                    if (!isAmbientSound(id)) {
                        tracks.add(id);
                    }
                }
            }
        }
        Dead_air.LOGGER.info("Found {} Zombiecraft music tracks for Zombiecraft Radio", tracks.size());
    }
    
    /**
     * Scan for Medieval themed music tracks.
     * Only includes actual music tracks (songs), not ambient sounds.
     */
    private static void scanForMedievalMusic(List<ResourceLocation> tracks) {
        for (SoundEvent soundEvent : ForgeRegistries.SOUND_EVENTS.getValues()) {
            ResourceLocation id = ForgeRegistries.SOUND_EVENTS.getKey(soundEvent);
            if (id != null) {
                String path = id.getPath().toLowerCase();
                // Look for medieval/fantasy themed music
                if (path.contains("medieval") || path.contains("fantasy") || 
                    path.contains("castle") || path.contains("kingdom") ||
                    path.contains("tavern") || path.contains("bard")) {
                    // Only include actual music tracks, exclude ambient sounds
                    if (path.contains("music") && !path.contains("ambient")) {
                        // Double-check: exclude common ambient sound patterns
                        if (!isAmbientSound(id)) {
                            tracks.add(id);
                        }
                    }
                }
            }
        }
        Dead_air.LOGGER.info("Found {} Medieval music tracks for Medieval FM", tracks.size());
    }
    
    /**
     * Scan for Jukebox/music disc tracks.
     */
    private static void scanForJukeboxMusic(List<ResourceLocation> tracks) {
        for (SoundEvent soundEvent : ForgeRegistries.SOUND_EVENTS.getValues()) {
            ResourceLocation id = ForgeRegistries.SOUND_EVENTS.getKey(soundEvent);
            if (id != null) {
                String path = id.getPath().toLowerCase();
                // Look for music disc tracks
                if (path.contains("music_disc") || path.contains("record") || 
                    path.contains("disc") || (id.getNamespace().equals("minecraft") && 
                    path.startsWith("music_disc"))) {
                    tracks.add(id);
                }
            }
        }
        Dead_air.LOGGER.info("Found {} Jukebox tracks for Jukebox FM", tracks.size());
    }
    
    /**
     * Scan for music tracks added by other mods.
     * Creates separate stations for each mod to avoid sharing tracks between stations.
     * Only Remix Radio should have tracks from multiple stations.
     */
    private static void scanForModMusic() {
        Dead_air.LOGGER.info("Scanning for mod music tracks...");
        
        // Track which mods we've already processed to avoid duplicates
        Set<String> processedMods = new HashSet<>();
        processedMods.add("minecraft"); // Already handled by Bedrock Radio
        processedMods.add("zombiecraft"); // Already handled by Zombiecraft Radio
        
        // Group tracks by mod namespace to create separate stations
        Map<String, List<ResourceLocation>> modTracks = new HashMap<>();
        
        for (SoundEvent soundEvent : ForgeRegistries.SOUND_EVENTS.getValues()) {
            ResourceLocation id = ForgeRegistries.SOUND_EVENTS.getKey(soundEvent);
            if (id != null && isMusicTrack(id)) {
                String namespace = id.getNamespace();
                
                // Skip if already processed or is our own mod
                if (processedMods.contains(namespace) || namespace.equals(Dead_air.MODID)) {
                    continue;
                }
                
                // Only add to mod-specific station (not to existing stations)
                modTracks.computeIfAbsent(namespace, k -> new ArrayList<>()).add(id);
            }
        }
        
        // Create separate stations for each mod
        for (Map.Entry<String, List<ResourceLocation>> entry : modTracks.entrySet()) {
            String modNamespace = entry.getKey();
            List<ResourceLocation> tracks = entry.getValue();
            
            if (tracks.isEmpty()) {
                continue;
            }
            
            // Create station for this mod
            ResourceLocation stationId = ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "music_" + modNamespace);
            STATION_TRACKS.put(stationId, tracks);
            
            // Create station if it doesn't exist
            RadioStation existingStation = StationRegistry.getStation(stationId);
            if (existingStation == null) {
                // Check if we've hit the maximum number of stations
                int maxStations = StationRegistry.getMaxStationsInRange();
                if (StationRegistry.getAllStations().size() >= maxStations) {
                    Dead_air.LOGGER.warn("Maximum number of stations ({}) reached. Cannot create new station for {}", 
                        maxStations, modNamespace);
                    continue;
                }
                
                String genre = determineGenre(ResourceLocation.fromNamespaceAndPath(modNamespace, "music"));
                float frequency = generateFrequencyForStation(stationId);
                
                if (!StationRegistry.isFrequencyAvailable(frequency, StationRegistry.getMinFrequencySpacing())) {
                    Dead_air.LOGGER.warn("Frequency {} MHz is not available for station {}. Skipping.", 
                        frequency, stationId);
                    continue;
                }
                
                RadioStation station = new RadioStation(
                    stationId,
                    capitalize(modNamespace) + " Radio",
                    RadioStation.StationType.MUSIC,
                    genre,
                    frequency,
                    1500,
                    400
                );
                
                StationRegistry.registerStation(station);
                Dead_air.LOGGER.info("Created dynamic station {} at {:.1f} MHz with {} tracks", 
                    station.getName(), frequency, tracks.size());
            }
        }
    }
    
    /**
     * Check if a sound event is a music track (actual song, not ambient sound).
     * Only includes actual music tracks that can pass as songs.
     */
    private static boolean isMusicTrack(ResourceLocation id) {
        String path = id.getPath().toLowerCase();
        // Include music and record tracks, but exclude ambient sounds
        if (path.contains("ambient")) {
            return false; // Ambient sounds should not play on radio
        }
        // Only include actual music tracks
        return (path.contains("music") || path.contains("record") || path.contains("disc")) 
               && !isAmbientSound(id);
    }
    
    /**
     * Check if a sound event is an ambient sound (should not play on radio).
     * Ambient sounds should play naturally in the world, not on radio stations.
     * Also excludes short sound effects (clicks, steps, hits, etc.) that are less than 20 seconds.
     */
    private static boolean isAmbientSound(ResourceLocation id) {
        String path = id.getPath().toLowerCase();
        String namespace = id.getNamespace().toLowerCase();
        
        // Common ambient sound patterns to exclude
        if (path.contains("ambient.") || path.contains(".ambient")) {
            return true;
        }
        if (path.contains("ambient_cave") || path.contains("ambient_underwater") ||
            path.contains("ambient_nether") || path.contains("ambient_end")) {
            return true;
        }
        // Minecraft ambient sounds
        if (namespace.equals("minecraft") && 
            (path.startsWith("ambient.") || path.contains("cave") || 
             path.contains("underwater") || path.contains("weather"))) {
            return true;
        }
        // Exclude biome ambient sounds
        if (path.contains("biome") && path.contains("ambient")) {
            return true;
        }
        
        // Exclude short sound effects (clicks, steps, hits, etc.) - these are typically < 1 second
        // Only actual music tracks should be 20+ seconds long
        if (path.contains("click") || path.contains("step") || path.contains("hit") ||
            path.contains("break") || path.contains("place") || path.contains("fall") ||
            path.contains("hurt") || path.contains("death") || path.contains("explode") ||
            path.contains("splash") || path.contains("swim") || path.contains("jump") ||
            path.contains("land") || path.contains("footstep") || path.contains("door") ||
            path.contains("chest") || path.contains("button") || path.contains("lever") ||
            path.contains("note") || path.contains("bell") || path.contains("chime") ||
            path.contains("pop") || path.contains("whoosh") || path.contains("thud") ||
            path.contains("impact") || path.contains("swing") || path.contains("draw") ||
            path.contains("shoot") || path.contains("reload") || path.contains("charge") ||
            path.contains("pickup") || path.contains("drop") || path.contains("equip") ||
            path.contains("unequip") || path.contains("eat") || path.contains("drink") ||
            path.contains("open") || path.contains("close") || path.contains("lock") ||
            path.contains("unlock") || path.contains("activate") || path.contains("deactivate")) {
            return true;
        }
        
        // Exclude UI sounds
        if (path.contains("ui.") || path.contains("gui.") || path.contains("menu.")) {
            return true;
        }
        
        // Exclude block interaction sounds (unless they're music discs)
        if (path.contains("block.") && !path.contains("music_disc") && !path.contains("record")) {
            // Allow through only if it explicitly contains "music" in the path
            if (!path.contains("music")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Determine genre from track ID.
     */
    private static String determineGenre(ResourceLocation trackId) {
        String path = trackId.getPath().toLowerCase();
        
        if (path.contains("medieval") || path.contains("fantasy")) return "Medieval";
        if (path.contains("sci") || path.contains("tech")) return "Sci-Fi";
        if (path.contains("ambient")) return "Ambient";
        if (path.contains("horror") || path.contains("dark")) return "Horror";
        
        return "Ambient";
    }
    
    /**
     * Generate a unique frequency for a station.
     * Ensures minimum spacing between stations to prevent interference.
     */
    private static float generateFrequencyForStation(ResourceLocation stationId) {
        // Generate frequency between 88.0 and 108.0 MHz
        float minFreq = 88.0f;
        float maxFreq = 108.0f;
        float minSpacing = uk.creatopia.unbound.dead_air.radio.StationRegistry.getMinFrequencySpacing();
        
        // Check if we've hit the maximum number of stations
        int maxStations = uk.creatopia.unbound.dead_air.radio.StationRegistry.getMaxStationsInRange();
        if (uk.creatopia.unbound.dead_air.radio.StationRegistry.getAllStations().size() >= maxStations) {
            // Use hash-based frequency but ensure spacing
            int hash = stationId.hashCode();
            float frequency = minFreq + (Math.abs(hash % (int)((maxFreq - minFreq) / minSpacing)) * minSpacing);
            return Math.round(frequency * 10.0f) / 10.0f;
        }
        
        // Try to find an available frequency
        int attempts = 0;
        int maxAttempts = 100;
        while (attempts < maxAttempts) {
            int hash = (stationId.hashCode() + attempts) % 1000;
            float frequency = minFreq + (Math.abs(hash % (int)((maxFreq - minFreq) / minSpacing)) * minSpacing);
            frequency = Math.round(frequency * 10.0f) / 10.0f;
            
            if (uk.creatopia.unbound.dead_air.radio.StationRegistry.isFrequencyAvailable(frequency, minSpacing)) {
                return frequency;
            }
            attempts++;
        }
        
        // Fallback: use hash-based frequency
        int hash = stationId.hashCode();
        float frequency = minFreq + (Math.abs(hash % (int)((maxFreq - minFreq) / minSpacing)) * minSpacing);
        return Math.round(frequency * 10.0f) / 10.0f;
    }
    
    /**
     * Capitalize first letter of string.
     */
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    /**
     * Get next track for a station (shuffle/loop). Uses fallback if station has no tracks.
     */
    public static ResourceLocation getNextTrack(ResourceLocation stationId) {
        List<ResourceLocation> tracks = getTracksForStation(stationId);
        if (tracks.isEmpty()) {
            return null;
        }
        int currentIndex = CURRENT_TRACK_INDEX.getOrDefault(stationId, -1);
        int nextIndex = (currentIndex + 1) % tracks.size();
        CURRENT_TRACK_INDEX.put(stationId, nextIndex);
        return tracks.get(nextIndex);
    }
    
    /**
     * Get random track for a station. Uses fallback if station has no tracks.
     */
    public static ResourceLocation getRandomTrack(ResourceLocation stationId) {
        List<ResourceLocation> tracks = getTracksForStation(stationId);
        if (tracks.isEmpty()) return null;
        Random random = new Random();
        int index = random.nextInt(tracks.size());
        CURRENT_TRACK_INDEX.put(stationId, index);
        return tracks.get(index);
    }
    
    /**
     * Get all tracks for a station. Never returns null or empty - uses fallback so every station can play.
     */
    public static List<ResourceLocation> getTracksForStation(ResourceLocation stationId) {
        List<ResourceLocation> tracks = STATION_TRACKS.get(stationId);
        if (tracks != null && !tracks.isEmpty()) {
            return tracks;
        }
        if (!FALLBACK_TRACKS.isEmpty()) {
            return new ArrayList<>(FALLBACK_TRACKS);
        }
        // Last resort so dynamic/unknown stations always have one track
        return List.of(ResourceLocation.withDefaultNamespace("music_disc.13"));
    }
    
    /**
     * Add a track to a station (public API for extensibility).
     * 
     * @param stationId The station to add the track to
     * @param trackId The sound event resource location
     * @return true if the track was added, false if it already exists
     */
    public static boolean addTrack(ResourceLocation stationId, ResourceLocation trackId) {
        List<ResourceLocation> tracks = STATION_TRACKS.computeIfAbsent(stationId, k -> new ArrayList<>());
        if (!tracks.contains(trackId)) {
            tracks.add(trackId);
            return true;
        }
        return false;
    }
    
    /**
     * Remove a track from a station.
     * 
     * @param stationId The station to remove the track from
     * @param trackId The track to remove
     * @return true if the track was removed, false if it didn't exist
     */
    public static boolean removeTrack(ResourceLocation stationId, ResourceLocation trackId) {
        List<ResourceLocation> tracks = STATION_TRACKS.get(stationId);
        if (tracks != null) {
            return tracks.remove(trackId);
        }
        return false;
    }
    
    /**
     * Clear all tracks from a station.
     * 
     * @param stationId The station to clear
     */
    public static void clearTracks(ResourceLocation stationId) {
        STATION_TRACKS.remove(stationId);
        CURRENT_TRACK_INDEX.remove(stationId);
    }
}
