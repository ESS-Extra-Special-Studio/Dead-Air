package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import uk.co.extraspecialstudio.dead_air.tower.KnownTowersClientCache;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server -> Client: sync known tower positions so client can compute signal locally (no per-tick packets needed). */
public class KnownTowersSyncPacket {
    public static final class TowerEntry {
        public final BlockPos towerPos;
        public final BlockPos panelPos;
        /** Null only for rare detection-only entries after station cleared; usually provisional or activated station. */
        public final ResourceLocation stationId;
        /** True when this panel has the Jukebox Upgrade module (RadioTowers default cycle may offer Jukebox FM). */
        public final boolean jukeboxModuleInstalled;
        /** True when Signal Upgrade / Tower Boost is installed (T2 cross-dim sync). */
        public final boolean signalBoostInstalled;
        /** True when the panel is activated/broadcasting. False = detected only (direction, not music). */
        public final boolean activated;

        public TowerEntry(BlockPos towerPos, BlockPos panelPos, ResourceLocation stationId) {
            this(towerPos, panelPos, stationId, false, false, true);
        }

        public TowerEntry(BlockPos towerPos, BlockPos panelPos, ResourceLocation stationId, boolean jukeboxModuleInstalled) {
            this(towerPos, panelPos, stationId, jukeboxModuleInstalled, false, true);
        }

        public TowerEntry(BlockPos towerPos, BlockPos panelPos, ResourceLocation stationId,
                          boolean jukeboxModuleInstalled, boolean signalBoostInstalled) {
            this(towerPos, panelPos, stationId, jukeboxModuleInstalled, signalBoostInstalled, true);
        }

        public TowerEntry(BlockPos towerPos, BlockPos panelPos, ResourceLocation stationId,
                          boolean jukeboxModuleInstalled, boolean signalBoostInstalled, boolean activated) {
            this.towerPos = towerPos.immutable();
            this.panelPos = panelPos.immutable();
            this.stationId = stationId;
            this.jukeboxModuleInstalled = jukeboxModuleInstalled;
            this.signalBoostInstalled = signalBoostInstalled;
            this.activated = activated;
        }
    }

    private final List<TowerEntry> towers;

    public KnownTowersSyncPacket(List<TowerEntry> towers) {
        this.towers = towers != null ? new ArrayList<>(towers) : List.of();
    }

    public static void encode(KnownTowersSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.towers.size());
        for (TowerEntry e : msg.towers) {
            buf.writeBlockPos(e.towerPos);
            buf.writeBlockPos(e.panelPos);
            buf.writeBoolean(e.stationId != null);
            if (e.stationId != null) buf.writeResourceLocation(e.stationId);
            buf.writeBoolean(e.jukeboxModuleInstalled);
            buf.writeBoolean(e.signalBoostInstalled);
            buf.writeBoolean(e.activated);
        }
    }

    public static KnownTowersSyncPacket decode(FriendlyByteBuf buf) {
        int n = buf.readInt();
        List<TowerEntry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BlockPos towerPos = buf.readBlockPos();
            BlockPos panelPos = buf.readBlockPos();
            ResourceLocation stationId = buf.readBoolean() ? buf.readResourceLocation() : null;
            boolean juke = buf.readBoolean();
            boolean signal = buf.readBoolean();
            boolean activated = buf.readBoolean();
            list.add(new TowerEntry(towerPos, panelPos, stationId, juke, signal, activated));
        }
        return new KnownTowersSyncPacket(list);
    }

    public static void handle(KnownTowersSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            KnownTowersClientCache.setTowers(msg.towers);
            boolean anyActivated = false;
            for (TowerEntry e : msg.towers) {
                if (e.activated) { anyActivated = true; break; }
            }
            if (anyActivated) {
                uk.co.extraspecialstudio.dead_air.audio.AudioManager.tryStartMusicIfTuned(null);
            }
        });
        ctx.setPacketHandled(true);
    }
}
