package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/** Server â†’ Client: create a temporary map-mod waypoint at EB / proximity coords. */
public class CompatWaypointPacket implements CustomPacketPayload {
    public static final Type<CompatWaypointPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "compat_waypoint"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CompatWaypointPacket> STREAM_CODEC =
        StreamCodec.of(CompatWaypointPacket::encode, CompatWaypointPacket::decode);

    public final int x;
    public final int y;
    public final int z;
    public final String dimension;
    public final String name;
    public final boolean temporary;

    public CompatWaypointPacket(int x, int y, int z, String dimension, String name, boolean temporary) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension != null ? dimension : "minecraft:overworld";
        this.name = name != null ? name : "Dead Air Signal";
        this.temporary = temporary;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, CompatWaypointPacket msg) {
        buf.writeVarInt(msg.x);
        buf.writeVarInt(msg.y);
        buf.writeVarInt(msg.z);
        buf.writeUtf(msg.dimension, 256);
        buf.writeUtf(msg.name, 128);
        buf.writeBoolean(msg.temporary);
    }

    public static CompatWaypointPacket decode(RegistryFriendlyByteBuf buf) {
        return new CompatWaypointPacket(
            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
            buf.readUtf(256), buf.readUtf(128), buf.readBoolean());
    }

    public static void handle(CompatWaypointPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
            uk.co.extraspecialstudio.dead_air.compat.MapWaypointCompat.clientCreateWaypoint(
                msg.x, msg.y, msg.z, msg.dimension, msg.name, msg.temporary));
    }
}
