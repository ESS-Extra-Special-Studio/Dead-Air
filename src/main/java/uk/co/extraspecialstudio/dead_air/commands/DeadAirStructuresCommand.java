package uk.co.extraspecialstudio.dead_air.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import uk.co.extraspecialstudio.dead_air.broadcast.BroadcastProgress;
import uk.co.extraspecialstudio.dead_air.broadcast.PendingSites;
import uk.co.extraspecialstudio.dead_air.broadcast.StoryStage;

import java.util.List;

/**
 * {@code /dead_air structures ...} — place RadioTowers and EB site kits at the player.
 */
public final class DeadAirStructuresCommand {

    private DeadAirStructuresCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> structures = Commands.literal("structures")
            .requires(src -> src.hasPermission(2))
            .then(SpawnTowerCommand.towerBranch())
            .then(Commands.literal("list").executes(ctx -> listSites(ctx.getSource())))
            .then(Commands.literal("camp").executes(ctx -> placeCamp(ctx.getSource())))
            .then(Commands.literal("auction")
                .executes(ctx -> placeHere(ctx.getSource(), PendingSites.KIND_AUCTION, "survivor_camp/auction")))
            .then(Commands.literal("convoy")
                .executes(ctx -> placeHere(ctx.getSource(), PendingSites.KIND_CONVOY, "survivor_camp/convoy")))
            .then(Commands.literal("crash")
                .executes(ctx -> placeHere(ctx.getSource(), PendingSites.KIND_CRASH, "procedural_crash")))
            .then(Commands.literal("relay")
                .executes(ctx -> placeRelay(ctx.getSource(), null))
                .then(Commands.argument("variant", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("small", "medium", "large"), b))
                    .executes(ctx -> placeRelay(ctx.getSource(), StringArgumentType.getString(ctx, "variant")))));

        dispatcher.register(Commands.literal("dead_air").then(structures));
    }

    private static void line(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), false);
    }

    private static int listSites(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        var owned = PendingSites.forOwner(p.server, p.getUUID());
        if (owned.isEmpty()) {
            line(src, "No pending EB sites. Casts fire any time of day; explore with T3 listening can discover.");
            return 1;
        }
        line(src, "Your pending EB sites (listen on T3 tuned to EB for compass):");
        for (PendingSites.Site s : owned) {
            line(src, "  " + s.kind + " X=" + s.center.getX() + " Z=" + s.center.getZ()
                + " placed=" + s.placed);
        }
        return 1;
    }

    private static int placeCamp(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        PendingSites.Site site = PendingSites.createHere(p, PendingSites.KIND_CAMP, "survivor_camp/camp_v2");
        line(src, site != null ? "Placed survivor camp at " + site.center.toShortString() : "Camp failed");
        return site != null ? 1 : 0;
    }

    private static int placeRelay(CommandSourceStack src, String variant) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        BroadcastProgress.PlayerProgress pr = BroadcastProgress.get(p.server).get(p.getUUID());
        String v = variant != null ? variant : switch (Math.min(pr.relaysFound.size(), 2)) {
            case 0 -> "small";
            case 1 -> "medium";
            default -> "large";
        };
        if (!List.of("small", "medium", "large").contains(v)) {
            line(src, "Variant must be small, medium or large.");
            return 0;
        }
        PendingSites.Site site = PendingSites.createHere(p, PendingSites.KIND_RELAY, "broken_relay/" + v);
        if (site == null) return 0;
        if (pr.stage < StoryStage.RELAYS) pr.stage = StoryStage.RELAYS;
        pr.targetRelay = site.keyPos != null ? site.keyPos : site.center;
        BroadcastProgress.get(p.server).setDirty();
        line(src, "Placed broken_relay/" + v
            + (site.keyPos != null ? " relay block at " + site.keyPos.toShortString() : ""));
        return 1;
    }

    private static int placeHere(CommandSourceStack src, String kind, String template) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        PendingSites.Site site = PendingSites.createHere(p, kind, template);
        line(src, site != null
            ? "Placed " + kind + " (" + template + ") at " + site.center.toShortString()
                + (site.placed ? " placed=true" : " placed=false")
                + (site.keyPos != null ? " key=" + site.keyPos.toShortString() : "")
            : "Place " + kind + " failed");
        return site != null ? 1 : 0;
    }
}
