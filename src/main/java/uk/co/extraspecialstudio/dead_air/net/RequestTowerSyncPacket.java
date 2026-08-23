package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.extraspecialstudio.dead_air.Dead_air;

public class RequestTowerSyncPacket implements CustomPacketPayload {
    public static final Type<RequestTowerSyncPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "request_tower_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestTowerSyncPacket> STREAM_CODEC =
        StreamCodec.unit(new RequestTowerSyncPacket());

    public RequestTowerSyncPacket() {}

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestTowerSyncPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                uk.co.extraspecialstudio.dead_air.events.ModEvents.syncKnownTowersToPlayer(player);
            }
        });
    }
}
