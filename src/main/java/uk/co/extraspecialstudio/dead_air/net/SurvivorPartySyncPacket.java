package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.client.SurvivorPartyHud;

import java.util.ArrayList;
import java.util.List;

/** Server ? client: current FOLLOWING party snapshot for the HUD. */
public class SurvivorPartySyncPacket implements CustomPacketPayload {
    public static final Type<SurvivorPartySyncPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "survivor_party_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SurvivorPartySyncPacket> STREAM_CODEC =
        StreamCodec.of(SurvivorPartySyncPacket::encode, SurvivorPartySyncPacket::decode);

    public final int maxPartySize;
    public final int partyCount;
    public final List<Member> members;

    public SurvivorPartySyncPacket(int maxPartySize, int partyCount, List<Member> members) {
        this.maxPartySize = maxPartySize;
        this.partyCount = partyCount;
        this.members = members;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, SurvivorPartySyncPacket msg) {
        buf.writeVarInt(msg.maxPartySize);
        buf.writeVarInt(msg.partyCount);
        buf.writeVarInt(msg.members.size());
        for (Member m : msg.members) {
            buf.writeVarInt(m.entityId);
            buf.writeUtf(m.name, 64);
            buf.writeUtf(m.profession, 32);
            buf.writeFloat(m.hp);
            buf.writeFloat(m.maxHp);
            buf.writeBoolean(m.downed);
            buf.writeVarInt(m.distBlocks);
            buf.writeByte(m.gender);
            buf.writeByte(m.skinVariant);
            buf.writeVarInt(m.level);
            buf.writeVarInt(m.xp);
            buf.writeVarInt(m.xpToNext);
            buf.writeByte(m.skillScavenge);
            buf.writeByte(m.skillMedicine);
            buf.writeByte(m.skillRadio);
            buf.writeByte(m.skillCombat);
            buf.writeByte(m.partyOrder);
        }
    }

    public static SurvivorPartySyncPacket decode(RegistryFriendlyByteBuf buf) {
        int max = buf.readVarInt();
        int count = buf.readVarInt();
        int n = buf.readVarInt();
        List<Member> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Member(
                buf.readVarInt(),
                buf.readUtf(64),
                buf.readUtf(32),
                buf.readFloat(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readByte(),
                buf.readByte(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readByte(),
                buf.readByte(),
                buf.readByte(),
                buf.readByte(),
                buf.readByte()));
        }
        return new SurvivorPartySyncPacket(max, count, list);
    }

    public static void handle(SurvivorPartySyncPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> SurvivorPartyHud.applySync(msg.maxPartySize, msg.partyCount, msg.members));
    }

    public record Member(
        int entityId,
        String name,
        String profession,
        float hp,
        float maxHp,
        boolean downed,
        int distBlocks,
        int gender,
        int skinVariant,
        int level,
        int xp,
        int xpToNext,
        int skillScavenge,
        int skillMedicine,
        int skillRadio,
        int skillCombat,
        int partyOrder
    ) {
        public String skinTextureKey() {
            String g = gender == 1 ? "female" : "male";
            int v = Math.floorMod(skinVariant, 2);
            String prof = profession == null || profession.isEmpty() ? "scavenger" : profession;
            return prof + "_" + g + "_" + v;
        }

        public float skillReadiness() {
            int sum = skillScavenge + skillMedicine + skillRadio + skillCombat;
            return Math.min(1f, sum / 20f);
        }
    }
}
