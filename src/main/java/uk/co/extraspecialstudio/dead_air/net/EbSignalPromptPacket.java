package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> Client: "EB signal detected — Tune now?" near a story/distress site. */
public class EbSignalPromptPacket {
    public final String siteKey;
    public final String siteKind;

    public EbSignalPromptPacket(String siteKey, String siteKind) {
        this.siteKey = siteKey != null ? siteKey : "";
        this.siteKind = siteKind != null ? siteKind : "";
    }

    public static void encode(EbSignalPromptPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.siteKey, 256);
        buf.writeUtf(msg.siteKind, 32);
    }

    public static EbSignalPromptPacket decode(FriendlyByteBuf buf) {
        return new EbSignalPromptPacket(buf.readUtf(256), buf.readUtf(32));
    }

    public static void handle(EbSignalPromptPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> uk.co.extraspecialstudio.dead_air.client.DeadAirClientPackets.onEbSignalPrompt(msg.siteKey, msg.siteKind)));
        ctx.setPacketHandled(true);
    }
}
