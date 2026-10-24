package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import uk.co.extraspecialstudio.dead_air.survivor.SurvivorParty;
import uk.co.extraspecialstudio.dead_air.survivor.SurvivorPartyOrders;

import java.util.function.Supplier;

/** Client → server: squad order for all FOLLOWING survivors owned by the sender. */
public class SurvivorPartyOrderPacket {
    public final int order;

    public SurvivorPartyOrderPacket(int order) {
        this.order = order;
    }

    public static void encode(SurvivorPartyOrderPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.order);
    }

    public static SurvivorPartyOrderPacket decode(FriendlyByteBuf buf) {
        return new SurvivorPartyOrderPacket(buf.readVarInt());
    }

    public static void handle(SurvivorPartyOrderPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            if (msg.order < SurvivorParty.ORDER_FOLLOW || msg.order > SurvivorParty.ORDER_REGROUP) return;
            SurvivorPartyOrders.apply(player, msg.order);
        });
        ctx.setPacketHandled(true);
    }
}
