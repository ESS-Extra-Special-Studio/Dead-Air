package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.survivor.SurvivorEntity;

/** Client -> Server: a button in the survivor dialogue. */
public class SurvivorActionPacket implements CustomPacketPayload {
    public static final Type<SurvivorActionPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "survivor_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SurvivorActionPacket> STREAM_CODEC =
        StreamCodec.of(SurvivorActionPacket::encode, SurvivorActionPacket::decode);

    public static final int RECRUIT = 0;
    public static final int FOLLOW = 1;
    public static final int SETTLE = 2;
    public static final int COLLECT = 3;
    public static final int DISMISS = 4;
    public static final int SEND_TO_BASE = 5;
    public static final int OPEN_EQUIPMENT = 6;
    public static final int IDLE_CHAT = 7;
    public static final int WHO_ARE_YOU = 8;
    public static final int CYCLE_JOB = 9;
    public static final int DEFEND_HERE = 10;
    public static final int PATROL_ADD = 11;
    public static final int PATROL_CLEAR = 12;
    public static final int PATROL_START = 13;

    public final int entityId;
    public final int action;

    public SurvivorActionPacket(int entityId, int action) {
        this.entityId = entityId;
        this.action = action;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, SurvivorActionPacket msg) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.action);
    }

    public static SurvivorActionPacket decode(RegistryFriendlyByteBuf buf) {
        return new SurvivorActionPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(SurvivorActionPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            var entity = player.level().getEntity(msg.entityId);
            if (entity instanceof SurvivorEntity survivor && survivor.distanceTo(player) < 12) {
                survivor.handleAction(player, msg.action);
            }
        });
    }
}
