package uk.co.extraspecialstudio.dead_air.station.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.music.MusicStationManager;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads station definitions from config folder:
 *   /config/dead_air/stations/*.json
 */
public final class ConfigStationLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigStationLoader() {}

    public static void loadAndRegisterStations() {
        try {
            Path configDir = Path.of("config").resolve(Dead_air.MODID).resolve("stations");
            if (!Files.isDirectory(configDir)) {
                Files.createDirectories(configDir);
                return;
            }

            try (var stream = Files.list(configDir)) {
                stream
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".json"))
                    .forEach(ConfigStationLoader::loadOne);
            }
        } catch (Exception e) {
            Dead_air.LOGGER.warn("[Dead Air] Failed loading config stations: {}", e.getMessage());
        }
    }

    private static void loadOne(Path file) {
        try (Reader r = Files.newBufferedReader(file)) {
            ConfigStationDefinition def = GSON.fromJson(r, ConfigStationDefinition.class);
            if (def == null) return;

            ResourceLocation id = parseId(def.station_id);
            if (id == null) {
                Dead_air.LOGGER.warn("[Dead Air] Station JSON missing/invalid station_id in {}", file.getFileName());
                return;
            }

            String name = safe(def.display_name, id.toString());
            RadioStation.StationType type = parseType(def.type);
            String genre = safe(def.genre, "Custom");

            int range = def.range != null ? def.range : (Config.musicStationRange > 0 ? Config.musicStationRange : 375);
            int spacing = def.min_tower_spacing != null ? def.min_tower_spacing : (Config.minTowerSpacing > 0 ? Config.minTowerSpacing : 400);

            float frequency = def.frequency != null ? def.frequency : generateFrequencyForStation(id);
            if (!StationRegistry.isFrequencyAvailable(frequency, StationRegistry.getMinFrequencySpacing())) {
                Dead_air.LOGGER.warn("[Dead Air] Station {} frequency {} MHz not available; skipping ({})", id, frequency, file.getFileName());
                return;
            }

            StationRegistry.registerStation(new RadioStation(id, name, type, genre, frequency, range, spacing));

            // Register tracks for client playback. MusicStationManager.initialize() runs later on client;
            // addTrack is safe (it will create the playlist if absent).
            if (def.tracks != null && !def.tracks.isEmpty()) {
                for (String t : def.tracks) {
                    ResourceLocation trackId = parseId(t);
                    if (trackId != null) {
                        MusicStationManager.addTrack(id, trackId);
                    }
                }
            }

            Dead_air.LOGGER.info("[Dead Air] Loaded config station {} ({})", id, file.getFileName());
        } catch (JsonParseException e) {
            Dead_air.LOGGER.warn("[Dead Air] Invalid JSON in {}: {}", file.getFileName(), e.getMessage());
        } catch (IOException e) {
            Dead_air.LOGGER.warn("[Dead Air] Could not read {}: {}", file.getFileName(), e.getMessage());
        } catch (Exception e) {
            Dead_air.LOGGER.warn("[Dead Air] Failed loading station {}: {}", file.getFileName(), e.getMessage());
        }
    }

    @SuppressWarnings("null")
    private static ResourceLocation parseId(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            final String trimmed = s.trim();
            return ResourceLocation.tryParse(trimmed);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String safe(String s, String fallback) {
        if (s == null || s.isBlank()) return fallback;
        return s;
    }

    private static RadioStation.StationType parseType(String s) {
        if (s == null || s.isBlank()) return RadioStation.StationType.MUSIC;
        try {
            return RadioStation.StationType.valueOf(s.trim().toUpperCase());
        } catch (Exception ignored) {
            return RadioStation.StationType.MUSIC;
        }
    }

    private static float generateFrequencyForStation(ResourceLocation stationId) {
        // Mirror MusicStationManager's dynamic station generation spacing
        float minFreq = 88.0f;
        float maxFreq = 108.0f;
        float minSpacing = StationRegistry.getMinFrequencySpacing();

        int attempts = 0;
        int maxAttempts = 200;
        while (attempts < maxAttempts) {
            int hash = (stationId.hashCode() + attempts) % 1000;
            float frequency = minFreq + (Math.abs(hash % (int) ((maxFreq - minFreq) / minSpacing)) * minSpacing);
            frequency = Math.round(frequency * 10.0f) / 10.0f;
            if (StationRegistry.isFrequencyAvailable(frequency, minSpacing)) {
                return frequency;
            }
            attempts++;
        }

        float fallback = minFreq + (Math.abs(stationId.hashCode() % (int) ((maxFreq - minFreq) / minSpacing)) * minSpacing);
        return Math.round(fallback * 10.0f) / 10.0f;
    }
}

