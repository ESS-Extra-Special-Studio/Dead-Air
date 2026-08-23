package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.extraspecialstudio.dead_air.Dead_air;

public class OpenWalkieGuiPacket implements CustomPacketPayload {
    public static final Type<OpenWalkieGuiPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "open_walkie_gui"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenWalkieGuiPacket> STREAM_CODEC =
        StreamCodec.unit(new OpenWalkieGuiPacket());

    public OpenWalkieGuiPacket() {}

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenWalkieGuiPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(DeadAirNet::runOpenWalkieGuiCallback);
    }
}
