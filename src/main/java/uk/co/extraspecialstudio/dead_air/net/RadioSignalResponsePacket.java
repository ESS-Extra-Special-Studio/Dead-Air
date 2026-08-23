package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import uk.co.extraspecialstudio.dead_air.audio.AudioManager;
import uk.co.extraspecialstudio.dead_air.tower.KnownTowersClientCache;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server -> Client: signal strength for a station (0 = no tower / not in range). Optional tower list so client can compute signal locally. */
public class RadioSignalResponsePacket {
    private final ResourceLocation stationId;
    private final float signal;
    /** When non-empty, client updates KnownTowersClientCache so it can compute signal from position every tick (volume steps, stop when out of range). */
    private final List<KnownTowersSyncPacket.TowerEntry> towers;

    public RadioSignalResponsePacket(ResourceLocation stationId, float signal) {
        this(stationId, signal, null);
    }

    public RadioSignalResponsePacket(ResourceLocation stationId, float signal, List<KnownTowersSyncPacket.TowerEntry> towers) {
        this.stationId = stationId;
        this.signal = signal;
        this.towers = towers != null ? new ArrayList<>(towers) : List.of();
    }

    public static void encode(RadioSignalResponsePacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.stationId);
        buf.writeFloat(msg.signal);
        buf.writeInt(msg.towers.size());
        for (KnownTowersSyncPacket.TowerEntry e : msg.towers) {
            buf.writeBlockPos(e.towerPos);
            buf.writeBlockPos(e.panelPos);
            buf.writeBoolean(e.stationId != null);
            if (e.stationId != null) buf.writeResourceLocation(e.stationId);
            buf.writeBoolean(e.jukeboxModuleInstalled);
            buf.writeBoolean(e.signalBoostInstalled);
            buf.writeBoolean(e.activated);
        }
    }

    public static RadioSignalResponsePacket decode(FriendlyByteBuf buf) {
        ResourceLocation stationId = buf.readResourceLocation();
        float signal = buf.readFloat();
        int n = buf.readInt();
        List<KnownTowersSyncPacket.TowerEntry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BlockPos towerPos = buf.readBlockPos();
            BlockPos panelPos = buf.readBlockPos();
            ResourceLocation sid = buf.readBoolean() ? buf.readResourceLocation() : null;
            boolean juke = buf.readBoolean();
            boolean signalBoost = buf.readBoolean();
            boolean activated = buf.readBoolean();
            list.add(new KnownTowersSyncPacket.TowerEntry(
                towerPos, panelPos, sid, juke, signalBoost, activated));
        }
        return new RadioSignalResponsePacket(stationId, signal, list);
    }

    public static void handle(RadioSignalResponsePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            int clientTick = 0;
            if (net.minecraft.client.Minecraft.getInstance().player != null) {
                clientTick = net.minecraft.client.Minecraft.getInstance().player.tickCount;
            }
            // Do not replace the cache with an empty list: non-Overworld / edge responses used to wipe sync and
            // forced the walkie GUI to fall back to all unlocked stations (every station ever discovered).
            if (!msg.towers.isEmpty()) {
                KnownTowersClientCache.setTowers(msg.towers);
            }
            AudioManager.setServerSignal(msg.stationId, msg.signal, clientTick);
            if (msg.signal >= 0.05f) {
                AudioManager.tryPlayFromServerSignal(msg.stationId, msg.signal);
            } else if (!msg.towers.isEmpty()) {
                AudioManager.tryStartMusicIfTuned(msg.stationId);
            }
        });
        ctx.setPacketHandled(true);
    }
}
