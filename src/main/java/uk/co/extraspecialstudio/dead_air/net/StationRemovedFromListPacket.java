package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import uk.co.extraspecialstudio.dead_air.station.StationUnlockManager;

import java.util.function.Supplier;

/** Server -> Client: a station was removed from the world (last tower broken); remove it from the GUI station list. */
public class StationRemovedFromListPacket {
    private final ResourceLocation stationId;

    public StationRemovedFromListPacket(ResourceLocation stationId) {
        this.stationId = stationId;
    }

    public static void encode(StationRemovedFromListPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.stationId);
    }

    public static StationRemovedFromListPacket decode(FriendlyByteBuf buf) {
        return new StationRemovedFromListPacket(buf.readResourceLocation());
    }

    public static void handle(StationRemovedFromListPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (msg.stationId == null) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                StationUnlockManager.removeStationForPlayer(mc.player.getUUID(), msg.stationId);
            }
        });
        ctx.setPacketHandled(true);
    }
}
