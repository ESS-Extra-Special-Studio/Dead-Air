package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.tower.KnownTowersClientCache;

import java.util.ArrayList;
import java.util.List;

public class KnownTowersSyncPacket implements CustomPacketPayload {
    public static final Type<KnownTowersSyncPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "known_towers_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, KnownTowersSyncPacket> STREAM_CODEC =
        StreamCodec.of(KnownTowersSyncPacket::encode, KnownTowersSyncPacket::decode);

    public static final class TowerEntry {
        public final BlockPos towerPos;
        public final BlockPos panelPos;
        public final ResourceLocation stationId;
        public final boolean jukeboxModuleInstalled;
        public final boolean signalBoostInstalled;
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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, KnownTowersSyncPacket msg) {
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

    public static KnownTowersSyncPacket decode(RegistryFriendlyByteBuf buf) {
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

    public static void handle(KnownTowersSyncPacket msg, IPayloadContext ctx) {
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
    }
}
