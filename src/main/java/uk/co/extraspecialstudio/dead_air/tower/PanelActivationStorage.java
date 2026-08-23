package uk.co.extraspecialstudio.dead_air.tower;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.saveddata.SavedData;
import uk.co.extraspecialstudio.dead_air.Dead_air;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent storage for Radio Panel activation states.
 * <p>
 * World-only: we store nothing in player data. When a player activates a panel we write
 * (dimension + block position) into the world: overworld SavedData and a backup file
 * {@code world/data/dead_air_panels.dat}. On load we read that file and treat those positions
 * as active. Any player can activate a panel; the tower stays on for the whole world.
 * Entries are keyed by (dimension, pos) to support panels in any dimension.
 */
@SuppressWarnings("null")
public class PanelActivationStorage extends SavedData {
    private static String getDataName() {
        return Dead_air.MODID + "_panel_activations";
    }

    private static String key(ResourceKey<Level> dimension, BlockPos pos) {
        return dimension.location().toString() + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private final Set<String> activatedKeys = new HashSet<>();

    public PanelActivationStorage() {
    }

    public PanelActivationStorage(CompoundTag nbt) {
        ListTag list = nbt.getList("activated_panels", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String dim = entry.getString("dim");
            int x = entry.getInt("x");
            int y = entry.getInt("y");
            int z = entry.getInt("z");
            activatedKeys.add(dim + "|" + x + "," + y + "," + z);
        }
    }

    @Override
    public CompoundTag save(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider provider) {
        writeToTag(nbt);
        return nbt;
    }

    /** Write current state to NBT (used by save() and backup). */
    private void writeToTag(CompoundTag nbt) {
        ListTag list = new ListTag();
        for (String key : activatedKeys) {
            int pipe = key.indexOf('|');
            if (pipe <= 0) continue;
            String dim = key.substring(0, pipe);
            String[] xyz = key.substring(pipe + 1).split(",");
            if (xyz.length != 3) continue;
            CompoundTag entry = new CompoundTag();
            entry.putString("dim", dim);
            entry.putInt("x", Integer.parseInt(xyz[0]));
            entry.putInt("y", Integer.parseInt(xyz[1]));
            entry.putInt("z", Integer.parseInt(xyz[2]));
            list.add(entry);
        }
        nbt.put("activated_panels", list);
    }

    /** Backup file name in world folder so we can restore if SavedData fails to persist. */
    private static final String BACKUP_FILE = "dead_air_panels.dat";

    /**
     * Cache of activated positions read directly from the backup file (no SavedData).
     * Used so we never call get(level)/computeIfAbsent during world or chunk load (avoids deadlock).
     * Key format: dimension.location() + "|" + x + "," + y + "," + z.
     */
    private static final java.util.Map<ResourceKey<Level>, Set<String>> BACKUP_CACHE = new ConcurrentHashMap<>();

    /**
     * Read backup file from disk only (no SavedData). Safe to call from ChunkEvent.Load.
     * Populates BACKUP_CACHE so isInBackupCache() returns true without touching get(level).
     */
    public static void loadBackupFileDirect(ServerLevel level) {
        if (level == null) return;
        var server = level.getServer();
        if (server == null) return;
        if (level.dimension() != Level.OVERWORLD) return;
        Path file = null;
        try {
            Path worldPath = server.getWorldPath(LevelResource.ROOT);
            file = worldPath.resolve("data").resolve(BACKUP_FILE);
            if (!Files.isRegularFile(file)) return;
            Set<String> keys = new HashSet<>();
            try (java.io.InputStream in = Files.newInputStream(file)) {
                CompoundTag nbt = net.minecraft.nbt.NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                ListTag list = nbt.getList("activated_panels", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag entry = list.getCompound(i);
                    keys.add(entry.getString("dim") + "|" + entry.getInt("x") + "," + entry.getInt("y") + "," + entry.getInt("z"));
                }
            }
            BACKUP_CACHE.put(level.dimension(), keys);
        } catch (IOException e) {
            Dead_air.LOGGER.warn("[Dead Air] Panel backup read failed: {} - {}", file != null ? file : "?", e.getMessage());
        }
    }

    /** Check backup cache only (no get(level)). Used during chunk load so we never touch SavedData. */
    public static boolean isInBackupCache(ResourceKey<Level> dimension, BlockPos pos) {
        Set<String> keys = BACKUP_CACHE.get(dimension);
        if (keys == null) return false;
        return keys.contains(key(dimension, pos.immutable()));
    }

    /** Merge BACKUP_CACHE into this storage so save() has full data. Call when first getting storage after load. */
    public void mergeFromBackupCache(ResourceKey<Level> dimension) {
        Set<String> keys = BACKUP_CACHE.get(dimension);
        if (keys == null || keys.isEmpty()) return;
        for (String k : keys) {
            if (activatedKeys.add(k)) setDirty();
        }
    }

    /**
     * Write current state to a backup file in the world's data folder.
     * Called on overworld save and immediately when a panel is activated.
     */
    public void writeBackup(ServerLevel overworld) {
        if (overworld == null) return;
        try {
            Path worldPath = overworld.getServer().getWorldPath(LevelResource.ROOT);
            Path dataPath = worldPath.resolve("data");
            Files.createDirectories(dataPath);
            CompoundTag nbt = new CompoundTag();
            writeToTag(nbt);
            Path file = dataPath.resolve(BACKUP_FILE);
            try (java.io.OutputStream out = Files.newOutputStream(file)) {
                net.minecraft.nbt.NbtIo.writeCompressed(nbt, out);
            }
        } catch (IOException e) {
            Dead_air.LOGGER.warn("PanelActivationStorage: could not write backup: {}", e.getMessage());
        }
    }

    /**
     * When SavedData storage is null, write this single panel activation directly to the backup file
     * (read existing backup, add position, write back) so activation survives restart.
     */
    public static void writeSinglePanelBackup(ServerLevel overworld, ResourceKey<Level> dimension, BlockPos pos) {
        if (overworld == null) return;
        try {
            Path worldPath = overworld.getServer().getWorldPath(LevelResource.ROOT);
            Path dataPath = worldPath.resolve("data");
            Files.createDirectories(dataPath);
            Path file = dataPath.resolve(BACKUP_FILE);
            Set<String> keys = new HashSet<>();
            if (Files.isRegularFile(file)) {
                try (java.io.InputStream in = Files.newInputStream(file)) {
                    CompoundTag nbt = net.minecraft.nbt.NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                    ListTag list = nbt.getList("activated_panels", Tag.TAG_COMPOUND);
                    for (int i = 0; i < list.size(); i++) {
                        CompoundTag entry = list.getCompound(i);
                        keys.add(entry.getString("dim") + "|" + entry.getInt("x") + "," + entry.getInt("y") + "," + entry.getInt("z"));
                    }
                }
            }
            keys.add(key(dimension, pos.immutable()));
            CompoundTag nbt = new CompoundTag();
            ListTag list = new ListTag();
            for (String key : keys) {
                int pipe = key.indexOf('|');
                if (pipe <= 0) continue;
                String dim = key.substring(0, pipe);
                String[] xyz = key.substring(pipe + 1).split(",");
                if (xyz.length != 3) continue;
                CompoundTag entry = new CompoundTag();
                entry.putString("dim", dim);
                entry.putInt("x", Integer.parseInt(xyz[0]));
                entry.putInt("y", Integer.parseInt(xyz[1]));
                entry.putInt("z", Integer.parseInt(xyz[2]));
                list.add(entry);
            }
            nbt.put("activated_panels", list);
            try (java.io.OutputStream out = Files.newOutputStream(file)) {
                net.minecraft.nbt.NbtIo.writeCompressed(nbt, out);
            }
        } catch (IOException e) {
            Dead_air.LOGGER.warn("PanelActivationStorage: direct backup write failed: {}", e.getMessage());
        }
    }

    /** Merge entries from NBT into this storage; returns count added. */
    public int mergeFromNbt(CompoundTag nbt) {
        ListTag list = nbt.getList("activated_panels", Tag.TAG_COMPOUND);
        int added = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String dim = entry.getString("dim");
            int x = entry.getInt("x");
            int y = entry.getInt("y");
            int z = entry.getInt("z");
            String key = dim + "|" + x + "," + y + "," + z;
            if (activatedKeys.add(key)) added++;
        }
        if (added > 0) setDirty();
        return added;
    }

    /** Replace this storage's contents with backup data (backup is source of truth on load). */
    public int replaceFromNbt(CompoundTag nbt) {
        activatedKeys.clear();
        int n = mergeFromNbt(nbt);
        if (n > 0) setDirty();
        return n;
    }

    /**
     * Load from backup file if it exists. Uses backup as source of truth so panel activations persist across restart.
     */
    public static void loadBackupInto(ServerLevel overworld, PanelActivationStorage storage) {
        if (overworld == null || storage == null) return;
        try {
            Path worldPath = overworld.getServer().getWorldPath(LevelResource.ROOT);
            Path file = worldPath.resolve("data").resolve(BACKUP_FILE);
            if (!Files.isRegularFile(file)) {
                return;
            }
            try (java.io.InputStream in = Files.newInputStream(file)) {
                CompoundTag nbt = net.minecraft.nbt.NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                storage.replaceFromNbt(nbt);
            }
        } catch (IOException e) {
            // Backup may not exist yet
        }
    }

    public boolean isActivated(ResourceKey<Level> dimension, BlockPos pos) {
        return activatedKeys.contains(key(dimension, pos.immutable()));
    }

    public void setActivated(ResourceKey<Level> dimension, BlockPos pos, boolean activated) {
        String k = key(dimension, pos.immutable());
        if (activated) {
            activatedKeys.add(k);
        } else {
            activatedKeys.remove(k);
        }
        setDirty();
    }

    /** For logging: number of panels currently stored as activated. */
    public int getActivatedCount() {
        return activatedKeys.size();
    }

    /** Returns a copy of activated positions for the given dimension (for session cache merge). */
    public Set<BlockPos> getActivatedPositions(ResourceKey<Level> dimension) {
        String prefix = dimension.location().toString() + "|";
        Set<BlockPos> out = new HashSet<>();
        for (String k : activatedKeys) {
            if (!k.startsWith(prefix)) continue;
            String rest = k.substring(prefix.length());
            String[] xyz = rest.split(",");
            if (xyz.length != 3) continue;
            try {
                out.add(new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])).immutable());
            } catch (NumberFormatException ignored) { }
        }
        return out;
    }

    /**
     * Get or create the saved data. Always uses the Overworld's storage so persistence is reliable.
     * Returns null only during shutdown/save to avoid blocking.
     */
    public static PanelActivationStorage get(ServerLevel level) {
        return getInternal(level, true);
    }

    /**
     * Get storage for save path only. Does not skip when shutdown is requested, so we can setDirty and write backup during LevelEvent.Save.
     */
    public static PanelActivationStorage getForSave(ServerLevel level) {
        return getInternal(level, false);
    }

    @SuppressWarnings("DeadCode") // defensive null checks for edge cases (shutdown, etc.)
    private static PanelActivationStorage getInternal(ServerLevel level, boolean skipIfShutdown) {
        try {
            if (level == null) return null;
            if (skipIfShutdown && Dead_air.isShutdownRequested()) return null;
            var server = level.getServer();
            if (server == null) return null;
            if (skipIfShutdown && (server.isStopped() || !server.isRunning())) return null;
            ServerLevel overworld = server.overworld();
            if (overworld == null) return null;
            var dataStorage = overworld.getDataStorage();
            if (dataStorage == null) return null;
            PanelActivationStorage storage = dataStorage.computeIfAbsent(
                new SavedData.Factory<>(PanelActivationStorage::new, PanelActivationStorage::load),
                getDataName()
            );
            if (storage != null) storage.mergeFromBackupCache(level.dimension());
            return storage;
        } catch (Exception e) {
            Dead_air.LOGGER.warn("Panel activation storage get failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Load existing storage from disk when the file exists.
     */
    public static PanelActivationStorage load(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider provider) {
        return new PanelActivationStorage(nbt);
    }
}
