package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/**
 * Network channel for syncing radio signal from server to client so music can play
 * when the client has no tower data (e.g. shared static state not populated yet, or dedicated server).
 */
public class DeadAirNet {
    private static final int PROTOCOL = 9;
    public static final SimpleChannel CHANNEL = ChannelBuilder
        .named(new ResourceLocation(Dead_air.MODID, "main"))
        .networkProtocolVersion(PROTOCOL)
        .clientAcceptedVersions(Channel.VersionTest.exact(PROTOCOL))
        .serverAcceptedVersions(Channel.VersionTest.exact(PROTOCOL))
        .simpleChannel();

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
        CHANNEL.messageBuilder(RadioSignalRequestPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(RadioSignalRequestPacket::encode)
            .decoder(RadioSignalRequestPacket::decode)
            .consumerMainThread(RadioSignalRequestPacket::handle)
            .add();
        CHANNEL.messageBuilder(RadioSignalResponsePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(RadioSignalResponsePacket::encode)
            .decoder(RadioSignalResponsePacket::decode)
            .consumerMainThread(RadioSignalResponsePacket::handle)
            .add();
        CHANNEL.messageBuilder(KnownTowersSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(KnownTowersSyncPacket::encode)
            .decoder(KnownTowersSyncPacket::decode)
            .consumerMainThread(KnownTowersSyncPacket::handle)
            .add();
        CHANNEL.messageBuilder(RequestTowerSyncPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(RequestTowerSyncPacket::encode)
            .decoder(RequestTowerSyncPacket::decode)
            .consumerMainThread(RequestTowerSyncPacket::handle)
            .add();
        CHANNEL.messageBuilder(OpenRadioGuiPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(OpenRadioGuiPacket::encode)
            .decoder(OpenRadioGuiPacket::decode)
            .consumerMainThread(OpenRadioGuiPacket::handle)
            .add();
        CHANNEL.messageBuilder(StationRemovedFromListPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(StationRemovedFromListPacket::encode)
            .decoder(StationRemovedFromListPacket::decode)
            .consumerMainThread(StationRemovedFromListPacket::handle)
            .add();
        CHANNEL.messageBuilder(RadioStateSyncPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(RadioStateSyncPacket::encode)
            .decoder(RadioStateSyncPacket::decode)
            .consumerMainThread(RadioStateSyncPacket::handle)
            .add();
        CHANNEL.messageBuilder(ConfigSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(ConfigSyncPacket::encode)
            .decoder(ConfigSyncPacket::decode)
            .consumerMainThread(ConfigSyncPacket::handle)
            .add();
        CHANNEL.messageBuilder(ExpansionPacksAnnouncePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(ExpansionPacksAnnouncePacket::encode)
            .decoder(ExpansionPacksAnnouncePacket::decode)
            .consumerMainThread(ExpansionPacksAnnouncePacket::handle)
            .add();
        CHANNEL.messageBuilder(BroadcastMessagePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(BroadcastMessagePacket::encode)
            .decoder(BroadcastMessagePacket::decode)
            .consumerMainThread(BroadcastMessagePacket::handle)
            .add();
        CHANNEL.messageBuilder(ForceEmergencyTunePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(ForceEmergencyTunePacket::encode)
            .decoder(ForceEmergencyTunePacket::decode)
            .consumerMainThread(ForceEmergencyTunePacket::handle)
            .add();
        CHANNEL.messageBuilder(TransmitPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(TransmitPacket::encode)
            .decoder(TransmitPacket::decode)
            .consumerMainThread(TransmitPacket::handle)
            .add();
        CHANNEL.messageBuilder(EncounterStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(EncounterStatePacket::encode)
            .decoder(EncounterStatePacket::decode)
            .consumerMainThread(EncounterStatePacket::handle)
            .add();
        CHANNEL.messageBuilder(SurvivorScreenPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SurvivorScreenPacket::encode)
            .decoder(SurvivorScreenPacket::decode)
            .consumerMainThread(SurvivorScreenPacket::handle)
            .add();
        CHANNEL.messageBuilder(SurvivorActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(SurvivorActionPacket::encode)
            .decoder(SurvivorActionPacket::decode)
            .consumerMainThread(SurvivorActionPacket::handle)
            .add();
        CHANNEL.messageBuilder(EbSignalPromptPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(EbSignalPromptPacket::encode)
            .decoder(EbSignalPromptPacket::decode)
            .consumerMainThread(EbSignalPromptPacket::handle)
            .add();
        CHANNEL.messageBuilder(EbTunePromptResponsePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(EbTunePromptResponsePacket::encode)
            .decoder(EbTunePromptResponsePacket::decode)
            .consumerMainThread(EbTunePromptResponsePacket::handle)
            .add();
        CHANNEL.messageBuilder(CompatWaypointPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(CompatWaypointPacket::encode)
            .decoder(CompatWaypointPacket::decode)
            .consumerMainThread(CompatWaypointPacket::handle)
            .add();
        CHANNEL.messageBuilder(SurvivorPartySyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SurvivorPartySyncPacket::encode)
            .decoder(SurvivorPartySyncPacket::decode)
            .consumerMainThread(SurvivorPartySyncPacket::handle)
            .add();
        CHANNEL.messageBuilder(SurvivorPartyOrderPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(SurvivorPartyOrderPacket::encode)
            .decoder(SurvivorPartyOrderPacket::decode)
            .consumerMainThread(SurvivorPartyOrderPacket::handle)
            .add();
        CHANNEL.messageBuilder(EbHuntSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(EbHuntSyncPacket::encode)
            .decoder(EbHuntSyncPacket::decode)
            .consumerMainThread(EbHuntSyncPacket::handle)
            .add();
    }

    public static void sendTo(ServerPlayer player, Object msg) {
        CHANNEL.send(msg, PacketDistributor.PLAYER.with(player));
    }

    public static void sendToServer(Object msg) {
        CHANNEL.send(msg, PacketDistributor.SERVER.noArg());
    }
}
