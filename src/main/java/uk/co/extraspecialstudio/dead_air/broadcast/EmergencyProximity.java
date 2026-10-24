package uk.co.extraspecialstudio.dead_air.broadcast;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.client.DeadAirSounds;
import uk.co.extraspecialstudio.dead_air.encounter.ListenerEncounter;
import uk.co.extraspecialstudio.dead_air.net.BroadcastMessagePacket;
import uk.co.extraspecialstudio.dead_air.net.DeadAirNet;
import uk.co.extraspecialstudio.dead_air.net.EbSignalPromptPacket;
import uk.co.extraspecialstudio.dead_air.net.ForceEmergencyTunePacket;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;
import uk.co.extraspecialstudio.dead_air.walkie.WalkieTalkieManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Hybrid Emergency Broadcast proximity (option D):
 * <ul>
 *   <li>Radio off / no walkie: no prompt, no proximity radio effects.</li>
 *   <li>Already on EB near a site: stay on EB; inject transmission once when close.</li>
 *   <li>On another station near a site: Yes/No "Tune now?" prompt (cooldown on dismiss).</li>
 *   <li>Story/camp proximity injects require T3 + EB (or accepting the prompt).</li>
 * </ul>
 */
public final class EmergencyProximity {
    private static final int CHECK_INTERVAL = 40;
    private static final Set<UUID> PROMPT_PENDING = new HashSet<>();

    public static void tick(MinecraftServer server) {
        if (!Config.broadcastsEnabled) return;
        if (server.getTickCount() % CHECK_INTERVAL != 0) return;
        PendingSites sites = PendingSites.get(server);
        BroadcastProgress data = BroadcastProgress.get(server);
        if (sites == null || data == null || sites.getSites().isEmpty()) return;

        int radius = Config.proximityDetectRadius;
        long radiusSq = (long) radius * radius;
        long now = server.overworld().getGameTime();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || ListenerEncounter.isParticipant(player)) continue;
            if (!BroadcastScheduler.hasT3(player)) continue;
            if (!WalkieTalkieManager.isListeningToRadio(player)) continue;

            BroadcastProgress.PlayerProgress progress = data.get(player.getUUID());
            PendingSites.Site nearest = nearestOwnedSite(sites, player, radiusSq);
            if (nearest == null) continue;

            String key = siteKey(nearest);
            if (progress.proximityInjected.contains(key)) continue;

            boolean onEb = isListeningOnEmergency(player);
            if (onEb) {
                inject(player, progress, nearest, key, data);
            } else {
                if (PROMPT_PENDING.contains(player.getUUID())) continue;
                Long until = progress.proximityPromptCooldownUntil.get(key);
                if (until != null && now < until) continue;
                PROMPT_PENDING.add(player.getUUID());
                DeadAirNet.sendTo(player, new EbSignalPromptPacket(key, nearest.kind));
            }
        }
    }

    public static void onTuneAccepted(ServerPlayer player, String siteKey) {
        if (player == null || siteKey == null || siteKey.isEmpty()) return;
        PROMPT_PENDING.remove(player.getUUID());
        if (!BroadcastScheduler.hasT3(player)) return;
        if (!WalkieTalkieManager.hasWalkieTalkieInInventory(player)) return;

        var emergency = StationRegistry.getStation(StationRegistry.EMERGENCY_BROADCAST_ID);
        if (emergency != null) {
            WalkieTalkieManager.tuneToStation(player, emergency);
            DeadAirNet.sendTo(player, new ForceEmergencyTunePacket(ForceEmergencyTunePacket.TUNE_EMERGENCY));
        }
        BroadcastProgress data = BroadcastProgress.get(player.server);
        PendingSites sites = PendingSites.get(player.server);
        if (data == null || sites == null) return;
        BroadcastProgress.PlayerProgress progress = data.get(player.getUUID());
        progress.proximityPromptCooldownUntil.remove(siteKey);
        PendingSites.Site site = findSite(sites, siteKey);
        if (site == null || progress.proximityInjected.contains(siteKey)) {
            data.setDirty();
            return;
        }
        int radius = Config.proximityDetectRadius;
        double dx = player.getX() - site.center.getX();
        double dz = player.getZ() - site.center.getZ();
        if (dx * dx + dz * dz > (long) radius * radius) {
            data.setDirty();
            return;
        }
        inject(player, progress, site, siteKey, data);
    }

    public static void onTuneDeclined(ServerPlayer player, String siteKey) {
        if (player == null) return;
        PROMPT_PENDING.remove(player.getUUID());
        if (siteKey == null || siteKey.isEmpty()) return;
        BroadcastProgress data = BroadcastProgress.get(player.server);
        if (data == null) return;
        BroadcastProgress.PlayerProgress progress = data.get(player.getUUID());
        long until = player.server.overworld().getGameTime()
            + (long) Config.proximityPromptCooldownSeconds * 20L;
        progress.proximityPromptCooldownUntil.put(siteKey, until);
        data.setDirty();
    }

    public static void clearPending(UUID playerId) {
        if (playerId != null) PROMPT_PENDING.remove(playerId);
    }

    private static void inject(ServerPlayer player, BroadcastProgress.PlayerProgress progress,
                               PendingSites.Site site, String key, BroadcastProgress data) {
        progress.proximityInjected.add(key);
        progress.proximityPromptCooldownUntil.remove(key);
        progress.decodedCount++;
        data.setDirty();

        boolean story = PendingSites.KIND_RELAY.equals(site.kind);
        String title = story ? "EMERGENCY PROXIMITY" : "DISTRESS PING";
        List<String> lines = proximityLines(site);
        DeadAirNet.sendTo(player, new BroadcastMessagePacket(title, lines, true, story));
        player.playNotifySound(DeadAirSounds.BROADCAST_ALERT.get(), SoundSource.RECORDS, 0.7f, 0.85f);
        player.playNotifySound(DeadAirSounds.BROADCAST_VOICE.get(), SoundSource.RECORDS, 0.85f, 0.9f);
        player.playNotifySound(DeadAirSounds.BROADCAST_DECODED.get(), SoundSource.RECORDS, 0.55f, 1.0f);
        try {
            BlockPos at = site.keyPos != null ? site.keyPos : site.center;
            uk.co.extraspecialstudio.dead_air.compat.MapWaypointCompat.markEbSite(player, at,
                story ? "Dead Air Relay" : "Dead Air Camp");
        } catch (Throwable ignored) {
        }
    }

    private static List<String> proximityLines(PendingSites.Site site) {
        BlockPos at = site.keyPos != null ? site.keyPos : site.center;
        String coords = "X " + at.getX() + "  Z " + at.getZ();
        if (PendingSites.KIND_RELAY.equals(site.kind)) {
            return List.of(
                "SIGNAL STRENGTH RISING. YOU ARE CLOSE.",
                "THE RELAY IS STILL BLEEDING STATIC AT " + coords + ".",
                "DO NOT TRANSMIT AFTER DARK.");
        }
        return List.of(
            "DISTRESS CARRIER LOCKED.",
            "CIVILIAN CAMP STILL BROADCASTING AT " + coords + ".",
            "SOMEONE IS WAITING.");
    }

    private static boolean isListeningOnEmergency(ServerPlayer player) {
        var stack = WalkieTalkieManager.findListeningWalkie(player);
        return WalkieTalkieManager.isWalkieTunedToStation(stack, StationRegistry.EMERGENCY_BROADCAST_ID);
    }

    private static PendingSites.Site nearestOwnedSite(PendingSites sites, ServerPlayer player, long radiusSq) {
        PendingSites.Site best = null;
        double bestDist = Double.MAX_VALUE;
        for (PendingSites.Site site : sites.getSites()) {
            if (!site.owner.equals(player.getUUID())) continue;
            if (!PendingSites.KIND_RELAY.equals(site.kind) && !PendingSites.KIND_CAMP.equals(site.kind)) continue;
            BlockPos c = site.center;
            double dx = player.getX() - c.getX();
            double dz = player.getZ() - c.getZ();
            double d = dx * dx + dz * dz;
            if (d <= radiusSq && d < bestDist) {
                bestDist = d;
                best = site;
            }
        }
        return best;
    }

    public static String siteKey(PendingSites.Site site) {
        if (site.siteId != null) return site.siteId.toString();
        return legacySiteKey(site);
    }

    private static String legacySiteKey(PendingSites.Site site) {
        return site.owner + "|" + site.kind + "|" + site.center.asLong();
    }

    /**
     * True if this site was already handled. Remaps pre-siteId keys onto the stable siteId key once.
     */
    public static boolean alreadyHandled(@javax.annotation.Nullable BroadcastProgress data, java.util.Set<String> set,
                                         PendingSites.Site site, String key) {
        if (set.contains(key)) return true;
        if (site.siteId == null) return false;
        String legacy = legacySiteKey(site);
        if (!set.contains(legacy)) return false;
        set.remove(legacy);
        set.add(key);
        if (data != null) data.setDirty();
        return true;
    }

    private static PendingSites.Site findSite(PendingSites sites, String key) {
        for (PendingSites.Site site : sites.getSites()) {
            if (siteKey(site).equals(key)) return site;
        }
        return null;
    }

    private EmergencyProximity() {}
}
