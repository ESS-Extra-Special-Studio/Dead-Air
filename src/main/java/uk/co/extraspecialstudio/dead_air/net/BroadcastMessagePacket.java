package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.extraspecialstudio.dead_air.Dead_air;

import java.util.ArrayList;
import java.util.List;

/** Server -> Client: an emergency broadcast transmission (already scrambled if the player has no decoder). */
public class BroadcastMessagePacket implements CustomPacketPayload {
    public static final Type<BroadcastMessagePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "broadcast_message"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BroadcastMessagePacket> STREAM_CODEC =
        StreamCodec.of(BroadcastMessagePacket::encode, BroadcastMessagePacket::decode);

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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, BroadcastMessagePacket msg) {
        buf.writeUtf(msg.title, MAX_LENGTH);
        int n = Math.min(msg.lines.size(), MAX_LINES);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) buf.writeUtf(msg.lines.get(i), MAX_LENGTH);
        buf.writeBoolean(msg.decoded);
        buf.writeBoolean(msg.story);
    }

    public static BroadcastMessagePacket decode(RegistryFriendlyByteBuf buf) {
        String title = buf.readUtf(MAX_LENGTH);
        int n = Math.min(buf.readVarInt(), MAX_LINES);
        List<String> lines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) lines.add(buf.readUtf(MAX_LENGTH));
        return new BroadcastMessagePacket(title, lines, buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(BroadcastMessagePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> uk.co.extraspecialstudio.dead_air.client.DeadAirClientPackets.onBroadcast(msg));
    }
}
