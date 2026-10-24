package uk.co.extraspecialstudio.dead_air.broadcast;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.base.PlayerBaseManager;
import uk.co.extraspecialstudio.dead_air.base.PlayerBaseStorage;
import uk.co.extraspecialstudio.dead_air.sound.DeadAirSounds;
import uk.co.extraspecialstudio.dead_air.survivor.SurvivorEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tracks Base Under Attack waves and notifies the player when the wave is cleared. */
public final class BaseAttackManager {
    private static final Map<UUID, Wave> ACTIVE = new HashMap<>();
    /**
     * After a wave ends, unloaded settled defenders still hold sticky DEFEND / shrunk home.
     * Survivors apply this on tick when they re-enter loaded chunks.
     */
    private static final Map<UUID, PendingRaidRelease> PENDING_RELEASE = new HashMap<>();

    private record PendingRaidRelease(BlockPos panel, ResourceKey<Level> dimension, int signalRange, long expireGameTime) {}

    private static final class Wave {
        final UUID owner;
        final ServerLevel level;
        final BlockPos panel;
        final List<UUID> mobIds = new ArrayList<>();
        int ticks;
        /** Consecutive ticks where every raid mob is missing while the panel chunk is loaded. */
        int allMissingTicks;
        boolean notified;
        /** True when the wave was emptied by the loaded-chunk despawn soft-clear (not combat). */
        boolean clearedByDespawn;

        Wave(UUID owner, ServerLevel level, BlockPos panel) {
            this.owner = owner;
            this.level = level;
            this.panel = panel;
        }
    }

    private BaseAttackManager() {}

    public static boolean hasActive(UUID owner) {
        return ACTIVE.containsKey(owner);
    }

    /** @return true if a wave was armed with at least one hostile */
    public static boolean startWave(ServerPlayer player, PlayerBaseStorage.Base base) {
        ServerLevel level = player.server.getLevel(base.dimension);
        if (level == null) return false;
        UUID id = player.getUUID();
        if (ACTIVE.containsKey(id)) return false;

        Wave wave = new Wave(id, level, base.panelPos.immutable());
        RandomSource random = level.getRandom();
        int count = Math.max(1, uk.co.extraspecialstudio.dead_air.compat.HordeCompat
            .adjustWaveCount(Config.baseAttackMobCount));
        int range = Math.max(16, PlayerBaseManager.signalRange(level, base.panelPos) / 4);
        try {
            uk.co.extraspecialstudio.dead_air.compat.HordeCompat.tryTriggerHorde(player, level, base.panelPos);
        } catch (Throwable ignored) {
        }

        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = 12 + random.nextInt(Math.max(1, range));
            BlockPos at = base.panelPos.offset((int) (Math.cos(angle) * dist), 0, (int) (Math.sin(angle) * dist));
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                at.getX(), at.getZ());
            at = new BlockPos(at.getX(), y, at.getZ());
            EntityType<? extends Mob> type = pickType(random);
            Mob mob = type.spawn(level, at, MobSpawnType.EVENT);
            if (mob != null) {
                mob.setTarget(player);
                mob.getPersistentData().putBoolean("DeadAirBaseAttack", true);
                mob.getPersistentData().putUUID("DeadAirBaseAttackOwner", id);
                wave.mobIds.add(mob.getUUID());
            }
        }

        // Spawn failed entirely — do not arm an empty wave (instant "cleared" + burned cooldown).
        if (wave.mobIds.isEmpty()) {
            Dead_air.LOGGER.warn("[Dead Air] Base attack spawned 0 mobs at {}", base.panelPos);
            return false;
        }

        // Settled survivors at this base switch to defend
        AABB box = new AABB(base.panelPos).inflate(PlayerBaseManager.signalRange(level, base.panelPos));
        for (SurvivorEntity s : level.getEntitiesOfClass(SurvivorEntity.class, box,
            e -> e.isOwnedBy(player) && e.getSurvivorState() == SurvivorEntity.SETTLED)) {
            s.forceDefendAt(base.panelPos);
        }
        // New wave supersedes any leftover unload-release from a prior raid.
        PENDING_RELEASE.remove(id);

        ACTIVE.put(id, wave);
        player.sendSystemMessage(Component.translatable("message.dead_air.base_attack.started", wave.mobIds.size())
            .withStyle(ChatFormatting.RED));
        playAlarm(player);
        return true;
    }

    /** Quiet radio-siren cue for the owner (encoded quieter + soft playback volume). */
    private static void playAlarm(ServerPlayer player) {
        if (player == null) return;
        player.playNotifySound(DeadAirSounds.BASE_UNDER_ATTACK.get(), SoundSource.HOSTILE, 0.45f, 1.0f);
    }

    private static EntityType<? extends Mob> pickType(RandomSource random) {
        return switch (random.nextInt(5)) {
            case 0 -> EntityType.HUSK;
            case 1 -> EntityType.PILLAGER;
            case 2 -> EntityType.SKELETON;
            default -> EntityType.ZOMBIE;
        };
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0 || ACTIVE.isEmpty()) return;
        Iterator<Map.Entry<UUID, Wave>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Wave> e = it.next();
            Wave wave = e.getValue();
            wave.ticks += 20;
            boolean anyAlive = false;
            Iterator<UUID> mobIt = wave.mobIds.iterator();
            while (mobIt.hasNext()) {
                Entity ent = wave.level.getEntity(mobIt.next());
                if (ent == null) continue; // unloaded or despawned — resolve below
                if (!ent.isAlive()) {
                    mobIt.remove();
                    continue;
                }
                anyAlive = true;
            }
            if (anyAlive) {
                wave.allMissingTicks = 0;
                // Re-cue the ~2.3s loopable alarm every ~3s while hostiles remain.
                if (wave.ticks % 60 == 0) {
                    playAlarm(server.getPlayerList().getPlayer(wave.owner));
                }
            } else if (!wave.mobIds.isEmpty()) {
                // Every remaining ID is missing. If the panel chunk is loaded, treat them as
                // despawned (not merely unloaded) so Base Under Attack cannot soft-lock
                // defenders / hasActive until the 12-minute safety timeout.
                boolean panelLoaded = wave.level.hasChunk(wave.panel.getX() >> 4, wave.panel.getZ() >> 4);
                if (panelLoaded) {
                    wave.allMissingTicks += 20;
                    if (wave.allMissingTicks >= 100) {
                        wave.mobIds.clear();
                        wave.clearedByDespawn = true;
                    }
                } else {
                    wave.allMissingTicks = 0;
                }
            }
            if (wave.mobIds.isEmpty() && !wave.notified) {
                wave.notified = true;
                releaseDefenders(wave);
                ServerPlayer owner = server.getPlayerList().getPlayer(wave.owner);
                if (owner != null) {
                    owner.sendSystemMessage(Component.translatable("message.dead_air.base_attack.cleared")
                        .withStyle(ChatFormatting.GREEN));
                    // Despawn soft-clear is anti-soft-lock — not "Get Off My Lawn".
                    if (!wave.clearedByDespawn) {
                        uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.award(owner,
                            uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.EB_BASE_CLEAR);
                    }
                }
                it.remove();
            } else if (wave.ticks > 20 * 60 * 12) {
                // Safety: abandon after ~12 minutes
                releaseDefenders(wave);
                it.remove();
            }
        }
    }

    /** Unwind forceDefendAt sticky DEFEND / shrunk homeRange after the raid. */
    private static void releaseDefenders(Wave wave) {
        int range = PlayerBaseManager.signalRange(wave.level, wave.panel);
        long expire = wave.level.getGameTime() + 20L * 60L * 30L; // 30 min for chunk reloads
        PENDING_RELEASE.put(wave.owner, new PendingRaidRelease(
            wave.panel.immutable(), wave.level.dimension(), range, expire));
        AABB box = new AABB(wave.panel).inflate(Math.max(32, range));
        for (SurvivorEntity s : wave.level.getEntitiesOfClass(SurvivorEntity.class, box,
            e -> wave.owner.equals(e.getOwnerUuid())
                && e.getSurvivorState() == SurvivorEntity.SETTLED)) {
            s.releaseRaidDefend(wave.panel, range);
        }
    }

    /**
     * Apply pending post-raid DEFEND release for an unloaded survivor that just ticked in.
     * Safe to call every tick — no-ops when nothing pending / wrong dim / not sticky-defending that panel.
     */
    public static void applyPendingReleaseIfAny(SurvivorEntity survivor) {
        if (survivor == null || survivor.level().isClientSide) return;
        UUID owner = survivor.getOwnerUuid();
        if (owner == null) return;
        PendingRaidRelease pending = PENDING_RELEASE.get(owner);
        if (pending == null) return;
        if (!(survivor.level() instanceof ServerLevel sl)) return;
        if (sl.getGameTime() > pending.expireGameTime()) {
            PENDING_RELEASE.remove(owner);
            return;
        }
        if (survivor.level().dimension() != pending.dimension()) return;
        if (survivor.getSurvivorState() != SurvivorEntity.SETTLED) return;
        survivor.releaseRaidDefend(pending.panel(), pending.signalRange());
    }
}
