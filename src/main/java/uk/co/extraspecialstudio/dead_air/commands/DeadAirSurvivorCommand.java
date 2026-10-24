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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import uk.co.extraspecialstudio.dead_air.base.PlayerBaseManager;
import uk.co.extraspecialstudio.dead_air.base.PlayerBaseStorage;
import uk.co.extraspecialstudio.dead_air.entity.DeadAirEntities;
import uk.co.extraspecialstudio.dead_air.net.SurvivorActionPacket;
import uk.co.extraspecialstudio.dead_air.survivor.SurvivorEntity;

/** {@code /deadair survivor spawn|atbase|gear} â€” debug helpers for the vertical slice. */
public final class DeadAirSurvivorCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("deadair").then(survivor()));
        dispatcher.register(Commands.literal("dead_air").then(survivor()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> survivor() {
        return Commands.literal("survivor")
            .requires(src -> src.hasPermission(2))
            .then(Commands.literal("spawn").executes(ctx -> spawn(ctx.getSource(), false)))
            .then(Commands.literal("atbase").executes(ctx -> spawn(ctx.getSource(), true)))
            .then(Commands.literal("gear").executes(ctx -> gear(ctx.getSource())));
    }

    private static int spawn(CommandSourceStack src, boolean atBase) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        SurvivorEntity survivor = DeadAirEntities.SURVIVOR.get().create(level);
        if (survivor == null) {
            src.sendFailure(Component.literal("Failed to create survivor"));
            return 0;
        }
        BlockPos at = player.blockPosition().above();
        if (atBase) {
            PlayerBaseStorage.Base base = PlayerBaseManager.getBase(player.server, player.getUUID());
            if (base == null || base.dimension != level.dimension()) {
                src.sendFailure(Component.translatable("message.dead_air.base.none"));
                return 0;
            }
            at = base.panelPos.above();
        }
        survivor.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot(), 0);
        survivor.initRandom(level.random, player.getUUID());
        level.addFreshEntity(survivor);
        survivor.handleAction(player, SurvivorActionPacket.RECRUIT);
        if (atBase) {
            survivor.handleAction(player, SurvivorActionPacket.SEND_TO_BASE);
        }
        src.sendSuccess(() -> Component.literal("Spawned " + survivor.getName().getString()
            + (atBase ? " at base" : " (following)")), true);
        return 1;
    }

    private static int gear(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        player.getInventory().add(new ItemStack(Items.IRON_SWORD));
        player.getInventory().add(new ItemStack(Items.IRON_CHESTPLATE));
        player.getInventory().add(new ItemStack(Items.SHIELD));
        player.getInventory().add(new ItemStack(Items.IRON_HELMET));
        src.sendSuccess(() -> Component.literal("Gave test gear â€” right-click a survivor while holding an item to equip, or use Equipment in dialogue"), false);
        return 1;
    }

    private DeadAirSurvivorCommand() {}
}
