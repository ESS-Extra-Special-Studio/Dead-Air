package uk.creatopia.unbound.dead_air.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import uk.creatopia.unbound.dead_air.Dead_air;
import uk.creatopia.unbound.dead_air.tower.KnownTowersClientCache;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server -> Client: sync known tower positions so client can compute signal locally (no per-tick packets needed). */
public class KnownTowersSyncPacket {
    public static final class TowerEntry {
        public final BlockPos towerPos;
        public final BlockPos panelPos;
        public final ResourceLocation stationId;

        public TowerEntry(BlockPos towerPos, BlockPos panelPos, ResourceLocation stationId) {
            this.towerPos = towerPos.immutable();
            this.panelPos = panelPos.immutable();
            this.stationId = stationId;
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
            buf.writeResourceLocation(e.stationId);
        }
    }

    public static KnownTowersSyncPacket decode(FriendlyByteBuf buf) {
        int n = buf.readInt();
        List<TowerEntry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BlockPos towerPos = buf.readBlockPos();
            BlockPos panelPos = buf.readBlockPos();
            ResourceLocation stationId = buf.readResourceLocation();
            list.add(new TowerEntry(towerPos, panelPos, stationId));
        }
        return new KnownTowersSyncPacket(list);
    }

    public static void handle(KnownTowersSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            KnownTowersClientCache.setTowers(msg.towers);
            Dead_air.LOGGER.info("[Dead Air] Client received known towers sync: {} towers", msg.towers.size());
        });
        ctx.setPacketHandled(true);
    }
}
