package uk.co.extraspecialstudio.dead_air.encounter;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import uk.co.extraspecialstudio.dead_air.broadcast.BroadcastProgress;
import uk.co.extraspecialstudio.dead_air.broadcast.StoryStage;
import uk.co.extraspecialstudio.dead_air.client.DeadAirSounds;
import uk.co.extraspecialstudio.dead_air.item.DeadAirItems;
import uk.co.extraspecialstudio.dead_air.walkie.WalkieTalkieManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server side of the T3 Transmit button. Answering on the hidden frequency summons The Listener. */
public final class Transmissions {
    private static final float MATCH_TOLERANCE = 0.15f;
    private static final int RELAY_RADIUS = 32;
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

    public static void onTransmit(ServerPlayer player, float frequency) {
        // Boss summon is impossible without a T3 radio in hand (Transmit button is T3-only).
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (!WalkieTalkieManager.isT3(main) && !WalkieTalkieManager.isT3(off)) {
            say(player, Component.translatable("message.dead_air.transmit.need_radio"), ChatFormatting.DARK_GRAY);
            return;
        }
        long now = player.serverLevel().getGameTime();
        Long last = COOLDOWN.get(player.getUUID());
        if (last != null && now - last < 60) return;
        COOLDOWN.put(player.getUUID(), now);

        player.playNotifySound(DeadAirSounds.STATIC.get(), SoundSource.RECORDS, 0.6f, 1.4f);
        BroadcastProgress data = BroadcastProgress.get(player.server);
        if (data == null) return;
        BroadcastProgress.PlayerProgress p = data.get(player.getUUID());
        float hidden = data.getHiddenFrequency(player.server.overworld().getRandom());

        if (Math.abs(frequency - hidden) > MATCH_TOLERANCE || p.stage < StoryStage.FINAL_BROADCAST) {
            say(player, Component.translatable("message.dead_air.transmit.static", String.format("%.1f", frequency)), ChatFormatting.GRAY);
            return;
        }
        if (p.stage >= StoryStage.DEFEATED) {
            say(player, Component.translatable("message.dead_air.transmit.silent"), ChatFormatting.GRAY);
            return;
        }
        if (ListenerEncounter.isParticipant(player)) return;
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD || !level.isNight()) {
            say(player, Component.translatable("message.dead_air.transmit.daylight"), ChatFormatting.DARK_GRAY);
            return;
        }
        if (!nearRelay(player, p)) {
            say(player, Component.translatable("message.dead_air.transmit.no_relay"), ChatFormatting.DARK_GRAY);
            return;
        }
        if (p.stage == StoryStage.FINAL_BROADCAST) {
            ListenerEncounter.start(player, ListenerEncounter.Mode.FIRST);
            return;
        }
        if (hasBreaker(player)) {
            ListenerEncounter.start(player, ListenerEncounter.Mode.FINAL);
        } else {
            say(player, Component.translatable("message.dead_air.transmit.need_breaker"), ChatFormatting.DARK_PURPLE);
        }
    }

    private static boolean nearRelay(ServerPlayer player, BroadcastProgress.PlayerProgress p) {
        BlockPos at = player.blockPosition();
        for (BlockPos relay : p.relaysFound) {
            if (relay.distSqr(at) <= RELAY_RADIUS * RELAY_RADIUS) return true;
        }
        return false;
    }

    public static boolean hasBreaker(ServerPlayer player) {
        return player.getInventory().contains(new ItemStack(DeadAirItems.SIGNAL_BREAKER.get()));
    }

    private static void say(ServerPlayer player, Component text, ChatFormatting color) {
        player.displayClientMessage(text.copy().withStyle(color), false);
    }

    private Transmissions() {}
}
