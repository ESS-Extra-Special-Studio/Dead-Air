package uk.co.extraspecialstudio.dead_air.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;

import javax.annotation.Nullable;

/**
 * Soft Zombie Awareness / Enhanced AI horde bump for Base Under Attack.
 * Prefers Zombie Awareness; falls back to Enhanced AI presence for a smaller bump.
 * Horde hook via reflection when available; otherwise just increases wave count.
 */
public final class HordeCompat {
    private static final String[] ZA_IDS = {"zombieawareness", "zombie_awareness"};
    private static final String[] EAI_IDS = {"enhancedai", "enhanced_ai"};

    @Nullable private static String modId;
    private static boolean za;
    private static boolean eai;
    private static boolean probed;
    @Nullable private static java.lang.reflect.Method hordeHook;

    private HordeCompat() {}

    public static void probe() {
        if (probed) return;
        probed = true;
        for (String id : ZA_IDS) {
            if (ModList.get().isLoaded(id)) {
                modId = id;
                za = true;
                break;
            }
        }
        if (!za) {
            for (String id : EAI_IDS) {
                if (ModList.get().isLoaded(id)) {
                    modId = id;
                    eai = true;
                    break;
                }
            }
        }
        if (!za && !eai) return;
        // Optional ZA/EAI horde helpers — shapes vary by version
        String[] hooks = {
            "com.corosus.zombieawareness.ZAUtil",
            "com.corosus.zombieawareness.ZombieAwareness",
            "com.corosus.zombieawareness.util.ZAUtil",
            "net.minecraftforge.event.ForgeEventFactory"
        };
        for (String cn : hooks) {
            try {
                Class<?> cl = Class.forName(cn);
                for (var m : cl.getMethods()) {
                    String n = m.getName().toLowerCase();
                    if (n.contains("horde") || n.contains("swarm") || n.contains("raid")) {
                        hordeHook = m;
                        Dead_air.LOGGER.info("[Dead Air] Horde hook candidate: {}.{}", cn, m.getName());
                        break;
                    }
                }
                if (hordeHook != null) break;
            } catch (Throwable ignored) {
            }
        }
        Dead_air.LOGGER.info("[Dead Air] Horde soft-compat: mod={} za={} eai={} hook={}",
            modId, za, eai, hordeHook != null);
    }

    public static boolean isLoaded() {
        if (!probed) probe();
        return za || eai;
    }

    public static String detectedModId() {
        if (!probed) probe();
        return modId != null ? modId : "zombieawareness|enhancedai";
    }

    /**
     * Adjust base-attack mob count when a horde mod is loaded.
     * ZA: +50% (min +3). Enhanced AI only: +2.
     */
    public static int adjustWaveCount(int baseCount) {
        if (!Config.compatHorde || !isLoaded()) return baseCount;
        try {
            if (za) return Math.max(baseCount + 3, (int) Math.ceil(baseCount * 1.5));
            if (eai) return baseCount + 2;
        } catch (Throwable ignored) {
        }
        return baseCount;
    }

    /** Optional extra horde trigger at panel; soft-fail if no hook. */
    public static void tryTriggerHorde(ServerPlayer player, ServerLevel level, BlockPos panel) {
        if (!Config.compatHorde || !isLoaded() || hordeHook == null) return;
        try {
            Class<?>[] pts = hordeHook.getParameterTypes();
            Object[] args = new Object[pts.length];
            for (int i = 0; i < pts.length; i++) {
                if (pts[i].isInstance(level)) args[i] = level;
                else if (pts[i].isInstance(player)) args[i] = player;
                else if (pts[i].isInstance(panel)) args[i] = panel;
                else if (pts[i] == int.class || pts[i] == Integer.class) args[i] = 8;
                else if (pts[i] == boolean.class || pts[i] == Boolean.class) args[i] = true;
                else args[i] = null;
            }
            Object target = java.lang.reflect.Modifier.isStatic(hordeHook.getModifiers()) ? null
                : hordeHook.getDeclaringClass().getConstructor().newInstance();
            hordeHook.invoke(target, args);
            Dead_air.LOGGER.debug("[Dead Air] Invoked horde hook at {}", panel);
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] Horde hook invoke failed: {}", t.toString());
        }
    }
}
