package uk.co.extraspecialstudio.dead_air.tower.modules;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Module/capability state for a single tower.
 *
 * This is the data backbone only (storage + serialization). Actual module behavior is implemented elsewhere.
 */
@SuppressWarnings("null")
public class TowerCapabilities {

    private boolean canRequestAirdrop;
    private boolean hasSafeZoneEmitter;
    /** Server: Jukebox Upgrade installed on this panel (off-hand apply on radio panel). */
    private boolean jukeboxModuleInstalled;
    /** Server: Signal Upgrade / Tower Boost — enables T2 walkie cross-dim sync. */
    private boolean signalBoostInstalled;
    private final Set<ResourceLocation> unlockedStations = new HashSet<>();

    public boolean canRequestAirdrop() {
        return canRequestAirdrop;
    }

    public void setCanRequestAirdrop(boolean canRequestAirdrop) {
        this.canRequestAirdrop = canRequestAirdrop;
    }

    public boolean hasSafeZoneEmitter() {
        return hasSafeZoneEmitter;
    }

    public void setHasSafeZoneEmitter(boolean hasSafeZoneEmitter) {
        this.hasSafeZoneEmitter = hasSafeZoneEmitter;
    }

    public boolean isJukeboxModuleInstalled() {
        return jukeboxModuleInstalled;
    }

    public void setJukeboxModuleInstalled(boolean jukeboxModuleInstalled) {
        this.jukeboxModuleInstalled = jukeboxModuleInstalled;
    }

    public boolean isSignalBoostInstalled() {
        return signalBoostInstalled;
    }

    public void setSignalBoostInstalled(boolean signalBoostInstalled) {
        this.signalBoostInstalled = signalBoostInstalled;
    }

    public Set<ResourceLocation> getUnlockedStations() {
        return Collections.unmodifiableSet(unlockedStations);
    }

    public boolean unlockStation(ResourceLocation stationId) {
        if (stationId == null) return false;
        return unlockedStations.add(stationId);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("canRequestAirdrop", canRequestAirdrop);
        tag.putBoolean("hasSafeZoneEmitter", hasSafeZoneEmitter);
        tag.putBoolean("jukeboxModuleInstalled", jukeboxModuleInstalled);
        tag.putBoolean("signalBoostInstalled", signalBoostInstalled);
        ListTag list = new ListTag();
        for (ResourceLocation id : unlockedStations) {
            list.add(StringTag.valueOf(id.toString()));
        }
        tag.put("unlockedStations", list);
        return tag;
    }

    public static TowerCapabilities fromTag(CompoundTag tag) {
        TowerCapabilities caps = new TowerCapabilities();
        if (tag == null) return caps;
        caps.canRequestAirdrop = tag.getBoolean("canRequestAirdrop");
        caps.hasSafeZoneEmitter = tag.getBoolean("hasSafeZoneEmitter");
        caps.jukeboxModuleInstalled = tag.getBoolean("jukeboxModuleInstalled");
        caps.signalBoostInstalled = tag.getBoolean("signalBoostInstalled");
        if (tag.contains("unlockedStations", 9)) {
            ListTag list = tag.getList("unlockedStations", 8);
            for (int i = 0; i < list.size(); i++) {
                String s = list.getString(i);
                ResourceLocation id = ResourceLocation.tryParse(s);
                if (id != null) caps.unlockedStations.add(id);
            }
        }
        return caps;
    }
}

