package uk.co.extraspecialstudio.dead_air.tower.modules;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import uk.co.extraspecialstudio.dead_air.Dead_air;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-world persistent storage of tower module/capability state.
 *
 * We key by Radio Panel position (which is also the tower key in TowerManager).
 */
@SuppressWarnings("null")
public class TowerModuleStorage extends SavedData {

    private static final String DATA_NAME = Dead_air.MODID + "_tower_modules";
    private final Map<BlockPos, CompoundTag> byPos = new HashMap<>();

    public static TowerModuleStorage get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(TowerModuleStorage::new, TowerModuleStorage::load), DATA_NAME);
    }

    private static TowerModuleStorage load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        TowerModuleStorage out = new TowerModuleStorage();
        if (tag == null) return out;
        CompoundTag map = tag.getCompound("byPos");
        for (String key : map.getAllKeys()) {
            try {
                long packed = Long.parseLong(key);
                BlockPos pos = BlockPos.of(packed);
                out.byPos.put(pos, map.getCompound(key));
            } catch (Exception ignored) {}
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        CompoundTag map = new CompoundTag();
        for (var e : byPos.entrySet()) {
            map.put(Long.toString(e.getKey().asLong()), e.getValue());
        }
        tag.put("byPos", map);
        return tag;
    }

    public TowerCapabilities getCapabilities(BlockPos towerPos) {
        CompoundTag tag = byPos.get(towerPos);
        return TowerCapabilities.fromTag(tag);
    }

    public void setCapabilities(BlockPos towerPos, TowerCapabilities caps) {
        if (towerPos == null || caps == null) return;
        byPos.put(towerPos.immutable(), caps.toTag());
        setDirty();
    }
}

