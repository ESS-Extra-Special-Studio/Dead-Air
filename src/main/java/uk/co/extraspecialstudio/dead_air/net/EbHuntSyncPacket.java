package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/** Server â†’ client: nearest pending EB pin for compass / beep hunt (or clear when inactive). */
public class EbHuntSyncPacket implements CustomPacketPayload {
    public static final Type<EbHuntSyncPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "eb_hunt_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EbHuntSyncPacket> STREAM_CODEC =
        StreamCodec.of(EbHuntSyncPacket::encode, EbHuntSyncPacket::decode);

    public final boolean active;
    public final int targetX;
    public final int targetZ;
    public final String siteKind;
    public final String siteKey;
    public final boolean playVo;

    public EbHuntSyncPacket(boolean active, int targetX, int targetZ, String siteKind, String siteKey, boolean playVo) {
        this.active = active;
        this.targetX = targetX;
        this.targetZ = targetZ;
        this.siteKind = siteKind != null ? siteKind : "";
        this.siteKey = siteKey != null ? siteKey : "";
        this.playVo = playVo;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, EbHuntSyncPacket msg) {
        buf.writeBoolean(msg.active);
        buf.writeVarInt(msg.targetX);
        buf.writeVarInt(msg.targetZ);
        buf.writeUtf(msg.siteKind, 32);
        buf.writeUtf(msg.siteKey, 256);
        buf.writeBoolean(msg.playVo);
    }

    public static EbHuntSyncPacket decode(RegistryFriendlyByteBuf buf) {
        return new EbHuntSyncPacket(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
            buf.readUtf(32), buf.readUtf(256), buf.readBoolean());
    }

    public static void handle(EbHuntSyncPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> uk.co.extraspecialstudio.dead_air.client.EbHuntClient.apply(msg));
    }
}
