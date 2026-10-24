package uk.co.extraspecialstudio.dead_air.survivor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import uk.co.extraspecialstudio.dead_air.Dead_air;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Which survivors each player has recruited, so the per-player cap holds while they are unloaded. */
@SuppressWarnings("null")
public class SurvivorRoster extends SavedData {
    private static final String DATA_NAME = Dead_air.MODID + "_survivors";
    private final Map<UUID, Set<UUID>> byOwner = new HashMap<>();
    /** FOLLOWING party members — includes unloaded entities so maxPartySize cannot be bypassed. */
    private final Map<UUID, Set<UUID>> followingByOwner = new HashMap<>();

    public static SurvivorRoster load(CompoundTag nbt) {
        SurvivorRoster r = new SurvivorRoster();
        CompoundTag owners = nbt.getCompound("owners");
        for (String key : owners.getAllKeys()) {
            Set<UUID> set = new LinkedHashSet<>();
            ListTag list = owners.getList(key, Tag.TAG_INT_ARRAY);
            for (Tag t : list) set.add(NbtUtils.loadUUID(t));
            try {
                r.byOwner.put(UUID.fromString(key), set);
            } catch (IllegalArgumentException ignored) {
            }
        }
        CompoundTag following = nbt.getCompound("following");
        for (String key : following.getAllKeys()) {
            Set<UUID> set = new LinkedHashSet<>();
            ListTag list = following.getList(key, Tag.TAG_INT_ARRAY);
            for (Tag t : list) set.add(NbtUtils.loadUUID(t));
            try {
                r.followingByOwner.put(UUID.fromString(key), set);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        CompoundTag owners = new CompoundTag();
        for (var e : byOwner.entrySet()) {
            ListTag list = new ListTag();
            for (UUID id : e.getValue()) list.add(NbtUtils.createUUID(id));
            owners.put(e.getKey().toString(), list);
        }
        nbt.put("owners", owners);
        CompoundTag following = new CompoundTag();
        for (var e : followingByOwner.entrySet()) {
            ListTag list = new ListTag();
            for (UUID id : e.getValue()) list.add(NbtUtils.createUUID(id));
            following.put(e.getKey().toString(), list);
        }
        nbt.put("following", following);
        return nbt;
    }

    public int count(UUID owner) {
        Set<UUID> s = byOwner.get(owner);
        return s == null ? 0 : s.size();
    }

    public void add(UUID owner, UUID survivor) {
        if (byOwner.computeIfAbsent(owner, k -> new LinkedHashSet<>()).add(survivor)) setDirty();
    }

    public boolean contains(UUID owner, UUID survivor) {
        Set<UUID> s = byOwner.get(owner);
        return s != null && s.contains(survivor);
    }

    public void remove(UUID owner, UUID survivor) {
        Set<UUID> s = byOwner.get(owner);
        if (s != null && s.remove(survivor)) setDirty();
        setFollowing(owner, survivor, false);
    }

    public void clear(UUID owner) {
        if (byOwner.remove(owner) != null) setDirty();
        if (followingByOwner.remove(owner) != null) setDirty();
    }

    public int countFollowing(UUID owner) {
        Set<UUID> s = followingByOwner.get(owner);
        return s == null ? 0 : s.size();
    }

    /** Snapshot of FOLLOWING UUIDs for reconcile (may be empty). */
    public Set<UUID> followingIds(UUID owner) {
        Set<UUID> s = followingByOwner.get(owner);
        return s == null || s.isEmpty() ? Set.of() : Set.copyOf(s);
    }

    public void setFollowing(UUID owner, UUID survivor, boolean following) {
        if (owner == null || survivor == null) return;
        if (following) {
            if (followingByOwner.computeIfAbsent(owner, k -> new LinkedHashSet<>()).add(survivor)) {
                setDirty();
            }
        } else {
            Set<UUID> s = followingByOwner.get(owner);
            if (s != null && s.remove(survivor)) {
                setDirty();
                if (s.isEmpty()) followingByOwner.remove(owner);
            }
        }
    }

    public static SurvivorRoster get(MinecraftServer server) {
        if (server == null) return null;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return null;
        return overworld.getDataStorage().computeIfAbsent(SurvivorRoster::load, SurvivorRoster::new, DATA_NAME);
    }
}
