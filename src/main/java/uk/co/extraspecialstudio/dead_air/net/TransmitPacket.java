package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;
import uk.co.extraspecialstudio.dead_air.encounter.Transmissions;


/** Client -> Server: the player pressed Transmit on a T3 radio at this frequency. */
public class TransmitPacket {
    public final float frequency;

    public TransmitPacket(float frequency) {
        this.frequency = frequency;
    }

    public static void encode(TransmitPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.frequency);
    }

    public static TransmitPacket decode(FriendlyByteBuf buf) {
        return new TransmitPacket(buf.readFloat());
    }

    public static void handle(TransmitPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getSender() != null && Float.isFinite(msg.frequency)) {
                Transmissions.onTransmit(ctx.getSender(), msg.frequency);
            }
        });
        ctx.setPacketHandled(true);
    }
}
