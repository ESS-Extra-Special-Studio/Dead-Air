package uk.co.extraspecialstudio.dead_air.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import uk.co.extraspecialstudio.dead_air.entity.DeadAirEntities;
import uk.co.extraspecialstudio.dead_air.entity.ListenerEntity;
import uk.co.extraspecialstudio.dead_air.broadcast.BroadcastEvent;
import uk.co.extraspecialstudio.dead_air.broadcast.BroadcastEventRegistry;
import uk.co.extraspecialstudio.dead_air.broadcast.BroadcastProgress;
import uk.co.extraspecialstudio.dead_air.broadcast.BroadcastScheduler;
import uk.co.extraspecialstudio.dead_air.broadcast.EbHuntTracker;
import uk.co.extraspecialstudio.dead_air.broadcast.ExploreDiscovery;
import uk.co.extraspecialstudio.dead_air.broadcast.ExtraBroadcasts;
import uk.co.extraspecialstudio.dead_air.broadcast.PendingSites;
import uk.co.extraspecialstudio.dead_air.broadcast.StoryStage;
import uk.co.extraspecialstudio.dead_air.encounter.ListenerEncounter;
import uk.co.extraspecialstudio.dead_air.item.DeadAirItems;
import uk.co.extraspecialstudio.dead_air.survivor.SurvivorRoster;

import java.util.List;

/** {@code /dead_air listener ...}: jump to any Dead Air 3.0 stage for testing. */
public final class DeadAirListenerCommand {

    private static final List<String> BROADCAST_IDS = List.of(
        "distress_call", "relay_transmission", "it_hears_you", "breaker_hint",
        "dead_channel_auction", "refugee_convoy", "base_under_attack",
        "missing_recruit", "pirate_fm_interruption", "downed_plane",
        "auction", "convoy", "baseattack", "missing", "piratefm", "plane");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dead_air").then(listener()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> listener() {
        return Commands.literal("listener")
            .requires(src -> src.hasPermission(2))
            .then(Commands.literal("progress").executes(ctx -> progress(ctx.getSource())))
            .then(Commands.literal("stage")
                .then(Commands.argument("stage", IntegerArgumentType.integer(0, 4))
                    .executes(ctx -> stage(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "stage")))))
            .then(Commands.literal("broadcast")
                .then(Commands.literal("story").executes(ctx -> broadcast(ctx.getSource(), BroadcastEvent.Category.STORY)))
                .then(Commands.literal("survivor").executes(ctx -> broadcast(ctx.getSource(), BroadcastEvent.Category.SURVIVOR)))
                .then(Commands.literal("traders").executes(ctx -> broadcast(ctx.getSource(), BroadcastEvent.Category.TRADERS)))
                .then(Commands.literal("defense").executes(ctx -> broadcast(ctx.getSource(), BroadcastEvent.Category.DEFENSE)))
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(BROADCAST_IDS, b))
                    .executes(ctx -> broadcastById(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
            .then(Commands.literal("decode").executes(ctx -> give(ctx.getSource(), new ItemStack(DeadAirItems.SIGNAL_DECODER.get()))))
            .then(Commands.literal("givet3").executes(ctx -> give(ctx.getSource(), new ItemStack(DeadAirItems.RADIO_T3.get()))))
            .then(Commands.literal("givet2").executes(ctx -> give(ctx.getSource(), new ItemStack(DeadAirItems.RADIO_T2.get()))))
            .then(Commands.literal("auction").executes(ctx -> forceId(ctx.getSource(), ExtraBroadcasts.DEAD_CHANNEL_AUCTION)))
            .then(Commands.literal("convoy").executes(ctx -> forceId(ctx.getSource(), ExtraBroadcasts.REFUGEE_CONVOY)))
            .then(Commands.literal("baseattack").executes(ctx -> forceId(ctx.getSource(), ExtraBroadcasts.BASE_UNDER_ATTACK)))
            .then(Commands.literal("missing").executes(ctx -> forceId(ctx.getSource(), ExtraBroadcasts.MISSING_RECRUIT)))
            .then(Commands.literal("piratefm").executes(ctx -> forceId(ctx.getSource(), ExtraBroadcasts.PIRATE_FM)))
            .then(Commands.literal("plane").executes(ctx -> forceId(ctx.getSource(), ExtraBroadcasts.DOWNED_PLANE)))
            .then(Commands.literal("downed_plane").executes(ctx -> forceId(ctx.getSource(), ExtraBroadcasts.DOWNED_PLANE)))
            .then(Commands.literal("frequency")
                .executes(ctx -> frequency(ctx.getSource()))
                .then(Commands.argument("mhz", FloatArgumentType.floatArg(88f, 109f))
                    .executes(ctx -> setFrequency(ctx.getSource(), FloatArgumentType.getFloat(ctx, "mhz")))))
            .then(Commands.literal("start")
                .then(Commands.literal("first").executes(ctx -> start(ctx.getSource(), ListenerEncounter.Mode.FIRST)))
                .then(Commands.literal("final").executes(ctx -> start(ctx.getSource(), ListenerEncounter.Mode.FINAL))))
            // Wild boss at the player — works in Peaceful; egg /summon use same entity type.
            .then(Commands.literal("summon").executes(ctx -> summonWild(ctx.getSource())))
            .then(Commands.literal("kill")
                .executes(ctx -> killListeners(ctx.getSource(), false))
                .then(Commands.literal("all").executes(ctx -> killListeners(ctx.getSource(), true))))
            .then(Commands.literal("purge").executes(ctx -> purge(ctx.getSource())))
            .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource())))
            .then(Commands.literal("anchors").executes(ctx -> anchors(ctx.getSource())))
            .then(Commands.literal("givebreaker").executes(ctx -> give(ctx.getSource(), new ItemStack(DeadAirItems.SIGNAL_BREAKER.get()))))
            .then(Commands.literal("components").executes(ctx -> components(ctx.getSource())))
            .then(Commands.literal("reset").executes(ctx -> reset(ctx.getSource())));
    }

    private static void line(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), false);
    }

    private static BroadcastProgress.PlayerProgress prog(ServerPlayer p) {
        return BroadcastProgress.get(p.server).get(p.getUUID());
    }

    private static int progress(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        BroadcastProgress data = BroadcastProgress.get(p.server);
        BroadcastProgress.PlayerProgress pr = data.get(p.getUUID());
        long today = p.server.overworld().getDayTime() / 24000L;
        boolean listening = uk.co.extraspecialstudio.dead_air.radio.RadioManager.isListeningToRadio(p);
        line(src, "=== Dead Air 3.0 progress: " + p.getGameProfile().getName() + " ===");
        line(src, "stage=" + pr.stage + " (" + StoryStage.name(pr.stage) + ") decoded=" + pr.decodedCount);
        line(src, "relaysFound=" + pr.relaysFound.size() + " " + pr.relaysFound);
        line(src, "targetRelay=" + pr.targetRelay + " storyQueued=" + pr.storyQueued
            + " nextBroadcastDay=" + pr.nextBroadcastDay + " (today " + today + ")");
        line(src, "firstSurvived=" + pr.firstEncounterSurvived + " bossMode=" + pr.bossModeUnlocked
            + " finalDefeated=" + pr.finalDefeated + " breakerHinted=" + pr.breakerHinted);
        line(src, "hiddenFrequency=" + String.format("%.1f", data.getHiddenFrequency(p.getRandom()))
            + " hasT3=" + BroadcastScheduler.hasT3(p) + " listening=" + listening);
        SurvivorRoster roster = SurvivorRoster.get(p.server);
        line(src, "survivors=" + (roster == null ? 0 : roster.count(p.getUUID())));
        var owned = PendingSites.forOwner(p.server, p.getUUID());
        if (owned.isEmpty()) {
            line(src, " pending sites: none (casts any time of day; explore while listening can discover)");
        } else {
            line(src, " pending sites (listen on T3 tuned to EB for compass):");
            for (PendingSites.Site s : owned) {
                line(src, "  " + s.kind + " X=" + s.center.getX() + " Z=" + s.center.getZ()
                    + " placed=" + s.placed
                    + (s.keyPos != null ? " key=" + s.keyPos.toShortString() : ""));
            }
        }
        line(src, "encounter=" + (ListenerEncounter.isParticipant(p) ? "active" : "none"));
        return 1;
    }

    private static int stage(CommandSourceStack src, int stage) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        BroadcastProgress data = BroadcastProgress.get(p.server);
        BroadcastProgress.PlayerProgress pr = data.get(p.getUUID());
        pr.stage = stage;
        if (stage >= StoryStage.FINAL_BROADCAST && pr.relaysFound.isEmpty()) {
            pr.relaysFound.add(p.blockPosition());
            line(src, "Recorded your position as a found relay so Transmit works here.");
        }
        pr.firstEncounterSurvived = stage >= StoryStage.FIRST_SURVIVED;
        pr.bossModeUnlocked = stage >= StoryStage.FIRST_SURVIVED;
        pr.finalDefeated = stage >= StoryStage.DEFEATED;
        data.setDirty();
        line(src, "Stage set to " + stage + " (" + StoryStage.name(stage) + ")");
        return 1;
    }

    private static int broadcast(CommandSourceStack src, BroadcastEvent.Category category) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        List<BroadcastEvent> events = BroadcastEventRegistry.eligible(category, p, prog(p));
        if (events.isEmpty()) {
            line(src, "No " + category + " broadcast can fire at this stage.");
            return 0;
        }
        boolean ok = BroadcastScheduler.deliver(p, events.get(0));
        line(src, (ok ? "Sent " : "Cancelled ") + events.get(0).id());
        return ok ? 1 : 0;
    }

    private static int broadcastById(CommandSourceStack src, String raw) throws CommandSyntaxException {
        return forceId(src, resolveBroadcastId(raw));
    }

    private static ResourceLocation resolveBroadcastId(String raw) {
        String key = switch (raw.toLowerCase()) {
            case "auction" -> "dead_channel_auction";
            case "convoy" -> "refugee_convoy";
            case "baseattack", "base_attack" -> "base_under_attack";
            case "missing" -> "missing_recruit";
            case "piratefm", "pirate" -> "pirate_fm_interruption";
            case "plane", "downed_plane", "crash" -> "downed_plane";
            default -> raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw;
        };
        return ResourceLocation.fromNamespaceAndPath("dead_air", key);
    }

    /** Force-deliver a broadcast by id, bypassing canTrigger (debug). */
    private static int forceId(CommandSourceStack src, ResourceLocation id) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        BroadcastEvent event = BroadcastEventRegistry.get(id);
        if (event == null) {
            line(src, "Unknown broadcast id: " + id);
            return 0;
        }
        // Soft-prep cooldowns so start() side effects are not double-blocked mid-debug
        BroadcastProgress.PlayerProgress pr = prog(p);
        if (ExtraBroadcasts.DEAD_CHANNEL_AUCTION.equals(id)) pr.lastAuctionDay = -1;
        if (ExtraBroadcasts.REFUGEE_CONVOY.equals(id)) pr.lastConvoyDay = -1;
        if (ExtraBroadcasts.BASE_UNDER_ATTACK.equals(id)) pr.lastBaseAttackDay = -1;
        if (ExtraBroadcasts.MISSING_RECRUIT.equals(id)) { pr.lastMissingDay = -1; pr.missingSurvivorId = null; }
        if (ExtraBroadcasts.PIRATE_FM.equals(id)) pr.pirateFmHeard = false;
        if (ExtraBroadcasts.DOWNED_PLANE.equals(id)) pr.lastPlaneDay = -1;
        BroadcastProgress.get(p.server).setDirty();
        boolean ok = BroadcastScheduler.deliver(p, event);
        line(src, (ok ? "Forced " : "Cancelled ") + id
            + (ok ? "" : " (event returned no lines — check prerequisites: T3/base/settled survivor/expansion pack)"));
        return ok ? 1 : 0;
    }

    private static int give(CommandSourceStack src, ItemStack stack) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (!p.getInventory().add(stack)) p.drop(stack, false);
        return 1;
    }

    private static int frequency(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        line(src, "Hidden frequency: " + String.format("%.1f", BroadcastProgress.get(p.server).getHiddenFrequency(p.getRandom())) + " MHz");
        return 1;
    }

    private static int setFrequency(CommandSourceStack src, float mhz) {
        BroadcastProgress.get(src.getServer()).setHiddenFrequency(Math.round(mhz * 10f) / 10f);
        line(src, "Hidden frequency set to " + String.format("%.1f", mhz) + " MHz");
        return 1;
    }

    private static int start(CommandSourceStack src, ListenerEncounter.Mode mode) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        return ListenerEncounter.start(p, mode) != null ? 1 : 0;
    }

    /** Spawn a persistent WILD Listener at the player (debug / creative egg alternative). */
    private static int summonWild(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ServerLevel level = p.serverLevel();
        ListenerEntity listener = DeadAirEntities.THE_LISTENER.get().create(level);
        if (listener == null) {
            line(src, "Failed to create The Listener entity type.");
            return 0;
        }
        listener.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0.0f);
        listener.setPersistenceRequired();
        listener.setMode(ListenerEntity.Mode.WILD, null);
        if (!level.addFreshEntity(listener)) {
            line(src, "addFreshEntity failed for Listener.");
            return 0;
        }
        line(src, "Summoned The Listener (WILD) at " + listener.blockPosition().toShortString()
            + " id=" + listener.getId());
        return 1;
    }

    /**
     * Force-remove Listeners (bypasses Acoustic; not vanilla {@code /kill}).
     * Console / {@code kill all}: full-dimension sweep. In-game without {@code all}: 96-block radius.
     * Always aborts linked encounters without awarding FINAL victory.
     */
    private static int killListeners(CommandSourceStack src, boolean all) {
        if (all || !(src.getEntity() instanceof ServerPlayer)) {
            return purge(src);
        }
        ServerPlayer p = (ServerPlayer) src.getEntity();
        ServerLevel level = p.serverLevel();
        List<ListenerEntity> found = level.getEntitiesOfClass(ListenerEntity.class, p.getBoundingBox().inflate(96.0));
        if (found.isEmpty()) {
            line(src, "No Listener within 96 blocks. Use /dead_air listener kill all (or purge) for a full sweep.");
            return 0;
        }
        for (ListenerEntity listener : found) {
            listener.killFromCommand();
        }
        ListenerEncounter.clearClientFx(src.getServer());
        line(src, "Removed " + found.size() + " Listener(s) + aborted linked encounters (no victory credit).");
        return found.size();
    }

    /**
     * Support recovery: abort all encounters, remove every loaded Listener/anchor in all dims,
     * clear boss bars and client fog/jam. Safe from dedicated-server console.
     */
    private static int purge(CommandSourceStack src) {
        ListenerEncounter.PurgeResult r = ListenerEncounter.adminPurge(src.getServer());
        line(src, "Listener purge complete: encounters=" + r.encounters()
            + " listeners=" + r.listeners() + " anchors=" + r.anchors()
            + " (client fog/jam cleared for all online players).");
        return Math.max(1, r.encounters() + r.listeners() + r.anchors());
    }

    private static int stop(CommandSourceStack src) {
        int n = ListenerEncounter.stopAll();
        ListenerEncounter.clearClientFx(src.getServer());
        line(src, "Stopped " + n + " encounter(s); client fog/jam cleared.");
        return n;
    }

    private static int anchors(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ListenerEncounter.Encounter enc = ListenerEncounter.forPlayer(p);
        if (enc == null) {
            line(src, "Start an encounter first (/dead_air listener start first).");
            return 0;
        }
        if (enc.mode != ListenerEncounter.Mode.FIRST) {
            line(src, "Signal Anchors are only used in the FIRST encounter.");
            return 0;
        }
        for (int i = 0; i < 3; i++) {
            double a = i * Math.PI * 2 / 3;
            BlockPos at = p.blockPosition().offset((int) (Math.cos(a) * 6), 0, (int) (Math.sin(a) * 6));
            int y = p.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
            ListenerEncounter.spawnAnchor(enc, new BlockPos(at.getX(), y, at.getZ()));
        }
        line(src, "Spawned 3 more Signal Anchors around you.");
        return 1;
    }

    private static int components(CommandSourceStack src) throws CommandSyntaxException {
        give(src, new ItemStack(DeadAirItems.COMPONENT_DISTORTION_CORE.get()));
        give(src, new ItemStack(DeadAirItems.COMPONENT_BROADCASTER_COIL.get()));
        give(src, new ItemStack(DeadAirItems.COMPONENT_MAW_FANG.get()));
        give(src, new ItemStack(DeadAirItems.COMPONENT_SCRAMBLER_CHIP.get()));
        give(src, new ItemStack(DeadAirItems.COMPONENT_ECHO_CRYSTAL.get()));
        return 1;
    }

    private static int reset(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        BroadcastProgress.get(p.server).reset(p.getUUID());
        PendingSites sites = PendingSites.get(p.server);
        if (sites != null) {
            sites.getSites().removeIf(s -> s.owner.equals(p.getUUID()));
            sites.setDirty();
        }
        ExploreDiscovery.clearPlayer(p.getUUID());
        EbHuntTracker.clearPlayer(p);
        SurvivorRoster roster = SurvivorRoster.get(p.server);
        if (roster != null) roster.clear(p.getUUID());
        line(src, "Dead Air 3.0 progress reset for " + p.getGameProfile().getName());
        return 1;
    }

    private DeadAirListenerCommand() {}
}
