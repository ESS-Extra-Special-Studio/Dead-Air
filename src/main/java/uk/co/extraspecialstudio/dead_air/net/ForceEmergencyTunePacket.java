package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> Client: switch the active radio to the Emergency Broadcast, or jam / unjam radios. */
public class ForceEmergencyTunePacket {
    public static final int TUNE_EMERGENCY = 0;
    public static final int JAM_ON = 1;
    public static final int JAM_OFF = 2;
    public final int mode;

    public ForceEmergencyTunePacket(int mode) {
        this.mode = mode;
    }

    public static void encode(ForceEmergencyTunePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.mode);
    }

    public static ForceEmergencyTunePacket decode(FriendlyByteBuf buf) {
        return new ForceEmergencyTunePacket(buf.readVarInt());
    }

    public static void handle(ForceEmergencyTunePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> uk.co.extraspecialstudio.dead_air.client.DeadAirClientPackets.onForceTune(msg.mode)));
        ctx.setPacketHandled(true);
    }
}
