package uk.creatopia.unbound.dead_air.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import uk.creatopia.unbound.dead_air.radio.RadioStation;
import uk.creatopia.unbound.dead_air.radio.RadioTower;
import uk.creatopia.unbound.dead_air.radio.SignalStrength;
import uk.creatopia.unbound.dead_air.Dead_air;
import uk.creatopia.unbound.dead_air.radio.StationRegistry;
import uk.creatopia.unbound.dead_air.radio.TowerManager;

import java.util.function.Supplier;

/** Client -> Server: request signal strength for a station at the player's position. */
public class RadioSignalRequestPacket {
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
                return;
            }
            RadioStation station = StationRegistry.getStation(msg.stationId);
            float signal = 0f;
            if (station != null) {
                Vec3 pos = player.position();
                RadioTower tower = TowerManager.getBestTower(serverLevel, pos, station);
                if (tower != null && tower.isPowered()) {
                    signal = SignalStrength.getFinalSignalStrength(serverLevel, tower, pos);
                }
            }
            if (signal >= 0.1f) {
                Dead_air.LOGGER.info("Radio signal request: station {} -> sending signal {} to player", msg.stationId, signal);
            } else {
                Dead_air.LOGGER.debug("Radio signal request: station {} -> no tower in range or not powered (signal 0)", msg.stationId);
            }
            DeadAirNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new RadioSignalResponsePacket(msg.stationId, signal));
        });
        ctx.setPacketHandled(true);
    }
}
