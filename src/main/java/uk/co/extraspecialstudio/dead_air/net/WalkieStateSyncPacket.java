package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;
import uk.co.extraspecialstudio.dead_air.walkie.WalkieNbt;
import uk.co.extraspecialstudio.dead_air.walkie.WalkieTalkieManager;

public class WalkieStateSyncPacket implements CustomPacketPayload {
    public static final Type<WalkieStateSyncPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "walkie_state_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WalkieStateSyncPacket> STREAM_CODEC =
        StreamCodec.of(WalkieStateSyncPacket::encode, WalkieStateSyncPacket::decode);

    private final int slot;
    private final boolean on;
    private final ResourceLocation stationId;
    private final boolean linked;
    private final ResourceLocation linkDim;
    private final BlockPos linkPanel;
    private final ResourceLocation linkStationId;

    public WalkieStateSyncPacket(int slot, boolean on, ResourceLocation stationId,
                                 boolean linked, ResourceLocation linkDim, BlockPos linkPanel,
                                 ResourceLocation linkStationId) {
        this.slot = slot;
        this.on = on;
        this.stationId = stationId;
        this.linked = linked;
        this.linkDim = linkDim;
        this.linkPanel = linkPanel;
        this.linkStationId = linkStationId;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static WalkieStateSyncPacket fromWalkie(int slot, ItemStack walkie) {
        boolean on = true;
        ResourceLocation stationId = WalkieTalkieManager.getTunedStationId(walkie);
        var tag = WalkieNbt.get(walkie);
        if (tag != null && tag.contains("DeadAirOn")) {
            on = tag.getBoolean("DeadAirOn");
        }
        boolean linked = WalkieTalkieManager.isUsingLinkedMode(walkie)
            || (WalkieTalkieManager.hasLinkedTower(walkie)
                && WalkieTalkieManager.getLinkMode(walkie) == WalkieTalkieManager.LinkMode.LINKED);
        ResourceLocation linkDim = null;
        BlockPos linkPanel = null;
        ResourceLocation linkStationId = null;
        if (WalkieTalkieManager.hasLinkedTower(walkie)) {
            var dimKey = WalkieTalkieManager.getLinkedDimension(walkie);
            if (dimKey != null) linkDim = dimKey.location();
            linkPanel = WalkieTalkieManager.getLinkedPanelPos(walkie);
            linkStationId = WalkieTalkieManager.getLinkedStationId(walkie);
            linked = WalkieTalkieManager.getLinkMode(walkie) == WalkieTalkieManager.LinkMode.LINKED;
        } else {
            linked = false;
        }
        return new WalkieStateSyncPacket(slot, on, stationId, linked, linkDim, linkPanel, linkStationId);
    }

    public static void encode(RegistryFriendlyByteBuf buf, WalkieStateSyncPacket msg) {
        buf.writeVarInt(msg.slot);
        buf.writeBoolean(msg.on);
        buf.writeBoolean(msg.stationId != null);
        if (msg.stationId != null) buf.writeResourceLocation(msg.stationId);
        buf.writeBoolean(msg.linked);
        boolean hasLinkTower = msg.linkDim != null && msg.linkPanel != null;
        buf.writeBoolean(hasLinkTower);
        if (hasLinkTower) {
            buf.writeResourceLocation(msg.linkDim);
            buf.writeBlockPos(msg.linkPanel);
            buf.writeBoolean(msg.linkStationId != null);
            if (msg.linkStationId != null) buf.writeResourceLocation(msg.linkStationId);
        }
    }

    public static WalkieStateSyncPacket decode(RegistryFriendlyByteBuf buf) {
        int slot = buf.readVarInt();
        boolean on = buf.readBoolean();
        ResourceLocation stationId = buf.readBoolean() ? buf.readResourceLocation() : null;
        boolean linked = buf.readBoolean();
        ResourceLocation linkDim = null;
        BlockPos linkPanel = null;
        ResourceLocation linkStationId = null;
        if (buf.readBoolean()) {
            linkDim = buf.readResourceLocation();
            linkPanel = buf.readBlockPos();
            if (buf.readBoolean()) linkStationId = buf.readResourceLocation();
        }
        return new WalkieStateSyncPacket(slot, on, stationId, linked, linkDim, linkPanel, linkStationId);
    }

    public static void handle(WalkieStateSyncPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ItemStack walkie = resolveWalkie(player, msg.slot);
            if (walkie.isEmpty() || !WalkieTalkieManager.isWalkieTalkieItem(walkie)) return;

            WalkieTalkieManager.claimAsActiveRadio(player, walkie);

            if (msg.stationId != null) {
                RadioStation station = StationRegistry.getStation(msg.stationId);
                if (station != null) {
                    WalkieTalkieManager.applyServerTune(player, walkie, station, msg.on);
                } else {
                    WalkieTalkieManager.clearTune(walkie);
                    WalkieTalkieManager.applyServerPower(player, walkie, msg.on);
                }
            } else {
                WalkieTalkieManager.clearTune(walkie);
                WalkieTalkieManager.applyServerPower(player, walkie, msg.on);
            }

            if (msg.linkDim != null && msg.linkPanel != null && WalkieTalkieManager.canLinkCrossDim(walkie)) {
                ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, msg.linkDim);
                RadioStation linkStation = msg.linkStationId != null
                    ? StationRegistry.getStation(msg.linkStationId)
                    : (msg.stationId != null ? StationRegistry.getStation(msg.stationId) : null);
                WalkieTalkieManager.applyServerLink(walkie, dim, msg.linkPanel, linkStation, msg.linked);
            } else if (WalkieTalkieManager.canLinkCrossDim(walkie)) {
                WalkieTalkieManager.clearLink(walkie);
            }
        });
    }

    private static ItemStack resolveWalkie(ServerPlayer player, int slot) {
        if (slot == -1) return player.getOffhandItem();
        if (slot < 0 || slot >= player.getInventory().items.size()) return ItemStack.EMPTY;
        return player.getInventory().items.get(slot);
    }
}
