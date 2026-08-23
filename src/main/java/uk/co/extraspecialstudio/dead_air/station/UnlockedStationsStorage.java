package uk.co.extraspecialstudio.dead_air.station;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import uk.co.extraspecialstudio.dead_air.Dead_air;

import java.util.*;

/**
 * Persists which stations each player has unlocked (discovered).
 * Stored on overworld SavedData so it survives restart.
 */
@SuppressWarnings("null")
public class UnlockedStationsStorage extends SavedData {
    private static String getDataName() {
        return Dead_air.MODID + "_unlocked_stations";
    }

    /** player UUID -> set of station IDs (as string) */
    private final Map<UUID, Set<String>> perPlayer = new HashMap<>();

    public UnlockedStationsStorage() {}

    public UnlockedStationsStorage(CompoundTag nbt) {
        for (String key : nbt.getAllKeys()) {
            if ("DataVersion".equals(key)) continue;
            ListTag list = nbt.getList(key, Tag.TAG_STRING);
            Set<String> stations = new HashSet<>();
            for (int i = 0; i < list.size(); i++) {
                stations.add(list.getString(i));
            }
            try {
                perPlayer.put(UUID.fromString(key), stations);
            } catch (Exception e) {
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        for (Map.Entry<UUID, Set<String>> e : perPlayer.entrySet()) {
            ListTag list = new ListTag();
            for (String id : e.getValue()) {
                list.add(net.minecraft.nbt.StringTag.valueOf(id));
            }
            nbt.put(e.getKey().toString(), list);
        }
        return nbt;
    }

    public Set<ResourceLocation> getStations(UUID playerId) {
        Set<String> ids = perPlayer.get(playerId);
        if (ids == null) return Set.of();
        Set<ResourceLocation> out = new HashSet<>();
        for (String id : ids) {
            try {
                out.add(ResourceLocation.parse(id));
            } catch (Exception ignored) {}
        }
        return out;
    }

    public void addStation(UUID playerId, ResourceLocation stationId) {
        perPlayer.computeIfAbsent(playerId, k -> new HashSet<>()).add(stationId.toString());
        setDirty();
    }

    /** Remove a station from a player's unlocked set (e.g. when the last tower for that station is removed). */
    public void removeStation(UUID playerId, ResourceLocation stationId) {
        Set<String> stations = perPlayer.get(playerId);
        if (stations != null && stations.remove(stationId.toString())) setDirty();
    }

    /** Remove a station from all players' unlocked sets. */
    public void removeStationFromAllPlayers(ResourceLocation stationId) {
        String id = stationId.toString();
        for (Set<String> stations : perPlayer.values()) {
            if (stations.remove(id)) setDirty();
        }
    }

    public static UnlockedStationsStorage get(ServerLevel level) {
        if (level == null || level.getServer() == null) return null;
        ServerLevel overworld = level.getServer().overworld();
        if (overworld == null) return null;
        return overworld.getDataStorage().computeIfAbsent(
            UnlockedStationsStorage::load,
            UnlockedStationsStorage::new,
            getDataName()
        );
    }

    public static UnlockedStationsStorage load(CompoundTag nbt) {
        return new UnlockedStationsStorage(nbt);
    }
}
