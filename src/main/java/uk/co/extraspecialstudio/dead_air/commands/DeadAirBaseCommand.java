package uk.co.extraspecialstudio.dead_air.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import uk.co.extraspecialstudio.dead_air.base.PlayerBaseManager;
import uk.co.extraspecialstudio.dead_air.compat.SurvivorMapCompat;
import uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerDetector;

/** {@code /deadair base set|unset|status} â€” player radio-panel bases. */
public final class DeadAirBaseCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("deadair").then(base()));
        dispatcher.register(Commands.literal("dead_air").then(base()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> base() {
        return Commands.literal("base")
            .requires(src -> src.hasPermission(0))
            .then(Commands.literal("set").executes(ctx -> set(ctx.getSource())))
            .then(Commands.literal("unset").executes(ctx -> unset(ctx.getSource())))
            .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())));
    }

    private static int set(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        BlockPos panel = findTargetPanel(player);
        if (panel == null) {
            src.sendFailure(Component.translatable("message.dead_air.base.look_at_panel"));
            return 0;
        }
        return PlayerBaseManager.setBase(player, panel) ? 1 : 0;
    }

    private static int unset(CommandSourceStack src) throws CommandSyntaxException {
        return PlayerBaseManager.unsetBase(src.getPlayerOrException()) ? 1 : 0;
    }

    private static int status(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        src.sendSuccess(() -> PlayerBaseManager.statusLine(player.server, player.getUUID()), false);
        src.sendSuccess(() -> Component.literal("map: " + SurvivorMapCompat.status()), false);
        return 1;
    }

    /** Same radius as RadioTowers opencall {@code findNearbyRadioPanel}. */
    private static final int NEARBY_PANEL_RADIUS = 10;

    private static BlockPos findTargetPanel(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return null;

        // Prefer the block under the crosshair when it is a panel.
        HitResult hit = player.pick(8.0, 0f, false);
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            if (ApocalypseTowerDetector.isRadioPanel(level, pos)) {
                return pos;
            }
        }

        // Fallback: nearest panel within ~10 blocks (tower panels are often above feet level).
        return ApocalypseTowerDetector.findNearestRadioPanel(level, player.blockPosition(), NEARBY_PANEL_RADIUS);
    }

    private DeadAirBaseCommand() {}
}
