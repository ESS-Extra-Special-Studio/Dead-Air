package uk.co.extraspecialstudio.dead_air.base;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import uk.co.extraspecialstudio.dead_air.Dead_air;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One primary radio-panel base per player. */
@SuppressWarnings("null")
public final class PlayerBaseStorage extends SavedData {
    private static final String DATA_NAME = Dead_air.MODID + "_player_bases";

    public static final class Base {
        public final ResourceKey<Level> dimension;
        public final BlockPos panelPos;

        public Base(ResourceKey<Level> dimension, BlockPos panelPos) {
            this.dimension = dimension;
            this.panelPos = panelPos.immutable();
        }
    }

    private final Map<UUID, Base> byPlayer = new HashMap<>();

    public static PlayerBaseStorage load(CompoundTag nbt) {
        PlayerBaseStorage data = new PlayerBaseStorage();
        CompoundTag map = nbt.getCompound("bases");
        for (String key : map.getAllKeys()) {
            try {
                UUID id = UUID.fromString(key);
                CompoundTag entry = map.getCompound(key);
                ResourceLocation dimId = ResourceLocation.tryParse(entry.getString("dim"));
                if (dimId == null) continue;
                ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimId);
                BlockPos pos = BlockPos.of(entry.getLong("pos"));
                data.byPlayer.put(id, new Base(dim, pos));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        CompoundTag map = new CompoundTag();
        for (var e : byPlayer.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("dim", e.getValue().dimension.location().toString());
            entry.putLong("pos", e.getValue().panelPos.asLong());
            map.put(e.getKey().toString(), entry);
        }
        nbt.put("bases", map);
        return nbt;
    }

    @Nullable
    public Base get(UUID player) {
        return byPlayer.get(player);
    }

    public void set(UUID player, ResourceKey<Level> dimension, BlockPos panelPos) {
        byPlayer.put(player, new Base(dimension, panelPos));
        setDirty();
    }

    public boolean clear(UUID player) {
        if (byPlayer.remove(player) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    public Map<UUID, Base> all() {
        return Map.copyOf(byPlayer);
    }

    public static PlayerBaseStorage get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(PlayerBaseStorage::load, PlayerBaseStorage::new, DATA_NAME);
    }
}
