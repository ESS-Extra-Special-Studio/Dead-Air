package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> Server: request known tower list so client can compute signal locally (volume steps, stop when out of range). */
public class RequestTowerSyncPacket {

    public RequestTowerSyncPacket() {}

    public static void encode(RequestTowerSyncPacket msg, FriendlyByteBuf buf) {}

    public static RequestTowerSyncPacket decode(FriendlyByteBuf buf) {
        return new RequestTowerSyncPacket();
    }

    public static void handle(RequestTowerSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (ctx.getSender() != null) {
                uk.co.extraspecialstudio.dead_air.events.ModEvents.syncKnownTowersToPlayer(ctx.getSender());
            }
        });
        ctx.setPacketHandled(true);
    }
}
