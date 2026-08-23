package uk.co.extraspecialstudio.dead_air.tower;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import uk.co.extraspecialstudio.dead_air.net.KnownTowersSyncPacket;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.SignalStrength;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Client-side cache of known tower positions, synced from server on login.
 * Allows overlay and AudioManager to compute signal locally without per-tick server packets.
 */
public final class KnownTowersClientCache {
    private static final List<KnownTowersSyncPacket.TowerEntry> TOWERS = new CopyOnWriteArrayList<>();

    public static void setTowers(List<KnownTowersSyncPacket.TowerEntry> towers) {
        TOWERS.clear();
        if (towers != null) {
            TOWERS.addAll(towers);
        }
    }

    public static void clear() {
        TOWERS.clear();
    }

    /**
     * Compute best signal for a station at player position using synced known towers.
     * Returns 0 if no towers in range or cache is empty.
     */
    public static float getSignalFromCache(ResourceLocation stationId, Vec3 playerPos, net.minecraft.world.level.Level level) {
        if (stationId == null || playerPos == null || level == null) return 0f;
        RadioStation station = StationRegistry.getStation(stationId);
        if (station == null) return 0f;
        // Emergency Broadcast always has full signal regardless of towers
        if (station.getType() == RadioStation.StationType.EMERGENCY_BROADCAST) return 1.0f;
        if (TOWERS.isEmpty()) return 0f;
        int range = station.getBroadcastRange();
        float best = 0f;
        for (KnownTowersSyncPacket.TowerEntry e : TOWERS) {
            if (!e.activated || e.stationId == null || !e.stationId.equals(stationId)) continue;
            if (StationRegistry.JUKEBOX_FM_ID.equals(e.stationId) && !e.jukeboxModuleInstalled) continue;
            double dist = Vec3.atCenterOf(e.towerPos).distanceTo(playerPos);
            if (dist > range) continue;
            float sig = SignalStrength.getFinalSignalStrengthFromPosition(
                level, Vec3.atCenterOf(e.towerPos), station, playerPos);
            if (sig > best) best = sig;
        }
        return best;
    }

    public static boolean hasCachedTowers() {
        return !TOWERS.isEmpty();
    }

    /**
     * Tower position for walkie compass: nearest detected or activated tower for this station in range.
     * Does not require the chunk to be loaded.
     */
    public static Vec3 getBestTowerPositionForStation(ResourceLocation stationId, Vec3 playerPos, net.minecraft.world.level.Level level) {
        if (TOWERS.isEmpty() || stationId == null || playerPos == null || level == null) return null;
        RadioStation station = StationRegistry.getStation(stationId);
        if (station == null) return null;
        int range = station.getBroadcastRange();
        float bestScore = -1f;
        Vec3 bestPos = null;
        for (KnownTowersSyncPacket.TowerEntry e : TOWERS) {
            if (e.stationId == null || !e.stationId.equals(stationId)) continue;
            if (StationRegistry.JUKEBOX_FM_ID.equals(e.stationId) && !e.jukeboxModuleInstalled) continue;
            Vec3 towerCenter = Vec3.atCenterOf(e.towerPos);
            double dist = towerCenter.distanceTo(playerPos);
            if (dist > range) continue;
            float score;
            if (e.activated) {
                score = SignalStrength.getFinalSignalStrengthFromPosition(level, towerCenter, station, playerPos);
            } else {
                // Detected only: distance-based score so compass still works without broadcasting
                score = 0.05f + 0.5f * (1f - (float) (dist / Math.max(1, range)));
            }
            if (score > bestScore) {
                bestScore = score;
                bestPos = towerCenter;
            }
        }
        return bestPos;
    }

    /**
     * Whether any detected/activated tower for this station is within broadcast range (for compass without music signal).
     */
    public static boolean hasTowerInRangeForStation(ResourceLocation stationId, Vec3 playerPos, net.minecraft.world.level.Level level) {
        return getBestTowerPositionForStation(stationId, playerPos, level) != null;
    }

    /**
     * Get distinct station IDs that have at least one <b>activated</b> tower in range.
     */
    public static List<ResourceLocation> getStationsInRange(Vec3 playerPos, net.minecraft.world.level.Level level) {
        if (TOWERS.isEmpty() || playerPos == null || level == null) return List.of();
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (KnownTowersSyncPacket.TowerEntry e : TOWERS) {
            if (!e.activated || e.stationId == null) continue;
            RadioStation station = StationRegistry.getStation(e.stationId);
            if (station == null) continue;
            if (StationRegistry.JUKEBOX_FM_ID.equals(e.stationId) && !e.jukeboxModuleInstalled) continue;
            double dist = Vec3.atCenterOf(e.towerPos).distanceTo(playerPos);
            if (dist <= station.getBroadcastRange())
                out.add(e.stationId);
        }
        return new ArrayList<>(out);
    }

    /**
     * Station on an activated panel (tooltip). Detected-only panels return null.
     */
    public static ResourceLocation getStationForPanel(BlockPos panelPos) {
        if (panelPos == null || TOWERS.isEmpty()) return null;
        BlockPos p = panelPos.immutable();
        for (KnownTowersSyncPacket.TowerEntry e : TOWERS) {
            if (e == null || e.panelPos == null || !e.activated) continue;
            if (e.panelPos.equals(p)) {
                if (StationRegistry.JUKEBOX_FM_ID.equals(e.stationId) && !e.jukeboxModuleInstalled) {
                    return null;
                }
                return e.stationId;
            }
        }
        return null;
    }

    /** Whether the synced tower entry for this panel includes the Jukebox Upgrade (RadioTowers default cycle). */
    public static boolean isJukeboxModuleInstalledForPanel(BlockPos panelPos) {
        if (panelPos == null || TOWERS.isEmpty()) return false;
        BlockPos p = panelPos.immutable();
        for (KnownTowersSyncPacket.TowerEntry e : TOWERS) {
            if (e.panelPos != null && e.panelPos.equals(p)) return e.jukeboxModuleInstalled;
        }
        return false;
    }

    /** Whether the synced tower entry for this panel includes the Signal Upgrade (Tower Boost). */
    public static boolean isSignalBoostInstalledForPanel(BlockPos panelPos) {
        if (panelPos == null || TOWERS.isEmpty()) return false;
        BlockPos p = panelPos.immutable();
        for (KnownTowersSyncPacket.TowerEntry e : TOWERS) {
            if (e.panelPos != null && e.panelPos.equals(p)) return e.signalBoostInstalled;
        }
        return false;
    }

    /**
     * Signal-Upgraded tower broadcasting {@code stationId} that is within that station's
     * broadcast range of the player (same reach as the walkie station list). Prefers strongest signal.
     */
    public static KnownTowersSyncPacket.TowerEntry findBoostedTowerInRangeForStation(
            Vec3 playerPos, ResourceLocation stationId, net.minecraft.world.level.Level level) {
        if (stationId == null || playerPos == null || level == null || TOWERS.isEmpty()) return null;
        RadioStation station = StationRegistry.getStation(stationId);
        if (station == null) return null;
        int range = station.getBroadcastRange();
        KnownTowersSyncPacket.TowerEntry best = null;
        float bestSig = -1f;
        for (KnownTowersSyncPacket.TowerEntry e : TOWERS) {
            if (!e.activated || !e.signalBoostInstalled || e.panelPos == null) continue;
            if (e.stationId == null || !e.stationId.equals(stationId)) continue;
            Vec3 towerCenter = Vec3.atCenterOf(e.towerPos);
            if (towerCenter.distanceTo(playerPos) > range) continue;
            float sig = SignalStrength.getFinalSignalStrengthFromPosition(level, towerCenter, station, playerPos);
            if (sig > bestSig) {
                bestSig = sig;
                best = e;
            }
        }
        return best;
    }

    /**
     * Any Signal-Upgraded tower whose station is currently in broadcast range of the player.
     * Prefers strongest final signal across candidates.
     */
    public static KnownTowersSyncPacket.TowerEntry findBoostedTowerInRange(
            Vec3 playerPos, net.minecraft.world.level.Level level) {
        if (playerPos == null || level == null || TOWERS.isEmpty()) return null;
        KnownTowersSyncPacket.TowerEntry best = null;
        float bestSig = -1f;
        for (KnownTowersSyncPacket.TowerEntry e : TOWERS) {
            if (!e.activated || !e.signalBoostInstalled || e.panelPos == null || e.stationId == null) continue;
            RadioStation station = StationRegistry.getStation(e.stationId);
            if (station == null) continue;
            Vec3 towerCenter = Vec3.atCenterOf(e.towerPos);
            if (towerCenter.distanceTo(playerPos) > station.getBroadcastRange()) continue;
            float sig = SignalStrength.getFinalSignalStrengthFromPosition(level, towerCenter, station, playerPos);
            if (sig > bestSig) {
                bestSig = sig;
                best = e;
            }
        }
        return best;
    }

    /** Panels that have at least one upgrade installed (client cache). */
    public static List<KnownTowersSyncPacket.TowerEntry> getPanelsWithUpgrades() {
        if (TOWERS.isEmpty()) return List.of();
        List<KnownTowersSyncPacket.TowerEntry> out = new ArrayList<>();
        for (KnownTowersSyncPacket.TowerEntry e : TOWERS) {
            if (e == null || e.panelPos == null) continue;
            if (e.jukeboxModuleInstalled || e.signalBoostInstalled) {
                out.add(e);
            }
        }
        return out;
    }
}
