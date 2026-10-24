package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/**
 * Client-only encounter visuals: static fog, light flicker, HUD static, the Listening Silence cue and the
 * scream flash. Also tracks whether radios are jammed to static. Nothing here touches blocks.
 */
@EventBusSubscriber(modid = Dead_air.MODID, value = Dist.CLIENT)
public final class ListenerClientEffects {
    private static boolean active;
    private static boolean jammed;
    private static int silenceTicks;
    private static int screamTicks;
    private static int flickerTicks;
    private static float flickerAlpha;
    private static DroneSound drone;
    private static final RandomSource RANDOM = RandomSource.create();

    public static boolean isActive() {
        return active;
    }

    /** Radios play static only (encounter or a nearby scream). */
    public static boolean isJammed() {
        return jammed || active;
    }

    public static void setJammed(boolean value) {
        jammed = value;
    }

    public static void apply(boolean isActive, int silence, int scream) {
        active = isActive;
        silenceTicks = silence;
        if (scream > 0) screamTicks = scream;
    }

    public static void reset() {
        active = false;
        jammed = false;
        silenceTicks = 0;
        screamTicks = 0;
        flickerTicks = 0;
        BroadcastHud.clear();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            if (active || jammed) reset();
            return;
        }
        if (silenceTicks > 0) silenceTicks--;
        if (screamTicks > 0) screamTicks--;
        if (flickerTicks > 0) {
            flickerTicks--;
        } else if (active && RANDOM.nextInt(40) == 0) {
            flickerTicks = 2 + RANDOM.nextInt(5);
            flickerAlpha = 0.3f + RANDOM.nextFloat() * 0.35f;
        }
        if (active && (drone == null || drone.isStopped())) {
            drone = new DroneSound();
            mc.getSoundManager().play(drone);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!active) return;
        float far = 26f + Mth.sin((System.currentTimeMillis() % 6283L) / 1000f) * 4f;
        if (flickerTicks > 0) far *= 0.6f;
        event.setNearPlaneDistance(0f);
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), far));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!active) return;
        event.setRed(0.10f);
        event.setGreen(0.09f);
        event.setBlue(0.15f);
    }

    public static void renderOverlay(GuiGraphics g, int width, int height, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (active) {
            int edge = 0x90100818;
            g.fillGradient(0, 0, width, height / 5, edge, 0x00100818);
            g.fillGradient(0, height - height / 5, width, height, 0x00100818, edge);
            for (int i = 0; i < 90; i++) {
                int x = RANDOM.nextInt(width);
                int y = RANDOM.nextInt(height);
                int shade = 0x60 + RANDOM.nextInt(0x60);
                g.fill(x, y, x + 1 + RANDOM.nextInt(2), y + 1, (0x50 << 24) | (shade << 16) | (shade << 8) | shade);
            }
            if (RANDOM.nextInt(6) == 0) {
                int y = RANDOM.nextInt(height);
                g.fill(0, y, width, y + 1, 0x302FA8FF);
            }
            if (flickerTicks > 0) {
                g.fill(0, 0, width, height, ((int) (flickerAlpha * 255) << 24) | 0x05030A);
            }
        }
        if (screamTicks > 0) {
            int alpha = (int) (screamTicks / 12f * 150);
            g.fill(0, 0, width, height, (alpha << 24) | 0xD8E8FF);
        }
        if (silenceTicks > 0) {
            float pulse = 0.6f + 0.4f * Mth.sin((System.currentTimeMillis() % 6283L) / 180f);
            int a = (int) (pulse * 255) << 24;
            Component title = Component.translatable("hud.dead_air.listener.listening");
            Component hint = Component.translatable("hud.dead_air.listener.listening_hint");
            int cy = height / 2 - 40;
            g.drawCenteredString(mc.font, title, width / 2, cy, a | 0xB070FF);
            g.drawCenteredString(mc.font, hint, width / 2, cy + 12, 0xC0C0C0C0);
        }
    }

    /** Low drone that loops while the encounter is active. */
    private static final class DroneSound extends AbstractTickableSoundInstance {
        DroneSound() {
            super(DeadAirSounds.LISTENER_DRONE.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            volume = 0.7f;
            relative = true;
            attenuation = Attenuation.NONE;
        }

        @Override
        public void tick() {
            if (!active) stop();
        }
    }

    private ListenerClientEffects() {}
}
