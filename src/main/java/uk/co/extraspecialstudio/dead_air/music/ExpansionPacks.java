package uk.co.extraspecialstudio.dead_air.music;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Soundtrack expansion packs are client-only mods: their audio never needs to be on a server.
 * A server only needs each pack's station (id, name, frequency) so towers can broadcast it.
 * It learns them from joining players (studio packs only), its own saved list, and the
 * {@code expansionStations} config list (any pack, including third-party).
 *
 * <p>Unreleased / planned packs live in {@link #PLANNED} for docs and genre helpers only —
 * they must never auto-register as selectable stations until the pack jar exists.
 */
@SuppressWarnings("null")
public final class ExpansionPacks {
    /** Released studio soundtrack packs: mod id -> station name. */
    public static final Map<String, String> KNOWN;
    /** Planned packs (not shipping yet) — names only; never auto-register. */
    public static final Map<String, String> PLANNED;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("dead_air_wayfarer_radio", "Wayfarer Radio");
        m.put("dead_air_frontline_fm", "Frontline FM");
        m.put("dead_air_after_hours_fm", "After Hours FM");
        m.put("dead_air_block_beats_fm", "Block Beats FM");
        m.put("dead_air_broken_youth_radio", "Broken Youth Radio");
        m.put("dead_air_iron_rain_fm", "Iron Rain FM");
        m.put("dead_air_zero_gravity", "Zero Gravity");
        KNOWN = Collections.unmodifiableMap(m);

        Map<String, String> planned = new LinkedHashMap<>();
        planned.put("dead_air_pop_paradise_radio", "Pop Paradise Radio");
        planned.put("dead_air_frequency_x", "Frequency X");
        planned.put("dead_air_parallel_horizons_fm", "Parallel Horizons FM");
        PLANNED = Collections.unmodifiableMap(planned);
    }

    private static final int MAX_ANNOUNCED = 32;
    /** Client: stations whose studio pack is not installed on this game. */
    private static final Set<ResourceLocation> MISSING_PACK_STATIONS = ConcurrentHashMap.newKeySet();

    private ExpansionPacks() {}

    public static ResourceLocation stationIdFor(String namespace) {
        return ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "music_" + namespace);
    }

    public static boolean isKnown(String namespace) {
        return namespace != null && KNOWN.containsKey(namespace.toLowerCase(Locale.ROOT));
    }

    public static String displayName(String namespace) {
        String key = namespace == null ? "" : namespace.toLowerCase(Locale.ROOT);
        String name = KNOWN.get(key);
        if (name == null) name = PLANNED.get(key);
        return name != null ? name : MusicStationManager.formatExpansionModNamespaceAsStationName(namespace);
    }

    /** Register the station for a pack if it is not already registered. Returns the station, or null if no frequency is free. */
    public static RadioStation ensureStation(String namespace) {
        ResourceLocation id = stationIdFor(namespace);
        RadioStation existing = StationRegistry.getStation(id);
        if (existing != null) return existing;
        float frequency = MusicStationManager.generateFrequencyForStation(id);
        if (!StationRegistry.isFrequencyAvailable(frequency, StationRegistry.getMinFrequencySpacing())) {
            Dead_air.LOGGER.warn("[Dead Air] No free frequency for expansion station {}", id);
            return null;
        }
        int range = Config.musicStationRange > 0 ? Config.musicStationRange : 375;
        int spacing = Config.minTowerSpacing > 0 ? Config.minTowerSpacing : 400;
        RadioStation station = new RadioStation(id, displayName(namespace), RadioStation.StationType.MUSIC,
            MusicStationManager.determineGenre(ResourceLocation.fromNamespaceAndPath(namespace, "music")),
            frequency, range, spacing);
        StationRegistry.registerStation(station);
        return station;
    }

    /** Studio packs installed on this game. */
    public static List<String> installedKnownPacks() {
        List<String> out = new ArrayList<>();
        for (String ns : KNOWN.keySet()) {
            if (ModList.get().isLoaded(ns)) out.add(ns);
        }
        return out;
    }

    /**
     * Client: mark already-registered released pack stations as missing when this game
     * does not have the jar (HUD / no audio). Does <b>not</b> invent stations for packs
     * that were never installed — that was listing Parallel Horizons / Frequency X / etc.
     * in the panel with nothing to play.
     */
    public static void registerMissingPackStations() {
        MISSING_PACK_STATIONS.clear();
        for (String ns : KNOWN.keySet()) {
            if (ModList.get().isLoaded(ns)) continue;
            ResourceLocation id = stationIdFor(ns);
            if (StationRegistry.getStation(id) != null) {
                MISSING_PACK_STATIONS.add(id);
            }
        }
    }

    public static boolean isMissingPack(ResourceLocation stationId) {
        return stationId != null && MISSING_PACK_STATIONS.contains(stationId);
    }

    public static String missingPackMessage(RadioStation station) {
        return "Requires the " + station.getName() + " pack";
    }

    /** Server start: stations this world has already seen, plus the config list. */
    public static void onServerStarted(MinecraftServer server) {
        ExpansionStationsStorage storage = ExpansionStationsStorage.get(server);
        if (storage != null) {
            for (String ns : storage.getNamespaces()) {
                String clean = ns == null ? "" : ns.toLowerCase(Locale.ROOT);
                // Never restore unreleased planned packs unless their jar is actually loaded
                if (PLANNED.containsKey(clean) && !ModList.get().isLoaded(clean)) continue;
                ensureStation(clean);
            }
            if (!storage.getNamespaces().isEmpty()) {
                Dead_air.LOGGER.info("[Dead Air] Expansion stations restored for this world: {}", storage.getNamespaces());
            }
        }
        applyConfigList();
    }

    public static void applyConfigList() {
        for (String ns : Config.expansionStations) {
            String clean = ns == null ? "" : ns.trim().toLowerCase(Locale.ROOT);
            if (clean.isEmpty() || !ResourceLocation.isValidNamespace(clean)) {
                Dead_air.LOGGER.warn("[Dead Air] expansionStations: '{}' is not a valid mod id", ns);
                continue;
            }
            boolean isNew = StationRegistry.getStation(stationIdFor(clean)) == null;
            RadioStation station = ensureStation(clean);
            if (isNew && station != null) {
                Dead_air.LOGGER.info("[Dead Air] Expansion station {} ({} MHz) enabled from config", station.getName(), station.getFrequency());
            }
        }
    }

    /** Server: a joining player listed the studio packs installed on their game. */
    public static void acceptFromPlayer(ServerPlayer player, List<String> namespaces) {
        if (player == null || namespaces == null) return;
        ExpansionStationsStorage storage = ExpansionStationsStorage.get(player.getServer());
        int count = 0;
        for (String raw : namespaces) {
            if (++count > MAX_ANNOUNCED) break;
            if (!isKnown(raw)) continue;
            String ns = raw.toLowerCase(Locale.ROOT);
            boolean isNew = StationRegistry.getStation(stationIdFor(ns)) == null;
            if (ensureStation(ns) == null) continue;
            if (storage != null) storage.add(ns);
            if (isNew) {
                Dead_air.LOGGER.info("[Dead Air] Expansion station {} enabled (announced by {})", displayName(ns), player.getGameProfile().getName());
            }
        }
    }
}
