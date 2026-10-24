package uk.co.extraspecialstudio.dead_air.survivor;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import uk.co.extraspecialstudio.dead_air.Config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Helpers for FOLLOWING party size, listing, and formation slots. */
public final class SurvivorParty {
    public static final int ORDER_FOLLOW = 0;
    public static final int ORDER_DEFEND = 1;
    public static final int ORDER_ATTACK = 2;
    public static final int ORDER_RETREAT = 3;
    public static final int ORDER_REGROUP = 4;

    private SurvivorParty() {}

    public static List<SurvivorEntity> listFollowing(MinecraftServer server, UUID owner) {
        List<SurvivorEntity> out = new ArrayList<>();
        if (server == null || owner == null) return out;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof SurvivorEntity s) || s.isRemoved() || s.isDeadOrDying()) continue;
                if (owner.equals(s.getOwnerUuid()) && s.getSurvivorState() == SurvivorEntity.FOLLOWING) {
                    out.add(s);
                }
            }
        }
        out.sort(Comparator.comparing(SurvivorEntity::getUUID));
        return out;
    }

    public static int countFollowing(MinecraftServer server, UUID owner) {
        SurvivorRoster roster = SurvivorRoster.get(server);
        if (roster == null) {
            return listFollowing(server, owner).size();
        }
        reconcileFollowing(server, owner, roster);
        return roster.countFollowing(owner);
    }

    /**
     * Keep persisted FOLLOWING UUIDs aligned with loaded entities.
     * Unloaded FOLLOWING members stay counted so the party cap cannot be bypassed.
     */
    private static void reconcileFollowing(MinecraftServer server, UUID owner, SurvivorRoster roster) {
        Set<UUID> loadedFollowing = new HashSet<>();
        for (SurvivorEntity s : listFollowing(server, owner)) {
            loadedFollowing.add(s.getUUID());
            roster.setFollowing(owner, s.getUUID(), true);
        }
        for (UUID id : roster.followingIds(owner)) {
            if (loadedFollowing.contains(id)) continue;
            // FOLLOWING must also be owned — drop inconsistent ghost slots.
            if (!roster.contains(owner, id)) {
                roster.setFollowing(owner, id, false);
                continue;
            }
            Entity e = findEntity(server, id);
            if (e == null) continue; // unloaded — keep counting
            if (!(e instanceof SurvivorEntity s) || s.isRemoved() || s.isDeadOrDying()
                || s.getSurvivorState() != SurvivorEntity.FOLLOWING
                || s.getProfession() == SurvivorEntity.Profession.PILOT
                || !owner.equals(s.getOwnerUuid())) {
                roster.setFollowing(owner, id, false);
            }
        }
    }

    private static Entity findEntity(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }

    public static boolean partyFull(MinecraftServer server, UUID owner) {
        return countFollowing(server, owner) >= Config.maxPartySize;
    }

    /**
     * Contiguous formation slot among loaded followers (stable UUID order).
     * Prefer this for pathing so the squad fans out instead of hashing into collisions.
     */
    public static int formationIndex(MinecraftServer server, SurvivorEntity self) {
        UUID owner = self == null ? null : self.getOwnerUuid();
        if (owner == null || server == null) return 0;
        List<SurvivorEntity> party = listFollowing(server, owner);
        for (int i = 0; i < party.size(); i++) {
            if (party.get(i).getUUID().equals(self.getUUID())) return i;
        }
        return Math.max(0, party.size());
    }

    /** Cheap fallback when a server reference is unavailable. */
    public static int formationIndex(SurvivorEntity self) {
        if (self == null) return 0;
        if (self.level() instanceof ServerLevel sl) {
            return formationIndex(sl.getServer(), self);
        }
        return Math.floorMod(Long.hashCode(self.getUUID().getLeastSignificantBits()), 8);
    }

    public static List<SurvivorEntity> listFollowingNear(ServerPlayer player, double range) {
        List<SurvivorEntity> out = new ArrayList<>();
        if (player == null) return out;
        AABB box = player.getBoundingBox().inflate(range);
        for (SurvivorEntity s : player.level().getEntitiesOfClass(SurvivorEntity.class, box)) {
            if (s.isOwnedBy(player) && s.getSurvivorState() == SurvivorEntity.FOLLOWING) {
                out.add(s);
            }
        }
        out.sort(Comparator.comparing(SurvivorEntity::getUUID));
        return out;
    }
}
