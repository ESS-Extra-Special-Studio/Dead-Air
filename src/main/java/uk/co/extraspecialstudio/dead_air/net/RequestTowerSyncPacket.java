package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;


/** Client -> Server: request known tower list so client can compute signal locally (volume steps, stop when out of range). */
public class RequestTowerSyncPacket {

    public RequestTowerSyncPacket() {}

    public static void encode(RequestTowerSyncPacket msg, FriendlyByteBuf buf) {}

    public static RequestTowerSyncPacket decode(FriendlyByteBuf buf) {
        return new RequestTowerSyncPacket();
    }

    public static void handle(RequestTowerSyncPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getSender() != null) {
                uk.co.extraspecialstudio.dead_air.events.ModEvents.syncKnownTowersToPlayer(ctx.getSender());
            }
        });
        ctx.setPacketHandled(true);
    }
}
