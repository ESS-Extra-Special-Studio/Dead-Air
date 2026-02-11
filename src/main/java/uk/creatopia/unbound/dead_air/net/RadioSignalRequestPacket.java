package uk.creatopia.unbound.dead_air.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import uk.creatopia.unbound.dead_air.Dead_air;
import uk.creatopia.unbound.dead_air.radio.RadioStation;
import uk.creatopia.unbound.dead_air.radio.StationRegistry;

import java.util.function.Supplier;

/** Client -> Server: request signal strength for a station at the player's position. Resolves from persisted known towers + panel backup only (no chunk scan). */
public class RadioSignalRequestPacket {
    private static long lastEarlyReturnLog = 0;
    private static final long EARLY_RETURN_LOG_INTERVAL_MS = 5000;
    private final ResourceLocation stationId;

    public RadioSignalRequestPacket(ResourceLocation stationId) {
        this.stationId = stationId;
    }

    public static void encode(RadioSignalRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.stationId);
    }

    public static RadioSignalRequestPacket decode(FriendlyByteBuf buf) {
        return new RadioSignalRequestPacket(buf.readResourceLocation());
    }

    public static void handle(RadioSignalRequestPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.level() == null || !(player.level() instanceof ServerLevel serverLevel)) {
                long now = System.currentTimeMillis();
                if (now - lastEarlyReturnLog > EARLY_RETURN_LOG_INTERVAL_MS) {
                    lastEarlyReturnLog = now;
                    Dead_air.LOGGER.warn("[Dead Air] Signal request skipped: station={} playerNull={} levelNull={} notServerLevel={}",
                        msg.stationId, player == null, player != null && player.level() == null,
                        player != null && player.level() != null && !(player.level() instanceof ServerLevel));
                }
                if (player != null) {
                    DeadAirNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new RadioSignalResponsePacket(msg.stationId, 0f));
                }
                return;
            }
            // Load panel + known-tower backups only (no chunk scan). Once a panel is activated, tuning in GUI works.
            uk.creatopia.unbound.dead_air.events.ChunkEvents.ensureOverworldReadyForSignal(serverLevel);
            RadioStation station = StationRegistry.getStation(msg.stationId);
            float signal = 0f;
            if (station != null && serverLevel.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                Vec3 pos = player.position();
                signal = uk.creatopia.unbound.dead_air.tower.KnownTowerStorage.getSignalFromKnownTowersOnly(serverLevel, msg.stationId, pos);
                int knownCount = uk.creatopia.unbound.dead_air.tower.KnownTowerStorage.getKnownTowers(serverLevel.dimension()).size();
                Dead_air.LOGGER.info("[Dead Air] Signal request: station={} signal={} knownTowersTotal={}", msg.stationId, String.format("%.2f", signal), knownCount);
            }
            DeadAirNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new RadioSignalResponsePacket(msg.stationId, signal));
        });
        ctx.setPacketHandled(true);
    }
}
