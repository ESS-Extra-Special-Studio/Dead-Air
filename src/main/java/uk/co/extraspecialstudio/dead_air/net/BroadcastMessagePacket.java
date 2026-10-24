package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server -> Client: an emergency broadcast transmission (already scrambled if the player has no decoder). */
public class BroadcastMessagePacket {
    private static final int MAX_LINES = 12;
    private static final int MAX_LENGTH = 256;
    public final String title;
    public final List<String> lines;
    public final boolean decoded;
    public final boolean story;

    public BroadcastMessagePacket(String title, List<String> lines, boolean decoded, boolean story) {
        this.title = title;
        this.lines = lines;
        this.decoded = decoded;
        this.story = story;
    }

    public static void encode(BroadcastMessagePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.title, MAX_LENGTH);
        int n = Math.min(msg.lines.size(), MAX_LINES);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) buf.writeUtf(msg.lines.get(i), MAX_LENGTH);
        buf.writeBoolean(msg.decoded);
        buf.writeBoolean(msg.story);
    }

    public static BroadcastMessagePacket decode(FriendlyByteBuf buf) {
        String title = buf.readUtf(MAX_LENGTH);
        int n = Math.min(buf.readVarInt(), MAX_LINES);
        List<String> lines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) lines.add(buf.readUtf(MAX_LENGTH));
        return new BroadcastMessagePacket(title, lines, buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(BroadcastMessagePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> uk.co.extraspecialstudio.dead_air.client.DeadAirClientPackets.onBroadcast(msg)));
        ctx.setPacketHandled(true);
    }
}
