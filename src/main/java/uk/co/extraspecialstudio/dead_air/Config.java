package uk.co.extraspecialstudio.dead_air;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * Human-readable common config for Dead Air.
 *
 * Keep keys at the root for compatibility with existing worlds and modpacks.
 * Visual groups are provided by concise section comments instead of changing
 * key paths. Airdrop, wave, and RadioTowers worldgen options intentionally
 * live in config/radiotowers-common.toml.
 */
@EventBusSubscriber(modid = Dead_air.MODID, bus = EventBusSubscriber.Bus.MOD)
@SuppressWarnings("null")
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ===== TOWERS AND SIGNAL =====
    private static final ModConfigSpec.IntValue EMERGENCY_BROADCAST_RANGE = BUILDER
        .comment(
            "===== TOWERS AND SIGNAL =====",
            "Emergency Broadcast range in blocks.",
            "This affects Dead Air radio reception, not RadioTowers structure spawning."
        )
        .defineInRange("emergencyBroadcastRange", 2000, 100, 10000);
    
    private static final ModConfigSpec.IntValue MUSIC_STATION_RANGE = BUILDER
        .comment(
            "Music station range in blocks.",
            "Signal bars step down with distance; 0/5 means outside this range."
        )
        .defineInRange("musicStationRange", 375, 75, 10000);

    private static final ModConfigSpec.IntValue MIN_TOWER_SPACING = BUILDER
        .comment(
            "Minimum spacing used by Dead Air station definitions.",
            "RadioTowers structure density is configured in radiotowers-common.toml."
        )
        .defineInRange("minTowerSpacing", 400, 50, 2000);
    
    private static final ModConfigSpec.BooleanValue ENABLE_LINE_OF_SIGHT = BUILDER
        .comment("Walls and terrain weaken radio signals.")
        .define("enableLineOfSight", true);
    
    private static final ModConfigSpec.BooleanValue ENABLE_WEATHER_EFFECTS = BUILDER
        .comment("Rain and storms weaken radio signals.")
        .define("enableWeatherEffects", true);
    
    // ===== RADIO PLAYBACK =====
    private static final ModConfigSpec.DoubleValue MAX_VOLUME = BUILDER
        .comment(
            "===== RADIO PLAYBACK =====",
            "Loudest radio volume at full signal."
        )
        .defineInRange("maxVolume", 0.7, 0.1, 1.0);
    
    private static final ModConfigSpec.DoubleValue MIN_VOLUME = BUILDER
        .comment("Quietest audible radio volume at weak signal.")
        .defineInRange("minVolume", 0.1, 0.0, 0.5);

    private static final ModConfigSpec.BooleanValue RADIO_ALWAYS_ON = BUILDER
        .comment(
            "true: the active tuned walkie plays anywhere in your inventory.",
            "false: it only plays while held. Dropped walkies never play."
        )
        .define("radioAlwaysOn", true);

    private static final ModConfigSpec.BooleanValue MUSIC_PLAYS_WITHOUT_TOWER = BUILDER
        .comment(
            "Allow a tuned station to keep playing when no powered tower is in range.",
            "Set false for strict tower-only reception."
        )
        .define("musicPlaysWithoutTower", true);

    // ===== STATIONS =====
    private static final ModConfigSpec.BooleanValue AUTO_DISCOVER_MOD_MUSIC = BUILDER
        .comment(
            "===== STATIONS =====",
            "Automatically discover compatible music from installed mods."
        )
        .define("autoDiscoverModMusic", true);

    // ===== WALKIE HUD =====
    private static final ModConfigSpec.ConfigValue<String> OVERLAY_CORNER = BUILDER
        .comment(
            "===== WALKIE HUD =====",
            "HUD corner: top_right, top_left, bottom_right, or bottom_left."
        )
        .define("overlayCorner", "top_right");
    private static final ModConfigSpec.IntValue OVERLAY_OFFSET_X = BUILDER
        .comment("Horizontal pixel offset. Positive moves inward from the chosen corner.")
        .defineInRange("overlayOffsetX", 0, -200, 200);
    private static final ModConfigSpec.IntValue OVERLAY_OFFSET_Y = BUILDER
        .comment("Vertical pixel offset. Positive moves inward from the chosen corner.")
        .defineInRange("overlayOffsetY", 0, -200, 200);

    // ===== CUSTOM MUSIC =====
    private static final ModConfigSpec.ConfigValue<String> CUSTOM_MUSIC_PATH = BUILDER
        .comment(
            "===== CUSTOM MUSIC =====",
            "Optional resource-pack folder containing:",
            "assets/dead_air/sounds/music/custom/*.ogg",
            "Leave empty to disable custom music loading."
        )
        .define("customMusicPath", "");

    static final ModConfigSpec SPEC = BUILDER.build();

    // Config values
    public static int emergencyBroadcastRange;
    public static int musicStationRange;
    public static int minTowerSpacing;
    public static boolean enableLineOfSight;
    public static boolean enableWeatherEffects;
    public static double maxVolume;
    public static double minVolume;
    public static boolean autoDiscoverModMusic;
    public static boolean radioAlwaysOn;
    public static boolean musicPlaysWithoutTower;
    public static String overlayCorner;
    public static int overlayOffsetX;
    public static int overlayOffsetY;
    public static String customMusicPath;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        reloadFromSpec();
    }

    public static void reloadFromSpec() {
        emergencyBroadcastRange = EMERGENCY_BROADCAST_RANGE.get();
        musicStationRange = MUSIC_STATION_RANGE.get();
        minTowerSpacing = MIN_TOWER_SPACING.get();
        enableLineOfSight = ENABLE_LINE_OF_SIGHT.get();
        enableWeatherEffects = ENABLE_WEATHER_EFFECTS.get();
        maxVolume = MAX_VOLUME.get();
        minVolume = MIN_VOLUME.get();
        autoDiscoverModMusic = AUTO_DISCOVER_MOD_MUSIC.get();
        radioAlwaysOn = RADIO_ALWAYS_ON.get();
        musicPlaysWithoutTower = MUSIC_PLAYS_WITHOUT_TOWER.get();
        overlayCorner = OVERLAY_CORNER.get();
        overlayOffsetX = OVERLAY_OFFSET_X.get();
        overlayOffsetY = OVERLAY_OFFSET_Y.get();
        customMusicPath = CUSTOM_MUSIC_PATH.get() != null ? CUSTOM_MUSIC_PATH.get().trim() : "";
    }

    /** Distinctive values so each Dead Air key is obvious in-game. Writes toml. */
    public static void applyTestProfile() {
        EMERGENCY_BROADCAST_RANGE.set(250);
        MUSIC_STATION_RANGE.set(75);
        MIN_TOWER_SPACING.set(400);
        ENABLE_LINE_OF_SIGHT.set(true);
        ENABLE_WEATHER_EFFECTS.set(true);
        MAX_VOLUME.set(1.0);
        MIN_VOLUME.set(0.0);
        AUTO_DISCOVER_MOD_MUSIC.set(true);
        RADIO_ALWAYS_ON.set(false);
        MUSIC_PLAYS_WITHOUT_TOWER.set(false);
        OVERLAY_CORNER.set("bottom_left");
        OVERLAY_OFFSET_X.set(40);
        OVERLAY_OFFSET_Y.set(20);
        CUSTOM_MUSIC_PATH.set("");
        saveAll();
        reloadFromSpec();
    }

    public static void restoreDefaults() {
        EMERGENCY_BROADCAST_RANGE.set(2000);
        MUSIC_STATION_RANGE.set(375);
        MIN_TOWER_SPACING.set(400);
        ENABLE_LINE_OF_SIGHT.set(true);
        ENABLE_WEATHER_EFFECTS.set(true);
        MAX_VOLUME.set(0.7);
        MIN_VOLUME.set(0.1);
        AUTO_DISCOVER_MOD_MUSIC.set(true);
        RADIO_ALWAYS_ON.set(true);
        MUSIC_PLAYS_WITHOUT_TOWER.set(true);
        OVERLAY_CORNER.set("top_right");
        OVERLAY_OFFSET_X.set(0);
        OVERLAY_OFFSET_Y.set(0);
        CUSTOM_MUSIC_PATH.set("");
        saveAll();
        reloadFromSpec();
    }

    private static void saveAll() {
        EMERGENCY_BROADCAST_RANGE.save();
        MUSIC_STATION_RANGE.save();
        MIN_TOWER_SPACING.save();
        ENABLE_LINE_OF_SIGHT.save();
        ENABLE_WEATHER_EFFECTS.save();
        MAX_VOLUME.save();
        MIN_VOLUME.save();
        AUTO_DISCOVER_MOD_MUSIC.save();
        RADIO_ALWAYS_ON.save();
        MUSIC_PLAYS_WITHOUT_TOWER.save();
        OVERLAY_CORNER.save();
        OVERLAY_OFFSET_X.save();
        OVERLAY_OFFSET_Y.save();
        CUSTOM_MUSIC_PATH.save();
    }

    /** Setters for in-game config screen. Update both static field and spec, then save to file. */
    public static void setMaxVolume(double v) {
        maxVolume = Math.max(0.1, Math.min(1.0, v));
        MAX_VOLUME.set(maxVolume);
        MAX_VOLUME.save();
    }
    public static void setMinVolume(double v) {
        minVolume = Math.max(0.0, Math.min(0.5, v));
        MIN_VOLUME.set(minVolume);
        MIN_VOLUME.save();
    }
    public static void setRadioAlwaysOn(boolean v) {
        radioAlwaysOn = v;
        RADIO_ALWAYS_ON.set(v);
        RADIO_ALWAYS_ON.save();
    }
    public static void setOverlayCorner(String v) {
        if (v == null || (!v.equals("top_left") && !v.equals("top_right") && !v.equals("bottom_left") && !v.equals("bottom_right"))) return;
        overlayCorner = v;
        OVERLAY_CORNER.set(v);
        OVERLAY_CORNER.save();
    }
    public static void setOverlayOffsetX(int v) {
        overlayOffsetX = Math.max(-200, Math.min(200, v));
        OVERLAY_OFFSET_X.set(overlayOffsetX);
        OVERLAY_OFFSET_X.save();
    }
    public static void setOverlayOffsetY(int v) {
        overlayOffsetY = Math.max(-200, Math.min(200, v));
        OVERLAY_OFFSET_Y.set(overlayOffsetY);
        OVERLAY_OFFSET_Y.save();
    }
}
