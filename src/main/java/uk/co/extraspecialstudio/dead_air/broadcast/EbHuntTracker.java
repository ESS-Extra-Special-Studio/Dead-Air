package uk.co.extraspecialstudio.dead_air.broadcast;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.item.RadioItem;
import uk.co.extraspecialstudio.dead_air.net.DeadAirNet;
import uk.co.extraspecialstudio.dead_air.net.EbHuntSyncPacket;
import uk.co.extraspecialstudio.dead_air.radio.RadioManager;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * While listening on a T3 tuned to EB with a pending owned site, sync hunt target to client.
 * Prefers unplaced / not-yet-injected sites; throttles packets to key/coord changes.
 */
public final class EbHuntTracker {
    private static final int CHECK_INTERVAL = 10;
    /** Refresh coords to client at most this often when the target key is unchanged. */
    private static final int REFRESH_INTERVAL_TICKS = 40;
    private static final Map<UUID, String> LAST_KEY = new HashMap<>();
    private static final Map<UUID, Long> LAST_SENT_GAME_TIME = new HashMap<>();
    private static final Map<UUID, Long> LAST_SENT_CENTER = new HashMap<>();

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_INTERVAL != 0) return;
        if (!Config.broadcastsEnabled) {
            // Clear any active hunt clients when broadcasts are disabled mid-session.
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                clearPlayer(player);
            }
            return;
        }
        PendingSites sites = PendingSites.get(server);
        BroadcastProgress data = BroadcastProgress.get(server);
        if (sites == null || data == null) return;
        long gameTime = server.overworld().getGameTime();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            BroadcastProgress.PlayerProgress progress = data.get(player.getUUID());
            boolean hunting = isHunting(player);
            PendingSites.Site nearest = hunting ? nearestHuntTarget(sites, player, progress, data) : null;
            if (nearest == null) {
                if (LAST_KEY.remove(player.getUUID()) != null) {
                    DeadAirNet.sendTo(player, new EbHuntSyncPacket(false, 0, 0, "", "", false));
                }
                LAST_SENT_GAME_TIME.remove(player.getUUID());
                LAST_SENT_CENTER.remove(player.getUUID());
                continue;
            }
            String key = EmergencyProximity.siteKey(nearest);
            long centerPacked = nearest.center.asLong();
            boolean playVo = !progress.huntVoPlayed.contains(key);
            String prev = LAST_KEY.get(player.getUUID());
            Long lastSent = LAST_SENT_GAME_TIME.get(player.getUUID());
            Long lastCenter = LAST_SENT_CENTER.get(player.getUUID());
            boolean keyChanged = !key.equals(prev);
            boolean centerChanged = lastCenter == null || lastCenter != centerPacked;
            boolean refreshDue = lastSent == null || gameTime - lastSent >= REFRESH_INTERVAL_TICKS;
            if (!playVo && !keyChanged && !centerChanged && !refreshDue) {
                continue;
            }
            if (playVo) {
                progress.huntVoPlayed.add(key);
                data.setDirty();
            }
            LAST_KEY.put(player.getUUID(), key);
            LAST_SENT_GAME_TIME.put(player.getUUID(), gameTime);
            LAST_SENT_CENTER.put(player.getUUID(), centerPacked);
            DeadAirNet.sendTo(player, new EbHuntSyncPacket(true, nearest.center.getX(), nearest.center.getZ(),
                nearest.kind, key, playVo));
        }
    }

    /** Listening T3 on Emergency Broadcast (inventory OK when radioAlwaysOn). Overworld only. */
    private static boolean isHunting(ServerPlayer player) {
        // Site pins are overworld X/Z — Nether/End coords produce garbage bearing.
        if (player.level() != player.server.overworld()) return false;
        if (!BroadcastScheduler.hasT3(player)) return false;
        if (!RadioManager.isListeningToRadio(player)) return false;
        if (!Config.radioAlwaysOn && !RadioManager.isHoldingRadio(player)) return false;
        ItemStack listening = RadioManager.findListeningRadio(player);
        if (!(listening.getItem() instanceof RadioItem radio) || radio.getTier() != RadioItem.Tier.T3) {
            return false;
        }
        return RadioManager.isRadioTunedToStation(listening, StationRegistry.EMERGENCY_BROADCAST_ID);
    }

    /**
     * Nearest owned site still worth hunting: skip proximity-injected (already resolved).
     * Prefer unplaced pins over placed wrecks so the compass leads to the next approach.
     */
    private static PendingSites.Site nearestHuntTarget(PendingSites sites, ServerPlayer player,
                                                       BroadcastProgress.PlayerProgress progress,
                                                       BroadcastProgress data) {
        PendingSites.Site bestUnplaced = null;
        PendingSites.Site bestPlaced = null;
        double bestUnplacedD = Double.MAX_VALUE;
        double bestPlacedD = Double.MAX_VALUE;
        for (PendingSites.Site site : sites.getSites()) {
            if (!site.owner.equals(player.getUUID())) continue;
            if (site.kind == null || site.kind.isEmpty()) continue;
            String key = EmergencyProximity.siteKey(site);
            if (EmergencyProximity.alreadyHandled(data, progress.proximityInjected, site, key)) continue;
            BlockPos c = site.center;
            double dx = player.getX() - c.getX();
            double dz = player.getZ() - c.getZ();
            double d = dx * dx + dz * dz;
            if (!site.placed) {
                if (d < bestUnplacedD) {
                    bestUnplacedD = d;
                    bestUnplaced = site;
                }
            } else if (d < bestPlacedD) {
                bestPlacedD = d;
                bestPlaced = site;
            }
        }
        return bestUnplaced != null ? bestUnplaced : bestPlaced;
    }

    /** Clear hunt for a known online player (logout / death). */
    public static void clearPlayer(ServerPlayer player) {
        if (player == null) return;
        UUID id = player.getUUID();
        if (LAST_KEY.remove(id) != null) {
            DeadAirNet.sendTo(player, new EbHuntSyncPacket(false, 0, 0, "", "", false));
        }
        LAST_SENT_GAME_TIME.remove(id);
        LAST_SENT_CENTER.remove(id);
    }

    public static void clearPlayer(UUID id) {
        if (id == null) return;
        LAST_KEY.remove(id);
        LAST_SENT_GAME_TIME.remove(id);
        LAST_SENT_CENTER.remove(id);
    }

    private EbHuntTracker() {}
}
