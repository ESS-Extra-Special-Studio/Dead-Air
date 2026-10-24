package uk.co.extraspecialstudio.dead_air.broadcast;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player Dead Air 3.0 story progress plus the world's hidden Listener frequency.
 * Stored on the overworld so it survives restarts and dimension changes.
 */
@SuppressWarnings("null")
public class BroadcastProgress extends SavedData {
    private static final String DATA_NAME = Dead_air.MODID + "_broadcast_progress";

    private final Map<UUID, PlayerProgress> players = new HashMap<>();
    private float hiddenFrequency = -1f;

    public static final class PlayerProgress {
        public int stage = StoryStage.NONE;
        public int decodedCount;
        public final List<BlockPos> relaysFound = new ArrayList<>();
        /** Relay the latest story broadcast pointed at; null when none is outstanding. */
        public BlockPos targetRelay;
        public boolean firstEncounterSurvived;
        public boolean bossModeUnlocked;
        public boolean finalDefeated;
        public long nextBroadcastDay = -1;
        /** Fire the next eligible story broadcast on the next scheduler pass, day or night. */
        public boolean storyQueued;
        public boolean breakerHinted;
        /** Site keys that already fired a proximity inject on Emergency Broadcast. */
        public final Set<String> proximityInjected = new HashSet<>();
        /** Site keys that already played the hunt VO beep. */
        public final Set<String> huntVoPlayed = new HashSet<>();
        /** Site key -> game time until the Yes/No tune prompt may show again after dismiss. */
        public final Map<String, Long> proximityPromptCooldownUntil = new HashMap<>();
        public long lastBaseAttackDay = -1;
        public long lastAuctionDay = -1;
        public long lastConvoyDay = -1;
        public long lastMissingDay = -1;
        public long lastPlaneDay = -1;
        public boolean pirateFmHeard;
        @javax.annotation.Nullable public UUID missingSurvivorId;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("stage", stage);
            t.putInt("decoded", decodedCount);
            ListTag found = new ListTag();
            for (BlockPos p : relaysFound) found.add(LongTag.valueOf(p.asLong()));
            t.put("relays", found);
            if (targetRelay != null) t.putLong("target", targetRelay.asLong());
            t.putBoolean("firstSurvived", firstEncounterSurvived);
            t.putBoolean("bossMode", bossModeUnlocked);
            t.putBoolean("finalDefeated", finalDefeated);
            t.putLong("nextDay", nextBroadcastDay);
            t.putBoolean("queued", storyQueued);
            t.putBoolean("breakerHinted", breakerHinted);
            ListTag injected = new ListTag();
            for (String key : proximityInjected) injected.add(net.minecraft.nbt.StringTag.valueOf(key));
            t.put("proxInjected", injected);
            ListTag vo = new ListTag();
            for (String key : huntVoPlayed) vo.add(net.minecraft.nbt.StringTag.valueOf(key));
            t.put("huntVo", vo);
            CompoundTag cool = new CompoundTag();
            for (var e : proximityPromptCooldownUntil.entrySet()) cool.putLong(e.getKey(), e.getValue());
            t.put("proxPromptCd", cool);
            t.putLong("lastBaseAttack", lastBaseAttackDay);
            t.putLong("lastAuction", lastAuctionDay);
            t.putLong("lastConvoy", lastConvoyDay);
            t.putLong("lastMissing", lastMissingDay);
            t.putLong("lastPlane", lastPlaneDay);
            t.putBoolean("pirateFm", pirateFmHeard);
            if (missingSurvivorId != null) t.putUUID("missingSurvivor", missingSurvivorId);
            return t;
        }

        static PlayerProgress load(CompoundTag t) {
            PlayerProgress p = new PlayerProgress();
            p.stage = t.getInt("stage");
            p.decodedCount = t.getInt("decoded");
            ListTag found = t.getList("relays", Tag.TAG_LONG);
            for (int i = 0; i < found.size(); i++) {
                p.relaysFound.add(BlockPos.of(((LongTag) found.get(i)).getAsLong()));
            }
            if (t.contains("target")) p.targetRelay = BlockPos.of(t.getLong("target"));
            p.firstEncounterSurvived = t.getBoolean("firstSurvived");
            p.bossModeUnlocked = t.getBoolean("bossMode");
            p.finalDefeated = t.getBoolean("finalDefeated");
            p.nextBroadcastDay = t.contains("nextDay") ? t.getLong("nextDay") : -1;
            p.storyQueued = t.getBoolean("queued");
            p.breakerHinted = t.getBoolean("breakerHinted");
            ListTag injected = t.getList("proxInjected", Tag.TAG_STRING);
            for (int i = 0; i < injected.size(); i++) p.proximityInjected.add(injected.getString(i));
            ListTag vo = t.getList("huntVo", Tag.TAG_STRING);
            for (int i = 0; i < vo.size(); i++) p.huntVoPlayed.add(vo.getString(i));
            CompoundTag cool = t.getCompound("proxPromptCd");
            for (String key : cool.getAllKeys()) p.proximityPromptCooldownUntil.put(key, cool.getLong(key));
            p.lastBaseAttackDay = t.contains("lastBaseAttack") ? t.getLong("lastBaseAttack") : -1;
            p.lastAuctionDay = t.contains("lastAuction") ? t.getLong("lastAuction") : -1;
            p.lastConvoyDay = t.contains("lastConvoy") ? t.getLong("lastConvoy") : -1;
            p.lastMissingDay = t.contains("lastMissing") ? t.getLong("lastMissing") : -1;
            p.lastPlaneDay = t.contains("lastPlane") ? t.getLong("lastPlane") : -1;
            p.pirateFmHeard = t.getBoolean("pirateFm");
            if (t.hasUUID("missingSurvivor")) p.missingSurvivorId = t.getUUID("missingSurvivor");
            return p;
        }
    }

    public static BroadcastProgress load(CompoundTag nbt) {
        BroadcastProgress s = new BroadcastProgress();
        s.hiddenFrequency = nbt.contains("hiddenFrequency") ? nbt.getFloat("hiddenFrequency") : -1f;
        CompoundTag list = nbt.getCompound("players");
        for (String key : list.getAllKeys()) {
            try {
                s.players.put(UUID.fromString(key), PlayerProgress.load(list.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return s;
    }

    public static BroadcastProgress load(CompoundTag nbt, HolderLookup.Provider provider) {
        return load(nbt);
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        nbt.putFloat("hiddenFrequency", hiddenFrequency);
        CompoundTag list = new CompoundTag();
        for (var e : players.entrySet()) list.put(e.getKey().toString(), e.getValue().save());
        nbt.put("players", list);
        return nbt;
    }

    public PlayerProgress get(UUID player) {
        return players.computeIfAbsent(player, k -> new PlayerProgress());
    }

    public void reset(UUID player) {
        players.remove(player);
        setDirty();
    }

    /** Hidden frequency for this world, picked once so it never sits on a real station. */
    public float getHiddenFrequency(RandomSource random) {
        if (hiddenFrequency < 88f) {
            hiddenFrequency = pickHiddenFrequency(random);
            setDirty();
        }
        return hiddenFrequency;
    }

    public void setHiddenFrequency(float freq) {
        hiddenFrequency = freq;
        setDirty();
    }

    private static float pickHiddenFrequency(RandomSource random) {
        for (int attempt = 0; attempt < 200; attempt++) {
            float f = Math.round((88.5f + random.nextFloat() * 20f) * 10f) / 10f;
            boolean clash = false;
            for (RadioStation s : StationRegistry.getAllStations()) {
                if (Math.abs(s.getFrequency() - f) < 0.6f) {
                    clash = true;
                    break;
                }
            }
            if (!clash) return f;
        }
        return 108.7f;
    }

    public static BroadcastProgress get(MinecraftServer server) {
        if (server == null) return null;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return null;
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(BroadcastProgress::new, BroadcastProgress::load, null), DATA_NAME);
    }
}
