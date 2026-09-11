package uk.co.extraspecialstudio.dead_air;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Common config for Dead Air ({@code config/dead_air-common.toml}).
 * <p>
 * Section banners and push/pop match the RadioTowers / Dead Letters style so the
 * in-game config screen and toml stay easy to scan. Airdrop, wave, and RadioTowers
 * worldgen options live in {@code config/radiotowers-common.toml}.
 */
@Mod.EventBusSubscriber(modid = Dead_air.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
@SuppressWarnings("null")
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // --- Towers and signal ---
    private static final ForgeConfigSpec.IntValue EMERGENCY_BROADCAST_RANGE;
    private static final ForgeConfigSpec.IntValue MUSIC_STATION_RANGE;
    private static final ForgeConfigSpec.IntValue MIN_TOWER_SPACING;
    private static final ForgeConfigSpec.BooleanValue ENABLE_LINE_OF_SIGHT;
    private static final ForgeConfigSpec.BooleanValue ENABLE_WEATHER_EFFECTS;

    // --- Radio playback ---
    private static final ForgeConfigSpec.DoubleValue MAX_VOLUME;
    private static final ForgeConfigSpec.DoubleValue MIN_VOLUME;
    private static final ForgeConfigSpec.BooleanValue RADIO_ALWAYS_ON;
    private static final ForgeConfigSpec.BooleanValue MUSIC_PLAYS_WITHOUT_TOWER;

    // --- Stations ---
    private static final ForgeConfigSpec.BooleanValue AUTO_DISCOVER_MOD_MUSIC;

    // --- Walkie HUD ---
    private static final ForgeConfigSpec.ConfigValue<String> OVERLAY_CORNER;
    private static final ForgeConfigSpec.IntValue OVERLAY_OFFSET_X;
    private static final ForgeConfigSpec.IntValue OVERLAY_OFFSET_Y;

    // --- Custom music ---
    private static final ForgeConfigSpec.ConfigValue<String> CUSTOM_MUSIC_PATH;

    static {
        // ========== TOWERS AND SIGNAL ==========
        BUILDER.comment(
                "============================================================",
                "TOWERS AND SIGNAL",
                "How far Dead Air radio signals reach from powered towers,",
                "and whether walls or weather weaken reception.",
                "These affect Dead Air only — RadioTowers structure density",
                "is configured in radiotowers-common.toml.",
                "============================================================"
        ).push("towers");

        EMERGENCY_BROADCAST_RANGE = BUILDER
                .comment(
                        "----- START HERE: SIGNAL RANGE -----",
                        "Emergency Broadcast range in blocks.",
                        "This affects Dead Air radio reception, not RadioTowers structure spawning.",
                        "Default: 2000."
                )
                .defineInRange("emergencyBroadcastRange", 2000, 100, 10000);
        MUSIC_STATION_RANGE = BUILDER
                .comment(
                        "Music station range in blocks.",
                        "Signal bars step down with distance; 0/5 means outside this range.",
                        "Default: 375."
                )
                .defineInRange("musicStationRange", 375, 75, 10000);
        MIN_TOWER_SPACING = BUILDER
                .comment(
                        "Minimum spacing used by Dead Air station definitions.",
                        "RadioTowers structure density is configured in radiotowers-common.toml.",
                        "Default: 400."
                )
                .defineInRange("minTowerSpacing", 400, 50, 2000);
        ENABLE_LINE_OF_SIGHT = BUILDER
                .comment(
                        "Walls and terrain weaken radio signals.",
                        "Default: true."
                )
                .define("enableLineOfSight", true);
        ENABLE_WEATHER_EFFECTS = BUILDER
                .comment(
                        "Rain and storms weaken radio signals.",
                        "Default: true."
                )
                .define("enableWeatherEffects", true);
        BUILDER.pop();

        // ========== RADIO PLAYBACK ==========
        BUILDER.comment(
                "============================================================",
                "RADIO PLAYBACK",
                "Volume limits and when a tuned walkie is allowed to play.",
                "============================================================"
        ).push("playback");

        MAX_VOLUME = BUILDER
                .comment(
                        "----- START HERE: VOLUME -----",
                        "Loudest radio volume at full signal.",
                        "Default: 0.7."
                )
                .defineInRange("maxVolume", 0.7, 0.1, 1.0);
        MIN_VOLUME = BUILDER
                .comment(
                        "Quietest audible radio volume at weak signal.",
                        "Default: 0.1."
                )
                .defineInRange("minVolume", 0.1, 0.0, 0.5);
        RADIO_ALWAYS_ON = BUILDER
                .comment(
                        "true: the active tuned walkie plays anywhere in your inventory.",
                        "false: it only plays while held. Dropped walkies never play.",
                        "Default: true."
                )
                .define("radioAlwaysOn", true);
        MUSIC_PLAYS_WITHOUT_TOWER = BUILDER
                .comment(
                        "Allow a tuned station to keep playing when no powered tower is in range.",
                        "Set false for strict tower-only reception.",
                        "Default: true."
                )
                .define("musicPlaysWithoutTower", true);
        BUILDER.pop();

        // ========== STATIONS ==========
        BUILDER.comment(
                "============================================================",
                "STATIONS",
                "How Dead Air finds music stations from other mods.",
                "============================================================"
        ).push("stations");

        AUTO_DISCOVER_MOD_MUSIC = BUILDER
                .comment(
                        "----- START HERE: MOD MUSIC -----",
                        "Automatically discover compatible music from installed mods.",
                        "Default: true."
                )
                .define("autoDiscoverModMusic", true);
        BUILDER.pop();

        // ========== WALKIE HUD ==========
        BUILDER.comment(
                "============================================================",
                "WALKIE HUD",
                "Where the walkie overlay sits on screen.",
                "============================================================"
        ).push("hud");

        OVERLAY_CORNER = BUILDER
                .comment(
                        "----- START HERE: OVERLAY POSITION -----",
                        "HUD corner: top_right, top_left, bottom_right, or bottom_left.",
                        "Default: top_right."
                )
                .define("overlayCorner", "top_right");
        OVERLAY_OFFSET_X = BUILDER
                .comment(
                        "Horizontal pixel offset. Positive moves inward from the chosen corner.",
                        "Default: 0."
                )
                .defineInRange("overlayOffsetX", 0, -200, 200);
        OVERLAY_OFFSET_Y = BUILDER
                .comment(
                        "Vertical pixel offset. Positive moves inward from the chosen corner.",
                        "Default: 0."
                )
                .defineInRange("overlayOffsetY", 0, -200, 200);
        BUILDER.pop();

        // ========== CUSTOM MUSIC ==========
        BUILDER.comment(
                "============================================================",
                "CUSTOM MUSIC",
                "Optional resource-pack folder for player-added OGG tracks.",
                "============================================================"
        ).push("customMusic");

        CUSTOM_MUSIC_PATH = BUILDER
                .comment(
                        "----- START HERE: CUSTOM FOLDER -----",
                        "Optional resource-pack folder containing:",
                        "assets/dead_air/sounds/music/custom/*.ogg",
                        "Leave empty to disable custom music loading.",
                        "Default: (empty)."
                )
                .define("customMusicPath", "");
        BUILDER.pop();
    }

    static final ForgeConfigSpec SPEC = BUILDER.build();

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
