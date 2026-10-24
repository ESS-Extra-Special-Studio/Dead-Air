package uk.co.extraspecialstudio.dead_air.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.base.PlayerBaseManager;
import uk.co.extraspecialstudio.dead_air.base.PlayerBaseStorage;
import uk.co.extraspecialstudio.dead_air.entity.DeadAirEntities;
import uk.co.extraspecialstudio.dead_air.net.SurvivorActionPacket;
import uk.co.extraspecialstudio.dead_air.survivor.SurvivorEntity;
import uk.co.extraspecialstudio.esl.forceload.EslForceLoadRegistry;

import javax.annotation.Nullable;
import java.util.UUID;

/** {@code /dead_air survivor spawn|atbase|gear|audit|testdownedabandon} — debug helpers. */
public final class DeadAirSurvivorCommand {

    /** TickTask does not delay — assert is ticked down from {@link #tickPendingTests}. */
    private static final int ASSERT_DELAY_TICKS = 100; // > abandon gate (60/100)
    @Nullable private static PendingDownedAbandon pendingDownedAbandon;

    private record PendingDownedAbandon(UUID playerId, UUID survivorId, BlockPos basePos, int ticksLeft) {
        PendingDownedAbandon dec() {
            return new PendingDownedAbandon(playerId, survivorId, basePos, ticksLeft - 1);
        }
    }

    /** Call once per server tick (END) — may no-op if ModEvents early-outs on shutdownRequested. */
    public static void tickPendingTests(MinecraftServer server) {
        tickPendingDownedAbandon(server);
    }

    /**
     * Preferred clock: survivor entity tick (runs even when ModEvents has shutdownRequested).
     * Call from {@link SurvivorEntity#tick()} on the server.
     */
    public static void onSurvivorServerTick(SurvivorEntity survivor) {
        if (pendingDownedAbandon == null || survivor == null) return;
        if (!survivor.getUUID().equals(pendingDownedAbandon.survivorId())) return;
        if (!(survivor.level() instanceof ServerLevel sl)) return;
        tickPendingDownedAbandon(sl.getServer());
    }

    private static void tickPendingDownedAbandon(MinecraftServer server) {
        if (pendingDownedAbandon == null || server == null) return;
        PendingDownedAbandon p = pendingDownedAbandon.dec();
        if (p.ticksLeft() > 0) {
            pendingDownedAbandon = p;
            return;
        }
        pendingDownedAbandon = null;
        finishDownedAbandonAssert(server, p.playerId(), p.survivorId(), p.basePos());
    }

    private static void finishDownedAbandonAssert(MinecraftServer server, UUID playerId,
                                                  UUID survivorId, BlockPos basePos) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        SurvivorEntity s = null;
        for (ServerLevel sl : server.getAllLevels()) {
            var e = sl.getEntity(survivorId);
            if (e instanceof SurvivorEntity se) {
                s = se;
                break;
            }
        }
        if (s == null || !s.isAlive()) {
            String msg = "TEST downed-abandon: FAIL survivor gone/dead (bled out or despawned)";
            Dead_air.LOGGER.error("[Dead Air] {}", msg);
            if (player != null) player.sendSystemMessage(Component.literal(msg));
            return;
        }
        ServerLevel level = (ServerLevel) s.level();
        boolean downed = s.isDowned();
        boolean settled = s.getSurvivorState() == SurvivorEntity.SETTLED;
        double distBase = Math.sqrt(s.distanceToSqr(basePos.getX() + 0.5, s.getY(), basePos.getZ() + 0.5));
        boolean nearBase = distBase <= Math.max(24, PlayerBaseManager.signalRange(level, basePos));
        boolean stillFollowing = s.getSurvivorState() == SurvivorEntity.FOLLOWING;
        boolean pass = downed && settled && nearBase && !stillFollowing;
        String detail = "downed=" + downed + " settled=" + settled + " nearBase=" + nearBase
            + " distBase=" + String.format("%.1f", distBase) + " state=" + s.getSurvivorState()
            + " pos=" + s.blockPosition();
        if (pass) {
            Dead_air.LOGGER.info("[Dead Air] TEST downed-abandon: PASS — {}", detail);
            if (player != null) player.sendSystemMessage(Component.literal("TEST downed-abandon: PASS — " + detail));
        } else {
            Dead_air.LOGGER.error("[Dead Air] TEST downed-abandon: FAIL — {}", detail);
            if (player != null) player.sendSystemMessage(Component.literal("TEST downed-abandon: FAIL — " + detail));
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dead_air").then(survivor()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> survivor() {
        return Commands.literal("survivor")
            .requires(src -> src.hasPermission(2))
            .then(Commands.literal("spawn").executes(ctx -> spawn(ctx.getSource(), false)))
            .then(Commands.literal("atbase").executes(ctx -> spawn(ctx.getSource(), true)))
            .then(Commands.literal("gear").executes(ctx -> gear(ctx.getSource())))
            .then(Commands.literal("audit").executes(ctx -> audit(ctx.getSource(), 12)))
            .then(Commands.literal("testdownedabandon").executes(ctx -> testDownedAbandon(ctx.getSource())));
    }

    /**
     * SP integration check: following survivor goes down → player teleports far → after abandon
     * window they should be SETTLED+downed at the radio base (not bled out in place).
     * Logs {@code [Dead Air] TEST downed-abandon: PASS|FAIL …} for live log grepping.
     */
    private static int testDownedAbandon(CommandSourceStack src) throws CommandSyntaxException {
        runDownedAbandonTest(src.getPlayerOrException());
        src.sendSuccess(() -> Component.literal(
            "TEST downed-abandon: running (watch log for PASS/FAIL in ~4s)…"), false);
        return 1;
    }

    /**
     * Same test as {@code /dead_air survivor testdownedabandon}. Safe to call from login hooks.
     * @return false if setup failed before the delayed assert
     */
    public static boolean runDownedAbandonTest(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        MinecraftServer server = player.server;
        UUID ownerId = player.getUUID();

        PlayerBaseStorage.Base base = PlayerBaseManager.getBase(server, ownerId);
        BlockPos basePos;
        if (base != null && base.dimension == level.dimension()) {
            basePos = base.panelPos.immutable();
        } else {
            // Synthetic base at the player's feet so the test is self-contained.
            basePos = player.blockPosition().immutable();
            PlayerBaseStorage.get(server).set(ownerId, level.dimension(), basePos);
            EslForceLoadRegistry.set(server, PlayerBaseManager.FORCE_LOAD_MOD, ownerId.toString(),
                level.dimension(), basePos);
            Dead_air.LOGGER.info("[Dead Air] TEST downed-abandon: pinned synthetic base at {}", basePos);
        }

        SurvivorEntity survivor = DeadAirEntities.SURVIVOR.get().create(level);
        if (survivor == null) {
            Dead_air.LOGGER.error("[Dead Air] TEST downed-abandon: FAIL create survivor");
            player.sendSystemMessage(Component.literal("TEST downed-abandon: FAIL create survivor"));
            return false;
        }
        BlockPos spawnAt = basePos.north(2);
        survivor.moveTo(spawnAt.getX() + 0.5, spawnAt.getY(), spawnAt.getZ() + 0.5, player.getYRot(), 0);
        survivor.initRandom(level.random, ownerId);
        level.addFreshEntity(survivor);
        survivor.handleAction(player, SurvivorActionPacket.RECRUIT);
        if (survivor.getSurvivorState() != SurvivorEntity.FOLLOWING) {
            Dead_air.LOGGER.error("[Dead Air] TEST downed-abandon: FAIL recruit state={}",
                survivor.getSurvivorState());
            player.sendSystemMessage(Component.literal("TEST downed-abandon: FAIL recruit (state="
                + survivor.getSurvivorState() + ")"));
            survivor.discard();
            return false;
        }
        survivor.debugForceDowned();
        if (!survivor.isDowned()) {
            Dead_air.LOGGER.error("[Dead Air] TEST downed-abandon: FAIL force downed");
            player.sendSystemMessage(Component.literal("TEST downed-abandon: FAIL force downed"));
            survivor.discard();
            return false;
        }

        UUID survivorId = survivor.getUUID();
        BlockPos far = basePos.offset(220, 0, 0);
        // Standable Y near current player Y so we don't fall into void/caves mid-test.
        player.teleportTo(level, far.getX() + 0.5, player.getY(), far.getZ() + 0.5,
            player.getYRot(), player.getXRot());
        Dead_air.LOGGER.info(
            "[Dead Air] TEST downed-abandon: started survivor={} base={} playerFar={} — waiting {} ticks",
            survivorId, basePos, far, ASSERT_DELAY_TICKS);
        pendingDownedAbandon = new PendingDownedAbandon(ownerId, survivorId, basePos, ASSERT_DELAY_TICKS);
        return true;
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
        String kit = describeKit(survivor);
        src.sendSuccess(() -> Component.literal("Spawned " + survivor.getName().getString()
            + " [" + survivor.getProfession().id + "/" + survivor.getSkinTextureKey() + "] "
            + kit + (atBase ? " @base" : " (following)")), true);
        return 1;
    }

    /** Roll N kits without keeping entities — reports TaCZ/vanilla weapon hit rate. */
    private static int audit(CommandSourceStack src, int count) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        int guns = 0;
        int vanilla = 0;
        int empty = 0;
        StringBuilder samples = new StringBuilder();
        for (int i = 0; i < count; i++) {
            SurvivorEntity survivor = DeadAirEntities.SURVIVOR.get().create(level);
            if (survivor == null) continue;
            survivor.initRandom(level.random, player.getUUID());
            ItemStack hand = survivor.getMainHandItem();
            String line = describeKit(survivor);
            if (uk.co.extraspecialstudio.dead_air.compat.SurvivorGunCompat.isGun(hand)) {
                guns++;
            } else if (hand.isEmpty()) {
                empty++;
            } else {
                vanilla++;
            }
            if (i < 6) {
                if (samples.length() > 0) samples.append(" | ");
                samples.append(survivor.getName().getString()).append(' ').append(line);
            }
            survivor.discard();
        }
        int g = guns, v = vanilla, e = empty;
        String sample = samples.toString();
        src.sendSuccess(() -> Component.literal(
            "Survivor kit audit x" + count + ": guns=" + g + " melee/other=" + v + " empty=" + e
                + (sample.isEmpty() ? "" : " — " + sample)), true);
        return 1;
    }

    private static String describeKit(SurvivorEntity survivor) {
        ItemStack hand = survivor.getMainHandItem();
        if (hand.isEmpty()) return "main=empty";
        String name = hand.getHoverName().getString();
        if (hand.hasTag() && hand.getTag().contains("GunId")) {
            return "main=" + name + " {" + hand.getTag().getString("GunId") + "}";
        }
        var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(hand.getItem());
        return "main=" + name + (key != null ? " [" + key + "]" : "");
    }

    private static int gear(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        player.getInventory().add(new ItemStack(Items.IRON_SWORD));
        player.getInventory().add(new ItemStack(Items.IRON_CHESTPLATE));
        player.getInventory().add(new ItemStack(Items.SHIELD));
        player.getInventory().add(new ItemStack(Items.IRON_HELMET));
        src.sendSuccess(() -> Component.literal("Gave test gear — right-click a survivor while holding an item to equip, or use Equipment in dialogue"), false);
        return 1;
    }

    private DeadAirSurvivorCommand() {}
}
