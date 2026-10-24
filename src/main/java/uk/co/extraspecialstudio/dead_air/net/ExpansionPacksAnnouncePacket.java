package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.music.ExpansionPacks;

import java.util.ArrayList;
import java.util.List;

/** Client -> Server: studio soundtrack packs installed on this player's game. */
public class ExpansionPacksAnnouncePacket implements CustomPacketPayload {
    public static final Type<ExpansionPacksAnnouncePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "expansion_packs"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExpansionPacksAnnouncePacket> STREAM_CODEC =
        StreamCodec.of(ExpansionPacksAnnouncePacket::encode, ExpansionPacksAnnouncePacket::decode);

    private static final int MAX_ENTRIES = 32;
    private static final int MAX_LENGTH = 64;
    private final List<String> namespaces;

    public ExpansionPacksAnnouncePacket(List<String> namespaces) {
        this.namespaces = namespaces;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, ExpansionPacksAnnouncePacket msg) {
        int n = Math.min(msg.namespaces.size(), MAX_ENTRIES);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) buf.writeUtf(msg.namespaces.get(i), MAX_LENGTH);
    }

    public static ExpansionPacksAnnouncePacket decode(RegistryFriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX_ENTRIES);
        List<String> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(buf.readUtf(MAX_LENGTH));
        return new ExpansionPacksAnnouncePacket(list);
    }

    public static void handle(ExpansionPacksAnnouncePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) ExpansionPacks.acceptFromPlayer(sp, msg.namespaces);
        });
    }
}
