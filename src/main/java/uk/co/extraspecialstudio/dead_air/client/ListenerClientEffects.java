package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.entity.ListenerEntity;
import uk.co.extraspecialstudio.dead_air.sound.DeadAirSounds;

/**
 * Client-only encounter visuals: static fog, light flicker, HUD static, the Listening Silence cue and the
 * scream flash. Atmospheric distortion stays on while any living Listener is within
 * {@link #AURA_RADIUS}. Also tracks whether radios are jammed to static. Nothing here touches blocks.
 */
@Mod.EventBusSubscriber(modid = Dead_air.MODID, value = Dist.CLIENT)
public final class ListenerClientEffects {
    /** Presence fog / static — matches a comfortable fight + approach range. */
    public static final double AURA_RADIUS = 48.0;

    private static boolean active;
    /** Living Listener within {@link #AURA_RADIUS} of the local player. */
    private static boolean proximityAura;
    private static boolean jammed;
    private static int silenceTicks;
    private static int screamTicks;
    private static int flickerTicks;
    private static float flickerAlpha;
    private static DroneSound drone;
    private static FadePulseSound fadePulse;
    private static final RandomSource RANDOM = RandomSource.create();

    public static boolean isActive() {
        return active;
    }

    /** Fog + screen static while a Listener haunts nearby air. */
    public static boolean hasAtmosphereDistort() {
        return proximityAura;
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
        // Always apply scream (0 clears a leftover flash on encounter end / silence clear).
        screamTicks = scream;
    }

    public static void reset() {
        active = false;
        proximityAura = false;
        jammed = false;
        silenceTicks = 0;
        screamTicks = 0;
        flickerTicks = 0;
        BroadcastHud.clear();
        SurvivorPartyHud.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            if (active || jammed || proximityAura) reset();
            return;
        }
        proximityAura = scanProximityAura(mc);
        if (silenceTicks > 0) silenceTicks--;
        if (screamTicks > 0) screamTicks--;
        if (flickerTicks > 0) {
            flickerTicks--;
        } else if (proximityAura && RANDOM.nextInt(40) == 0) {
            flickerTicks = 2 + RANDOM.nextInt(5);
            flickerAlpha = 0.3f + RANDOM.nextFloat() * 0.35f;
        }
        if (active && (drone == null || drone.isStopped())) {
            drone = new DroneSound();
            mc.getSoundManager().play(drone);
        }
        if (active && (fadePulse == null || fadePulse.isStopped())) {
            fadePulse = new FadePulseSound();
            mc.getSoundManager().play(fadePulse);
        }
    }

    private static boolean scanProximityAura(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return false;
        double r = AURA_RADIUS;
        AABB box = player.getBoundingBox().inflate(r);
        for (ListenerEntity listener : mc.level.getEntitiesOfClass(ListenerEntity.class, box,
            e -> e.isAlive() && !e.isRemoved())) {
            if (player.distanceTo(listener) <= r) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!proximityAura) return;
        float far = 26f + Mth.sin((System.currentTimeMillis() % 6283L) / 1000f) * 4f;
        if (flickerTicks > 0) far *= 0.6f;
        event.setNearPlaneDistance(0f);
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), far));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!proximityAura) return;
        event.setRed(0.10f);
        event.setGreen(0.09f);
        event.setBlue(0.15f);
    }

    public static void renderOverlay(GuiGraphics g, int width, int height, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (proximityAura) {
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
            boolean quiet = mc.player.isCrouching() && !mc.player.isSprinting();
            Component hint = Component.translatable(quiet
                ? "hud.dead_air.listener.listening_quiet"
                : "hud.dead_air.listener.listening_hint");
            int cy = height / 2 - 40;
            g.drawCenteredString(mc.font, title, width / 2, cy, a | 0xB070FF);
            g.drawCenteredString(mc.font, hint, width / 2, cy + 12, quiet ? 0xC088FFAA : 0xC0FFAA88);
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

    /** Quiet presence swell under the translucency pulse — layered under the drone. */
    private static final class FadePulseSound extends AbstractTickableSoundInstance {
        FadePulseSound() {
            super(DeadAirSounds.LISTENER_FADE_PULSE.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            volume = 0.35f;
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
