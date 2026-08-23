package uk.co.extraspecialstudio.dead_air.tower;

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
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;
import uk.co.extraspecialstudio.dead_air.tower.modules.TowerModuleStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persisted list of towers: (dimension, towerPos, panelPos, stationId, activated).
 * <ul>
 *   <li><b>Detected</b> ({@code activated=false}): written on chunk scan — walkie direction only.</li>
 *   <li><b>Activated</b> ({@code activated=true}): panel turned on — music / unlock / full signal.</li>
 * </ul>
 * Signal for music resolves from activated entries only — no chunk scanning.
 */
public final class KnownTowerStorage {
    private static final String BACKUP_FILE = "dead_air_known_towers.dat";

    private static final java.util.Map<ResourceKey<Level>, List<KnownTowerEntry>> CACHE = new ConcurrentHashMap<>();

    public static final class KnownTowerEntry {
        public final BlockPos towerPos;
        public final BlockPos panelPos;
        /** May be null for detection-only after deactivate; provisional station for detected towers. */
        public final ResourceLocation stationId;
        /** True when the panel is broadcasting / activated. */
        public final boolean activated;

        public KnownTowerEntry(BlockPos towerPos, BlockPos panelPos, ResourceLocation stationId, boolean activated) {
            this.towerPos = towerPos.immutable();
            this.panelPos = panelPos.immutable();
            this.stationId = stationId;
            this.activated = activated;
        }

        /** Backward-compatible: activated known tower with a station. */
        public KnownTowerEntry(BlockPos towerPos, BlockPos panelPos, ResourceLocation stationId) {
            this(towerPos, panelPos, stationId, true);
        }
    }

    /**
     * Collapse stale duplicates so one physical tower contributes one entry.
     * Prefers activated over detected when merging the same panel/tower.
     */
    private static List<KnownTowerEntry> dedupeEntries(List<KnownTowerEntry> entries) {
        if (entries == null || entries.isEmpty()) return new ArrayList<>();
        List<KnownTowerEntry> deduped = new ArrayList<>();
        for (KnownTowerEntry e : entries) {
            if (e == null || e.panelPos == null || e.towerPos == null) continue;
            KnownTowerEntry existing = null;
            for (KnownTowerEntry d : deduped) {
                if (d.panelPos.equals(e.panelPos) || d.towerPos.equals(e.towerPos)) {
                    existing = d;
                    break;
                }
            }
            if (existing == null) {
                deduped.add(e);
                continue;
            }
            // Keep activated if either is activated; prefer non-null station
            boolean act = existing.activated || e.activated;
            ResourceLocation station = e.activated && e.stationId != null ? e.stationId
                : (existing.activated && existing.stationId != null ? existing.stationId
                : (e.stationId != null ? e.stationId : existing.stationId));
            BlockPos tower = e.activated ? e.towerPos : existing.towerPos;
            BlockPos panel = e.activated ? e.panelPos : existing.panelPos;
            if (existing.activated && !e.activated) {
                tower = existing.towerPos;
                panel = existing.panelPos;
            }
            deduped.remove(existing);
            deduped.add(new KnownTowerEntry(tower, panel, station, act));
        }
        return deduped;
    }

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
                return;
            }
            List<KnownTowerEntry> entries = new ArrayList<>();
            try (java.io.InputStream in = Files.newInputStream(file)) {
                CompoundTag nbt = net.minecraft.nbt.NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                ListTag list = nbt.getList("known_towers", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag c = list.getCompound(i);
                    BlockPos towerPos = new BlockPos(c.getInt("tx"), c.getInt("ty"), c.getInt("tz"));
                    BlockPos panelPos = new BlockPos(c.getInt("px"), c.getInt("py"), c.getInt("pz"));
                    ResourceLocation stationId = null;
                    if (c.contains("station", Tag.TAG_STRING)) {
                        String s = c.getString("station");
                        if (s != null && !s.isEmpty()) stationId = ResourceLocation.tryParse(s);
                    }
                    // Legacy entries (no "activated" key) are treated as activated when they have a station
                    boolean activated = c.contains("activated") ? c.getBoolean("activated") : (stationId != null);
                    if (stationId == null && !activated) {
                        // Detection-only without station is still valid for nearest-tower direction helpers
                        entries.add(new KnownTowerEntry(towerPos, panelPos, null, false));
                    } else if (stationId != null) {
                        entries.add(new KnownTowerEntry(towerPos, panelPos, stationId, activated));
                    }
                }
            }
            List<KnownTowerEntry> deduped = dedupeEntries(entries);
            CACHE.put(level.dimension(), deduped);
            if (deduped.size() != entries.size()) {
                writeBackup(level);
            }
        } catch (IOException e) {
            Dead_air.LOGGER.warn("[Dead Air] Known towers read failed: {} - {}", file != null ? file : "?", e.getMessage());
            CACHE.put(level.dimension(), new ArrayList<>());
        }
    }

    public static List<KnownTowerEntry> getKnownTowers(ResourceKey<Level> dimension) {
        List<KnownTowerEntry> list = CACHE.get(dimension);
        return list != null ? new ArrayList<>(list) : List.of();
    }

    /** Activated towers only (broadcasting). */
    public static List<KnownTowerEntry> getActivatedTowers(ResourceKey<Level> dimension) {
        List<KnownTowerEntry> out = new ArrayList<>();
        for (KnownTowerEntry e : getKnownTowers(dimension)) {
            if (e.activated && e.stationId != null) out.add(e);
        }
        return out;
    }

    /**
     * Station assigned to an activated panel. Returns null if never activated / deactivated.
     */
    public static ResourceLocation getStationForPanel(ServerLevel level, BlockPos panelPos) {
        if (level == null || panelPos == null || level.dimension() != Level.OVERWORLD) return null;
        if (!CACHE.containsKey(level.dimension())) loadBackupFileDirect(level);
        BlockPos p = panelPos.immutable();
        for (KnownTowerEntry e : getKnownTowers(level.dimension())) {
            if (e.panelPos.equals(p) && e.activated) return e.stationId;
        }
        return null;
    }

    /**
     * Record a tower discovered by chunk scan (not broadcasting yet).
     * Does not unlock stations or start music. Preserves an existing activated entry.
     */
    /**
     * @return true if storage changed (new detection or updated detected entry)
     */
    public static boolean addDetectedTower(ServerLevel level, BlockPos towerPos, BlockPos panelPos, ResourceLocation provisionalStationId) {
        if (level == null || level.dimension() != Level.OVERWORLD || panelPos == null || towerPos == null) return false;
        if (!CACHE.containsKey(level.dimension())) loadBackupFileDirect(level);
        List<KnownTowerEntry> list = CACHE.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
        BlockPos t = towerPos.immutable();
        BlockPos p = panelPos.immutable();
        for (int i = 0; i < list.size(); i++) {
            KnownTowerEntry e = list.get(i);
            if (e.panelPos.equals(p) || e.towerPos.equals(t)) {
                if (e.activated) {
                    // Already broadcasting — leave alone
                    return false;
                }
                ResourceLocation station = provisionalStationId != null ? provisionalStationId : e.stationId;
                if (e.towerPos.equals(t) && e.panelPos.equals(p)
                    && java.util.Objects.equals(e.stationId, station)) {
                    return false; // unchanged
                }
                list.set(i, new KnownTowerEntry(t, p, station, false));
                writeBackup(level);
                return true;
            }
        }
        list.add(new KnownTowerEntry(t, p, provisionalStationId, false));
        writeBackup(level);
        return true;
    }

    /**
     * Add or update an activated known tower for a panel.
     */
    public static void addKnownTower(ServerLevel level, BlockPos towerPos, BlockPos panelPos, ResourceLocation stationId) {
        if (level == null || level.dimension() != Level.OVERWORLD || stationId == null || panelPos == null) return;
        if (!CACHE.containsKey(level.dimension())) loadBackupFileDirect(level);
        List<KnownTowerEntry> list = CACHE.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
        BlockPos t = towerPos.immutable();
        BlockPos p = panelPos.immutable();
        list.removeIf(e -> e.panelPos.equals(p) || e.towerPos.equals(t));
        list.add(new KnownTowerEntry(t, p, stationId, true));
        writeBackup(level);
    }

    /**
     * Demote a panel to detection-only (panel turned off). Keeps position for walkie direction.
     */
    public static void deactivateToDetected(ServerLevel level, BlockPos panelPos) {
        if (level == null || level.dimension() != Level.OVERWORLD || panelPos == null) return;
        if (!CACHE.containsKey(level.dimension())) loadBackupFileDirect(level);
        List<KnownTowerEntry> list = CACHE.get(level.dimension());
        if (list == null) return;
        BlockPos p = panelPos.immutable();
        for (int i = 0; i < list.size(); i++) {
            KnownTowerEntry e = list.get(i);
            if (e.panelPos.equals(p)) {
                // Keep last station id as provisional for direction; clear activation
                list.set(i, new KnownTowerEntry(e.towerPos, e.panelPos, e.stationId, false));
                writeBackup(level);
                return;
            }
        }
    }

    /**
     * @deprecated Prefer {@link #deactivateToDetected} so walkie can still point at the tower.
     * Kept as alias for older call sites; now demotes instead of removing.
     */
    @Deprecated
    public static void removePanel(ServerLevel level, BlockPos panelPos) {
        deactivateToDetected(level, panelPos);
    }

    /**
     * Update the station on an activated panel. No-op if panel is not activated.
     */
    public static void setStationForPanel(ServerLevel level, BlockPos panelPos, ResourceLocation stationId) {
        if (level == null || level.dimension() != Level.OVERWORLD || panelPos == null || stationId == null) return;
        if (!CACHE.containsKey(level.dimension())) loadBackupFileDirect(level);
        List<KnownTowerEntry> list = CACHE.get(level.dimension());
        if (list == null) return;
        BlockPos p = panelPos.immutable();
        for (int i = 0; i < list.size(); i++) {
            KnownTowerEntry e = list.get(i);
            if (e.panelPos.equals(p) && e.activated) {
                list.set(i, new KnownTowerEntry(e.towerPos, e.panelPos, stationId, true));
                writeBackup(level);
                return;
            }
        }
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
                if (e.stationId != null) c.putString("station", e.stationId.toString());
                c.putBoolean("activated", e.activated);
                list.add(c);
            }
            nbt.put("known_towers", list);
            try (java.io.OutputStream out = Files.newOutputStream(file)) {
                net.minecraft.nbt.NbtIo.writeCompressed(nbt, out);
            }
        } catch (IOException e) {
            Dead_air.LOGGER.warn("[Dead Air] Known towers write failed: {}", e.getMessage());
        }
    }

    public static void persistToFile(ServerLevel overworld) {
        if (overworld == null || overworld.dimension() != Level.OVERWORLD) return;
        List<KnownTowerEntry> list = CACHE.get(overworld.dimension());
        if (list == null || list.isEmpty()) return;
        writeBackup(overworld);
    }

    /**
     * Best music signal for a station from activated towers only.
     */
    public static float getSignalFromKnownTowersOnly(ServerLevel level, ResourceLocation stationId, Vec3 playerPos) {
        if (level == null || stationId == null) return 0f;
        if (level.dimension() == Level.OVERWORLD) loadBackupFileDirect(level);
        List<KnownTowerEntry> known = getKnownTowers(level.dimension());
        uk.co.extraspecialstudio.dead_air.radio.RadioStation station = StationRegistry.getStation(stationId);
        if (station == null) return 0f;
        int range = station.getBroadcastRange();
        float best = 0f;
        for (KnownTowerEntry e : known) {
            if (!e.activated || e.stationId == null || !e.stationId.equals(stationId)) continue;
            if (StationRegistry.JUKEBOX_FM_ID.equals(e.stationId)
                && !TowerModuleStorage.get(level).getCapabilities(e.panelPos).isJukeboxModuleInstalled()) {
                continue;
            }
            double dist = Vec3.atCenterOf(e.towerPos).distanceTo(playerPos);
            if (dist > range) continue;
            float sig = uk.co.extraspecialstudio.dead_air.radio.SignalStrength.getFinalSignalStrengthFromPosition(
                level, Vec3.atCenterOf(e.towerPos), station, playerPos);
            if (sig > best) best = sig;
        }
        return best;
    }
}
