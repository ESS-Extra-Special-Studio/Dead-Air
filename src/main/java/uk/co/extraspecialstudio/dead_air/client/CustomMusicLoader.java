package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.music.CustomStationFolders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Loads custom .ogg files from the configured folder, registers them as SoundEvents,
 * and prepares a sanitized pack cache so paths with spaces/casing still load.
 */
public final class CustomMusicLoader {
    private static final String SOUND_PREFIX = "music.custom.";
    /** Sound IDs we registered so MusicStationManager can find them via scanForCreatopiaMusic. */
    static final List<ResourceLocation> REGISTERED_CUSTOM_IDS = new ArrayList<>();

    /**
     * Call from RegisterEvent for SOUND_EVENTS.
     * Legacy flat custom tracks and folder custom stations are independent — station folders
     * still register even when {@code customMusicPath} is empty / missing.
     */
    public static void registerCustomSounds(RegisterEvent event) {
        if (!event.getRegistryKey().equals(ForgeRegistries.Keys.SOUND_EVENTS)) return;
        REGISTERED_CUSTOM_IDS.clear();
        registerLegacyCustomTracks(event);
        registerCustomStationTracks(event);
    }

    private static String sanitizeSoundPath(String name) {
        return CustomStationFolders.sanitizeSlug(name);
    }

    private static void registerLegacyCustomTracks(RegisterEvent event) {
        String pathStr = getCustomMusicPath();
        if (pathStr == null || pathStr.isEmpty()) return;
        Path dir = Path.of(pathStr);
        if (!Files.isDirectory(dir)) return;
        Path soundsSubdir = dir.resolve("assets").resolve("dead_air").resolve("sounds").resolve("music").resolve("custom");
        Path scanDir = Files.isDirectory(soundsSubdir) ? soundsSubdir : dir;
        try (Stream<Path> stream = Files.list(scanDir)) {
            stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".ogg"))
                .forEach(p -> {
                    String name = p.getFileName().toString();
                    name = name.substring(0, name.length() - 4);
                    String safe = sanitizeSoundPath(name);
                    if (safe.isEmpty()) return;
                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, SOUND_PREFIX + safe);
                    event.register(ForgeRegistries.Keys.SOUND_EVENTS, id, () -> SoundEvent.createVariableRangeEvent(id));
                    REGISTERED_CUSTOM_IDS.add(id);
                });
        } catch (IOException e) {
            Dead_air.LOGGER.warn("[Dead Air] Could not list custom music folder: {}", pathStr, e);
        }
    }

    private static void registerCustomStationTracks(RegisterEvent event) {
        Path root = CustomStationFolders.root();
        try {
            Files.createDirectories(root);
            ensureCustomStationsReadme(root);
        } catch (IOException e) {
            Dead_air.LOGGER.warn("[Dead Air] Could not prepare custom stations folder {}", root, e);
            return;
        }
        for (CustomStationFolders.StationFolder folder : CustomStationFolders.scan()) {
            for (String trackSlug : folder.trackSlugs()) {
                ResourceLocation id = CustomStationFolders.trackSoundId(folder.slug(), trackSlug);
                event.register(ForgeRegistries.Keys.SOUND_EVENTS, id, () -> SoundEvent.createVariableRangeEvent(id));
                REGISTERED_CUSTOM_IDS.add(id);
            }
            Dead_air.LOGGER.info(
                "[Dead Air] Registered {} sound(s) for custom station folder '{}'",
                folder.trackSlugs().size(),
                folder.displayName()
            );
        }
    }

    private static void ensureCustomStationsReadme(Path root) {
        Path readme = root.resolve("README.txt");
        String txt = """
            Dead Air custom stations

            Create one folder per station in this directory:
              config/dead_air/custom stations/<Your Station Name>/

            Put .ogg files directly inside that station folder, for example:
              config/dead_air/custom stations/LoFi Nights/track_01.ogg
              config/dead_air/custom stations/LoFi Nights/track_02.ogg

            On next launch, Dead Air creates "LoFi Nights Radio" automatically.
            Folder stations always appear in the walkie station list (no tower required).
            A nearby tower can still broadcast them for signal bars like any other station.

            Restart the game (or reload resources) after adding / renaming folders or tracks.
            """;
        try {
            Files.writeString(readme, txt);
        } catch (IOException ignored) {
        }
    }

    /** Get custom music path: from Config if loaded, otherwise from config file or default. */
    private static String getCustomMusicPath() {
        try {
            if (Config.customMusicPath != null && !Config.customMusicPath.isEmpty()) return Config.customMusicPath.trim();
        } catch (Throwable ignored) { }
        try {
            Path configPath = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("dead_air-common.toml");
            if (Files.isRegularFile(configPath)) {
                String content = Files.readString(configPath);
                java.util.regex.Matcher m = Pattern.compile("customMusicPath\\s*=\\s*[\"']([^\"']*)[\"']").matcher(content);
                if (m.find()) return m.group(1).trim();
            }
        } catch (Throwable ignored) { }
        return "";
    }

    /** Path currently used for the legacy custom music folder (for the resource pack). Empty is fine. */
    public static String getCustomMusicPathForPack() {
        String path = null;
        try {
            if (Config.customMusicPath != null && !Config.customMusicPath.isEmpty()) path = Config.customMusicPath.trim();
        } catch (Throwable ignored) { }
        return path != null ? path : "";
    }

    /** Expected subfolder for pack structure; if present we use it, else we scan the path itself. */
    public static final String PACK_SOUNDS_SUBPATH = "assets/dead_air/sounds/music/custom";

    /**
     * Prepare a cache directory with sanitized file names so the pack can load .ogg files
     * regardless of original casing/spaces (e.g. "Forest ambiance.ogg" -> "forest_ambiance.ogg").
     * Returns the cache path to use as pack root, or null if no custom music.
     */
    public static Path preparePackCache(Path customMusicRoot) {
        Path soundsDir = null;
        boolean hasConfiguredRoot = customMusicRoot != null
            && !customMusicRoot.toString().isBlank();
        if (hasConfiguredRoot) {
            soundsDir = customMusicRoot.resolve("assets").resolve(Dead_air.MODID).resolve("sounds").resolve("music").resolve("custom");
            if (!Files.isDirectory(soundsDir)) soundsDir = customMusicRoot;
        }
        Path customStationsRoot = CustomStationFolders.root();
        boolean hasLegacy = soundsDir != null && Files.isDirectory(soundsDir);
        boolean hasStations = Files.isDirectory(customStationsRoot);
        if (!hasLegacy && !hasStations) return null;
        // Keep cache in a stable, always-writeable location under config so "custom stations" works
        // even when customMusicPath does not exist yet.
        Path cacheRoot = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
            .resolve(Dead_air.MODID)
            .resolve(".pack_cache");
        Path cacheSounds = cacheRoot.resolve("assets").resolve(Dead_air.MODID).resolve("sounds").resolve("music").resolve("custom");
        List<String> sanitizedNames = new ArrayList<>();
        Map<String, List<String>> stationTracks = new HashMap<>();
        try {
            Files.createDirectories(cacheSounds);
            if (hasLegacy) {
                try (Stream<Path> stream = Files.list(soundsDir)) {
                    for (Path p : stream.filter(f -> Files.isRegularFile(f) && f.getFileName().toString().toLowerCase().endsWith(".ogg")).toList()) {
                        String name = p.getFileName().toString();
                        name = name.substring(0, name.length() - 4);
                        String safe = sanitizeSoundPath(name);
                        if (safe.isEmpty()) continue;
                        Path dest = cacheSounds.resolve(safe + ".ogg");
                        Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                        sanitizedNames.add(safe);
                    }
                }
            }
            if (hasStations) {
                Path stationDestRoot = cacheRoot.resolve("assets").resolve(Dead_air.MODID).resolve("sounds").resolve("music").resolve("custom_stations");
                Files.createDirectories(stationDestRoot);
                try (Stream<Path> stationDirs = Files.list(customStationsRoot)) {
                    for (Path stationDir : stationDirs.filter(Files::isDirectory).toList()) {
                        String stationSlug = sanitizeSoundPath(stationDir.getFileName().toString());
                        if (stationSlug.isEmpty()) continue;
                        Path destDir = stationDestRoot.resolve(stationSlug);
                        Files.createDirectories(destDir);
                        List<String> tracks = stationTracks.computeIfAbsent(stationSlug, k -> new ArrayList<>());
                        try (Stream<Path> files = Files.list(stationDir)) {
                            for (Path file : files.filter(f -> Files.isRegularFile(f) && f.getFileName().toString().toLowerCase().endsWith(".ogg")).toList()) {
                                String trackName = file.getFileName().toString();
                                trackName = trackName.substring(0, trackName.length() - 4);
                                String trackSlug = sanitizeSoundPath(trackName);
                                if (trackSlug.isEmpty()) continue;
                                Files.copy(file, destDir.resolve(trackSlug + ".ogg"), StandardCopyOption.REPLACE_EXISTING);
                                if (!tracks.contains(trackSlug)) tracks.add(trackSlug);
                            }
                        }
                    }
                }
            }
            if (sanitizedNames.isEmpty() && stationTracks.isEmpty()) return null;
            generateSoundsJson(cacheRoot, sanitizedNames, stationTracks);
            try {
                Path packMeta = cacheRoot.resolve("pack.mcmeta");
                if (!Files.exists(packMeta)) {
                    Files.writeString(packMeta, "{\"pack\":{\"description\":\"Dead Air Custom Music\",\"pack_format\":15}}");
                }
            } catch (IOException ignored) {}
            return cacheRoot;
        } catch (IOException e) {
            Dead_air.LOGGER.warn("[Dead Air] Could not prepare custom music pack cache: {}", e.getMessage());
            return null;
        }
    }

    private static void generateSoundsJson(Path packRoot, List<String> sanitizedNames, Map<String, List<String>> customStationTracks) {
        if (sanitizedNames.isEmpty() && (customStationTracks == null || customStationTracks.isEmpty())) return;
        StringBuilder json = new StringBuilder("{\n");
        List<String> entries = new ArrayList<>();
        for (String safe : sanitizedNames) {
            String key = SOUND_PREFIX + safe;
            String value = Dead_air.MODID + ":music/custom/" + safe;
            entries.add("  \"" + key + "\": {\"category\":\"music\",\"sounds\":[{\"name\":\"" + value + "\",\"stream\":true}]}");
        }
        if (customStationTracks != null) {
            for (var stationEntry : customStationTracks.entrySet()) {
                String stationSlug = stationEntry.getKey();
                for (String trackSlug : stationEntry.getValue()) {
                    String key = CustomStationFolders.SOUND_PATH_PREFIX + stationSlug + "." + trackSlug;
                    String value = Dead_air.MODID + ":music/custom_stations/" + stationSlug + "/" + trackSlug;
                    entries.add("  \"" + key + "\": {\"category\":\"music\",\"sounds\":[{\"name\":\"" + value + "\",\"stream\":true}]}");
                }
            }
        }
        for (int i = 0; i < entries.size(); i++) {
            json.append(entries.get(i));
            if (i < entries.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append("}");
        try {
            Path soundsJson = packRoot.resolve("assets").resolve(Dead_air.MODID).resolve("sounds.json");
            Files.createDirectories(soundsJson.getParent());
            Files.writeString(soundsJson, json.toString());
        } catch (IOException e) {
            Dead_air.LOGGER.warn("[Dead Air] Could not write sounds.json for custom music pack", e);
        }
    }

    public static void generateSoundsJson(Path packRoot) {
        Path soundsDir = packRoot.resolve("assets").resolve(Dead_air.MODID).resolve("sounds").resolve("music").resolve("custom");
        if (!Files.isDirectory(soundsDir)) return;
        List<String> names = new ArrayList<>();
        try (Stream<Path> stream = Files.list(soundsDir)) {
            stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".ogg"))
                .forEach(p -> {
                    String name = p.getFileName().toString();
                    name = name.substring(0, name.length() - 4);
                    String safe = sanitizeSoundPath(name);
                    if (!safe.isEmpty()) names.add(safe);
                });
        } catch (IOException ignored) { return; }
        generateSoundsJson(packRoot, names, Map.of());
    }

    private CustomMusicLoader() {}
}
