package uk.co.extraspecialstudio.dead_air.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.PacketDistributor;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.net.CompatWaypointPacket;
import uk.co.extraspecialstudio.dead_air.net.DeadAirNet;

/**
 * Soft JourneyMap / Xaero temporary waypoints for EB sites (client API via reflection).
 * Uses a stable id per site so create / place / proximity refresh one pin instead of stacking.
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
        markEbSite(player, pos, label, null);
    }

    public static void markEbSite(ServerPlayer player, BlockPos pos, String label, String stableId) {
        if (player == null || pos == null) return;
        try {
            boolean wantJm = Config.compatJourneymap && JOURNEYMAP;
            boolean wantXaero = Config.compatXaero && XAERO;
            if (!wantJm && !wantXaero) return;
            String name = label == null || label.isBlank() ? "Dead Air Signal" : label;
            String id = stableId != null && !stableId.isBlank()
                ? stableId
                : ("da_" + pos.getX() + "_" + pos.getZ());
            DeadAirNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new CompatWaypointPacket(pos.getX(), pos.getY(), pos.getZ(),
                    player.level().dimension().location().toString(), name, true, id));
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] Waypoint send failed: {}", t.toString());
        }
    }

    /** Client: create/update temporary waypoints via reflection. */
    public static void clientCreateWaypoint(int x, int y, int z, String dimId, String name, boolean temporary) {
        clientCreateWaypoint(x, y, z, dimId, name, temporary, "da_" + x + "_" + z);
    }

    public static void clientCreateWaypoint(int x, int y, int z, String dimId, String name,
                                            boolean temporary, String stableId) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        String id = stableId != null && !stableId.isBlank() ? stableId : ("da_" + x + "_" + z);
        try {
            if (Config.compatJourneymap && JOURNEYMAP) {
                tryJourneyMap(x, y, z, dimId, name, temporary, id);
            }
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] JourneyMap waypoint failed: {}", t.toString());
        }
        try {
            if (Config.compatXaero && XAERO) {
                tryXaero(x, y, z, name, temporary, id);
            }
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] Xaero waypoint failed: {}", t.toString());
        }
    }

    private static void tryJourneyMap(int x, int y, int z, String dimId, String name,
                                      boolean temporary, String stableId) throws Exception {
        Class<?> waypointCl;
        try {
            waypointCl = Class.forName("journeymap.client.api.display.Waypoint");
        } catch (ClassNotFoundException e) {
            waypointCl = Class.forName("journeymap.client.waypoint.Waypoint");
        }
        Object waypoint = null;
        try {
            var dimRl = net.minecraft.resources.ResourceLocation.tryParse(dimId);
            if (dimRl == null) return;
            ResourceKey<Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimRl);
            var ctor = waypointCl.getConstructor(String.class, String.class, String.class,
                ResourceKey.class, BlockPos.class);
            // Stable id (no timestamp) — JM replaces / updates the same pin on refresh.
            waypoint = ctor.newInstance(Dead_air.MODID, stableId, name, dim, new BlockPos(x, y, z));
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
                        api = plugin.getMethod("getClientAPI").invoke(inst);
                    } catch (NoSuchMethodException e) {
                        try {
                            api = plugin.getMethod("getClientApi").invoke(inst);
                        } catch (NoSuchMethodException e2) {
                            api = inst;
                        }
                    }
                    if (api != null) break;
                } catch (Throwable ignored) {
                }
            }
            if (api != null) {
                // Drop prior pin with same id if the API exposes remove.
                for (String m : new String[]{"remove", "removeWaypoint"}) {
                    try {
                        api.getClass().getMethod(m, String.class, String.class).invoke(api, Dead_air.MODID, stableId);
                        break;
                    } catch (NoSuchMethodException ignored) {
                    }
                    try {
                        api.getClass().getMethod(m, waypointCl).invoke(api, waypoint);
                        break;
                    } catch (NoSuchMethodException ignored) {
                    }
                }
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

    private static void tryXaero(int x, int y, int z, String name, boolean temporary, String stableId) throws Exception {
        Class<?> wpCl;
        try {
            wpCl = Class.forName("xaero.common.minimap.waypoints.Waypoint");
        } catch (ClassNotFoundException e) {
            wpCl = Class.forName("xaero.hud.minimap.waypoint.Waypoint");
        }
        Object wp;
        try {
            var ctor = wpCl.getConstructor(int.class, int.class, int.class, String.class, String.class,
                int.class, int.class, boolean.class);
            String symbol = name != null && !name.isBlank() ? name.substring(0, 1).toUpperCase() : "D";
            wp = ctor.newInstance(x, y, z, name, symbol, 14, 0, temporary);
        } catch (NoSuchMethodException e) {
            var ctor = wpCl.getConstructor(int.class, int.class, int.class, String.class, String.class, int.class);
            String symbol = name != null && !name.isBlank() ? name.substring(0, 1).toUpperCase() : "D";
            wp = ctor.newInstance(x, y, z, name, symbol, 14);
        }
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
                    java.util.List<Object> listObj = null;
                    if (current instanceof java.util.Collection<?> col) {
                        @SuppressWarnings("unchecked")
                        java.util.Collection<Object> c = (java.util.Collection<Object>) col;
                        removeXaeroDupes(c, name, x, z);
                        c.add(wp);
                        Dead_air.LOGGER.debug("[Dead Air] Xaero waypoint added to collection");
                        return;
                    }
                    if (current != null) {
                        try {
                            Object list = current.getClass().getMethod("getList").invoke(current);
                            if (list instanceof java.util.List<?> l) {
                                @SuppressWarnings("unchecked")
                                java.util.List<Object> lo = (java.util.List<Object>) l;
                                listObj = lo;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                    if (listObj != null) {
                        removeXaeroDupes(listObj, name, x, z);
                        listObj.add(wp);
                        Dead_air.LOGGER.debug("[Dead Air] Xaero waypoint added via getList");
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** Drop prior Dead Air / same-name / same-spot pins so refresh does not stack. */
    private static void removeXaeroDupes(java.util.Collection<Object> list, String name, int x, int z) {
        java.util.Iterator<Object> it = list.iterator();
        while (it.hasNext()) {
            Object existing = it.next();
            if (existing == null) continue;
            try {
                String n = null;
                try {
                    Object raw = existing.getClass().getMethod("getName").invoke(existing);
                    n = raw != null ? raw.toString() : null;
                } catch (NoSuchMethodException e) {
                    try {
                        Object raw = existing.getClass().getMethod("getWaypointName").invoke(existing);
                        n = raw != null ? raw.toString() : null;
                    } catch (NoSuchMethodException ignored) {
                    }
                }
                int ex = readCoord(existing, "getX", "x");
                int ez = readCoord(existing, "getZ", "z");
                boolean sameName = n != null && name != null && n.equalsIgnoreCase(name);
                boolean sameSpot = Math.abs(ex - x) <= 4 && Math.abs(ez - z) <= 4;
                boolean daNamed = n != null && (n.startsWith("DA ") || n.startsWith("Dead Air")
                    || n.equalsIgnoreCase("Civilian Camp") || n.equalsIgnoreCase("Dead-Channel Auction")
                    || n.equalsIgnoreCase("Refugee Convoy") || n.equalsIgnoreCase("Broken Relay"));
                if (sameName || (sameSpot && daNamed)) {
                    it.remove();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static int readCoord(Object wp, String getter, String field) {
        try {
            Object v = wp.getClass().getMethod(getter).invoke(wp);
            if (v instanceof Number n) return n.intValue();
        } catch (Throwable ignored) {
        }
        try {
            var f = wp.getClass().getDeclaredField(field);
            f.setAccessible(true);
            Object v = f.get(wp);
            if (v instanceof Number n) return n.intValue();
        } catch (Throwable ignored) {
        }
        return Integer.MIN_VALUE / 4;
    }
}
