package uk.creatopia.unbound.dead_air.tower;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import uk.creatopia.unbound.dead_air.Dead_air;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persisted list of "known" towers: (dimension, towerPos, panelPos, stationId).
 * When a player activates a panel we add that tower here. Signal requests then resolve
 * from this list + panel activation backup only — no chunk scanning.
 */
public final class KnownTowerStorage {
    private static final String BACKUP_FILE = "dead_air_known_towers.dat";

    /** In-memory cache: dimension -> list of (towerPos, panelPos, stationId). Loaded from file, no SavedData. */
    private static final java.util.Map<ResourceKey<Level>, List<KnownTowerEntry>> CACHE = new ConcurrentHashMap<>();

    public static final class KnownTowerEntry {
        public final BlockPos towerPos;
        public final BlockPos panelPos;
        public final ResourceLocation stationId;

        public KnownTowerEntry(BlockPos towerPos, BlockPos panelPos, ResourceLocation stationId) {
            this.towerPos = towerPos.immutable();
            this.panelPos = panelPos.immutable();
            this.stationId = stationId;
        }
    }

    /**
     * Load known towers from world data file (no SavedData). Safe to call from packet handler.
     */
    public static void loadBackupFileDirect(ServerLevel level) {
        if (level == null || level.dimension() != Level.OVERWORLD) return;
        var server = level.getServer();
        if (server == null) return;
        Path file = null;
        try {
            Path worldPath = server.getWorldPath(LevelResource.ROOT);
            file = worldPath.resolve("data").resolve(BACKUP_FILE);
            if (!Files.isRegularFile(file)) {
                CACHE.put(level.dimension(), new ArrayList<>());
                Dead_air.LOGGER.info("[Dead Air] No known towers file at {} (using empty list)", file);
                return;
            }
            List<KnownTowerEntry> entries = new ArrayList<>();
            try (java.io.InputStream in = Files.newInputStream(file)) {
                CompoundTag nbt = net.minecraft.nbt.NbtIo.readCompressed(in);
                ListTag list = nbt.getList("known_towers", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag c = list.getCompound(i);
                    BlockPos towerPos = new BlockPos(c.getInt("tx"), c.getInt("ty"), c.getInt("tz"));
                    BlockPos panelPos = new BlockPos(c.getInt("px"), c.getInt("py"), c.getInt("pz"));
                    ResourceLocation stationId = ResourceLocation.tryParse(c.getString("station"));
                    if (stationId != null) entries.add(new KnownTowerEntry(towerPos, panelPos, stationId));
                }
            }
            CACHE.put(level.dimension(), entries);
            if (!entries.isEmpty()) {
                Dead_air.LOGGER.info("[Dead Air] Known towers loaded from {} ({} towers) - tune in GUI to hear", file, entries.size());
            }
        } catch (IOException e) {
            Dead_air.LOGGER.warn("[Dead Air] Known towers read failed: {} - {}", file != null ? file : "?", e.getMessage());
            CACHE.put(level.dimension(), new ArrayList<>());
        }
    }

    /** Get known tower entries for a dimension (from cache). Returns empty list if not loaded. */
    public static List<KnownTowerEntry> getKnownTowers(ResourceKey<Level> dimension) {
        List<KnownTowerEntry> list = CACHE.get(dimension);
        return list != null ? new ArrayList<>(list) : List.of();
    }

    /**
     * Get the station assigned to a panel. Returns null if panel is not known (never activated).
     * Each panel is locked to one station once assigned.
     */
    public static ResourceLocation getStationForPanel(ServerLevel level, BlockPos panelPos) {
        if (level == null || panelPos == null || level.dimension() != Level.OVERWORLD) return null;
        if (!CACHE.containsKey(level.dimension())) loadBackupFileDirect(level);
        BlockPos p = panelPos.immutable();
        for (KnownTowerEntry e : getKnownTowers(level.dimension())) {
            if (e.panelPos.equals(p)) return e.stationId;
        }
        return null;
    }

    /**
     * Add a tower as "known" when a panel is activated. Persists to file so tuning in GUI works without chunk scan.
     * Each panel can only be assigned ONE station - once assigned, it is locked. Re-activation does not change it.
     */
    public static void addKnownTower(ServerLevel level, BlockPos towerPos, BlockPos panelPos, ResourceLocation stationId) {
        if (level == null || level.dimension() != Level.OVERWORLD || stationId == null) return;
        if (!CACHE.containsKey(level.dimension())) loadBackupFileDirect(level);
        List<KnownTowerEntry> list = CACHE.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
        BlockPos t = towerPos.immutable();
        BlockPos p = panelPos.immutable();
        for (KnownTowerEntry e : list) {
            if (e.panelPos.equals(p)) return; // Panel already has a station - locked, do not change
        }
        list.add(new KnownTowerEntry(t, p, stationId));
        writeBackup(level);
    }

    private static void writeBackup(ServerLevel overworld) {
        if (overworld == null) return;
        try {
            Path worldPath = overworld.getServer().getWorldPath(LevelResource.ROOT);
            Path dataPath = worldPath.resolve("data");
            Files.createDirectories(dataPath);
            Path file = dataPath.resolve(BACKUP_FILE);
            CompoundTag nbt = new CompoundTag();
            ListTag list = new ListTag();
            for (KnownTowerEntry e : getKnownTowers(overworld.dimension())) {
                CompoundTag c = new CompoundTag();
                c.putString("dim", overworld.dimension().location().toString());
                c.putInt("tx", e.towerPos.getX());
                c.putInt("ty", e.towerPos.getY());
                c.putInt("tz", e.towerPos.getZ());
                c.putInt("px", e.panelPos.getX());
                c.putInt("py", e.panelPos.getY());
                c.putInt("pz", e.panelPos.getZ());
                c.putString("station", e.stationId.toString());
                list.add(c);
            }
            nbt.put("known_towers", list);
            try (java.io.OutputStream out = Files.newOutputStream(file)) {
                net.minecraft.nbt.NbtIo.writeCompressed(nbt, out);
            }
            int count = list.size();
            if (count > 0) {
                Dead_air.LOGGER.info("[Dead Air] Wrote {} known tower(s) to {}", count, file);
            }
        } catch (IOException e) {
            Dead_air.LOGGER.warn("[Dead Air] Known towers write failed: {}", e.getMessage());
        }
    }

    /**
     * Persist known towers cache to file (e.g. on level save or server stop). Safe to call from ModEvents.
     */
    public static void persistToFile(ServerLevel overworld) {
        if (overworld == null || overworld.dimension() != Level.OVERWORLD) return;
        List<KnownTowerEntry> list = CACHE.get(overworld.dimension());
        if (list == null || list.isEmpty()) return;
        writeBackup(overworld);
    }

    /**
     * Resolve best signal for a station at player position using only known towers (no panel backup, no chunk access).
     * Once a tower is known (added when player clicked its panel), tuning to that station in the GUI gives signal when in range.
     */
    public static float getSignalFromKnownTowersOnly(ServerLevel level, ResourceLocation stationId, Vec3 playerPos) {
        if (level == null || stationId == null) return 0f;
        if (level.dimension() == Level.OVERWORLD) loadBackupFileDirect(level);
        List<KnownTowerEntry> known = getKnownTowers(level.dimension());
        uk.creatopia.unbound.dead_air.radio.RadioStation station = uk.creatopia.unbound.dead_air.radio.StationRegistry.getStation(stationId);
        if (station == null) return 0f;
        int range = station.getBroadcastRange();
        float best = 0f;
        int inRangeForStation = 0;
        for (KnownTowerEntry e : known) {
            if (!e.stationId.equals(stationId)) continue;
            double dist = Vec3.atCenterOf(e.towerPos).distanceTo(playerPos);
            if (dist > range) continue;
            inRangeForStation++;
            float sig = uk.creatopia.unbound.dead_air.radio.SignalStrength.getFinalSignalStrengthFromPosition(
                level, Vec3.atCenterOf(e.towerPos), station, playerPos);
            if (sig > best) best = sig;
        }
        if (known.stream().anyMatch(e -> e.stationId.equals(stationId)) && inRangeForStation == 0) {
            Dead_air.LOGGER.info("[Dead Air] Station {} has known tower(s) but none in range (range={})", stationId, range);
        }
        return best;
    }
}
