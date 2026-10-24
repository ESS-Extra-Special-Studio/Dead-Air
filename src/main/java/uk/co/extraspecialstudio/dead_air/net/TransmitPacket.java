package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.encounter.Transmissions;

/** Client -> Server: the player pressed Transmit on a T3 radio at this frequency. */
public class TransmitPacket implements CustomPacketPayload {
    public static final Type<TransmitPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "transmit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TransmitPacket> STREAM_CODEC =
        StreamCodec.of(TransmitPacket::encode, TransmitPacket::decode);

    public final float frequency;

    public TransmitPacket(float frequency) {
        this.frequency = frequency;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, TransmitPacket msg) {
        buf.writeFloat(msg.frequency);
    }

    public static TransmitPacket decode(RegistryFriendlyByteBuf buf) {
        return new TransmitPacket(buf.readFloat());
    }

    public static void handle(TransmitPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp && Float.isFinite(msg.frequency)) {
                Transmissions.onTransmit(sp, msg.frequency);
            }
        });
    }
}
