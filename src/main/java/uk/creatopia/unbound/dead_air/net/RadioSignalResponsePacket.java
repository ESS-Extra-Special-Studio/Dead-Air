package uk.creatopia.unbound.dead_air.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import uk.creatopia.unbound.dead_air.Dead_air;
import uk.creatopia.unbound.dead_air.audio.AudioManager;

import java.util.function.Supplier;

/** Server -> Client: signal strength for a station (0 = no tower / not in range). */
public class RadioSignalResponsePacket {
    private final ResourceLocation stationId;
    private final float signal;

    public RadioSignalResponsePacket(ResourceLocation stationId, float signal) {
        this.stationId = stationId;
        this.signal = signal;
    }

    public static void encode(RadioSignalResponsePacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.stationId);
        buf.writeFloat(msg.signal);
    }

    public static RadioSignalResponsePacket decode(FriendlyByteBuf buf) {
        return new RadioSignalResponsePacket(buf.readResourceLocation(), buf.readFloat());
    }

    public static void handle(RadioSignalResponsePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (msg.signal >= 0.1f) {
                Dead_air.LOGGER.info("Radio signal response: station {} signal {} (will play if tuned)", msg.stationId, msg.signal);
            }
            AudioManager.setServerSignal(msg.stationId, msg.signal);
        });
        ctx.setPacketHandled(true);
    }
}
