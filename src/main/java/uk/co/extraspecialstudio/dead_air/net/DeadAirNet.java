package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class DeadAirNet {
    public static final String PROTOCOL = "1";

    private static Runnable openWalkieGuiCallback;

    public static void setOpenWalkieGuiCallback(Runnable r) {
        openWalkieGuiCallback = r;
    }

    static void runOpenWalkieGuiCallback() {
        if (openWalkieGuiCallback != null) {
            openWalkieGuiCallback.run();
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar(PROTOCOL);
        reg.playToServer(RadioSignalRequestPacket.TYPE, RadioSignalRequestPacket.STREAM_CODEC, RadioSignalRequestPacket::handle);
        reg.playToClient(RadioSignalResponsePacket.TYPE, RadioSignalResponsePacket.STREAM_CODEC, RadioSignalResponsePacket::handle);
        reg.playToClient(KnownTowersSyncPacket.TYPE, KnownTowersSyncPacket.STREAM_CODEC, KnownTowersSyncPacket::handle);
        reg.playToServer(RequestTowerSyncPacket.TYPE, RequestTowerSyncPacket.STREAM_CODEC, RequestTowerSyncPacket::handle);
        reg.playToClient(OpenWalkieGuiPacket.TYPE, OpenWalkieGuiPacket.STREAM_CODEC, OpenWalkieGuiPacket::handle);
        reg.playToClient(StationRemovedFromListPacket.TYPE, StationRemovedFromListPacket.STREAM_CODEC, StationRemovedFromListPacket::handle);
        reg.playToServer(WalkieStateSyncPacket.TYPE, WalkieStateSyncPacket.STREAM_CODEC, WalkieStateSyncPacket::handle);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToAllPlayers(CustomPacketPayload payload) {
        PacketDistributor.sendToAllPlayers(payload);
    }

    private DeadAirNet() {}
}
