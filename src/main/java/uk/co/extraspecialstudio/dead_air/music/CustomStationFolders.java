package uk.co.extraspecialstudio.dead_air.music;

import net.minecraft.resources.ResourceLocation;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Shared scan/register helpers for {@code config/dead_air/custom stations/<Station Name>/*.ogg}.
 * Safe on dedicated server (no SoundEvent registration required for station metadata).
 */
public final class CustomStationFolders {
    public static final String CUSTOM_STATIONS_FOLDER = "custom stations";
    public static final String STATION_ID_PREFIX = "custom_station_";
    public static final String SOUND_PATH_PREFIX = "music.custom_stations.";

    public record StationFolder(String slug, String displayName, List<String> trackSlugs) {}

    private CustomStationFolders() {
    }

    public static Path root() {
        return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
            .resolve(Dead_air.MODID)
            .resolve(CUSTOM_STATIONS_FOLDER);
    }

    public static boolean isFolderCustomStation(ResourceLocation stationId) {
        return stationId != null
            && Dead_air.MODID.equals(stationId.getNamespace())
            && stationId.getPath().startsWith(STATION_ID_PREFIX);
    }

    public static ResourceLocation stationIdForSlug(String slug) {
        return ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, STATION_ID_PREFIX + slug);
    }

    public static ResourceLocation trackSoundId(String stationSlug, String trackSlug) {
        return ResourceLocation.fromNamespaceAndPath(
            Dead_air.MODID, SOUND_PATH_PREFIX + stationSlug + "." + trackSlug);
    }

    public static String sanitizeSlug(String name) {
        if (name == null) return "";
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/_.-]", "_");
    }

    public static String displayNameFromFolder(String folderName, String slug) {
        if (folderName != null && !folderName.isBlank()) {
            String trimmed = folderName.trim().replace('_', ' ');
            if (!trimmed.isEmpty()) {
                return titleCaseWords(trimmed) + " Radio";
            }
        }
        return titleCaseWords(slug.replace('_', ' ').replace('-', ' ')) + " Radio";
    }

    private static String titleCaseWords(String raw) {
        String[] parts = raw.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.length() > 0 ? sb.toString() : "Custom Radio";
    }

    public static List<StationFolder> scan() {
        List<StationFolder> out = new ArrayList<>();
        Path root = root();
        if (!Files.isDirectory(root)) {
            return out;
        }
        try (Stream<Path> stationDirs = Files.list(root)) {
            for (Path stationDir : stationDirs.filter(Files::isDirectory).toList()) {
                String folderName = stationDir.getFileName().toString();
                String slug = sanitizeSlug(folderName);
                if (slug.isEmpty()) continue;
                List<String> tracks = new ArrayList<>();
                try (Stream<Path> files = Files.list(stationDir)) {
                    for (Path file : files.filter(f -> Files.isRegularFile(f)
                            && f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg")).toList()) {
                        String trackName = file.getFileName().toString();
                        trackName = trackName.substring(0, trackName.length() - 4);
                        String trackSlug = sanitizeSlug(trackName);
                        if (!trackSlug.isEmpty()) {
                            tracks.add(trackSlug);
                        }
                    }
                } catch (IOException e) {
                    Dead_air.LOGGER.warn("[Dead Air] Could not read custom station folder {}", stationDir, e);
                }
                if (tracks.isEmpty()) {
                    Dead_air.LOGGER.warn("[Dead Air] Custom station folder '{}' has no .ogg files; skipping", folderName);
                    continue;
                }
                out.add(new StationFolder(slug, displayNameFromFolder(folderName, slug), tracks));
            }
        } catch (IOException e) {
            Dead_air.LOGGER.warn("[Dead Air] Could not list custom stations folder {}", root, e);
        }
        return out;
    }

    /**
     * Register folder stations into {@link StationRegistry} (common / server / client).
     * Idempotent: skips ids that already exist.
     */
    public static void registerStationsIntoRegistry() {
        int range = Config.musicStationRange > 0 ? Config.musicStationRange : 375;
        int spacing = Config.minTowerSpacing > 0 ? Config.minTowerSpacing : 400;
        for (StationFolder folder : scan()) {
            ResourceLocation stationId = stationIdForSlug(folder.slug());
            if (StationRegistry.getStation(stationId) != null) {
                continue;
            }
            float frequency = findAvailableFrequency(stationId);
            if (Float.isNaN(frequency)) {
                Dead_air.LOGGER.warn("[Dead Air] No free FM slot for custom station {}; skipping", folder.displayName());
                continue;
            }
            StationRegistry.registerStation(new RadioStation(
                stationId,
                folder.displayName(),
                RadioStation.StationType.MUSIC,
                "Custom",
                frequency,
                range,
                spacing
            ));
            Dead_air.LOGGER.info(
                "[Dead Air] Registered custom station '{}' ({} track{}) at {} MHz from config folders",
                folder.displayName(),
                folder.trackSlugs().size(),
                folder.trackSlugs().size() == 1 ? "" : "s",
                frequency
            );
        }
    }

    /** Prefer a free frequency; returns {@link Float#NaN} only if the band is completely full. */
    public static float findAvailableFrequency(ResourceLocation stationId) {
        float minFreq = 88.0f;
        float maxFreq = 108.0f;
        float minSpacing = StationRegistry.getMinFrequencySpacing();
        int slots = Math.max(1, (int) ((maxFreq - minFreq) / minSpacing));

        for (int attempt = 0; attempt < slots + 50; attempt++) {
            int hash = Math.floorMod(stationId.hashCode() + attempt, slots);
            float frequency = minFreq + (hash * minSpacing);
            frequency = Math.round(frequency * 10.0f) / 10.0f;
            if (StationRegistry.isFrequencyAvailable(frequency, minSpacing)) {
                return frequency;
            }
        }
        for (int i = 0; i < slots; i++) {
            float frequency = Math.round((minFreq + i * minSpacing) * 10.0f) / 10.0f;
            if (StationRegistry.isFrequencyAvailable(frequency, minSpacing)) {
                return frequency;
            }
        }
        return Float.NaN;
    }
}
