package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class DeadAirNet {
    public static final String PROTOCOL = "5";

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
        reg.playToClient(ConfigSyncPacket.TYPE, ConfigSyncPacket.STREAM_CODEC, ConfigSyncPacket::handle);
        reg.playToServer(ExpansionPacksAnnouncePacket.TYPE, ExpansionPacksAnnouncePacket.STREAM_CODEC, ExpansionPacksAnnouncePacket::handle);
        reg.playToClient(BroadcastMessagePacket.TYPE, BroadcastMessagePacket.STREAM_CODEC, BroadcastMessagePacket::handle);
        reg.playToClient(ForceEmergencyTunePacket.TYPE, ForceEmergencyTunePacket.STREAM_CODEC, ForceEmergencyTunePacket::handle);
        reg.playToServer(TransmitPacket.TYPE, TransmitPacket.STREAM_CODEC, TransmitPacket::handle);
        reg.playToClient(EncounterStatePacket.TYPE, EncounterStatePacket.STREAM_CODEC, EncounterStatePacket::handle);
        reg.playToClient(SurvivorScreenPacket.TYPE, SurvivorScreenPacket.STREAM_CODEC, SurvivorScreenPacket::handle);
        reg.playToServer(SurvivorActionPacket.TYPE, SurvivorActionPacket.STREAM_CODEC, SurvivorActionPacket::handle);
        reg.playToClient(EbSignalPromptPacket.TYPE, EbSignalPromptPacket.STREAM_CODEC, EbSignalPromptPacket::handle);
        reg.playToServer(EbTunePromptResponsePacket.TYPE, EbTunePromptResponsePacket.STREAM_CODEC, EbTunePromptResponsePacket::handle);
        reg.playToClient(CompatWaypointPacket.TYPE, CompatWaypointPacket.STREAM_CODEC, CompatWaypointPacket::handle);
        reg.playToClient(SurvivorPartySyncPacket.TYPE, SurvivorPartySyncPacket.STREAM_CODEC, SurvivorPartySyncPacket::handle);
        reg.playToServer(SurvivorPartyOrderPacket.TYPE, SurvivorPartyOrderPacket.STREAM_CODEC, SurvivorPartyOrderPacket::handle);
        reg.playToClient(EbHuntSyncPacket.TYPE, EbHuntSyncPacket.STREAM_CODEC, EbHuntSyncPacket::handle);
    }

    public static void sendTo(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
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
