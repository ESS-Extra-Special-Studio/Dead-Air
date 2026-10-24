package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/**
 * Server -> Client: encounter visuals. {@code active} drives static fog and light flicker;
 * {@code silenceTicks} > 0 shows the Listening Silence cue.
 */
public class EncounterStatePacket implements CustomPacketPayload {
    public static final Type<EncounterStatePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "encounter_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EncounterStatePacket> STREAM_CODEC =
        StreamCodec.of(EncounterStatePacket::encode, EncounterStatePacket::decode);

    public final boolean active;
    public final int silenceTicks;
    public final int screamFlashTicks;

    public EncounterStatePacket(boolean active, int silenceTicks, int screamFlashTicks) {
        this.active = active;
        this.silenceTicks = silenceTicks;
        this.screamFlashTicks = screamFlashTicks;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, EncounterStatePacket msg) {
        buf.writeBoolean(msg.active);
        buf.writeVarInt(msg.silenceTicks);
        buf.writeVarInt(msg.screamFlashTicks);
    }

    public static EncounterStatePacket decode(RegistryFriendlyByteBuf buf) {
        return new EncounterStatePacket(buf.readBoolean(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(EncounterStatePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> uk.co.extraspecialstudio.dead_air.client.DeadAirClientPackets.onEncounterState(msg));
    }
}
