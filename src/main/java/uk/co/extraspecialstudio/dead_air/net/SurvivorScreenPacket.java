package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/** Server -> Client: open the survivor dialogue for this entity. */
public class SurvivorScreenPacket implements CustomPacketPayload {
    public static final Type<SurvivorScreenPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "survivor_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SurvivorScreenPacket> STREAM_CODEC =
        StreamCodec.of(SurvivorScreenPacket::encode, SurvivorScreenPacket::decode);

    public final int entityId;
    public final String name;
    public final String profession;
    public final int state;
    public final boolean ownedByYou;
    public final boolean supplyReady;
    public final String line;
    public final boolean hasBase;
    public final String jobId;
    public final int duty;
    public final String personality;
    public final String orientation;
    public final String hopeKey;
    public final boolean missing;

    public SurvivorScreenPacket(int entityId, String name, String profession, int state, boolean ownedByYou,
                                boolean supplyReady, String line, boolean hasBase, String jobId, int duty,
                                String personality, String orientation, String hopeKey, boolean missing) {
        this.entityId = entityId;
        this.name = name;
        this.profession = profession;
        this.state = state;
        this.ownedByYou = ownedByYou;
        this.supplyReady = supplyReady;
        this.line = line;
        this.hasBase = hasBase;
        this.jobId = jobId;
        this.duty = duty;
        this.personality = personality;
        this.orientation = orientation;
        this.hopeKey = hopeKey;
        this.missing = missing;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, SurvivorScreenPacket msg) {
        buf.writeVarInt(msg.entityId);
        buf.writeUtf(msg.name, 64);
        buf.writeUtf(msg.profession, 64);
        buf.writeVarInt(msg.state);
        buf.writeBoolean(msg.ownedByYou);
        buf.writeBoolean(msg.supplyReady);
        buf.writeUtf(msg.line, 512);
        buf.writeBoolean(msg.hasBase);
        buf.writeUtf(msg.jobId, 32);
        buf.writeVarInt(msg.duty);
        buf.writeUtf(msg.personality, 32);
        buf.writeUtf(msg.orientation, 32);
        buf.writeUtf(msg.hopeKey, 128);
        buf.writeBoolean(msg.missing);
    }

    public static SurvivorScreenPacket decode(RegistryFriendlyByteBuf buf) {
        return new SurvivorScreenPacket(
            buf.readVarInt(), buf.readUtf(64), buf.readUtf(64), buf.readVarInt(),
            buf.readBoolean(), buf.readBoolean(), buf.readUtf(512),
            buf.readBoolean(), buf.readUtf(32), buf.readVarInt(),
            buf.readUtf(32), buf.readUtf(32), buf.readUtf(128), buf.readBoolean());
    }

    public static void handle(SurvivorScreenPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> uk.co.extraspecialstudio.dead_air.client.DeadAirClientPackets.onSurvivorScreen(msg));
    }
}
