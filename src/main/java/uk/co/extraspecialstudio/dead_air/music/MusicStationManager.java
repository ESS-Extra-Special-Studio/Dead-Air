package uk.co.extraspecialstudio.dead_air.music;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;
import uk.co.extraspecialstudio.dead_air.station.StationUnlockManager;

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
    /** Sound ids that belong to Jukebox FM (used to strip disc tracks from Remix until the player unlocks Jukebox FM). */
    private static Set<ResourceLocation> JUKEBOX_SOUND_IDS = Set.of();
    /** When we last "started" the virtual track for each station (for background playlist advancement when not tuned). */
    private static final Map<ResourceLocation, Long> LAST_VIRTUAL_TRACK_START_MS = new HashMap<>();
    /** Global fallback so every station has at least one playable track (never empty). */
    private static List<ResourceLocation> FALLBACK_TRACKS = new ArrayList<>();
    private static final int MIN_DYNAMIC_TRACKS = 5;
    private static final Set<String> DYNAMIC_NAMESPACE_BLOCKLIST = Set.of(
        "matmos_tct",
        "tctcore"
    );

    /**
     * Initialize music tracks for stations.
     */
    public static void initialize() {
        // Clear old tracks and track indices from previous sessions
        STATION_TRACKS.clear();
        CURRENT_TRACK_INDEX.clear();
        LAST_VIRTUAL_TRACK_START_MS.clear();

        // Creatopia Radio - Custom dead_air music only (same tracks also go to Bedrock and Remix)
        List<ResourceLocation> creatopiaTracks = new ArrayList<>();
        scanForCreatopiaMusic(creatopiaTracks);
        STATION_TRACKS.put(StationRegistry.CREATOPIA_RADIO_ID, creatopiaTracks);

        // Bedrock Radio - Vanilla Minecraft music + Creatopia tracks
        List<ResourceLocation> bedrockTracks = new ArrayList<>();
        scanForVanillaMusic(bedrockTracks);
        bedrockTracks.addAll(creatopiaTracks);
        STATION_TRACKS.put(StationRegistry.BEDROCK_RADIO_ID, bedrockTracks);
        
        // Jukebox FM - Jukebox/music disc tracks
        List<ResourceLocation> jukeboxTracks = new ArrayList<>();
        scanForJukeboxMusic(jukeboxTracks);
        STATION_TRACKS.put(StationRegistry.JUKEBOX_FM_ID, jukeboxTracks);

        // Internet streams: single virtual "track" id for overlay / events (playback uses InternetStreamManager, not SoundEvents)
        STATION_TRACKS.put(StationRegistry.INTERNET_CR1_ID, List.of(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "stream/internet_cr1")));
        STATION_TRACKS.put(StationRegistry.INTERNET_AMBIANCE_FM_ID, List.of(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "stream/internet_ambiance_fm")));

        registerCustomStationsFromFolders();

        if (uk.co.extraspecialstudio.dead_air.Config.autoDiscoverModMusic) {
            scanForModMusic();
        }

        // Remix Radio - all approved non-internet tracks from every station playlist.
        Set<ResourceLocation> remixTracksSet = new LinkedHashSet<>();
        for (var entry : STATION_TRACKS.entrySet()) {
            ResourceLocation stationId = entry.getKey();
            if (StationRegistry.REMIX_RADIO_ID.equals(stationId)) continue;
            if (StationRegistry.INTERNET_CR1_ID.equals(stationId) || StationRegistry.INTERNET_AMBIANCE_FM_ID.equals(stationId)) continue;
            List<ResourceLocation> list = entry.getValue();
            if (list != null) remixTracksSet.addAll(list);
        }
        remixTracksSet.removeIf(MusicStationManager::isAmbientSound);
        STATION_TRACKS.put(StationRegistry.REMIX_RADIO_ID, new ArrayList<>(remixTracksSet));
        
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
        }

        // Any station with 0 tracks gets music disc fallback (including dynamic stations)
        for (var entry : new HashMap<>(STATION_TRACKS).entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                STATION_TRACKS.put(entry.getKey(), new ArrayList<>(FALLBACK_TRACKS));
            }
        }

        List<ResourceLocation> jukeboxSnap = STATION_TRACKS.get(StationRegistry.JUKEBOX_FM_ID);
        JUKEBOX_SOUND_IDS = jukeboxSnap != null && !jukeboxSnap.isEmpty()
            ? Set.copyOf(new HashSet<>(jukeboxSnap))
            : Set.of();
    }

    /**
     * Get the track ID currently at the "current" index (for "now playing" display).
     * Call after getNextTrack was used to create a sound to get the track that is now playing.
     */
    public static ResourceLocation getCurrentTrackId(ResourceLocation stationId) {
        return getCurrentTrackId(stationId, null);
    }

    /**
     * Same as {@link #getCurrentTrackId(ResourceLocation)} but uses per-player Remix / Jukebox gating when {@code player} is non-null.
     */
    public static ResourceLocation getCurrentTrackId(ResourceLocation stationId, Player player) {
        List<ResourceLocation> tracks = getPlaybackTracks(stationId, player);
        if (tracks.isEmpty()) return null;
        int idx = CURRENT_TRACK_INDEX.getOrDefault(stationId, 0);
        return tracks.get(idx % tracks.size());
    }

    /**
     * Tracks used for playback and virtual playlist indices for this station and player.
     * Remix omits Jukebox disc sounds until Jukebox FM is unlocked; Jukebox FM returns an empty list until unlocked.
     */
    public static List<ResourceLocation> getPlaybackTracks(ResourceLocation stationId, Player player) {
        List<ResourceLocation> raw = STATION_TRACKS.get(stationId);
        if (raw == null || raw.isEmpty()) {
            return getTracksForStation(stationId);
        }
        RadioStation jukeboxStation = StationRegistry.getStation(StationRegistry.JUKEBOX_FM_ID);
        boolean jukeboxUnlocked = jukeboxStation != null && player != null && StationUnlockManager.hasUnlocked(player, jukeboxStation);

        if (StationRegistry.REMIX_RADIO_ID.equals(stationId)) {
            if (!jukeboxUnlocked && !JUKEBOX_SOUND_IDS.isEmpty()) {
                List<ResourceLocation> filtered = new ArrayList<>();
                for (ResourceLocation id : raw) {
                    if (!JUKEBOX_SOUND_IDS.contains(id)) {
                        filtered.add(id);
                    }
                }
                if (!filtered.isEmpty()) {
                    return filtered;
                }
            }
            return raw;
        }

        if (StationRegistry.JUKEBOX_FM_ID.equals(stationId) && !jukeboxUnlocked) {
            return List.of();
        }

        return raw;
    }

    /**
     * Format a track ID for display (e.g. "music_disc.13" -> "Music Disc 13", "music.custom.forest_ambiance" -> "Forest Ambiance").
     * Registry may use underscores (music_custom_fire_in_the_static); treat same as music.custom.* so display is just the song name.
     */
    public static String formatTrackDisplayName(ResourceLocation trackId) {
        if (trackId == null) return "?";
        String path = trackId.getPath();
        if (path.isEmpty()) return "?";
        if (path.startsWith("stream/internet_cr1")) return "CR1 (live)";
        if (path.startsWith("stream/internet_ambiance_fm")) return "Ambiance FM (live)";
        if (path.startsWith("music disc")) return path.replace("_", " ");
        if (path.startsWith("music.custom.")) {
            String rest = path.substring(13).replace("_", " ");
            return toTitleCase(rest);
        }
        if (path.startsWith("music_custom_")) {
            String rest = path.substring(13).replace("_", " ");
            return toTitleCase(rest);
        }
        if (path.startsWith("music_")) {
            String rest = path.substring(6).replace("_", " ");
            return toTitleCase(rest);
        }
        if (path.startsWith("music.")) return toTitleCase(path.substring(6).replace("_", " "));
        if (path.contains(".")) path = path.substring(path.lastIndexOf('.') + 1).replace("_", " ");
        return toTitleCase(path);
    }

    private static String toTitleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (char c : s.toCharArray()) {
            if (cap && Character.isLetter(c)) {
                sb.append(Character.toUpperCase(c));
                cap = false;
            } else {
                sb.append(Character.toLowerCase(c));
                if (c == ' ' || c == '-') cap = true;
            }
        }
        return sb.toString();
    }
    
    /**
     * Scan for vanilla Minecraft music tracks.
     * Only includes actual music tracks (songs), not ambient sounds.
     */
    /** Excluded vanilla track: removed from all playlists by request. */
    private static final String EXCLUDED_VANILLA_TRACK = "music.nether.warped_forest";

    private static void scanForVanillaMusic(List<ResourceLocation> tracks) {
        for (SoundEvent soundEvent : ForgeRegistries.SOUND_EVENTS.getValues()) {
            ResourceLocation id = ForgeRegistries.SOUND_EVENTS.getKey(soundEvent);
            if (id != null && id.getNamespace().equals("minecraft")) {
                String path = id.getPath();
                if (path.equals(EXCLUDED_VANILLA_TRACK)) continue;
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
    }

    /**
     * Scan for custom Dead Air (Creatopia) music tracks.
     * Any SoundEvent with namespace dead_air and path containing "music" is included.
     * Register your tracks in DeadAirSounds and add .ogg + sounds.json entries for them to appear here.
     */
    private static void scanForCreatopiaMusic(List<ResourceLocation> tracks) {
        for (SoundEvent soundEvent : ForgeRegistries.SOUND_EVENTS.getValues()) {
            ResourceLocation id = ForgeRegistries.SOUND_EVENTS.getKey(soundEvent);
            if (id != null && id.getNamespace().equals(Dead_air.MODID)) {
                String path = id.getPath().toLowerCase();
                if (path.startsWith("music.custom_stations.")) {
                    continue;
                }
                if (path.contains("music") && !isAmbientSound(id)) {
                    tracks.add(id);
                }
            }
        }
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
    }
    
    /**
     * Scan for music tracks added by other mods.
     * Creates separate stations for each mod to avoid sharing tracks between stations.
     * Only Remix Radio should have tracks from multiple stations.
     */
    private static void scanForModMusic() {
        // Track which mods we've already processed to avoid duplicates
        Set<String> processedMods = new HashSet<>();
        processedMods.add("minecraft"); // Already handled by Bedrock Radio
        
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
                if (DYNAMIC_NAMESPACE_BLOCKLIST.contains(namespace.toLowerCase(Locale.ROOT))) {
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
            String displayNameOverride = getDynamicStationDisplayNameOverride(modNamespace, tracks);
            String displayName = getDynamicStationDisplayName(modNamespace, tracks);
            int minimumTracks = (displayNameOverride != null) ? 1 : MIN_DYNAMIC_TRACKS;

            // Require enough tracks to form a useful station playlist (except explicit overrides).
            if (tracks.size() < minimumTracks) {
                continue;
            }
            
            // Create station for this mod with only that mod's tracks.
            ResourceLocation stationId = ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "music_" + modNamespace);
            STATION_TRACKS.put(stationId, new ArrayList<>(tracks));
            
            // Create or refresh station for this mod with only that mod's tracks.
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
                
                int range = Config.musicStationRange > 0 ? Config.musicStationRange : 375;
                int spacing = Config.minTowerSpacing > 0 ? Config.minTowerSpacing : 400;
                RadioStation station = new RadioStation(
                    stationId,
                    displayName,
                    RadioStation.StationType.MUSIC,
                    genre,
                    frequency,
                    range,
                    spacing
                );
                
                StationRegistry.registerStation(station);
            } else if (!displayName.equals(existingStation.getName())) {
                StationRegistry.replaceStation(new RadioStation(
                    existingStation.getId(),
                    displayName,
                    existingStation.getType(),
                    existingStation.getGenre(),
                    existingStation.getFrequency(),
                    existingStation.getBroadcastRange(),
                    existingStation.getMinTowerSpacing(),
                    existingStation.getInternetStreamUrl()
                ));
            }
        }
    }

    private static String getDynamicStationDisplayNameOverride(String modNamespace, List<ResourceLocation> tracks) {
        String ns = modNamespace == null ? "" : modNamespace.toLowerCase(Locale.ROOT);

        if (ns.equals("dead_air_wayfarer_radio")) return "Wayfarer Radio";
        if (ns.equals("dead_air_frontline_fm")) return "Frontline FM";
        if (ns.equals("dead_air_after_hours_fm")) return "After Hours FM";
        if (ns.equals("dead_air_block_beats_fm")) return "Block Beats FM";
        if (ns.equals("dead_air_broken_youth_radio")) return "Broken Youth Radio";
        if (ns.equals("dead_air_iron_rain_fm")) return "Iron Rain FM";
        if (ns.equals("dead_air_zero_gravity")) return "Zero Gravity";
        if (ns.equals("dead_air_pop_paradise_radio")) return "Pop Paradise Radio";
        if (ns.equals("dead_air_frequency_x")) return "Frequency X";
        if (ns.equals("dead_air_parallel_horizons_fm")) return "Parallel Horizons FM";

        boolean hasEerie = ns.contains("eerie");
        boolean hasMedieval = ns.contains("medieval");
        for (ResourceLocation id : tracks) {
            if (id == null) continue;
            String path = id.getPath().toLowerCase(Locale.ROOT);
            if (path.contains("eerie")) hasEerie = true;
            if (path.contains("medieval")) hasMedieval = true;
        }

        if (hasEerie) return "Eerie FM";
        if (hasMedieval) return "Medievil FM";
        return null;
    }

    private static String getDynamicStationDisplayName(String modNamespace, List<ResourceLocation> tracks) {
        String override = getDynamicStationDisplayNameOverride(modNamespace, tracks);
        if (override != null) return override;
        return formatExpansionModNamespaceAsStationName(modNamespace);
    }

    /** Fallback display name for expansion mods without an explicit override. */
    private static String formatExpansionModNamespaceAsStationName(String namespace) {
        if (namespace == null || namespace.isEmpty()) return "Custom Radio";
        String slug = namespace.toLowerCase(Locale.ROOT);
        if (slug.startsWith("dead_air_")) {
            slug = slug.substring("dead_air_".length());
        }
        if (slug.endsWith("_fm")) {
            return titleCaseSlug(slug.substring(0, slug.length() - 3)) + " FM";
        }
        if (slug.endsWith("_radio")) {
            return titleCaseSlug(slug.substring(0, slug.length() - 6)) + " Radio";
        }
        return titleCaseSlug(slug) + " Radio";
    }

    private static String titleCaseSlug(String slug) {
        if (slug == null || slug.isEmpty()) return "Custom";
        String[] parts = slug.split("[_\\-]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1));
        }
        return sb.length() > 0 ? sb.toString() : "Custom";
    }

    /** Build user stations from Config custom folder: config/dead_air/custom stations/<Station Name>/*.ogg */
    private static void registerCustomStationsFromFolders() {
        for (SoundEvent soundEvent : ForgeRegistries.SOUND_EVENTS.getValues()) {
            ResourceLocation id = ForgeRegistries.SOUND_EVENTS.getKey(soundEvent);
            if (id == null || !id.getNamespace().equals(Dead_air.MODID)) continue;
            String path = id.getPath().toLowerCase(Locale.ROOT);
            if (!path.startsWith("music.custom_stations.")) continue;
            String remainder = path.substring("music.custom_stations.".length());
            int sep = remainder.indexOf('.');
            if (sep <= 0) continue;
            String stationSlug = remainder.substring(0, sep);
            ResourceLocation stationId = ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "custom_station_" + stationSlug);
            STATION_TRACKS.computeIfAbsent(stationId, k -> new ArrayList<>()).add(id);

            if (StationRegistry.getStation(stationId) == null) {
                float frequency = generateFrequencyForStation(stationId);
                if (!StationRegistry.isFrequencyAvailable(frequency, StationRegistry.getMinFrequencySpacing())) continue;
                int range = Config.musicStationRange > 0 ? Config.musicStationRange : 375;
                int spacing = Config.minTowerSpacing > 0 ? Config.minTowerSpacing : 400;
                StationRegistry.registerStation(new RadioStation(
                    stationId,
                    humanizeNamespace(stationSlug) + " Radio",
                    RadioStation.StationType.MUSIC,
                    "Custom",
                    frequency,
                    range,
                    spacing
                ));
            }
        }
    }
    
    /**
     * Check if a sound event is a music track (actual song, not ambient sound).
     * Only includes actual music tracks that can pass as songs.
     */
    private static boolean isMusicTrack(ResourceLocation id) {
        String path = id.getPath().toLowerCase();
        String namespace = id.getNamespace().toLowerCase();
        // Include music-like tracks but exclude obvious ambient/effect sounds.
        if (path.contains("ambient")) {
            return false; // Ambient sounds should not play on radio
        }
        if (isAmbientSound(id)) return false;

        int score = 0;
        if (path.contains("music")) score += 3;
        if (path.contains("track") || path.contains("theme") || path.contains("soundtrack")
            || path.contains("bgm") || path.contains("song") || path.contains("tune")) score += 2;
        if (path.contains("biome") && (path.contains("day") || path.contains("night") || path.contains("menu"))) score += 2;
        if (path.contains("record") || path.contains("disc")) score += 2;
        if (namespace.contains("music") || namespace.contains("soundtrack")) score += 1;

        if (path.contains("step") || path.contains("hit") || path.contains("hurt")
            || path.contains("death") || path.contains("break") || path.contains("place")
            || path.contains("click") || path.contains("ui.") || path.contains("gui.")) score -= 4;

        return score >= 2;
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
        String ns = trackId.getNamespace().toLowerCase(Locale.ROOT);
        if (ns.equals("dead_air_after_hours_fm")) return "Lo-Fi";
        if (ns.equals("dead_air_block_beats_fm")) return "Beats";
        if (ns.equals("dead_air_broken_youth_radio")) return "Pop Punk";
        if (ns.equals("dead_air_iron_rain_fm")) return "Metal";
        if (ns.equals("dead_air_zero_gravity")) return "Pop Rock / Indie Pop";
        if (ns.equals("dead_air_pop_paradise_radio")) return "Pop";
        if (ns.equals("dead_air_frequency_x")) return "Electronic / Synthwave";
        if (ns.equals("dead_air_parallel_horizons_fm")) return "Alternative";

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
        float minSpacing = uk.co.extraspecialstudio.dead_air.radio.StationRegistry.getMinFrequencySpacing();
        
        // Check if we've hit the maximum number of stations
        int maxStations = uk.co.extraspecialstudio.dead_air.radio.StationRegistry.getMaxStationsInRange();
        if (uk.co.extraspecialstudio.dead_air.radio.StationRegistry.getAllStations().size() >= maxStations) {
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
            
            if (uk.co.extraspecialstudio.dead_air.radio.StationRegistry.isFrequencyAvailable(frequency, minSpacing)) {
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

    private static String humanizeNamespace(String namespace) {
        if (namespace == null || namespace.isEmpty()) return "Custom";
        return capitalize(namespace.replace('_', ' ').replace('-', ' '));
    }
    
    /**
     * Get next track for a station (sequential, looping). After the last track, returns the first.
     * Uses fallback if station has no tracks.
     */
    public static ResourceLocation getNextTrack(ResourceLocation stationId) {
        return getNextTrack(stationId, null);
    }

    public static ResourceLocation getNextTrack(ResourceLocation stationId, Player player) {
        List<ResourceLocation> tracks = getPlaybackTracks(stationId, player);
        if (tracks.isEmpty()) {
            return null;
        }
        int currentIndex = CURRENT_TRACK_INDEX.getOrDefault(stationId, -1);
        int nextIndex = (currentIndex + 1) % tracks.size(); // wraps to 0 after last track
        CURRENT_TRACK_INDEX.put(stationId, nextIndex);
        return tracks.get(nextIndex);
    }
    
    /**
     * Get random track for a station. Uses fallback if station has no tracks.
     */
    public static ResourceLocation getRandomTrack(ResourceLocation stationId) {
        return getRandomTrack(stationId, null);
    }

    public static ResourceLocation getRandomTrack(ResourceLocation stationId, Player player) {
        List<ResourceLocation> tracks = getPlaybackTracks(stationId, player);
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
        LAST_VIRTUAL_TRACK_START_MS.remove(stationId);
    }
    
    /**
     * Advance playlists for all stations except the one currently playing.
     * Simulates "radio in the background" so when you tune back, you join mid-playlist.
     * Call from client overlay when HUD is visible.
     */
    public static void advanceBackgroundPlaylists(ResourceLocation currentStationId, long nowMs) {
        advanceBackgroundPlaylists(currentStationId, nowMs, null);
    }

    /**
     * @param player local client player for Remix/Jukebox track gating; may be null (treated like no unlock info).
     */
    public static void advanceBackgroundPlaylists(ResourceLocation currentStationId, long nowMs, Player player) {
        for (ResourceLocation stationId : new java.util.HashSet<>(CURRENT_TRACK_INDEX.keySet())) {
            if (stationId.equals(currentStationId)) continue;
            long lastStart = LAST_VIRTUAL_TRACK_START_MS.getOrDefault(stationId, nowMs);
            if (lastStart == nowMs) {
                LAST_VIRTUAL_TRACK_START_MS.put(stationId, nowMs);
                continue;
            }
            ResourceLocation trackId = getCurrentTrackId(stationId, player);
            float durationSec = 0f;
            if (trackId != null) {
                try {
                    durationSec = SoundDurationChecker.getDurationSeconds(trackId);
                } catch (NoClassDefFoundError e) {
                    // Server-side: SoundDurationChecker is client-only
                }
            }
            if (durationSec <= 0f) durationSec = 180f;
            long elapsed = nowMs - lastStart;
            if (elapsed >= (long)(durationSec * 1000)) {
                getNextTrack(stationId, player);
                LAST_VIRTUAL_TRACK_START_MS.put(stationId, nowMs);
            }
        }
    }
}
