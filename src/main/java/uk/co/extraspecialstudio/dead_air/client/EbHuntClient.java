package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import uk.co.extraspecialstudio.dead_air.net.EbHuntSyncPacket;
import uk.co.extraspecialstudio.dead_air.sound.DeadAirSounds;

/**
 * Client hunt state while T3 is tuned to EB with a pending site.
 * Compass needle + range paint onto the radio live LCD ({@link RadioLiveDisplay}), not a floating HUD.
 */
public final class EbHuntClient {
    private static boolean active;
    private static int targetX;
    private static int targetZ;
    private static String siteKind = "";
    private static String siteKey = "";
    private static long nextBeepMs;
    private static String lastVoKey = "";

    public static void apply(EbHuntSyncPacket msg) {
        if (!msg.active) {
            // Keep lastVoKey so a reconnect clear does not re-fire VO for the same site.
            active = false;
            siteKey = "";
            siteKind = "";
            nextBeepMs = 0L;
            return;
        }
        active = true;
        targetX = msg.targetX;
        targetZ = msg.targetZ;
        siteKind = msg.siteKind;
        siteKey = msg.siteKey;
        // Server one-shots playVo via huntVoPlayed — trust that flag only (avoids reconnect re-VO).
        if (msg.playVo && !siteKey.equals(lastVoKey)) {
            playVo();
        }
    }

    private static void playVo() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        lastVoKey = siteKey;
        String key = "message.dead_air.hunt.vo." + (siteKind.isEmpty() ? "signal" : siteKind);
        mc.player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
        mc.player.playNotifySound(DeadAirSounds.BROADCAST_VOICE.get(), SoundSource.RECORDS, 0.75f, 1.0f);
        nextBeepMs = System.currentTimeMillis() + 3500L;
    }

    public static boolean isActive() {
        return active;
    }

    public static void clear() {
        active = false;
        siteKey = "";
        siteKind = "";
        lastVoKey = "";
        nextBeepMs = 0L;
    }

    /** Degrees clockwise from facing: 0 = target ahead, ±180 = behind. */
    public static float relativeBearingDegrees() {
        if (!active) return 0f;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return 0f;
        double dx = targetX + 0.5 - player.getX();
        double dz = targetZ + 0.5 - player.getZ();
        float bearing = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90F;
        return Mth.wrapDegrees(bearing - player.getYRot());
    }

    public static int distanceMeters() {
        if (!active) return 0;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return 0;
        double dx = targetX + 0.5 - player.getX();
        double dz = targetZ + 0.5 - player.getZ();
        return (int) Math.sqrt(dx * dx + dz * dz);
    }

    /** True when the player is roughly facing the pin (same cone as faster beeps). */
    public static boolean facingTarget() {
        return Math.abs(relativeBearingDegrees()) < 25f;
    }

    /**
     * Coarse fingerprint for LCD redraw — bearing buckets + metres so the needle tracks turn/walk.
     */
    public static String lcdFingerprint() {
        if (!active) return "off";
        int bearingBucket = Math.round(relativeBearingDegrees() / 5f) * 5;
        return bearingBucket + "|" + distanceMeters() + "|" + (facingTarget() ? 1 : 0);
    }

    /** Call from client tick while overlay may be visible. */
    public static void clientTick() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        long now = System.currentTimeMillis();
        if (now < nextBeepMs) return;

        float diff = Math.abs(relativeBearingDegrees());
        // Facing well: faster beeps. Facing away: slower.
        long interval = diff < 25f ? 280L : diff < 60f ? 550L : diff < 110f ? 900L : 1400L;
        int dist = distanceMeters();
        if (dist < 48) interval = Math.max(180L, interval - 120L);
        nextBeepMs = now + interval;
        float vol = diff < 60f ? 0.22f : 0.12f;
        float pitch = diff < 25f ? 1.15f : 0.95f;
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(DeadAirSounds.RELAY_PING.get(), pitch, vol));
        }
    }

    private EbHuntClient() {}
}
