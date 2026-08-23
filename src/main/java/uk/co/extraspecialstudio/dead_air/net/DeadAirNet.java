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
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "main"),
        () -> PROTOCOL,
        PROTOCOL::equals,
        PROTOCOL::equals
    );

    private static int id;

    /** Set by client in ClientModEvents; run when server sends OpenWalkieGuiPacket so we open our GUI. */
    private static Runnable openWalkieGuiCallback;

    public static void setOpenWalkieGuiCallback(Runnable r) {
        openWalkieGuiCallback = r;
    }

    static void runOpenWalkieGuiCallback() {
        if (openWalkieGuiCallback != null) {
            openWalkieGuiCallback.run();
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
        CHANNEL.registerMessage(id++, OpenWalkieGuiPacket.class,
            OpenWalkieGuiPacket::encode,
            OpenWalkieGuiPacket::decode,
            OpenWalkieGuiPacket::handle);
        CHANNEL.registerMessage(id++, StationRemovedFromListPacket.class,
            StationRemovedFromListPacket::encode,
            StationRemovedFromListPacket::decode,
            StationRemovedFromListPacket::handle);
        CHANNEL.registerMessage(id++, WalkieStateSyncPacket.class,
            WalkieStateSyncPacket::encode,
            WalkieStateSyncPacket::decode,
            WalkieStateSyncPacket::handle);
    }
}
