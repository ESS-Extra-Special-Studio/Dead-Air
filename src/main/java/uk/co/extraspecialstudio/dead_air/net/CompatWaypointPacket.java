package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → Client: create/update a temporary map-mod waypoint at EB / proximity coords. */
public class CompatWaypointPacket {
    public final int x;
    public final int y;
    public final int z;
    public final String dimension;
    public final String name;
    public final boolean temporary;
    /** Stable id so create/place/proximity refresh the same pin instead of stacking duplicates. */
    public final String stableId;

    public CompatWaypointPacket(int x, int y, int z, String dimension, String name, boolean temporary, String stableId) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension != null ? dimension : "minecraft:overworld";
        this.name = name != null ? name : "Dead Air Signal";
        this.temporary = temporary;
        this.stableId = stableId != null && !stableId.isBlank()
            ? stableId
            : ("da_" + x + "_" + z);
    }

    public static void encode(CompatWaypointPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.x);
        buf.writeVarInt(msg.y);
        buf.writeVarInt(msg.z);
        buf.writeUtf(msg.dimension, 256);
        buf.writeUtf(msg.name, 128);
        buf.writeBoolean(msg.temporary);
        buf.writeUtf(msg.stableId, 128);
    }

    public static CompatWaypointPacket decode(FriendlyByteBuf buf) {
        return new CompatWaypointPacket(
            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
            buf.readUtf(256), buf.readUtf(128), buf.readBoolean(),
            buf.readUtf(128));
    }

    public static void handle(CompatWaypointPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            uk.co.extraspecialstudio.dead_air.compat.MapWaypointCompat.clientCreateWaypoint(
                msg.x, msg.y, msg.z, msg.dimension, msg.name, msg.temporary, msg.stableId)));
        ctx.setPacketHandled(true);
    }
}
