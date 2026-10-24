package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/** Server -> Client: "EB signal detected â€” Tune now?" near a story/distress site. */
public class EbSignalPromptPacket implements CustomPacketPayload {
    public static final Type<EbSignalPromptPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "eb_signal_prompt"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EbSignalPromptPacket> STREAM_CODEC =
        StreamCodec.of(EbSignalPromptPacket::encode, EbSignalPromptPacket::decode);

    public final String siteKey;
    public final String siteKind;

    public EbSignalPromptPacket(String siteKey, String siteKind) {
        this.siteKey = siteKey != null ? siteKey : "";
        this.siteKind = siteKind != null ? siteKind : "";
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, EbSignalPromptPacket msg) {
        buf.writeUtf(msg.siteKey, 256);
        buf.writeUtf(msg.siteKind, 32);
    }

    public static EbSignalPromptPacket decode(RegistryFriendlyByteBuf buf) {
        return new EbSignalPromptPacket(buf.readUtf(256), buf.readUtf(32));
    }

    public static void handle(EbSignalPromptPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
            uk.co.extraspecialstudio.dead_air.client.DeadAirClientPackets.onEbSignalPrompt(msg.siteKey, msg.siteKind));
    }
}
