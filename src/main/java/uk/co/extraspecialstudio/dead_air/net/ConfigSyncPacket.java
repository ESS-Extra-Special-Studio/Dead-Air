package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import uk.co.extraspecialstudio.dead_air.Config;

import java.util.function.Supplier;

/**
 * Server -> Client: the server's gameplay config (ranges, spacing, line of sight, weather,
 * tower-only reception). Volume, HUD and custom music stay per player.
 */
public class ConfigSyncPacket {
    private final int emergencyBroadcastRange;
    private final int musicStationRange;
    private final int minTowerSpacing;
    private final boolean enableLineOfSight;
    private final boolean enableWeatherEffects;
    private final boolean musicPlaysWithoutTower;

    public ConfigSyncPacket(int emergencyBroadcastRange, int musicStationRange, int minTowerSpacing,
                            boolean enableLineOfSight, boolean enableWeatherEffects, boolean musicPlaysWithoutTower) {
        this.emergencyBroadcastRange = emergencyBroadcastRange;
        this.musicStationRange = musicStationRange;
        this.minTowerSpacing = minTowerSpacing;
        this.enableLineOfSight = enableLineOfSight;
        this.enableWeatherEffects = enableWeatherEffects;
        this.musicPlaysWithoutTower = musicPlaysWithoutTower;
    }

    public static ConfigSyncPacket fromConfig() {
        return new ConfigSyncPacket(Config.emergencyBroadcastRange, Config.musicStationRange, Config.minTowerSpacing,
            Config.enableLineOfSight, Config.enableWeatherEffects, Config.musicPlaysWithoutTower);
    }

    public static void encode(ConfigSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.emergencyBroadcastRange);
        buf.writeVarInt(msg.musicStationRange);
        buf.writeVarInt(msg.minTowerSpacing);
        buf.writeBoolean(msg.enableLineOfSight);
        buf.writeBoolean(msg.enableWeatherEffects);
        buf.writeBoolean(msg.musicPlaysWithoutTower);
    }

    public static ConfigSyncPacket decode(FriendlyByteBuf buf) {
        return new ConfigSyncPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
            buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(ConfigSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.hasSingleplayerServer()) return;
            Config.applyServerValues(msg.emergencyBroadcastRange, msg.musicStationRange, msg.minTowerSpacing,
                msg.enableLineOfSight, msg.enableWeatherEffects, msg.musicPlaysWithoutTower);
        });
        ctx.setPacketHandled(true);
    }
}
