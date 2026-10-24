package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import uk.co.extraspecialstudio.dead_air.client.SurvivorPartyHud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server → client: current FOLLOWING party snapshot for the HUD. */
public class SurvivorPartySyncPacket {
    public final int maxPartySize;
    /** Roster FOLLOWING count (includes unloaded). */
    public final int partyCount;
    public final List<Member> members;

    public SurvivorPartySyncPacket(int maxPartySize, int partyCount, List<Member> members) {
        this.maxPartySize = maxPartySize;
        this.partyCount = partyCount;
        this.members = members;
    }

    public static void encode(SurvivorPartySyncPacket msg, FriendlyByteBuf buf) {
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

    public static SurvivorPartySyncPacket decode(FriendlyByteBuf buf) {
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

    public static void handle(SurvivorPartySyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> SurvivorPartyHud.applySync(msg.maxPartySize, msg.partyCount, msg.members)));
        ctx.setPacketHandled(true);
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

        /** 0–1 average skill readiness. */
        public float skillReadiness() {
            int sum = skillScavenge + skillMedicine + skillRadio + skillCombat;
            return Math.min(1f, sum / 20f);
        }
    }
}
