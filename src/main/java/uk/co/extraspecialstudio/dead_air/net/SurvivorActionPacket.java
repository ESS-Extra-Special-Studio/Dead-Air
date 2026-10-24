package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import uk.co.extraspecialstudio.dead_air.survivor.SurvivorEntity;

import java.util.function.Supplier;

/** Client -> Server: a button in the survivor dialogue. */
public class SurvivorActionPacket {
    public static final int RECRUIT = 0;
    public static final int FOLLOW = 1;
    public static final int SETTLE = 2;
    public static final int COLLECT = 3;
    public static final int DISMISS = 4;
    public static final int SEND_TO_BASE = 5;
    public static final int OPEN_EQUIPMENT = 6;
    public static final int IDLE_CHAT = 7;
    public static final int WHO_ARE_YOU = 8;
    /** Legacy no-op (assignable jobs removed). Kept so old clients do not desync. */
    @Deprecated
    public static final int CYCLE_JOB = 9;
    public static final int DEFEND_HERE = 10;
    public static final int PATROL_ADD = 11;
    public static final int PATROL_CLEAR = 12;
    public static final int PATROL_START = 13;
    /** Medic profession only — heal owner or revive nearby downed party member. */
    public static final int HEAL = 14;
    /** Civilian training — change profession (SETTLED). */
    public static final int TRAIN_SCAVENGER = 15;
    public static final int TRAIN_MEDIC = 16;
    public static final int TRAIN_RADIO = 17;
    public static final int TRAIN_MILITARY = 18;
    public static final int TRAIN_PILOT = 19;
    /** Client closed the dialogue UI — survivor can move again. */
    public static final int END_DIALOGUE = 20;

    public final int entityId;
    public final int action;

    public SurvivorActionPacket(int entityId, int action) {
        this.entityId = entityId;
        this.action = action;
    }

    public static void encode(SurvivorActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.action);
    }

    public static SurvivorActionPacket decode(FriendlyByteBuf buf) {
        return new SurvivorActionPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(SurvivorActionPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            var player = ctx.getSender();
            if (player == null) return;
            var entity = player.level().getEntity(msg.entityId);
            if (entity instanceof SurvivorEntity survivor && survivor.distanceTo(player) < 12) {
                survivor.handleAction(player, msg.action);
            }
        });
        ctx.setPacketHandled(true);
    }
}
