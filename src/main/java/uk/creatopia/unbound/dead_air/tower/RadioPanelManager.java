package uk.creatopia.unbound.dead_air.tower;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import uk.creatopia.unbound.dead_air.Dead_air;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Radio Panel blocks from Apocalypse Structures: Radio Towers and Airdrops (MCreator, no libs).
 * Our persistent SavedData is the only source of truth: once we have recorded an activation, the panel
 * is always considered active for tower power, regardless of block/NBT state (radiotowers mod may reset on load).
 */
@SuppressWarnings("null")
public class RadioPanelManager {
    private static final Map<BlockPos, Boolean> PANEL_STATES = new ConcurrentHashMap<>();
    /** Session cache: positions we've seen as activated from storage this session (so we stay true if storage is briefly null). */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> SESSION_ACTIVATED = new ConcurrentHashMap<>();

    /**
     * Check if a Radio Panel at the given position is activated.
     * We override radiotowers logic: our persistent storage is the source of truth. If we have it saved as
     * activated, we always return true regardless of block entity or block state.
     */
    public static boolean isPanelActivated(ServerLevel level, BlockPos pos) {
        BlockPos immutablePos = pos.immutable();
        if (level.getServer() != null) {
            try {
                if (level.getServer().isStopped() || !level.getServer().isRunning() || level.getServer().isShutdown()) {
                    return checkSessionOrNBT(level, immutablePos, null);
                }
            } catch (Exception e) {
                return checkSessionOrNBT(level, immutablePos, null);
            }
        }

        PanelActivationStorage storage = null;
        try {
            storage = PanelActivationStorage.get(level);
            if (storage != null) {
                mergeStorageIntoSession(level, storage);
                if (storage.isActivated(level.dimension(), immutablePos)) {
                    PANEL_STATES.put(immutablePos, true);
                    return true;
                }
            }
        } catch (Exception e) {
            Dead_air.LOGGER.debug("Could not check persistent storage for panel at {}: {}", pos, e.getMessage());
        }

        boolean out = checkSessionOrNBT(level, immutablePos, storage);
        if (!out) {
            PanelActivationStorage recheck = PanelActivationStorage.get(level);
            if (recheck != null && recheck.isActivated(level.dimension(), immutablePos)) {
                mergeStorageIntoSession(level, recheck);
                return true;
            }
        }
        return out;
    }

    private static void mergeStorageIntoSession(ServerLevel level, PanelActivationStorage storage) {
        ResourceKey<Level> dim = level.dimension();
        SESSION_ACTIVATED.computeIfAbsent(dim, k -> ConcurrentHashMap.newKeySet()).addAll(storage.getActivatedPositions(dim));
    }

    /** Check session cache first (we saw this pos as activated from storage earlier), then NBT. */
    private static boolean checkSessionOrNBT(ServerLevel level, BlockPos immutablePos, PanelActivationStorage storage) {
        Set<BlockPos> session = SESSION_ACTIVATED.get(level.dimension());
        if (session != null && session.contains(immutablePos)) {
            PANEL_STATES.put(immutablePos, true);
            return true;
        }
        return checkPanelActivationFromNBT(level, immutablePos, storage);
    }
    
    /**
     * Check panel activation from NBT/block state (non-blocking, safe during shutdown).
     * Persistent storage is the source of truth when present.
     */
    private static boolean checkPanelActivationFromNBT(ServerLevel level, BlockPos pos, PanelActivationStorage storage) {
        if (storage != null && storage.isActivated(level.dimension(), pos)) {
            PANEL_STATES.put(pos, true);
            return true;
        }
        // Only check if chunk is loaded to avoid blocking during world load
        if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
            if (PANEL_STATES.containsKey(pos)) {
                return PANEL_STATES.get(pos);
            }
            return false;
        }
        
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            // Use try-catch to avoid issues if block entity isn't fully loaded yet
            CompoundTag nbt = null;
            try {
                nbt = be.saveWithFullMetadata();
            } catch (Exception e) {
                Dead_air.LOGGER.debug("Could not read block entity NBT at {} (may not be fully loaded): {}", pos, e.getMessage());
                // Fall back to cache or default
                if (PANEL_STATES.containsKey(pos)) {
                    return PANEL_STATES.get(pos);
                }
                return false;
            }
            
            if (nbt == null) {
                // NBT read failed, use cache
                if (PANEL_STATES.containsKey(pos)) {
                    return PANEL_STATES.get(pos);
                }
                return false;
            }
            
            // Check for various possible NBT keys that RadioTowers might use
            if (nbt.contains("activated")) {
                boolean activated = nbt.getBoolean("activated");
                if (activated && storage != null) {
                    // If NBT says activated but storage doesn't, sync it
                    storage.setActivated(level.dimension(), pos, true);
                }
                PANEL_STATES.put(pos, activated);
                return activated;
            }
            if (nbt.contains("active")) {
                boolean activated = nbt.getBoolean("active");
                if (activated && storage != null) {
                    storage.setActivated(level.dimension(), pos, true);
                }
                PANEL_STATES.put(pos, activated);
                return activated;
            }
            if (nbt.contains("powered")) {
                boolean activated = nbt.getBoolean("powered");
                if (activated && storage != null) {
                    storage.setActivated(level.dimension(), pos, true);
                }
                PANEL_STATES.put(pos, activated);
                return activated;
            }
            if (nbt.contains("enabled")) {
                boolean activated = nbt.getBoolean("enabled");
                if (activated && storage != null) {
                    storage.setActivated(level.dimension(), pos, true);
                }
                PANEL_STATES.put(pos, activated);
                return activated;
            }
            
            // Check for MCreator-specific NBT (RadioTowers is made with MCreator)
            for (String key : nbt.getAllKeys()) {
                String lowerKey = key.toLowerCase();
                if (lowerKey.contains("activated") || lowerKey.contains("active") || 
                    lowerKey.contains("powered") || lowerKey.contains("enabled") ||
                    lowerKey.contains("on") || lowerKey.contains("state")) {
                    boolean activated = false;
                    if (nbt.contains(key, 1)) { // byte
                        activated = nbt.getBoolean(key);
                    } else if (nbt.contains(key, 3)) { // int
                        activated = (nbt.getInt(key) != 0);
                    }
                    if (activated) {
                        if (storage != null) {
                            storage.setActivated(level.dimension(), pos, true);
                        }
                        PANEL_STATES.put(pos, true);
                        return true;
                    }
                }
            }
        }
        
        // Check cache
        if (PANEL_STATES.containsKey(pos)) {
            return PANEL_STATES.get(pos);
        }
        
        // Check block state properties
        BlockState state = level.getBlockState(pos);
        for (net.minecraft.world.level.block.state.properties.Property<?> prop : state.getProperties()) {
            if (prop instanceof net.minecraft.world.level.block.state.properties.BooleanProperty boolProp) {
                String propName = prop.getName().toLowerCase();
                if (propName.contains("activated") || propName.contains("active") || propName.contains("powered")) {
                    try {
                        boolean activated = state.getValue(boolProp);
                        if (activated && storage != null) {
                            storage.setActivated(level.dimension(), pos, true);
                        }
                        PANEL_STATES.put(pos, activated);
                        return activated;
                    } catch (Exception e) {
                        // Property value not available, continue
                    }
                }
            }
        }
        
        // Default: not activated (only cache when we're sure - don't overwrite if storage wasn't checked)
        if (storage == null || !storage.isActivated(level.dimension(), pos)) {
            PANEL_STATES.put(pos, false);
        }
        return false;
    }
    
    /**
     * Activate a Radio Panel (called when player interacts with it).
     * This should be called when the player right-clicks the Radio Panel in Apocalypse Structures.
     * Uses persistent SavedData storage to ensure activation persists across world reloads.
     */
    public static void activatePanel(ServerLevel level, BlockPos pos) {
        BlockPos immutablePos = pos.immutable();
        PANEL_STATES.put(immutablePos, true);
        SESSION_ACTIVATED.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet()).add(immutablePos);

        PanelActivationStorage storage = PanelActivationStorage.get(level);
        if (storage != null) {
            storage.setActivated(level.dimension(), immutablePos, true);
            Dead_air.LOGGER.info("Radio Panel activated at {} and saved to persistent storage (total saved: {})", pos, storage.getActivatedCount());
            // Write backup file immediately so activation persists even if SavedData save fails
            var overworld = level.getServer() != null ? level.getServer().overworld() : null;
            if (overworld != null) storage.writeBackup(overworld);
        } else {
            Dead_air.LOGGER.warn("Radio Panel activated at {} but persistent storage was null - activation may not survive reload", pos);
        }
        
        // Also try to update block entity NBT as a backup
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            CompoundTag nbt = be.saveWithFullMetadata();
            nbt.putBoolean("activated", true);
            nbt.putBoolean("active", true);
            nbt.putBoolean("powered", true);
            nbt.putBoolean("enabled", true);
            be.setChanged();
            be.load(nbt);
        }
    }
    
    /**
     * Deactivate a Radio Panel.
     */
    public static void deactivatePanel(ServerLevel level, BlockPos pos) {
        PANEL_STATES.put(pos, false);
        
        // Remove from persistent storage
        // Storage may be null during world load, so check before using
        PanelActivationStorage storage = PanelActivationStorage.get(level);
        if (storage != null) {
            storage.setActivated(level.dimension(), pos, false);
            Dead_air.LOGGER.info("Radio Panel deactivated at {}", pos);
        } else {
            // Storage not ready yet (world may still be loading) - cache will be used
            Dead_air.LOGGER.debug("Radio Panel deactivated at {} (storage not ready, using cache)", pos);
        }
        
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            CompoundTag nbt = be.saveWithFullMetadata();
            nbt.putBoolean("activated", false);
            nbt.putBoolean("active", false);
            be.setChanged();
            be.load(nbt);
        }
    }
    
    /**
     * Clear cached panel state (when panel is removed).
     */
    public static void clearPanel(BlockPos pos) {
        PANEL_STATES.remove(pos);
    }

    /** Clear session cache for a dimension when it unloads (avoids stale refs). */
    public static void clearSessionForDimension(ResourceKey<Level> dimension) {
        SESSION_ACTIVATED.remove(dimension);
    }
}
