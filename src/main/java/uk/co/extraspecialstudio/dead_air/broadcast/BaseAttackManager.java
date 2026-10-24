package uk.co.extraspecialstudio.dead_air.broadcast;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.base.PlayerBaseManager;
import uk.co.extraspecialstudio.dead_air.base.PlayerBaseStorage;
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

    private static final class Wave {
        final UUID owner;
        final ServerLevel level;
        final BlockPos panel;
        final List<UUID> mobIds = new ArrayList<>();
        int ticks;
        boolean notified;

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

    public static void startWave(ServerPlayer player, PlayerBaseStorage.Base base) {
        ServerLevel level = player.server.getLevel(base.dimension);
        if (level == null) return;
        UUID id = player.getUUID();
        if (ACTIVE.containsKey(id)) return;

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

        // Settled survivors at this base switch to defend
        AABB box = new AABB(base.panelPos).inflate(PlayerBaseManager.signalRange(level, base.panelPos));
        for (SurvivorEntity s : level.getEntitiesOfClass(SurvivorEntity.class, box,
            e -> e.isOwnedBy(player) && e.getSurvivorState() == SurvivorEntity.SETTLED)) {
            s.forceDefendAt(base.panelPos);
        }

        ACTIVE.put(id, wave);
        player.sendSystemMessage(Component.translatable("message.dead_air.base_attack.started", count)
            .withStyle(ChatFormatting.RED));
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
            wave.mobIds.removeIf(uuid -> {
                Entity ent = wave.level.getEntity(uuid);
                return ent == null || !ent.isAlive();
            });
            if (wave.mobIds.isEmpty() && !wave.notified) {
                wave.notified = true;
                ServerPlayer owner = server.getPlayerList().getPlayer(wave.owner);
                if (owner != null) {
                    owner.sendSystemMessage(Component.translatable("message.dead_air.base_attack.cleared")
                        .withStyle(ChatFormatting.GREEN));
                }
                it.remove();
            } else if (wave.ticks > 20 * 60 * 12) {
                // Safety: abandon after ~12 minutes
                it.remove();
            }
        }
    }
}
