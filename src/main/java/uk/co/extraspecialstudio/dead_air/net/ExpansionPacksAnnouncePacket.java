package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import uk.co.extraspecialstudio.dead_air.music.ExpansionPacks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Client -> Server: studio soundtrack packs installed on this player's game. */
public class ExpansionPacksAnnouncePacket {
    private static final int MAX_ENTRIES = 32;
    private static final int MAX_LENGTH = 64;
    private final List<String> namespaces;

    public ExpansionPacksAnnouncePacket(List<String> namespaces) {
        this.namespaces = namespaces;
    }

    public static void encode(ExpansionPacksAnnouncePacket msg, FriendlyByteBuf buf) {
        int n = Math.min(msg.namespaces.size(), MAX_ENTRIES);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) buf.writeUtf(msg.namespaces.get(i), MAX_LENGTH);
    }

    public static ExpansionPacksAnnouncePacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX_ENTRIES);
        List<String> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(buf.readUtf(MAX_LENGTH));
        return new ExpansionPacksAnnouncePacket(list);
    }

    public static void handle(ExpansionPacksAnnouncePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> ExpansionPacks.acceptFromPlayer(ctx.getSender(), msg.namespaces));
        ctx.setPacketHandled(true);
    }
}
