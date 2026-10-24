package uk.co.extraspecialstudio.dead_air.compat;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/**
 * Soft FTB Chunks integration when a player sets their radio-panel base.
 * Claims + force-loads the panel chunk (and a small radius from signal range)
 * for the player's team via reflection; soft-fails if API shape differs.
 */
public final class FtbChunksCompat {
    private static final boolean LOADED = ModList.get().isLoaded("ftbchunks");
    /** Cap claim radius so large signal ranges do not exhaust team claim limits. */
    private static final int MAX_CLAIM_RADIUS_CHUNKS = 2;
    private static boolean probed;
    private static boolean apiReady;

    private FtbChunksCompat() {}

    public static void probe() {
        if (probed) return;
        probed = true;
        if (!LOADED) return;
        try {
            Class.forName("dev.ftb.mods.ftbchunks.api.FTBChunksAPI");
            Class.forName("dev.ftb.mods.ftblibrary.math.ChunkDimPos");
            apiReady = true;
            Dead_air.LOGGER.info("[Dead Air] FTB Chunks API class present â€” base claim/forceload enabled");
        } catch (Throwable t) {
            apiReady = false;
            Dead_air.LOGGER.info("[Dead Air] FTB Chunks loaded but API class missing â€” log-only integrate");
        }
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    /** Soft check whether a chunk is claimed (best-effort; false if API unavailable). */
    public static boolean isChunkClaimed(net.minecraft.server.level.ServerLevel level, int chunkX, int chunkZ) {
        if (!Config.compatFtbChunks || !LOADED) return false;
        if (!probed) probe();
        if (!apiReady) return false;
        try {
            Object manager = getManager();
            if (manager == null) return false;
            Class<?> chunkDimPosCl = Class.forName("dev.ftb.mods.ftblibrary.math.ChunkDimPos");
            var ctor = chunkDimPosCl.getConstructor(net.minecraft.resources.ResourceKey.class, int.class, int.class);
            Object pos = ctor.newInstance(level.dimension(), chunkX, chunkZ);
            for (String m : new String[]{"getChunk", "getClaimedChunk", "getData"}) {
                try {
                    Object data = manager.getClass().getMethod(m, chunkDimPosCl).invoke(manager, pos);
                    if (data != null) return true;
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * Claim / force-load the panel chunk (plus small radius) for the player's team. Never throws.
     * @return short status for logging / commands
     */
    public static String onBaseSet(ServerPlayer player, ResourceKey<Level> dim, BlockPos panel, int range) {
        if (!Config.compatFtbChunks || !LOADED) return "skipped";
        if (!probed) probe();
        try {
            int cx = panel.getX() >> 4;
            int cz = panel.getZ() >> 4;
            Dead_air.LOGGER.info("[Dead Air] FTB Chunks: player {} set base at chunk [{}, {}] dim={} range={}",
                player.getGameProfile().getName(), cx, cz, dim.location(), range);

            if (!apiReady) return "logged";

            Object manager = getManager();
            if (manager == null) return "logged";

            Object teamData = manager.getClass().getMethod("getOrCreateData", ServerPlayer.class)
                .invoke(manager, player);
            if (teamData == null) return "logged";

            CommandSourceStack source = player.createCommandSourceStack();
            Class<?> chunkDimPosCl = Class.forName("dev.ftb.mods.ftblibrary.math.ChunkDimPos");
            var chunkDimPosCtor = chunkDimPosCl.getConstructor(ResourceKey.class, int.class, int.class);

            int radius = Math.min(MAX_CLAIM_RADIUS_CHUNKS, Math.max(0, (range + 15) / 16));
            int claimed = 0;
            int forced = 0;
            int claimFail = 0;
            int forceFail = 0;

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Object pos = chunkDimPosCtor.newInstance(dim, cx + dx, cz + dz);
                    Object claimResult = teamData.getClass()
                        .getMethod("claim", CommandSourceStack.class, chunkDimPosCl, boolean.class)
                        .invoke(teamData, source, pos, false);
                    if (isSuccess(claimResult)) {
                        claimed++;
                    } else {
                        claimFail++;
                        Dead_air.LOGGER.debug("[Dead Air] FTB claim [{},{}] -> {}",
                            cx + dx, cz + dz, String.valueOf(claimResult));
                    }
                    if (dx == 0 && dz == 0) {
                        Object forceResult = invokeForceLoad(teamData, source, chunkDimPosCl, pos);
                        if (isSuccess(forceResult)) {
                            forced++;
                        } else {
                            forceFail++;
                            Dead_air.LOGGER.debug("[Dead Air] FTB forceload centre -> {}",
                                String.valueOf(forceResult));
                        }
                    }
                }
            }

            Dead_air.LOGGER.info(
                "[Dead Air] FTB Chunks base integrate: claimed={} force={} claimFail={} forceFail={} radius={}",
                claimed, forced, claimFail, forceFail, radius);
            if (claimed > 0 || forced > 0) {
                return "claimed=" + claimed + ",forced=" + forced;
            }
            return claimFail > 0 ? "claim-failed" : "logged+api";
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] FTB Chunks base integrate failed: {}", t.toString());
            return "failed-soft";
        }
    }

    private static Object getManager() throws Exception {
        Class<?> apiCl = Class.forName("dev.ftb.mods.ftbchunks.api.FTBChunksAPI");
        Object api;
        try {
            api = apiCl.getMethod("api").invoke(null);
        } catch (NoSuchMethodException e) {
            api = apiCl.getMethod("getInstance").invoke(null);
        }
        if (api == null) return null;
        try {
            return api.getClass().getMethod("getManager").invoke(api);
        } catch (NoSuchMethodException e) {
            return api;
        }
    }

    private static Object invokeForceLoad(Object teamData, CommandSourceStack source,
                                          Class<?> chunkDimPosCl, Object pos) {
        try {
            return teamData.getClass()
                .getMethod("forceLoad", CommandSourceStack.class, chunkDimPosCl, boolean.class)
                .invoke(teamData, source, pos, false);
        } catch (NoSuchMethodException e) {
            try {
                return teamData.getClass()
                    .getMethod("forceLoad", CommandSourceStack.class, chunkDimPosCl, boolean.class, boolean.class)
                    .invoke(teamData, source, pos, false, true);
            } catch (Throwable t) {
                Dead_air.LOGGER.debug("[Dead Air] FTB forceLoad invoke failed: {}", t.toString());
                return null;
            }
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] FTB forceLoad invoke failed: {}", t.toString());
            return null;
        }
    }

    private static boolean isSuccess(Object result) {
        if (result == null) return false;
        try {
            var m = result.getClass().getMethod("isSuccess");
            Object v = m.invoke(result);
            if (v instanceof Boolean b) return b;
        } catch (Throwable ignored) {
        }
        String s = String.valueOf(result);
        return s.contains("SUCCESS") || "true".equalsIgnoreCase(s);
    }
}
