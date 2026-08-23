package uk.co.extraspecialstudio.dead_air.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.client.DeadAirSounds;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.SignalStrength;

/**
 * Manages static noise playback for:
 * - Emergency Broadcast station (static only)
 * - Low signal (0-1 bars) on music stations (static overlay on music)
 * - No signal (static only when tuned to music station but out of range)
 * <p>
 * Sound instance is a nested class so ModuleClassLoader resolves it with this manager
 * (avoids NoClassDefFoundError for a sibling top-level class that is already in the JAR).
 */
@SuppressWarnings("null")
public class StaticSoundManager {
    private static LoopingStaticSound activeStatic = null;

    /**
     * Update static playback based on station, signal strength, and whether music is playing.
     * Call from WalkieTalkieOverlay when the HUD is visible.
     *
     * @param mc           Minecraft instance
     * @param station      Current tuned station (null if no station)
     * @param signalStrength Signal 0-1 (0 = no signal)
     * @param hasMusicPlaying True if music is currently playing for this station
     */
    public static void update(Minecraft mc, RadioStation station, float signalStrength, boolean hasMusicPlaying) {
        try {
            if (mc == null || mc.getSoundManager() == null) {
                stopStatic(mc);
                return;
            }

            // No station or walkie off: no static
            if (station == null) {
                stopStatic(mc);
                return;
            }

            int bars = SignalStrength.getSignalBars(signalStrength);
            float maxVol = (float) (Config.maxVolume >= 0.01 ? Config.maxVolume : 0.7f);
            // Static volume halved overall; halved again for 1/5 (1 bar) overlay
            float staticScale = 0.5f;

            // Emergency Broadcast: static at half volume
            if (station.getType() == RadioStation.StationType.EMERGENCY_BROADCAST) {
                playOrUpdateStatic(mc, maxVol * staticScale);
                return;
            }

            // Music station with no signal: play static only (music is stopped by AudioManager)
            if (signalStrength < 0.05f || (bars == 0 && !Config.musicPlaysWithoutTower)) {
                playOrUpdateStatic(mc, maxVol * staticScale);
                return;
            }

            // Music station with low signal (0 or 1 bars): static overlay on music (halved; 1 bar halved again)
            if (bars <= 1) {
                float staticVol = bars == 0 ? 0.5f : 0.35f * 0.5f; // 0 bars = 0.5, 1 bar = 0.175 (halved again for 1/5)
                staticVol *= staticScale * maxVol;
                playOrUpdateStatic(mc, staticVol);
                return;
            }

            // 2+ bars: no static
            stopStatic(mc);
        } catch (Throwable t) {
            // Never crash the client over static (ModuleClassLoader / missing sound edge cases)
            Dead_air.LOGGER.debug("StaticSoundManager.update failed: {}", t.toString());
            try {
                stopStatic(mc);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Stop static. Call when walkie off, no station, or switching away.
     */
    public static void stopStatic(Minecraft mc) {
        if (activeStatic != null) {
            if (mc != null && mc.getSoundManager() != null) {
                mc.getSoundManager().stop(activeStatic);
            }
            activeStatic = null;
        }
    }

    private static void playOrUpdateStatic(Minecraft mc, float volume) {
        if (activeStatic != null) {
            activeStatic.setVolume(volume);
            return;
        }
        LoopingStaticSound sound = new LoopingStaticSound(volume);
        activeStatic = sound;
        mc.getSoundManager().play(sound);
    }

    /** Looping static noise; nested so it loads with {@link StaticSoundManager}. */
    public static final class LoopingStaticSound extends AbstractSoundInstance {
        public LoopingStaticSound(float volume) {
            super(DeadAirSounds.STATIC.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.volume = Math.max(0f, Math.min(1f, volume));
            this.looping = true;
            this.attenuation = Attenuation.NONE;
        }

        @Override
        public boolean isRelative() {
            return true;
        }

        public void setVolume(float volume) {
            this.volume = Math.max(0f, Math.min(1f, volume));
        }
    }
}
