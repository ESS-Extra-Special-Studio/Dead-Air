package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/** Server -> Client: switch the active walkie to the Emergency Broadcast, or jam / unjam radios. */
public class ForceEmergencyTunePacket implements CustomPacketPayload {
    public static final Type<ForceEmergencyTunePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "force_emergency_tune"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ForceEmergencyTunePacket> STREAM_CODEC =
        StreamCodec.of(ForceEmergencyTunePacket::encode, ForceEmergencyTunePacket::decode);

    public static final int TUNE_EMERGENCY = 0;
    public static final int JAM_ON = 1;
    public static final int JAM_OFF = 2;
    public final int mode;

    public ForceEmergencyTunePacket(int mode) {
        this.mode = mode;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, ForceEmergencyTunePacket msg) {
        buf.writeVarInt(msg.mode);
    }

    public static ForceEmergencyTunePacket decode(RegistryFriendlyByteBuf buf) {
        return new ForceEmergencyTunePacket(buf.readVarInt());
    }

    public static void handle(ForceEmergencyTunePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> uk.co.extraspecialstudio.dead_air.client.DeadAirClientPackets.onForceTune(msg.mode));
    }
}
