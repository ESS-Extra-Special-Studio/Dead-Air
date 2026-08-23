package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.audio.AudioManager;
import uk.co.extraspecialstudio.dead_air.tower.KnownTowersClientCache;

import java.util.ArrayList;
import java.util.List;

public class RadioSignalResponsePacket implements CustomPacketPayload {
    public static final Type<RadioSignalResponsePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "radio_signal_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadioSignalResponsePacket> STREAM_CODEC =
        StreamCodec.of(RadioSignalResponsePacket::encode, RadioSignalResponsePacket::decode);

    private final ResourceLocation stationId;
    private final float signal;
    private final List<KnownTowersSyncPacket.TowerEntry> towers;

    public RadioSignalResponsePacket(ResourceLocation stationId, float signal) {
        this(stationId, signal, null);
    }

    public RadioSignalResponsePacket(ResourceLocation stationId, float signal, List<KnownTowersSyncPacket.TowerEntry> towers) {
        this.stationId = stationId;
        this.signal = signal;
        this.towers = towers != null ? new ArrayList<>(towers) : List.of();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, RadioSignalResponsePacket msg) {
        buf.writeResourceLocation(msg.stationId);
        buf.writeFloat(msg.signal);
        buf.writeInt(msg.towers.size());
        for (KnownTowersSyncPacket.TowerEntry e : msg.towers) {
            buf.writeBlockPos(e.towerPos);
            buf.writeBlockPos(e.panelPos);
            buf.writeBoolean(e.stationId != null);
            if (e.stationId != null) buf.writeResourceLocation(e.stationId);
            buf.writeBoolean(e.jukeboxModuleInstalled);
            buf.writeBoolean(e.signalBoostInstalled);
            buf.writeBoolean(e.activated);
        }
    }

    public static RadioSignalResponsePacket decode(RegistryFriendlyByteBuf buf) {
        ResourceLocation stationId = buf.readResourceLocation();
        float signal = buf.readFloat();
        int n = buf.readInt();
        List<KnownTowersSyncPacket.TowerEntry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BlockPos towerPos = buf.readBlockPos();
            BlockPos panelPos = buf.readBlockPos();
            ResourceLocation sid = buf.readBoolean() ? buf.readResourceLocation() : null;
            boolean juke = buf.readBoolean();
            boolean signalBoost = buf.readBoolean();
            boolean activated = buf.readBoolean();
            list.add(new KnownTowersSyncPacket.TowerEntry(towerPos, panelPos, sid, juke, signalBoost, activated));
        }
        return new RadioSignalResponsePacket(stationId, signal, list);
    }

    public static void handle(RadioSignalResponsePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            int clientTick = 0;
            if (net.minecraft.client.Minecraft.getInstance().player != null) {
                clientTick = net.minecraft.client.Minecraft.getInstance().player.tickCount;
            }
            if (!msg.towers.isEmpty()) {
                KnownTowersClientCache.setTowers(msg.towers);
            }
            AudioManager.setServerSignal(msg.stationId, msg.signal, clientTick);
            if (msg.signal >= 0.05f) {
                AudioManager.tryPlayFromServerSignal(msg.stationId, msg.signal);
            } else if (!msg.towers.isEmpty()) {
                AudioManager.tryStartMusicIfTuned(msg.stationId);
            }
        });
    }
}
