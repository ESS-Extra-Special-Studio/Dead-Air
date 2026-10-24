package uk.co.extraspecialstudio.dead_air.broadcast;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.api.events.DeadAirEvents;
import uk.co.extraspecialstudio.dead_air.api.events.types.DeadAirEmergencySignalEvent;
import uk.co.extraspecialstudio.dead_air.base.PlayerBaseManager;
import uk.co.extraspecialstudio.dead_air.client.DeadAirSounds;
import uk.co.extraspecialstudio.dead_air.encounter.ListenerEncounter;
import uk.co.extraspecialstudio.dead_air.item.WalkieItem;
import uk.co.extraspecialstudio.dead_air.net.BroadcastMessagePacket;
import uk.co.extraspecialstudio.dead_air.net.DeadAirNet;
import uk.co.extraspecialstudio.dead_air.net.ForceEmergencyTunePacket;

import java.util.List;

/**
 * Decides when each eligible player hears a broadcast, then delivers it.
 * Eligible = T3 radio in inventory, or a claimed radio-panel base.
 * T3 Radios decode transmissions; EB is not hard-locked unless config force-tunes story casts.
 */
public final class BroadcastScheduler {
    private static final int CHECK_INTERVAL = 40;
    private static final long NIGHT_START = 13000L;
    private static final long NIGHT_END = 23000L;

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_INTERVAL != 0) return;
        BroadcastProgress data = BroadcastProgress.get(server);
        if (data == null) return;
        long dayTime = server.overworld().getDayTime();
        long day = dayTime / 24000L;
        long time = dayTime % 24000L;
        boolean night = time >= NIGHT_START && time <= NIGHT_END;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            BroadcastProgress.PlayerProgress progress = data.get(player.getUUID());
            if (progress.storyQueued && night && canHearEmergency(player) && !ListenerEncounter.isParticipant(player)) {
                List<BroadcastEvent> story = BroadcastEventRegistry.eligible(BroadcastEvent.Category.STORY, player, progress);
                progress.storyQueued = false;
                data.setDirty();
                if (!story.isEmpty()) deliver(player, story.get(0));
                continue;
            }
            if (!Config.broadcastsEnabled || !canHearEmergency(player)) continue;
            if (progress.nextBroadcastDay < 0) {
                progress.nextBroadcastDay = day;
                data.setDirty();
            }
            if (!night || day < progress.nextBroadcastDay) continue;
            if (ListenerEncounter.isParticipant(player)) continue;

            BroadcastEvent event = pick(player, progress);
            int span = Math.max(0, Config.broadcastMaxDays - Config.broadcastMinDays);
            progress.nextBroadcastDay = day + Config.broadcastMinDays + (span > 0 ? player.getRandom().nextInt(span + 1) : 0);
            data.setDirty();
            if (event != null) deliver(player, event);
        }
    }

    /** Shared day cooldown after a successful scheduled or explore cast. */
    public static void burnCooldown(ServerPlayer player, BroadcastProgress.PlayerProgress progress,
                                    BroadcastProgress data, long day) {
        int span = Math.max(0, Config.broadcastMaxDays - Config.broadcastMinDays);
        progress.nextBroadcastDay = day + Config.broadcastMinDays
            + (span > 0 ? player.getRandom().nextInt(span + 1) : 0);
        data.setDirty();
    }

    public static BroadcastEvent pick(ServerPlayer player, BroadcastProgress.PlayerProgress progress) {
        List<BroadcastEvent> story = BroadcastEventRegistry.eligible(BroadcastEvent.Category.STORY, player, progress);
        // High-priority story (e.g. IT_HEARS_YOU) always wins so side events never soft-lock progression.
        if (!story.isEmpty() && story.get(0).priority() >= 5) return story.get(0);

        List<BroadcastEvent> side = new java.util.ArrayList<>();
        if (Config.survivorsEnabled) {
            side.addAll(BroadcastEventRegistry.eligible(BroadcastEvent.Category.SURVIVOR, player, progress));
            side.addAll(BroadcastEventRegistry.eligible(BroadcastEvent.Category.TRADERS, player, progress));
        }
        side.addAll(BroadcastEventRegistry.eligible(BroadcastEvent.Category.DEFENSE, player, progress));
        if (story.isEmpty() && side.isEmpty()) return null;
        if (side.isEmpty()) return story.get(0);
        if (story.isEmpty()) return side.get(player.getRandom().nextInt(side.size()));
        return player.getRandom().nextFloat() < 0.65f ? story.get(0) : side.get(player.getRandom().nextInt(side.size()));
    }

    /** Run the event and send its transmission to the player. Returns false if the event cancelled itself. */
    public static boolean deliver(ServerPlayer player, BroadcastEvent event) {
        BroadcastProgress data = BroadcastProgress.get(player.getServer());
        if (data == null) return false;
        BroadcastProgress.PlayerProgress progress = data.get(player.getUUID());
        List<String> lines;
        try {
            lines = event.start(player, progress);
        } catch (Exception e) {
            Dead_air.LOGGER.error("[Dead Air] Broadcast event {} failed to start", event.id(), e);
            return false;
        }
        data.setDirty();
        if (lines == null || lines.isEmpty()) return false;

        // T3 itself decodes (Signal Decoder is the T2â†’T3 upgrade, not a separate inventory gate).
        boolean decoded = hasT3(player);
        if (decoded) progress.decodedCount++;
        boolean storyLike = event.category() == BroadcastEvent.Category.STORY
            || event.category() == BroadcastEvent.Category.DEFENSE;
        DeadAirNet.sendTo(player,
            new BroadcastMessagePacket(event.title(), decoded ? lines : BroadcastText.scramble(lines), decoded, storyLike));
        // Survivor/trader casts never hard-lock the dial. Story/defense only if config asks for it.
        if (storyLike && Config.forceTuneOnStoryBroadcast) {
            DeadAirNet.sendTo(player, new ForceEmergencyTunePacket(ForceEmergencyTunePacket.TUNE_EMERGENCY));
        }
        player.playNotifySound(DeadAirSounds.BROADCAST_ALERT.get(), SoundSource.RECORDS, 0.8f, 1.0f);
        player.playNotifySound(DeadAirSounds.BROADCAST_VOICE.get(), SoundSource.RECORDS, 0.9f, 1.0f);
        if (decoded) player.playNotifySound(DeadAirSounds.BROADCAST_DECODED.get(), SoundSource.RECORDS, 0.7f, 1.0f);
        DeadAirEvents.post(new DeadAirEmergencySignalEvent(event.id(), event.category().name().toLowerCase()));
        return true;
    }

    public static boolean hasT3(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof WalkieItem w && w.getTier() == WalkieItem.Tier.T3) return true;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.getItem() instanceof WalkieItem w && w.getTier() == WalkieItem.Tier.T3) return true;
        }
        return false;
    }

    /** T3 handheld or claimed radio-panel base. */
    public static boolean canHearEmergency(Player player) {
        if (player == null) return false;
        if (hasT3(player)) return true;
        if (player instanceof ServerPlayer sp) {
            return PlayerBaseManager.hasBase(sp.server, sp.getUUID());
        }
        return false;
    }

    /** At least one online non-spectator player can hear / follow up on EB content. */
    public static boolean serverHasEbAudience(MinecraftServer server) {
        if (server == null) return false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            if (canHearEmergency(player)) return true;
        }
        return false;
    }

    /**
     * @deprecated Signal Decoder is no longer an inventory decode gate; T3 radios decode.
     *             Prefer {@link #hasT3(Player)}. Kept as an alias for older call sites / commands.
     */
    @Deprecated
    public static boolean hasDecoder(Player player) {
        return hasT3(player);
    }

    private BroadcastScheduler() {}
}
