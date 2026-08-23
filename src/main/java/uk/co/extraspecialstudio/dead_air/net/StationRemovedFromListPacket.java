package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.station.StationUnlockManager;

public class StationRemovedFromListPacket implements CustomPacketPayload {
    public static final Type<StationRemovedFromListPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "station_removed"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StationRemovedFromListPacket> STREAM_CODEC =
        StreamCodec.of(StationRemovedFromListPacket::encode, StationRemovedFromListPacket::decode);

    private final ResourceLocation stationId;

    public StationRemovedFromListPacket(ResourceLocation stationId) {
        this.stationId = stationId;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, StationRemovedFromListPacket msg) {
        buf.writeResourceLocation(msg.stationId);
    }

    public static StationRemovedFromListPacket decode(RegistryFriendlyByteBuf buf) {
        return new StationRemovedFromListPacket(buf.readResourceLocation());
    }

    public static void handle(StationRemovedFromListPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (msg.stationId == null) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                StationUnlockManager.removeStationForPlayer(mc.player.getUUID(), msg.stationId);
            }
        });
    }
}
