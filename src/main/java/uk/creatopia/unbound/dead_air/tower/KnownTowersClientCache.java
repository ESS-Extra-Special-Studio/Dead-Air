package uk.creatopia.unbound.dead_air.tower;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import uk.creatopia.unbound.dead_air.net.KnownTowersSyncPacket;
import net.minecraft.world.phys.Vec3;
import uk.creatopia.unbound.dead_air.radio.RadioStation;
import uk.creatopia.unbound.dead_air.radio.SignalStrength;
import uk.creatopia.unbound.dead_air.radio.StationRegistry;

import java.util.ArrayList;
import java.util.List;
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
        if (TOWERS.isEmpty() || stationId == null || playerPos == null || level == null) {
            return 0f;
        }
        RadioStation station = StationRegistry.getStation(stationId);
        if (station == null) return 0f;
        int range = station.getBroadcastRange();
        float best = 0f;
        for (KnownTowersSyncPacket.TowerEntry e : TOWERS) {
            if (!e.stationId.equals(stationId)) continue;
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
     * Get the station assigned to a panel (for tooltip when looking at the block).
     * Returns null if panel is not in the synced cache.
     */
    public static ResourceLocation getStationForPanel(BlockPos panelPos) {
        if (panelPos == null || TOWERS.isEmpty()) return null;
        BlockPos p = panelPos.immutable();
        for (KnownTowersSyncPacket.TowerEntry e : TOWERS) {
            if (e.panelPos.equals(p)) return e.stationId;
        }
        return null;
    }
}
