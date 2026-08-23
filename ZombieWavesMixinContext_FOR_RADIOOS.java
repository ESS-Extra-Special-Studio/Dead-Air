package net.mcreator.radiotowers.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Holds level/position context for the Zombie Waves API mixin.
 * Set by ZombieWavesAPILoader before calling the API; the mixin injects this level
 * into the API's WavesManager so the wave runs in the correct dimension.
 */
public final class ZombieWavesMixinContext {

    private static ServerLevel pendingLevel;
    private static BlockPos pendingPos;
    private static ServerLevel currentLevel;

    public static void setPending(ServerLevel level, BlockPos pos) {
        pendingLevel = level;
        pendingPos = pos;
    }

    public static ServerLevel getAndClearPendingLevel() {
        ServerLevel level = pendingLevel;
        pendingLevel = null;
        pendingPos = null;
        currentLevel = level;
        return level;
    }

    public static ServerLevel getCurrentLevel() {
        return currentLevel;
    }

    public static void clearCurrentLevel() {
        currentLevel = null;
    }

    public static boolean hasPending() {
        return pendingLevel != null;
    }

    public static ServerLevel getBerezkaCurWorld() {
        try {
            var opt = net.minecraftforge.fml.ModList.get().getModContainerById("berezka_api");
            if (opt.isEmpty()) return null;
            Class<?> main = Class.forName("org.berezka.berezka_api.berezka_api_main", true, opt.get().getMod().getClass().getClassLoader());
            java.lang.reflect.Field f = main.getField("curWorld");
            Object w = f.get(null);
            return w instanceof ServerLevel ? (ServerLevel) w : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static String describeContext() {
        return "pending=" + (pendingLevel != null ? pendingLevel.dimension().location().toString() : "null")
            + " current=" + (currentLevel != null ? currentLevel.dimension().location().toString() : "null");
    }
}
