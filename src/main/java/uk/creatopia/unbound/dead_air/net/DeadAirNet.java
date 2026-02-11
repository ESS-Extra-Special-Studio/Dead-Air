package uk.creatopia.unbound.dead_air.net;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import uk.creatopia.unbound.dead_air.Dead_air;

/**
 * Network channel for syncing radio signal from server to client so music can play
 * when the client has no tower data (e.g. shared static state not populated yet, or dedicated server).
 */
public class DeadAirNet {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(Dead_air.MODID, "main"),
        () -> PROTOCOL,
        PROTOCOL::equals,
        PROTOCOL::equals
    );

    private static int id;

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
    }
}
