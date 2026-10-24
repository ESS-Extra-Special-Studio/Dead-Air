package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> Client: encounter audio / HUD cues. {@code active} keeps the encounter drone + radio jam;
 * atmospheric static fog is proximity-driven on the client while a Listener is nearby.
 * {@code silenceTicks} > 0 shows the Listening Silence cue.
 */
public class EncounterStatePacket {
    public final boolean active;
    public final int silenceTicks;
    public final int screamFlashTicks;

    public EncounterStatePacket(boolean active, int silenceTicks, int screamFlashTicks) {
        this.active = active;
        this.silenceTicks = silenceTicks;
        this.screamFlashTicks = screamFlashTicks;
    }

    public static void encode(EncounterStatePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.active);
        buf.writeVarInt(msg.silenceTicks);
        buf.writeVarInt(msg.screamFlashTicks);
    }

    public static EncounterStatePacket decode(FriendlyByteBuf buf) {
        return new EncounterStatePacket(buf.readBoolean(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(EncounterStatePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> uk.co.extraspecialstudio.dead_air.client.DeadAirClientPackets.onEncounterState(msg)));
        ctx.setPacketHandled(true);
    }
}
