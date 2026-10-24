package uk.co.extraspecialstudio.dead_air.broadcast;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements;
import uk.co.extraspecialstudio.dead_air.api.events.DeadAirEvents;
import uk.co.extraspecialstudio.dead_air.api.events.types.DeadAirEmergencySignalEvent;
import uk.co.extraspecialstudio.dead_air.base.PlayerBaseManager;
import uk.co.extraspecialstudio.dead_air.sound.DeadAirSounds;
import uk.co.extraspecialstudio.dead_air.encounter.ListenerEncounter;
import uk.co.extraspecialstudio.dead_air.item.RadioItem;
import uk.co.extraspecialstudio.dead_air.net.BroadcastMessagePacket;
import uk.co.extraspecialstudio.dead_air.net.DeadAirNet;
import uk.co.extraspecialstudio.dead_air.net.ForceEmergencyTunePacket;
import java.util.List;

/**
 * Decides when each eligible player hears a broadcast, then delivers it.
 * Eligible = T3 radio in inventory, or a claimed radio-panel base.
 * Casts may fire any time of day; spacing uses {@code nextBroadcastDay} (default: daily).
 * T3 radios decode; EB hard-lock only if config force-tunes story casts.
 */
public final class BroadcastScheduler {
    private static final int CHECK_INTERVAL = 40;

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_INTERVAL != 0) return;
        BroadcastProgress data = BroadcastProgress.get(server);
        if (data == null) return;
        long day = server.overworld().getDayTime() / 24000L;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            BroadcastProgress.PlayerProgress progress = data.get(player.getUUID());
            if (progress.storyQueued && canHearEmergency(player) && !ListenerEncounter.isInvolved(player)) {
                List<BroadcastEvent> story = BroadcastEventRegistry.eligible(BroadcastEvent.Category.STORY, player, progress);
                if (story.isEmpty()) {
                    progress.storyQueued = false;
                    data.setDirty();
                } else if (deliver(player, story.get(0))) {
                    progress.storyQueued = false;
                    data.setDirty();
                    continue;
                } else {
                    continue;
                }
            }
            if (!Config.broadcastsEnabled || !canHearEmergency(player)) continue;
            if (progress.nextBroadcastDay < 0) {
                progress.nextBroadcastDay = day;
                data.setDirty();
            }
            if (day < progress.nextBroadcastDay) continue;
            if (ListenerEncounter.isInvolved(player)) continue;

            BroadcastEvent event = pick(player, progress);
            if (event == null) continue;
            if (!deliver(player, event)) continue;
            burnCooldown(player, progress, data, day);
        }
    }

    /**
     * T3 handheld can tune the EB channel; a claimed radio-panel base also counts as an audience
     * (base alert / camp follow-up). Either is enough to receive scheduled casts.
     */
    public static boolean canHearEmergency(Player player) {
        if (player == null) return false;
        if (hasT3(player)) return true;
        if (player instanceof ServerPlayer sp) {
            return PlayerBaseManager.hasBase(sp.server, sp.getUUID());
        }
        return false;
    }

    /** True when at least one online non-spectator player can hear / follow up on EB content. */
    public static boolean serverHasEbAudience(MinecraftServer server) {
        if (server == null) return false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            if (canHearEmergency(player)) return true;
        }
        return false;
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

        boolean decoded = hasT3(player);
        if (decoded) progress.decodedCount++;
        boolean storyLike = event.category() == BroadcastEvent.Category.STORY
            || event.category() == BroadcastEvent.Category.DEFENSE;
        DeadAirNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
            new BroadcastMessagePacket(event.title(), decoded ? lines : BroadcastText.scramble(lines), decoded, storyLike));
        if (storyLike && Config.forceTuneOnStoryBroadcast) {
            DeadAirNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ForceEmergencyTunePacket(ForceEmergencyTunePacket.TUNE_EMERGENCY));
        }
        player.playNotifySound(DeadAirSounds.BROADCAST_ALERT.get(), SoundSource.RECORDS, 0.8f, 1.0f);
        playBroadcastVoice(player, event, 0.9f, 1.0f);
        if (decoded) player.playNotifySound(DeadAirSounds.BROADCAST_DECODED.get(), SoundSource.RECORDS, 0.7f, 1.0f);
        // Chat lines + HUD come from BroadcastMessagePacket. Site coords / tune hint come from
        // PendingSites.notifySitePin when a pin is created — avoid triple notify spam here.
        DeadAirEvents.post(new DeadAirEmergencySignalEvent(event.id(), event.category().name().toLowerCase()));
        awardBroadcastAdvancements(player, event);
        return true;
    }

    private static void awardBroadcastAdvancements(ServerPlayer player, BroadcastEvent event) {
        DeadAirAdvancements.award(player, DeadAirAdvancements.EB_FIRST);
        if (event.category() == BroadcastEvent.Category.SURVIVOR) {
            DeadAirAdvancements.award(player, DeadAirAdvancements.EB_SURVIVOR_CALL);
        }
        var id = event.id();
        if (ExtraBroadcasts.DEAD_CHANNEL_AUCTION.equals(id)) {
            DeadAirAdvancements.award(player, DeadAirAdvancements.EB_AUCTION_HEAR);
        } else if (ExtraBroadcasts.PIRATE_FM.equals(id)) {
            DeadAirAdvancements.award(player, DeadAirAdvancements.EB_PIRATE);
        } else if (StoryBroadcasts.RELAY_TRANSMISSION.equals(id)) {
            DeadAirAdvancements.award(player, DeadAirAdvancements.STORY_RELAY_BROADCAST);
        } else if (StoryBroadcasts.IT_HEARS_YOU.equals(id)) {
            DeadAirAdvancements.award(player, DeadAirAdvancements.STORY_IT_HEARS_YOU);
        }
    }

    public static boolean hasT3(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof RadioItem w && w.getTier() == RadioItem.Tier.T3) return true;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.getItem() instanceof RadioItem w && w.getTier() == RadioItem.Tier.T3) return true;
        }
        return false;
    }

    /**
     * Camp / convoy / relay casts use the survivor distress VO; everything else keeps the
     * garbled {@link DeadAirSounds#BROADCAST_VOICE} bank.
     */
    public static boolean usesDistressVoice(BroadcastEvent event) {
        if (event == null) return false;
        var id = event.id();
        return uk.co.extraspecialstudio.dead_air.survivor.SurvivorBroadcasts.DISTRESS_CALL.equals(id)
            || ExtraBroadcasts.REFUGEE_CONVOY.equals(id)
            || StoryBroadcasts.RELAY_TRANSMISSION.equals(id);
    }

    public static boolean usesDistressVoiceForSite(String kind) {
        return PendingSites.KIND_CAMP.equals(kind)
            || PendingSites.KIND_CONVOY.equals(kind)
            || PendingSites.KIND_RELAY.equals(kind);
    }

    public static void playBroadcastVoice(ServerPlayer player, BroadcastEvent event, float volume, float pitch) {
        if (usesDistressVoice(event)) {
            player.playNotifySound(DeadAirSounds.BROADCAST_DISTRESS_CALL.get(), SoundSource.RECORDS, volume, pitch);
        } else {
            player.playNotifySound(DeadAirSounds.BROADCAST_VOICE.get(), SoundSource.RECORDS, volume, pitch);
        }
    }

    public static void playBroadcastVoiceForSite(ServerPlayer player, String kind, float volume, float pitch) {
        if (usesDistressVoiceForSite(kind)) {
            player.playNotifySound(DeadAirSounds.BROADCAST_DISTRESS_CALL.get(), SoundSource.RECORDS, volume, pitch);
        } else {
            player.playNotifySound(DeadAirSounds.BROADCAST_VOICE.get(), SoundSource.RECORDS, volume, pitch);
        }
    }

    @Deprecated
    public static boolean hasDecoder(Player player) {
        return hasT3(player);
    }

    private BroadcastScheduler() {}
}
