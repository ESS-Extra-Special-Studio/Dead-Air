package uk.co.extraspecialstudio.dead_air.broadcast;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.PacketDistributor;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.sound.DeadAirSounds;
import uk.co.extraspecialstudio.dead_air.encounter.ListenerEncounter;
import uk.co.extraspecialstudio.dead_air.net.BroadcastMessagePacket;
import uk.co.extraspecialstudio.dead_air.net.DeadAirNet;
import uk.co.extraspecialstudio.dead_air.net.EbSignalPromptPacket;
import uk.co.extraspecialstudio.dead_air.net.ForceEmergencyTunePacket;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;
import uk.co.extraspecialstudio.dead_air.radio.RadioManager;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hybrid Emergency Broadcast proximity (option D):
 * <ul>
 *   <li>Radio off / no radio: no prompt, no proximity radio effects.</li>
 *   <li>Already on EB near a site: stay on EB; inject transmission once when close.</li>
 *   <li>On another station near a site: Yes/No "Tune now?" prompt (cooldown on dismiss).</li>
 *   <li>Story/camp proximity injects require T3 + EB (or accepting the prompt).</li>
 * </ul>
 */
public final class EmergencyProximity {
    private static final int CHECK_INTERVAL = 40;
    /** If the client never answers (packet loss / screen never opened), free the player. */
    private static final int PROMPT_TIMEOUT_TICKS = 30 * 20;

    private static final class PendingPrompt {
        final String siteKey;
        final long expiresAt;

        PendingPrompt(String siteKey, long expiresAt) {
            this.siteKey = siteKey;
            this.expiresAt = expiresAt;
        }
    }

    /** Players with an unanswered tune prompt (siteKey + expiry). */
    private static final Map<UUID, PendingPrompt> PROMPT_PENDING = new HashMap<>();

    public static void tick(MinecraftServer server) {
        if (!Config.broadcastsEnabled) return;
        if (server.getTickCount() % CHECK_INTERVAL != 0) return;
        PendingSites sites = PendingSites.get(server);
        BroadcastProgress data = BroadcastProgress.get(server);
        if (sites == null || data == null || sites.getSites().isEmpty()) return;

        int radius = Config.proximityDetectRadius;
        long radiusSq = (long) radius * radius;
        long now = server.overworld().getGameTime();
        expireStalePrompts(server, now);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || ListenerEncounter.isInvolved(player)) continue;
            // Site pins are overworld — same gate as EbHuntTracker.
            if (player.level() != server.overworld()) continue;
            if (!BroadcastScheduler.hasT3(player)) continue;
            if (!RadioManager.isListeningToRadio(player)) continue;

            BroadcastProgress.PlayerProgress progress = data.get(player.getUUID());
            PendingSites.Site nearest = nearestOwnedSite(sites, player, radiusSq);
            if (nearest == null) continue;

            String key = siteKey(nearest);
            if (alreadyHandled(data, progress.proximityInjected, nearest, key)) continue;

            boolean onEb = isListeningOnEmergency(player);
            if (onEb) {
                inject(player, progress, nearest, key, data);
            } else {
                if (PROMPT_PENDING.containsKey(player.getUUID())) continue;
                Long until = progress.proximityPromptCooldownUntil.get(key);
                if (until != null && now < until) continue;
                PROMPT_PENDING.put(player.getUUID(), new PendingPrompt(key, now + PROMPT_TIMEOUT_TICKS));
                DeadAirNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new EbSignalPromptPacket(key, nearest.kind));
            }
        }
    }

    /** Treat unanswered prompts as a decline so proximity is never stuck for the session. */
    private static void expireStalePrompts(MinecraftServer server, long now) {
        Iterator<Map.Entry<UUID, PendingPrompt>> it = PROMPT_PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PendingPrompt> e = it.next();
            if (now < e.getValue().expiresAt) continue;
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player != null) {
                applyDeclineCooldown(player, e.getValue().siteKey);
            }
        }
    }

    /** Player accepted the tune prompt: switch to EB and inject if still in range. */
    public static void onTuneAccepted(ServerPlayer player, String siteKey) {
        if (player == null || siteKey == null || siteKey.isEmpty()) return;
        PROMPT_PENDING.remove(player.getUUID());
        if (!BroadcastScheduler.hasT3(player)) return;
        if (!RadioManager.hasRadioInInventory(player)) return;

        var emergency = StationRegistry.getStation(StationRegistry.EMERGENCY_BROADCAST_ID);
        if (emergency != null) {
            RadioManager.tuneToStation(player, emergency);
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
        applyDeclineCooldown(player, siteKey);
    }

    private static void applyDeclineCooldown(ServerPlayer player, String siteKey) {
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
        DeadAirNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
            new BroadcastMessagePacket(title, lines, true, story));
        player.playNotifySound(DeadAirSounds.BROADCAST_ALERT.get(), SoundSource.RECORDS, 0.7f, 0.85f);
        BroadcastScheduler.playBroadcastVoiceForSite(player, site.kind, 0.85f, 0.9f);
        player.playNotifySound(DeadAirSounds.BROADCAST_DECODED.get(), SoundSource.RECORDS, 0.55f, 1.0f);
        try {
            // Same stable id + label as PendingSites.create/place — refresh, do not stack.
            BlockPos at = site.keyPos != null ? site.keyPos : site.center;
            uk.co.extraspecialstudio.dead_air.compat.MapWaypointCompat.markEbSite(
                player, at, PendingSites.waypointLabel(site.kind), PendingSites.waypointStableId(site));
        } catch (Throwable ignored) {
        }
        // Mayday grants on opening crash loot (DeadAirAdvancementEvents), not the proximity ping.
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
        if (PendingSites.KIND_CAMP.equals(site.kind)) {
            return List.of(
                "DISTRESS CARRIER LOCKED.",
                "CIVILIAN CAMP STILL BROADCASTING AT " + coords + ".",
                "SOMEONE IS WAITING.");
        }
        if (PendingSites.KIND_MISSING.equals(site.kind)) {
            return List.of(
                "ROSTER PING REACQUIRED.",
                "LAST KNOWN CONTACT NEAR " + coords + ".",
                "MOVE IN BEFORE THE STATIC SWALLOWS THEM.");
        }
        if (PendingSites.KIND_AUCTION.equals(site.kind)) {
            return List.of(
                "DEAD-CHANNEL TRADE MARKER LOCKED.",
                "MEET SITE AT " + coords + ".",
                "BRING SCRAP. BRING CAUTION.");
        }
        if (PendingSites.KIND_CONVOY.equals(site.kind)) {
            return List.of(
                "REFUGEE CONVOY BEACON LOCKED.",
                "ROUTE POINT AT " + coords + ".",
                "THEY WILL NOT WAIT LONG.");
        }
        if (PendingSites.KIND_CRASH.equals(site.kind)) {
            return List.of(
                "WRECKAGE BEACON LOCKED.",
                "DOWNED CRAFT NEAR " + coords + ".",
                "SALVAGE WHAT YOU CAN.");
        }
        return List.of(
            "EMERGENCY CARRIER LOCKED.",
            "SITE MARKED AT " + coords + ".",
            "INVESTIGATE.");
    }

    private static boolean isListeningOnEmergency(ServerPlayer player) {
        var stack = RadioManager.findListeningRadio(player);
        return RadioManager.isRadioTunedToStation(stack, StationRegistry.EMERGENCY_BROADCAST_ID);
    }

    private static PendingSites.Site nearestOwnedSite(PendingSites sites, ServerPlayer player, long radiusSq) {
        PendingSites.Site best = null;
        double bestDist = Double.MAX_VALUE;
        for (PendingSites.Site site : sites.getSites()) {
            if (!site.owner.equals(player.getUUID())) continue;
            // All EB site kinds can inject when you walk into range (not just relay/camp).
            if (site.kind == null || site.kind.isEmpty()) continue;
            // Wait until the kit exists — injecting on an unplaced pin burns the one-shot line.
            if (!site.placed && !PendingSites.KIND_MISSING.equals(site.kind)) continue;
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
        // Legacy fallback before siteId existed (center-based — breaks if the pin drifts).
        return legacySiteKey(site);
    }

    private static String legacySiteKey(PendingSites.Site site) {
        return site.owner + "|" + site.kind + "|" + site.center.asLong();
    }

    /**
     * True if this site was already handled. Remaps pre-siteId keys
     * ({@code owner|kind|center}) onto the stable {@code siteId} key once and dirties progress.
     */
    static boolean alreadyHandled(@Nullable BroadcastProgress data, java.util.Set<String> set,
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
