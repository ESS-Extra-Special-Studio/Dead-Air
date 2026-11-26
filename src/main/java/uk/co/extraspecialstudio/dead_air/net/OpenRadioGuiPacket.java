package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.extraspecialstudio.dead_air.Dead_air;

public class OpenRadioGuiPacket implements CustomPacketPayload {
    public static final Type<OpenRadioGuiPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "open_radio_gui"));
    /** StreamCodec.unit requires the exact same instance on encode — never {@code new}. */
    public static final OpenRadioGuiPacket INSTANCE = new OpenRadioGuiPacket();
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRadioGuiPacket> STREAM_CODEC =
        StreamCodec.unit(INSTANCE);

    private OpenRadioGuiPacket() {}

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenRadioGuiPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(DeadAirNet::runOpenRadioGuiCallback);
    }
}
