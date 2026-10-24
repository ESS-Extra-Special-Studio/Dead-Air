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
