package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: nearest pending EB pin for compass / beep hunt (or clear when inactive). */
public class EbHuntSyncPacket {
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

    public static void encode(EbHuntSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.active);
        buf.writeVarInt(msg.targetX);
        buf.writeVarInt(msg.targetZ);
        buf.writeUtf(msg.siteKind, 32);
        buf.writeUtf(msg.siteKey, 256);
        buf.writeBoolean(msg.playVo);
    }

    public static EbHuntSyncPacket decode(FriendlyByteBuf buf) {
        return new EbHuntSyncPacket(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
            buf.readUtf(32), buf.readUtf(256), buf.readBoolean());
    }

    public static void handle(EbHuntSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
            net.minecraftforge.api.distmarker.Dist.CLIENT,
            () -> () -> uk.co.extraspecialstudio.dead_air.client.EbHuntClient.apply(msg)));
        ctx.setPacketHandled(true);
    }
}
