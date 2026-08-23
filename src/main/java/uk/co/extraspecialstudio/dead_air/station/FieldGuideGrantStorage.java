package uk.co.extraspecialstudio.dead_air.station;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import uk.co.extraspecialstudio.dead_air.Dead_air;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks which players have already received the Dead Air Field Guide on first join.
 * Uses overworld {@link SavedData} so it persists reliably across logins (player entity NBT alone was not enough in the wild).
 */
@SuppressWarnings("null")
public final class FieldGuideGrantStorage extends SavedData {
    private static final String DATA_ID = Dead_air.MODID + "_field_guide_grants";
    private static final String PLAYERS_KEY = "Players";

    private final Set<UUID> grantedPlayerIds = new HashSet<>();

    public FieldGuideGrantStorage() {}

    public FieldGuideGrantStorage(CompoundTag nbt) {
        ListTag list = nbt.getList(PLAYERS_KEY, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                grantedPlayerIds.add(UUID.fromString(list.getString(i)));
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (UUID id : grantedPlayerIds) {
            list.add(StringTag.valueOf(id.toString()));
        }
        nbt.put(PLAYERS_KEY, list);
        return nbt;
    }

    public boolean hasReceived(UUID playerId) {
        return grantedPlayerIds.contains(playerId);
    }

    /** Returns true if this call newly recorded the player (dirty). */
    public boolean markReceived(UUID playerId) {
        if (grantedPlayerIds.add(playerId)) {
            setDirty();
            return true;
        }
        return false;
    }

    public static FieldGuideGrantStorage load(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider provider) {
        return new FieldGuideGrantStorage(nbt);
    }

    public static FieldGuideGrantStorage get(ServerLevel anyLevel) {
        if (anyLevel == null || anyLevel.getServer() == null) {
            return null;
        }
        ServerLevel overworld = anyLevel.getServer().overworld();
        if (overworld == null) {
            return null;
        }
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(FieldGuideGrantStorage::new, FieldGuideGrantStorage::load),
            DATA_ID
        );
    }
}
