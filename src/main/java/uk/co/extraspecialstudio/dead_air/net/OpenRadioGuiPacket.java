package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;


/**
 * Server -> Client: open the Dead Air radio tuning GUI.
 * Sent when the server cancels a radio right-click so the client opens our GUI instead.
 */
public class OpenRadioGuiPacket {

    public static void encode(OpenRadioGuiPacket msg, FriendlyByteBuf buf) {}

    public static OpenRadioGuiPacket decode(FriendlyByteBuf buf) {
        return new OpenRadioGuiPacket();
    }

    public static void handle(OpenRadioGuiPacket msg, CustomPayloadEvent.Context ctx) {
        if (ctx.getDirection().getReceptionSide().isClient()) {
            ctx.enqueueWork(DeadAirNet::runOpenRadioGuiCallback);
        }
        ctx.setPacketHandled(true);
    }
}
