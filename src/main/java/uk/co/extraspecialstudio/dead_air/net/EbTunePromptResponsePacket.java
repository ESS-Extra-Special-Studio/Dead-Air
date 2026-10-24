package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.broadcast.EmergencyProximity;

/** Client -> Server: player answered the Emergency Broadcast tune prompt. */
public class EbTunePromptResponsePacket implements CustomPacketPayload {
    public static final Type<EbTunePromptResponsePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "eb_tune_prompt_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EbTunePromptResponsePacket> STREAM_CODEC =
        StreamCodec.of(EbTunePromptResponsePacket::encode, EbTunePromptResponsePacket::decode);

    public final String siteKey;
    public final boolean accepted;

    public EbTunePromptResponsePacket(String siteKey, boolean accepted) {
        this.siteKey = siteKey != null ? siteKey : "";
        this.accepted = accepted;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, EbTunePromptResponsePacket msg) {
        buf.writeUtf(msg.siteKey, 256);
        buf.writeBoolean(msg.accepted);
    }

    public static EbTunePromptResponsePacket decode(RegistryFriendlyByteBuf buf) {
        return new EbTunePromptResponsePacket(buf.readUtf(256), buf.readBoolean());
    }

    public static void handle(EbTunePromptResponsePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (msg.accepted) EmergencyProximity.onTuneAccepted(sp, msg.siteKey);
            else EmergencyProximity.onTuneDeclined(sp, msg.siteKey);
        });
    }
}
