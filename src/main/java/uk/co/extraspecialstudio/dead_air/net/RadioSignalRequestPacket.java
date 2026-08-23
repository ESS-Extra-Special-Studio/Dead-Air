package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;
import uk.co.extraspecialstudio.dead_air.tower.modules.TowerModuleStorage;

import java.util.ArrayList;

public class RadioSignalRequestPacket implements CustomPacketPayload {
    public static final Type<RadioSignalRequestPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "radio_signal_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadioSignalRequestPacket> STREAM_CODEC =
        StreamCodec.of(RadioSignalRequestPacket::encode, RadioSignalRequestPacket::decode);

    private final ResourceLocation stationId;

    public RadioSignalRequestPacket(ResourceLocation stationId) {
        this.stationId = stationId;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, RadioSignalRequestPacket msg) {
        buf.writeResourceLocation(msg.stationId);
    }

    public static RadioSignalRequestPacket decode(RegistryFriendlyByteBuf buf) {
        return new RadioSignalRequestPacket(buf.readResourceLocation());
    }

    public static void handle(RadioSignalRequestPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (player.level() == null || !(player.level() instanceof ServerLevel serverLevel)) {
                DeadAirNet.sendToPlayer(player, new RadioSignalResponsePacket(msg.stationId, 0f));
                return;
            }
            uk.co.extraspecialstudio.dead_air.events.ChunkEvents.ensureOverworldReadyForSignal(serverLevel);
            RadioStation station = StationRegistry.getStation(msg.stationId);
            float signal = 0f;
            java.util.List<KnownTowersSyncPacket.TowerEntry> towerList = null;
            if (station != null && serverLevel.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                Vec3 pos = player.position();
                signal = uk.co.extraspecialstudio.dead_air.tower.KnownTowerStorage.getSignalFromKnownTowersOnly(serverLevel, msg.stationId, pos);
                var entries = uk.co.extraspecialstudio.dead_air.tower.KnownTowerStorage.getKnownTowers(serverLevel.dimension());
                if (!entries.isEmpty()) {
                    towerList = new ArrayList<>();
                    for (var e : entries) {
                        var caps = TowerModuleStorage.get(serverLevel).getCapabilities(e.panelPos);
                        towerList.add(new KnownTowersSyncPacket.TowerEntry(
                            e.towerPos, e.panelPos, e.stationId,
                            caps.isJukeboxModuleInstalled(), caps.isSignalBoostInstalled(), e.activated));
                    }
                }
            }
            DeadAirNet.sendToPlayer(player, new RadioSignalResponsePacket(msg.stationId, signal, towerList));
        });
    }
}
