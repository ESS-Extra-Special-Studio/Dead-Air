package uk.co.extraspecialstudio.dead_air.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import uk.co.extraspecialstudio.dead_air.Dead_air;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Remembers tent exit positions so waking players land outside, not inside the mesh. */
@EventBusSubscriber(modid = Dead_air.MODID)
public final class TentSleepTracker {
    private static final Map<UUID, BlockPos> EXIT = new ConcurrentHashMap<>();

    private TentSleepTracker() {}

    public static void mark(UUID id, BlockPos exit) {
        EXIT.put(id, exit.immutable());
    }

    public static void clear(UUID id) {
        EXIT.remove(id);
    }

    @SubscribeEvent
    public static void onWake(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BlockPos exit = EXIT.remove(player.getUUID());
        if (exit == null) return;
        if (player.level().getBlockState(exit).isAir() || player.level().getBlockState(exit).canBeReplaced()) {
            player.teleportTo(exit.getX() + 0.5, exit.getY(), exit.getZ() + 0.5);
        }
    }
}
