package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/**
 * Network channel for syncing radio signal from server to client so music can play
 * when the client has no tower data (e.g. shared static state not populated yet, or dedicated server).
 */
public class DeadAirNet {
    private static final String PROTOCOL = "9";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "main"),
        () -> PROTOCOL,
        PROTOCOL::equals,
        PROTOCOL::equals
    );

    private static int id;

    /** Set by client in ClientModEvents; run when server sends OpenRadioGuiPacket so we open our GUI. */
    private static Runnable openRadioGuiCallback;

    public static void setOpenRadioGuiCallback(Runnable r) {
        openRadioGuiCallback = r;
    }

    static void runOpenRadioGuiCallback() {
        if (openRadioGuiCallback != null) {
            openRadioGuiCallback.run();
        }
    }

    public static void register() {
        CHANNEL.registerMessage(id++, RadioSignalRequestPacket.class,
            RadioSignalRequestPacket::encode,
            RadioSignalRequestPacket::decode,
            RadioSignalRequestPacket::handle);
        CHANNEL.registerMessage(id++, RadioSignalResponsePacket.class,
            RadioSignalResponsePacket::encode,
            RadioSignalResponsePacket::decode,
            RadioSignalResponsePacket::handle);
        CHANNEL.registerMessage(id++, KnownTowersSyncPacket.class,
            KnownTowersSyncPacket::encode,
            KnownTowersSyncPacket::decode,
            KnownTowersSyncPacket::handle);
        CHANNEL.registerMessage(id++, RequestTowerSyncPacket.class,
            RequestTowerSyncPacket::encode,
            RequestTowerSyncPacket::decode,
            RequestTowerSyncPacket::handle);
        CHANNEL.registerMessage(id++, OpenRadioGuiPacket.class,
            OpenRadioGuiPacket::encode,
            OpenRadioGuiPacket::decode,
            OpenRadioGuiPacket::handle);
        CHANNEL.registerMessage(id++, StationRemovedFromListPacket.class,
            StationRemovedFromListPacket::encode,
            StationRemovedFromListPacket::decode,
            StationRemovedFromListPacket::handle);
        CHANNEL.registerMessage(id++, RadioStateSyncPacket.class,
            RadioStateSyncPacket::encode,
            RadioStateSyncPacket::decode,
            RadioStateSyncPacket::handle);
        CHANNEL.registerMessage(id++, ConfigSyncPacket.class,
            ConfigSyncPacket::encode,
            ConfigSyncPacket::decode,
            ConfigSyncPacket::handle);
        CHANNEL.registerMessage(id++, ExpansionPacksAnnouncePacket.class,
            ExpansionPacksAnnouncePacket::encode,
            ExpansionPacksAnnouncePacket::decode,
            ExpansionPacksAnnouncePacket::handle,
            java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, BroadcastMessagePacket.class,
            BroadcastMessagePacket::encode,
            BroadcastMessagePacket::decode,
            BroadcastMessagePacket::handle,
            java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ForceEmergencyTunePacket.class,
            ForceEmergencyTunePacket::encode,
            ForceEmergencyTunePacket::decode,
            ForceEmergencyTunePacket::handle,
            java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, TransmitPacket.class,
            TransmitPacket::encode,
            TransmitPacket::decode,
            TransmitPacket::handle,
            java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, EncounterStatePacket.class,
            EncounterStatePacket::encode,
            EncounterStatePacket::decode,
            EncounterStatePacket::handle,
            java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, SurvivorScreenPacket.class,
            SurvivorScreenPacket::encode,
            SurvivorScreenPacket::decode,
            SurvivorScreenPacket::handle,
            java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, SurvivorActionPacket.class,
            SurvivorActionPacket::encode,
            SurvivorActionPacket::decode,
            SurvivorActionPacket::handle,
            java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, EbSignalPromptPacket.class,
            EbSignalPromptPacket::encode,
            EbSignalPromptPacket::decode,
            EbSignalPromptPacket::handle,
            java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, EbTunePromptResponsePacket.class,
            EbTunePromptResponsePacket::encode,
            EbTunePromptResponsePacket::decode,
            EbTunePromptResponsePacket::handle,
            java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, CompatWaypointPacket.class,
            CompatWaypointPacket::encode,
            CompatWaypointPacket::decode,
            CompatWaypointPacket::handle,
            java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, SurvivorPartySyncPacket.class,
            SurvivorPartySyncPacket::encode,
            SurvivorPartySyncPacket::decode,
            SurvivorPartySyncPacket::handle,
            java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, SurvivorPartyOrderPacket.class,
            SurvivorPartyOrderPacket::encode,
            SurvivorPartyOrderPacket::decode,
            SurvivorPartyOrderPacket::handle,
            java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, EbHuntSyncPacket.class,
            EbHuntSyncPacket::encode,
            EbHuntSyncPacket::decode,
            EbHuntSyncPacket::handle,
            java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendTo(net.minecraft.server.level.ServerPlayer player, Object msg) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), msg);
    }
}
