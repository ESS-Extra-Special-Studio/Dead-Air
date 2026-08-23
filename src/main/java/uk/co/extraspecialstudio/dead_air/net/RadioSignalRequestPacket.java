package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;
import uk.co.extraspecialstudio.dead_air.tower.modules.TowerModuleStorage;

import java.util.function.Supplier;

/** Client -> Server: request signal strength for a station at the player's position. Resolves from persisted known towers + panel backup only (no chunk scan). */
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
                if (player != null) {
                    DeadAirNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new RadioSignalResponsePacket(msg.stationId, 0f));
                }
                return;
            }
            // Load panel + known-tower backups only (no chunk scan). Once a panel is activated, tuning in GUI works.
            uk.co.extraspecialstudio.dead_air.events.ChunkEvents.ensureOverworldReadyForSignal(serverLevel);
            RadioStation station = StationRegistry.getStation(msg.stationId);
            float signal = 0f;
            java.util.List<KnownTowersSyncPacket.TowerEntry> towerList = null;
            if (station != null && serverLevel.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                Vec3 pos = player.position();
                signal = uk.co.extraspecialstudio.dead_air.tower.KnownTowerStorage.getSignalFromKnownTowersOnly(serverLevel, msg.stationId, pos);
                var entries = uk.co.extraspecialstudio.dead_air.tower.KnownTowerStorage.getKnownTowers(serverLevel.dimension());
                if (!entries.isEmpty()) {
                    towerList = new java.util.ArrayList<>();
                    for (var e : entries) {
                        var caps = TowerModuleStorage.get(serverLevel).getCapabilities(e.panelPos);
                        towerList.add(new KnownTowersSyncPacket.TowerEntry(
                            e.towerPos, e.panelPos, e.stationId,
                            caps.isJukeboxModuleInstalled(), caps.isSignalBoostInstalled(), e.activated));
                    }
                }
            }
            DeadAirNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new RadioSignalResponsePacket(msg.stationId, signal, towerList));
        });
        ctx.setPacketHandled(true);
    }
}
