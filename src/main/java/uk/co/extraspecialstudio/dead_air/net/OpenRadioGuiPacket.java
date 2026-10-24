package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> Client: open the Dead Air radio tuning GUI.
 * Sent when the server cancels a radio right-click so the client opens our GUI instead.
 */
public class OpenRadioGuiPacket {

    public static void encode(OpenRadioGuiPacket msg, FriendlyByteBuf buf) {}

    public static OpenRadioGuiPacket decode(FriendlyByteBuf buf) {
        return new OpenRadioGuiPacket();
    }

    public static void handle(OpenRadioGuiPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        if (ctx.getDirection().getReceptionSide().isClient()) {
            ctx.enqueueWork(DeadAirNet::runOpenRadioGuiCallback);
        }
        ctx.setPacketHandled(true);
    }
}
