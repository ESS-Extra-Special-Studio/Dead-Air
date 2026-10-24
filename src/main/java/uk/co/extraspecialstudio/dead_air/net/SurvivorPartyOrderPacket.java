package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.survivor.SurvivorParty;
import uk.co.extraspecialstudio.dead_air.survivor.SurvivorPartyOrders;

/** Client â†’ server: squad order for all FOLLOWING survivors owned by the sender. */
public class SurvivorPartyOrderPacket implements CustomPacketPayload {
    public static final Type<SurvivorPartyOrderPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "survivor_party_order"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SurvivorPartyOrderPacket> STREAM_CODEC =
        StreamCodec.of(SurvivorPartyOrderPacket::encode, SurvivorPartyOrderPacket::decode);

    public final int order;

    public SurvivorPartyOrderPacket(int order) {
        this.order = order;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, SurvivorPartyOrderPacket msg) {
        buf.writeVarInt(msg.order);
    }

    public static SurvivorPartyOrderPacket decode(RegistryFriendlyByteBuf buf) {
        return new SurvivorPartyOrderPacket(buf.readVarInt());
    }

    public static void handle(SurvivorPartyOrderPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (msg.order < SurvivorParty.ORDER_FOLLOW || msg.order > SurvivorParty.ORDER_REGROUP) return;
            SurvivorPartyOrders.apply(player, msg.order);
        });
    }
}
