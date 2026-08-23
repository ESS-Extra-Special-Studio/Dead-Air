package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> Client: open the Dead Air walkie tuning GUI.
 * Sent when the server cancels a walkie right-click so the client opens our GUI instead.
 */
public class OpenWalkieGuiPacket {

    public static void encode(OpenWalkieGuiPacket msg, FriendlyByteBuf buf) {}

    public static OpenWalkieGuiPacket decode(FriendlyByteBuf buf) {
        return new OpenWalkieGuiPacket();
    }

    public static void handle(OpenWalkieGuiPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        if (ctx.getDirection().getReceptionSide().isClient()) {
            ctx.enqueueWork(DeadAirNet::runOpenWalkieGuiCallback);
        }
        ctx.setPacketHandled(true);
    }
}
