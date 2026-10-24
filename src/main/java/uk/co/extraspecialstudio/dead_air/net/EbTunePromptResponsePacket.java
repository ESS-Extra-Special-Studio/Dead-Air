package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import uk.co.extraspecialstudio.dead_air.broadcast.EmergencyProximity;

import java.util.function.Supplier;

/** Client -> Server: player answered the Emergency Broadcast tune prompt. */
public class EbTunePromptResponsePacket {
    public final String siteKey;
    public final boolean accepted;

    public EbTunePromptResponsePacket(String siteKey, boolean accepted) {
        this.siteKey = siteKey != null ? siteKey : "";
        this.accepted = accepted;
    }

    public static void encode(EbTunePromptResponsePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.siteKey, 256);
        buf.writeBoolean(msg.accepted);
    }

    public static EbTunePromptResponsePacket decode(FriendlyByteBuf buf) {
        return new EbTunePromptResponsePacket(buf.readUtf(256), buf.readBoolean());
    }

    public static void handle(EbTunePromptResponsePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (ctx.getSender() == null) return;
            if (msg.accepted) EmergencyProximity.onTuneAccepted(ctx.getSender(), msg.siteKey);
            else EmergencyProximity.onTuneDeclined(ctx.getSender(), msg.siteKey);
        });
        ctx.setPacketHandled(true);
    }
}
