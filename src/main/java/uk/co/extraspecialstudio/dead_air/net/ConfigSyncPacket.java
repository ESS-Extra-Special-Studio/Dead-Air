package uk.co.extraspecialstudio.dead_air.net;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/**
 * Server -> Client: the server's gameplay config (ranges, spacing, line of sight, weather,
 * tower-only reception). Volume, HUD and custom music stay per player.
 */
public class ConfigSyncPacket implements CustomPacketPayload {
    public static final Type<ConfigSyncPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "config_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSyncPacket> STREAM_CODEC =
        StreamCodec.of(ConfigSyncPacket::encode, ConfigSyncPacket::decode);

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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, ConfigSyncPacket msg) {
        buf.writeVarInt(msg.emergencyBroadcastRange);
        buf.writeVarInt(msg.musicStationRange);
        buf.writeVarInt(msg.minTowerSpacing);
        buf.writeBoolean(msg.enableLineOfSight);
        buf.writeBoolean(msg.enableWeatherEffects);
        buf.writeBoolean(msg.musicPlaysWithoutTower);
    }

    public static ConfigSyncPacket decode(RegistryFriendlyByteBuf buf) {
        return new ConfigSyncPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
            buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(ConfigSyncPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.hasSingleplayerServer()) return;
            Config.applyServerValues(msg.emergencyBroadcastRange, msg.musicStationRange, msg.minTowerSpacing,
                msg.enableLineOfSight, msg.enableWeatherEffects, msg.musicPlaysWithoutTower);
        });
    }
}
