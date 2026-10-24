package uk.co.extraspecialstudio.dead_air.base;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.compat.SurvivorMapCompat;
import uk.co.extraspecialstudio.dead_air.radio.RadioTower;
import uk.co.extraspecialstudio.dead_air.radio.TowerManager;
import uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerDetector;
import uk.co.extraspecialstudio.esl.forceload.EslForceLoadRegistry;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Player radio-panel bases: one primary panel per player, force-loads that chunk via ESL,
 * and exposes the panel's broadcast/signal range as the base wander zone.
 */
public final class PlayerBaseManager {
    public static final String FORCE_LOAD_MOD = Dead_air.MODID;

    private PlayerBaseManager() {}

    @Nullable
    public static PlayerBaseStorage.Base getBase(MinecraftServer server, UUID player) {
        return PlayerBaseStorage.get(server).get(player);
    }

    public static boolean hasBase(MinecraftServer server, UUID player) {
        return getBase(server, player) != null;
    }

    /**
     * Set the player's primary base to this radio panel. Force-loads the panel chunk.
     * @return false if the block is not a radio panel
     */
    public static boolean setBase(ServerPlayer player, BlockPos panelPos) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        if (!ApocalypseTowerDetector.isRadioPanel(level, panelPos)) {
            player.sendSystemMessage(Component.translatable("message.dead_air.base.look_at_panel")
                .withStyle(ChatFormatting.RED));
            return false;
        }

        MinecraftServer server = player.server;
        UUID id = player.getUUID();
        PlayerBaseStorage storage = PlayerBaseStorage.get(server);
        PlayerBaseStorage.Base previous = storage.get(id);
        if (previous != null) {
            EslForceLoadRegistry.clear(server, FORCE_LOAD_MOD, id.toString());
            SurvivorMapCompat.removeBaseMarker(previous);
        }

        BlockPos at = panelPos.immutable();
        storage.set(id, level.dimension(), at);
        // ESL persists sites + tickets; clear above prevents leaking the previous chunk ticket.
        EslForceLoadRegistry.set(server, FORCE_LOAD_MOD, id.toString(), level.dimension(), at);
        SurvivorMapCompat.addOrUpdateBaseMarker(player, level.dimension(), at, signalRange(level, at));

        int range = signalRange(level, at);
        player.sendSystemMessage(Component.translatable("message.dead_air.base.set",
            at.toShortString(), range).withStyle(ChatFormatting.GREEN));
        uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.award(player,
            uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.SURVIVOR_BASE);
        return true;
    }

    public static boolean unsetBase(ServerPlayer player) {
        MinecraftServer server = player.server;
        UUID id = player.getUUID();
        PlayerBaseStorage storage = PlayerBaseStorage.get(server);
        PlayerBaseStorage.Base previous = storage.get(id);
        if (previous == null) {
            player.sendSystemMessage(Component.translatable("message.dead_air.base.none")
                .withStyle(ChatFormatting.GRAY));
            return false;
        }
        storage.clear(id);
        EslForceLoadRegistry.clear(server, FORCE_LOAD_MOD, id.toString());
        SurvivorMapCompat.removeBaseMarker(previous);
        player.sendSystemMessage(Component.translatable("message.dead_air.base.unset")
            .withStyle(ChatFormatting.GRAY));
        return true;
    }

    /** Clear every player base pinned to this panel (e.g. panel broken). */
    public static void clearBasesAt(MinecraftServer server, ResourceKey<Level> dimension, BlockPos panelPos) {
        if (server == null || dimension == null || panelPos == null) return;
        PlayerBaseStorage storage = PlayerBaseStorage.get(server);
        BlockPos at = panelPos.immutable();
        for (var e : new java.util.ArrayList<>(storage.all().entrySet())) {
            PlayerBaseStorage.Base base = e.getValue();
            if (base == null || !base.dimension.equals(dimension) || !base.panelPos.equals(at)) continue;
            clearOne(server, storage, e.getKey(), base, true);
        }
    }

    /**
     * Drop ghost bases when the panel is gone (explosions / fill / other non-BreakEvent paths).
     * Only inspects loaded chunks so unloaded panels are left alone.
     */
    public static void validateBases(MinecraftServer server) {
        if (server == null || server.getTickCount() % 100 != 0) return;
        PlayerBaseStorage storage = PlayerBaseStorage.get(server);
        for (var e : new java.util.ArrayList<>(storage.all().entrySet())) {
            PlayerBaseStorage.Base base = e.getValue();
            if (base == null) continue;
            ServerLevel level = server.getLevel(base.dimension);
            if (level == null || !level.hasChunk(base.panelPos.getX() >> 4, base.panelPos.getZ() >> 4)) continue;
            if (ApocalypseTowerDetector.isRadioPanel(level, base.panelPos)) continue;
            clearOne(server, storage, e.getKey(), base, true);
        }
    }

    private static void clearOne(MinecraftServer server, PlayerBaseStorage storage, UUID id,
                                 PlayerBaseStorage.Base base, boolean notify) {
        storage.clear(id);
        EslForceLoadRegistry.clear(server, FORCE_LOAD_MOD, id.toString());
        SurvivorMapCompat.removeBaseMarker(base);
        if (!notify) return;
        ServerPlayer owner = server.getPlayerList().getPlayer(id);
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable("message.dead_air.base.panel_gone")
                .withStyle(ChatFormatting.GOLD));
        }
    }

    /** Broadcast / music range for this panel's tower, else config musicStationRange. */
    public static int signalRange(ServerLevel level, BlockPos panelPos) {
        RadioTower tower = TowerManager.findTowerForPanel(level, panelPos);
        if (tower != null && tower.getStation() != null) {
            return tower.getStation().getBroadcastRange();
        }
        return Config.musicStationRange > 0 ? Config.musicStationRange : 375;
    }

    @Nullable
    public static ServerLevel baseLevel(MinecraftServer server, UUID player) {
        PlayerBaseStorage.Base base = getBase(server, player);
        if (base == null) return null;
        return server.getLevel(base.dimension);
    }

    public static void reapplyForceLoads(MinecraftServer server) {
        var bases = PlayerBaseStorage.get(server).all();
        for (var e : bases.entrySet()) {
            EslForceLoadRegistry.set(server, FORCE_LOAD_MOD, e.getKey().toString(),
                e.getValue().dimension, e.getValue().panelPos);
        }
        if (!bases.isEmpty()) {
            Dead_air.LOGGER.info("[Dead Air] Re-applied {} player-base force-load(s)", bases.size());
        }
    }

    public static Component statusLine(MinecraftServer server, UUID player) {
        PlayerBaseStorage.Base base = getBase(server, player);
        if (base == null) {
            return Component.translatable("message.dead_air.base.none");
        }
        ServerLevel level = server.getLevel(base.dimension);
        int range = level != null ? signalRange(level, base.panelPos) : -1;
        return Component.translatable("message.dead_air.base.status",
            base.dimension.location().toString(),
            base.panelPos.toShortString(),
            range);
    }
}
