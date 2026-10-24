package uk.co.extraspecialstudio.dead_air.broadcast;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.encounter.ListenerEncounter;
import uk.co.extraspecialstudio.dead_air.walkie.WalkieTalkieManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cooldown-gated explore path: listening T3 players who travel far enough may roll the same
 * broadcast pick/deliver as the scheduler (shares {@code nextBroadcastDay}).
 */
public final class ExploreDiscovery {
    private static final int CHECK_INTERVAL = 100;
    private static final double MIN_TRAVEL = 96.0;
    private static final float CHANCE = 0.12f;

    private static final Map<UUID, double[]> LAST_POS = new HashMap<>();

    public static void tick(MinecraftServer server) {
        if (!Config.broadcastsEnabled) return;
        if (server.getTickCount() % CHECK_INTERVAL != 0) return;
        BroadcastProgress data = BroadcastProgress.get(server);
        if (data == null) return;
        long day = server.overworld().getDayTime() / 24000L;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            if (player.isSpectator()
                || !BroadcastScheduler.canHearEmergency(player)
                || !WalkieTalkieManager.isListeningToRadio(player)
                || ListenerEncounter.isInvolved(player)) {
                // Drop anchor so re-listen / re-equip requires a fresh travel segment.
                LAST_POS.remove(id);
                continue;
            }

            double x = player.getX();
            double z = player.getZ();
            double[] last = LAST_POS.get(id);
            if (last == null) {
                LAST_POS.put(id, new double[] {x, z});
                continue;
            }
            double dx = x - last[0];
            double dz = z - last[1];
            double distSq = dx * dx + dz * dz;
            // Always refresh anchor so cooldown / failed rolls do not accumulate a giant catch-up jump.
            if (distSq < MIN_TRAVEL * MIN_TRAVEL) continue;
            LAST_POS.put(id, new double[] {x, z});

            BroadcastProgress.PlayerProgress progress = data.get(id);
            if (progress.nextBroadcastDay < 0) {
                progress.nextBroadcastDay = day;
                data.setDirty();
            }
            if (day < progress.nextBroadcastDay) continue;
            if (progress.storyQueued) continue;

            if (player.getRandom().nextFloat() > CHANCE) continue;
            BroadcastEvent event = BroadcastScheduler.pick(player, progress);
            if (event == null) continue;
            if (!BroadcastScheduler.deliver(player, event)) continue;
            BroadcastScheduler.burnCooldown(player, progress, data, day);
        }
    }

    public static void clearPlayer(UUID id) {
        if (id != null) LAST_POS.remove(id);
    }

    private ExploreDiscovery() {}
}
