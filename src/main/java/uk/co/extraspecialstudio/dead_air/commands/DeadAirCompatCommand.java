package uk.co.extraspecialstudio.dead_air.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import uk.co.extraspecialstudio.dead_air.compat.DeadAirCompat;

/** {@code /deadair compat status} â€” loaded + enabled soft-compat integrators. */
public final class DeadAirCompatCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("deadair").then(compat()));
        dispatcher.register(Commands.literal("dead_air").then(compat()));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> compat() {
        return Commands.literal("compat")
            .requires(s -> s.hasPermission(2))
            .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())));
    }

    private static int status(CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal("Dead Air soft-compat:")
            .withStyle(ChatFormatting.GOLD), false);
        for (String line : DeadAirCompat.statusLines()) {
            src.sendSuccess(() -> Component.literal("  " + line).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private DeadAirCompatCommand() {}
}
