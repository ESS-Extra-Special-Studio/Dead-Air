package uk.co.extraspecialstudio.dead_air.broadcast;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.block.BrokenRelayBlock;
import uk.co.extraspecialstudio.dead_air.client.DeadAirSounds;
import uk.co.extraspecialstudio.dead_air.item.RelayLogItem;

import java.util.List;

/** Right-clicking a Broken Relay: hand over its log and advance the story. */
public final class RelayInvestigation {

    public static void investigate(ServerPlayer player, BlockPos pos, BlockState state) {
        ServerLevel level = player.serverLevel();
        BroadcastProgress data = BroadcastProgress.get(player.server);
        if (data == null) return;
        BroadcastProgress.PlayerProgress p = data.get(player.getUUID());

        for (BlockPos found : p.relaysFound) {
            if (found.distSqr(pos) < 16 * 16) {
                player.displayClientMessage(Component.translatable("message.dead_air.relay.already").withStyle(ChatFormatting.GRAY), true);
                return;
            }
        }

        int index = p.relaysFound.size();
        p.relaysFound.add(pos.immutable());
        if (p.targetRelay != null && p.targetRelay.distSqr(new BlockPos(pos.getX(), p.targetRelay.getY(), pos.getZ())) < 96 * 96) {
            p.targetRelay = null;
        }
        if (p.stage < StoryStage.RELAYS) p.stage = StoryStage.RELAYS;
        data.setDirty();

        if (!player.getInventory().add(RelayLogItem.create(index, logLines(index)))) {
            player.drop(RelayLogItem.create(index, logLines(index)), false);
        }
        if (state.getValue(BrokenRelayBlock.ACTIVE)) {
            level.setBlock(pos, state.setValue(BrokenRelayBlock.ACTIVE, false), Block.UPDATE_ALL);
        }
        level.playSound(null, pos, DeadAirSounds.RELAY_PING.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.05);

        int required = Config.storyRelaysRequired;
        player.sendSystemMessage(Component.translatable("message.dead_air.relay.recovered",
            Math.min(index + 1, required), required).withStyle(ChatFormatting.AQUA));
        if (p.relaysFound.size() >= required && p.stage <= StoryStage.RELAYS) {
            p.storyQueued = true;
            data.setDirty();
            player.sendSystemMessage(Component.translatable("message.dead_air.relay.all_found").withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    public static List<String> logLines(int index) {
        return switch (index) {
            case 0 -> List.of(
                "Day 41. Towers keep catching a bell that isn't ours.",
                "It echoes our traffic a few seconds late â€” same voice, wrong words.",
                "Ops called it a reflection. Reflections don't rewrite the sentence.");
            case 1 -> List.of(
                "Day 44. Marsh keyed the bell band for a range check.",
                "An hour later something stepped out of the fog. Tall. Antenna spine.",
                "Rounds did nothing. It backed off when we smashed the glowing anchors.");
            default -> List.of(
                "Day 47. It hunts by sound, not sight.",
                "Hold still. Crouch. Don't swing, don't eat, don't run when it goes quiet.",
                "Break the anchors or outlast it. We still can't kill the thing.");
        };
    }

    private RelayInvestigation() {}
}
