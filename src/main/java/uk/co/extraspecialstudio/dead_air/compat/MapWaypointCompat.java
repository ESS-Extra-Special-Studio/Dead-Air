package uk.co.extraspecialstudio.dead_air.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.net.CompatWaypointPacket;
import uk.co.extraspecialstudio.dead_air.net.DeadAirNet;

/**
 * Soft JourneyMap / Xaero temporary waypoints for EB sites (client API via reflection).
 * Server sends {@link CompatWaypointPacket}; client handlers never crash if map mods missing.
 */
public final class MapWaypointCompat {
    private static final boolean JOURNEYMAP = ModList.get().isLoaded("journeymap");
    private static final boolean XAERO = ModList.get().isLoaded("xaerominimap")
        || ModList.get().isLoaded("xaeroworldmap");

    private MapWaypointCompat() {}

    public static void probeServer() {
        if (JOURNEYMAP || XAERO) {
            Dead_air.LOGGER.info("[Dead Air] Map waypoint soft-compat ready (JM={} Xaero={})", JOURNEYMAP, XAERO);
        }
    }

    public static boolean anyMapLoaded() {
        return JOURNEYMAP || XAERO;
    }

    /** Temporary waypoint at an EB / proximity site (server). */
    public static void markEbSite(ServerPlayer player, BlockPos pos, String label) {
        if (player == null || pos == null) return;
        try {
            boolean wantJm = Config.compatJourneymap && JOURNEYMAP;
            boolean wantXaero = Config.compatXaero && XAERO;
            if (!wantJm && !wantXaero) return;
            String name = label == null || label.isBlank() ? "Dead Air Signal" : label;
            DeadAirNet.sendTo(player,
                new CompatWaypointPacket(pos.getX(), pos.getY(), pos.getZ(),
                    player.level().dimension().location().toString(), name, true));
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] Waypoint send failed: {}", t.toString());
        }
    }

    /** Client: create temporary waypoints via reflection. */
    public static void clientCreateWaypoint(int x, int y, int z, String dimId, String name, boolean temporary) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        try {
            if (Config.compatJourneymap && JOURNEYMAP) {
                tryJourneyMap(x, y, z, dimId, name, temporary);
            }
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] JourneyMap waypoint failed: {}", t.toString());
        }
        try {
            if (Config.compatXaero && XAERO) {
                tryXaero(x, y, z, name, temporary);
            }
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] Xaero waypoint failed: {}", t.toString());
        }
    }

    private static void tryJourneyMap(int x, int y, int z, String dimId, String name, boolean temporary) throws Exception {
        // JourneyMap 5.x / 6.x soft paths
        Class<?> waypointCl;
        try {
            waypointCl = Class.forName("journeymap.client.api.display.Waypoint");
        } catch (ClassNotFoundException e) {
            waypointCl = Class.forName("journeymap.client.waypoint.Waypoint");
        }
        Object waypoint = null;
        // Try (modId, id, name, ResourceKey, BlockPos) then simpler ctors
        try {
            var dimRl = net.minecraft.resources.ResourceLocation.tryParse(dimId);
            if (dimRl == null) return;
            ResourceKey<Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimRl);
            var ctor = waypointCl.getConstructor(String.class, String.class, String.class,
                ResourceKey.class, BlockPos.class);
            waypoint = ctor.newInstance(Dead_air.MODID, "da_" + x + "_" + z + "_" + (System.currentTimeMillis() % 100000),
                name, dim, new BlockPos(x, y, z));
        } catch (Throwable ignored) {
            try {
                var ctor = waypointCl.getConstructor(String.class, String.class, int.class, int.class, int.class);
                waypoint = ctor.newInstance(name, dimId, x, y, z);
            } catch (Throwable ignored2) {
            }
        }
        if (waypoint == null) return;
        try {
            var setTemp = waypointCl.getMethod("setPersistent", boolean.class);
            setTemp.invoke(waypoint, !temporary);
        } catch (Throwable ignored) {
        }
        // Show via IClientAPI if present
        try {
            Class<?> apiCl = Class.forName("journeymap.client.api.IClientAPI");
            Object api = null;
            for (Class<?> plugin : new Class<?>[]{
                Class.forName("journeymap.client.JourneymapClient"),
                Class.forName("journeymap.client.api.ClientPlugin")
            }) {
                try {
                    Object inst = plugin.getMethod("getInstance").invoke(null);
                    try {
                        api = plugin.getMethod("getClientApi").invoke(inst);
                    } catch (NoSuchMethodException e) {
                        api = inst;
                    }
                    if (api != null) break;
                } catch (Throwable ignored) {
                }
            }
            if (api != null) {
                for (String m : new String[]{"show", "addWaypoint", "display"}) {
                    try {
                        api.getClass().getMethod(m, waypointCl).invoke(api, waypoint);
                        Dead_air.LOGGER.debug("[Dead Air] JourneyMap waypoint via {}", m);
                        return;
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        Dead_air.LOGGER.debug("[Dead Air] JourneyMap waypoint object built but no show API matched");
    }

    private static void tryXaero(int x, int y, int z, String name, boolean temporary) throws Exception {
        Class<?> wpCl;
        try {
            wpCl = Class.forName("xaero.common.minimap.waypoints.Waypoint");
        } catch (ClassNotFoundException e) {
            wpCl = Class.forName("xaero.hud.minimap.waypoint.Waypoint");
        }
        Object wp;
        try {
            // (x, y, z, name, symbol, color, type, temporary)
            var ctor = wpCl.getConstructor(int.class, int.class, int.class, String.class, String.class,
                int.class, int.class, boolean.class);
            // Symbol must be a single letter; color 14 = yellow. Name is the full label.
            String symbol = name != null && !name.isBlank() ? name.substring(0, 1).toUpperCase() : "D";
            wp = ctor.newInstance(x, y, z, name, symbol, 14, 0, temporary);
        } catch (NoSuchMethodException e) {
            var ctor = wpCl.getConstructor(int.class, int.class, int.class, String.class, String.class, int.class);
            String symbol = name != null && !name.isBlank() ? name.substring(0, 1).toUpperCase() : "D";
            wp = ctor.newInstance(x, y, z, name, symbol, 14);
        }
        // Best-effort add to current set â€” API shapes vary; log if we cannot.
        try {
            Class<?> sessionCl = Class.forName("xaero.common.XaeroMinimapSession");
            Object session = sessionCl.getMethod("getCurrentSession").invoke(null);
            if (session != null) {
                Object wps = session.getClass().getMethod("getWaypointsManager").invoke(session);
                if (wps != null) {
                    Object current = null;
                    try {
                        current = wps.getClass().getMethod("getWaypoints").invoke(wps);
                    } catch (NoSuchMethodException e) {
                        current = wps.getClass().getMethod("getCurrentWorld").invoke(wps);
                    }
                    if (current instanceof java.util.Collection<?> col) {
                        @SuppressWarnings("unchecked")
                        java.util.Collection<Object> c = (java.util.Collection<Object>) col;
                        c.add(wp);
                        Dead_air.LOGGER.debug("[Dead Air] Xaero waypoint added to collection");
                        return;
                    }
                    if (current != null) {
                        try {
                            Object list = current.getClass().getMethod("getList").invoke(current);
                            if (list instanceof java.util.List<?> l) {
                                @SuppressWarnings("unchecked")
                                java.util.List<Object> listObj = (java.util.List<Object>) l;
                                listObj.add(wp);
                                Dead_air.LOGGER.debug("[Dead Air] Xaero waypoint added via getList");
                                return;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        Dead_air.LOGGER.debug("[Dead Air] Xaero waypoint constructed; session add path unavailable");
    }
}
