package uk.creatopia.unbound.dead_air.tower;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.saveddata.SavedData;
import uk.creatopia.unbound.dead_air.Dead_air;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Persistent storage for Radio Panel activation states.
 * Stored on the Overworld's SavedData so it persists reliably (Overworld is never fully unloaded).
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
        Dead_air.LOGGER.info("Loaded {} activated panel states from saved data", activatedKeys.size());
        if (!activatedKeys.isEmpty()) {
            Dead_air.LOGGER.info("Activated panel keys (first 5): {}", activatedKeys.stream().limit(5).toList());
        }
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        writeToTag(nbt);
        Dead_air.LOGGER.info("PanelActivationStorage.save() writing {} positions to SavedData", activatedKeys.size());
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
            Dead_air.LOGGER.debug("PanelActivationStorage: wrote backup to {}", file);
        } catch (IOException e) {
            Dead_air.LOGGER.warn("PanelActivationStorage: could not write backup: {}", e.getMessage());
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
            if (!Files.isRegularFile(file)) return;
            try (java.io.InputStream in = Files.newInputStream(file)) {
                CompoundTag nbt = net.minecraft.nbt.NbtIo.readCompressed(in);
                int count = storage.replaceFromNbt(nbt);
                Dead_air.LOGGER.info("PanelActivationStorage: loaded {} activated panels from backup (source of truth)", count);
            }
        } catch (IOException e) {
            Dead_air.LOGGER.debug("PanelActivationStorage: no backup or read failed: {}", e.getMessage());
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

    private static PanelActivationStorage getInternal(ServerLevel level, boolean skipIfShutdown) {
        try {
            if (level == null) return null;
            if (skipIfShutdown && Dead_air.isShutdownRequested()) return null;
            var server = level.getServer();
            if (server != null && (server.isStopped() || !server.isRunning())) return null;
            ServerLevel overworld = server.overworld();
            if (overworld == null) return null;
            var dataStorage = overworld.getDataStorage();
            if (dataStorage == null) return null;
            PanelActivationStorage storage = dataStorage.computeIfAbsent(
                PanelActivationStorage::load,
                PanelActivationStorage::new,
                getDataName()
            );
            return storage;
        } catch (Exception e) {
            Dead_air.LOGGER.warn("Panel activation storage get failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Load existing storage from disk when the file exists.
     */
    public static PanelActivationStorage load(CompoundTag nbt) {
        return new PanelActivationStorage(nbt);
    }
}
