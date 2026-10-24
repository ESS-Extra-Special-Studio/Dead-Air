package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;
import uk.co.extraspecialstudio.dead_air.radio.RadioManager;

import java.util.function.Supplier;

/**
 * Client → Server: apply the active radio's tune/power/link NBT and untune every other radio.
 * Required so Static Note drops (server-side) see the same active radio as client audio.
 */
public class RadioStateSyncPacket {
    /** Inventory slot in {@code player.getInventory().items}, or -1 for offhand. */
    private final int slot;
    private final boolean on;
    private final ResourceLocation stationId;
    private final boolean linked;
    private final ResourceLocation linkDim;
    private final BlockPos linkPanel;
    private final ResourceLocation linkStationId;

    public RadioStateSyncPacket(int slot, boolean on, ResourceLocation stationId,
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

    public static RadioStateSyncPacket fromRadio(int slot, ItemStack radio) {
        boolean on = true;
        ResourceLocation stationId = RadioManager.getTunedStationId(radio);
        var tag = radio.getTag();
        if (tag != null && tag.contains("DeadAirOn")) {
            on = tag.getBoolean("DeadAirOn");
        }
        boolean linked = RadioManager.isUsingLinkedMode(radio)
            || (RadioManager.hasLinkedTower(radio)
                && RadioManager.getLinkMode(radio) == RadioManager.LinkMode.LINKED);
        ResourceLocation linkDim = null;
        BlockPos linkPanel = null;
        ResourceLocation linkStationId = null;
        if (RadioManager.hasLinkedTower(radio)) {
            var dimKey = RadioManager.getLinkedDimension(radio);
            if (dimKey != null) linkDim = dimKey.location();
            linkPanel = RadioManager.getLinkedPanelPos(radio);
            linkStationId = RadioManager.getLinkedStationId(radio);
            linked = RadioManager.getLinkMode(radio) == RadioManager.LinkMode.LINKED;
        } else {
            linked = false;
        }
        return new RadioStateSyncPacket(slot, on, stationId, linked, linkDim, linkPanel, linkStationId);
    }

    public static void encode(RadioStateSyncPacket msg, FriendlyByteBuf buf) {
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

    public static RadioStateSyncPacket decode(FriendlyByteBuf buf) {
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
        return new RadioStateSyncPacket(slot, on, stationId, linked, linkDim, linkPanel, linkStationId);
    }

    public static void handle(RadioStateSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ItemStack radio = resolveRadio(player, msg.slot);
            if (radio.isEmpty() || !RadioManager.isRadioItem(radio)) return;

            RadioManager.claimAsActiveRadio(player, radio);

            if (msg.stationId != null) {
                RadioStation station = StationRegistry.getStation(msg.stationId);
                if (station != null) {
                    RadioManager.applyServerTune(player, radio, station, msg.on);
                } else {
                    RadioManager.clearTune(radio);
                    RadioManager.applyServerPower(player, radio, msg.on);
                }
            } else {
                RadioManager.clearTune(radio);
                RadioManager.applyServerPower(player, radio, msg.on);
            }

            if (msg.linkDim != null && msg.linkPanel != null && RadioManager.canLinkCrossDim(radio)) {
                ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, msg.linkDim);
                RadioStation linkStation = msg.linkStationId != null
                    ? StationRegistry.getStation(msg.linkStationId)
                    : (msg.stationId != null ? StationRegistry.getStation(msg.stationId) : null);
                RadioManager.applyServerLink(radio, dim, msg.linkPanel, linkStation, msg.linked);
            } else if (RadioManager.canLinkCrossDim(radio)) {
                RadioManager.clearLink(radio);
            }
        });
        ctx.setPacketHandled(true);
    }

    private static ItemStack resolveRadio(ServerPlayer player, int slot) {
        if (slot == -1) return player.getOffhandItem();
        if (slot < 0 || slot >= player.getInventory().items.size()) return ItemStack.EMPTY;
        return player.getInventory().items.get(slot);
    }
}
