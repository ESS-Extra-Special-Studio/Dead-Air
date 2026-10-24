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
    private static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> EXPANSION_STATIONS;

    // --- Radio HUD ---
    private static final ForgeConfigSpec.ConfigValue<String> OVERLAY_CORNER;
    private static final ForgeConfigSpec.IntValue OVERLAY_OFFSET_X;
    private static final ForgeConfigSpec.IntValue OVERLAY_OFFSET_Y;

    // --- Custom music ---
    private static final ForgeConfigSpec.ConfigValue<String> CUSTOM_MUSIC_PATH;

    // --- Emergency broadcasts / The Listener ---
    private static final ForgeConfigSpec.BooleanValue BROADCASTS_ENABLED;
    private static final ForgeConfigSpec.IntValue BROADCAST_MIN_DAYS;
    private static final ForgeConfigSpec.IntValue BROADCAST_MAX_DAYS;
    private static final ForgeConfigSpec.BooleanValue FORCE_TUNE_ON_STORY_BROADCAST;
    private static final ForgeConfigSpec.IntValue STORY_RELAYS_REQUIRED;
    private static final ForgeConfigSpec.IntValue RELAY_MIN_DISTANCE;
    private static final ForgeConfigSpec.IntValue RELAY_MAX_DISTANCE;
    private static final ForgeConfigSpec.IntValue ENCOUNTER_DURATION_SECONDS;
    private static final ForgeConfigSpec.IntValue PROXIMITY_DETECT_RADIUS;
    private static final ForgeConfigSpec.IntValue PROXIMITY_PROMPT_COOLDOWN_SECONDS;
    private static final ForgeConfigSpec.IntValue EB_SITE_MIN_SEPARATION;
    private static final ForgeConfigSpec.IntValue EB_SITE_MAX_PER_CHUNK;
    private static final ForgeConfigSpec.BooleanValue EB_SITE_CLEAR_TREES;
    private static final ForgeConfigSpec.BooleanValue EB_SITE_AVOID_CLAIMS;
    private static final ForgeConfigSpec.BooleanValue EB_SITE_AVOID_STRUCTURES;

    // --- Survivors ---
    private static final ForgeConfigSpec.BooleanValue SURVIVORS_ENABLED;
    private static final ForgeConfigSpec.IntValue MAX_SURVIVORS_PER_PLAYER;
    private static final ForgeConfigSpec.IntValue MAX_PARTY_SIZE;
    private static final ForgeConfigSpec.BooleanValue PARTY_HUD_ENABLED;
    private static final ForgeConfigSpec.ConfigValue<String> PARTY_HUD_CORNER;
    private static final ForgeConfigSpec.IntValue PARTY_HUD_OFFSET_X;
    private static final ForgeConfigSpec.IntValue PARTY_HUD_OFFSET_Y;
    private static final ForgeConfigSpec.IntValue PARTY_FOLLOW_LEASH;
    private static final ForgeConfigSpec.IntValue PARTY_LOST_DISTANCE;
    private static final ForgeConfigSpec.DoubleValue PARTY_FORMATION_SPACING;
    private static final ForgeConfigSpec.IntValue SURVIVOR_SUPPLY_DAYS;
    private static final ForgeConfigSpec.IntValue BASE_ATTACK_MOB_COUNT;
    private static final ForgeConfigSpec.IntValue BASE_ATTACK_COOLDOWN_DAYS;
    private static final ForgeConfigSpec.IntValue AUCTION_DESPAWN_DAYS;
    private static final ForgeConfigSpec.IntValue AUCTION_DESPAWN_DISTANCE;
    private static final ForgeConfigSpec.IntValue SIDE_EVENT_COOLDOWN_DAYS;

    // --- Soft compat ---
    private static final ForgeConfigSpec.BooleanValue COMPAT_TACZ;
    private static final ForgeConfigSpec.BooleanValue COMPAT_JOURNEYMAP;
    private static final ForgeConfigSpec.BooleanValue COMPAT_XAERO;
    private static final ForgeConfigSpec.BooleanValue COMPAT_FTB_CHUNKS;
    private static final ForgeConfigSpec.BooleanValue COMPAT_COLD_SWEAT;
    private static final ForgeConfigSpec.BooleanValue COMPAT_TOUGH_AS_NAILS;
    private static final ForgeConfigSpec.BooleanValue COMPAT_VEHICLE;
    private static final ForgeConfigSpec.BooleanValue COMPAT_IMMERSIVE_AIRCRAFT;
    private static final ForgeConfigSpec.BooleanValue COMPAT_POINT_BLANK;
    private static final ForgeConfigSpec.BooleanValue COMPAT_HORDE;
    private static final ForgeConfigSpec.BooleanValue COMPAT_APOCALYPSE_GEAR;
    private static final ForgeConfigSpec.BooleanValue SURVIVOR_GUN_FULL_MODELS;

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
                "Volume limits and when a tuned radio is allowed to play.",
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
                        "true: the active tuned radio plays anywhere in your inventory.",
                        "false: it only plays while held. Dropped radios never play.",
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
        EXPANSION_STATIONS = BUILDER
                .comment(
                        "Soundtrack expansion packs are client-only: players install them, the server does not need them.",
                        "Extra Special Studio packs are picked up automatically when a player who has one joins.",
                        "List other packs here by mod id so towers can broadcast them,",
                        "e.g. [\"my_music_pack\"]. Players without that pack see \"Requires ... pack\" instead of music.",
                        "Default: []."
                )
                .defineListAllowEmpty(java.util.List.of("expansionStations"), java.util.List::of, o -> o instanceof String);
        BUILDER.pop();

        // ========== Radio HUD ==========
        BUILDER.comment(
                "============================================================",
                "Radio HUD",
                "Where the radio overlay sits on screen.",
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

        // ========== EMERGENCY BROADCASTS ==========
        BUILDER.comment(
                "============================================================",
                "EMERGENCY BROADCASTS",
                "Story and survivor broadcasts heard on a T3 radio (Signal Decoder upgrades T2→T3),",
                "broken relays, Emergency Broadcast proximity injects, and The Listener.",
                "============================================================"
        ).push("broadcasts");

        BROADCASTS_ENABLED = BUILDER
                .comment(
                        "----- START HERE: BROADCASTS -----",
                        "Players with a T3 radio or a claimed radio-panel base can receive emergency broadcasts.",
                        "Default spacing is one cast per in-game day (broadcastMinDays = broadcastMaxDays = 1).",
                        "Explore while listening can also discover (same day cooldown).",
                        "T3 is crafted by installing a Signal Decoder on a T2 radio.",
                        "Default: true."
                )
                .define("broadcastsEnabled", true);
        BROADCAST_MIN_DAYS = BUILDER
                .comment(
                        "Fewest in-game days between broadcasts for one player.",
                        "With max also 1, eligible players get about one EB per day.",
                        "Default: 1."
                )
                .defineInRange("broadcastMinDays", 1, 1, 30);
        BROADCAST_MAX_DAYS = BUILDER
                .comment(
                        "Most in-game days between broadcasts for one player.",
                        "Keep at 1 for a daily EB cadence; raise to space them out more.",
                        "Default: 1."
                )
                .defineInRange("broadcastMaxDays", 1, 1, 30);
        FORCE_TUNE_ON_STORY_BROADCAST = BUILDER
                .comment(
                        "Story casts hard-switch the dial to Emergency Broadcast.",
                        "Default false: hybrid proximity / chase uses a Yes/No tune prompt instead.",
                        "Default: false."
                )
                .define("forceTuneOnStoryBroadcast", false);
        STORY_RELAYS_REQUIRED = BUILDER
                .comment(
                        "Broken relays a player must investigate before the final broadcast.",
                        "Default: 3."
                )
                .defineInRange("storyRelaysRequired", 3, 1, 10);
        RELAY_MIN_DISTANCE = BUILDER
                .comment(
                        "Closest a broken relay can appear from the player, in blocks.",
                        "Default: 300."
                )
                .defineInRange("relayMinDistance", 300, 32, 5000);
        RELAY_MAX_DISTANCE = BUILDER
                .comment(
                        "Farthest a broken relay can appear from the player, in blocks.",
                        "Default: 700."
                )
                .defineInRange("relayMaxDistance", 700, 64, 10000);
        ENCOUNTER_DURATION_SECONDS = BUILDER
                .comment(
                        "How long the first encounter lasts if the anchors are not destroyed.",
                        "Default: 180."
                )
                .defineInRange("encounterDurationSeconds", 180, 30, 1200);
        PROXIMITY_DETECT_RADIUS = BUILDER
                .comment(
                        "Horizontal radius (blocks) for Emergency Broadcast proximity detection",
                        "around pending or placed story/distress sites. Slightly larger than place radius (96).",
                        "Default: 112."
                )
                .defineInRange("proximityDetectRadius", 112, 32, 512);
        PROXIMITY_PROMPT_COOLDOWN_SECONDS = BUILDER
                .comment(
                        "After dismissing \"EB signal detected — Tune now?\", wait this many seconds",
                        "before prompting again for the same site.",
                        "Default: 120."
                )
                .defineInRange("proximityPromptCooldownSeconds", 120, 10, 3600);
        EB_SITE_MIN_SEPARATION = BUILDER
                .comment(
                        "Minimum horizontal blocks between any two EB site centres (pending or placed).",
                        "Stops kits from carving the same stretch of land repeatedly.",
                        "Default: 96."
                )
                .defineInRange("ebSiteMinSeparation", 96, 32, 512);
        EB_SITE_MAX_PER_CHUNK = BUILDER
                .comment(
                        "Max EB sites whose centre falls in the same chunk.",
                        "Default: 1."
                )
                .defineInRange("ebSiteMaxPerChunk", 1, 1, 8);
        EB_SITE_CLEAR_TREES = BUILDER
                .comment(
                        "When placing a kit, flatten a pad and remove trees/foliage in the clear radius.",
                        "Default: true."
                )
                .define("ebSiteClearTrees", true);
        EB_SITE_AVOID_CLAIMS = BUILDER
                .comment(
                        "Never pin or place EB sites in FTB Chunks claimed land (any claim).",
                        "Default: true."
                )
                .define("ebSiteAvoidClaims", true);
        EB_SITE_AVOID_STRUCTURES = BUILDER
                .comment(
                        "Never pin or place EB sites on villages / other structure pieces.",
                        "Default: true."
                )
                .define("ebSiteAvoidStructures", true);
        BUILDER.pop();

        // ========== SURVIVORS ==========
        BUILDER.comment(
                "============================================================",
                "SURVIVORS",
                "Distress calls lead to stranded survivors you can recruit.",
                "============================================================"
        ).push("survivors");

        SURVIVORS_ENABLED = BUILDER
                .comment(
                        "----- START HERE: SURVIVORS -----",
                        "Distress calls and recruitable survivors.",
                        "Default: true."
                )
                .define("survivorsEnabled", true);
        MAX_SURVIVORS_PER_PLAYER = BUILDER
                .comment(
                        "Most survivors one player can own (following + settled).",
                        "Default: 5."
                )
                .defineInRange("maxSurvivorsPerPlayer", 5, 1, 32);
        MAX_PARTY_SIZE = BUILDER
                .comment(
                        "Max survivors FOLLOWING you at once (party / squad).",
                        "Default: 5."
                )
                .defineInRange("maxPartySize", 5, 1, 16);
        PARTY_HUD_ENABLED = BUILDER
                .comment("Show the party HUD when you have followers.", "Default: true.")
                .define("partyHudEnabled", true);
        PARTY_HUD_CORNER = BUILDER
                .comment("Party HUD corner: top_left, top_right, bottom_left, bottom_right.", "Default: top_left.")
                .define("partyHudCorner", "top_left");
        PARTY_HUD_OFFSET_X = BUILDER
                .comment("Party HUD X offset from corner.", "Default: 0.")
                .defineInRange("partyHudOffsetX", 0, -200, 200);
        PARTY_HUD_OFFSET_Y = BUILDER
                .comment("Party HUD Y offset from corner.", "Default: 0.")
                .defineInRange("partyHudOffsetY", 0, -200, 200);
        PARTY_FOLLOW_LEASH = BUILDER
                .comment(
                        "While FOLLOWING, drop combat targets farther than this from the owner (blocks).",
                        "Default: 28."
                )
                .defineInRange("partyFollowLeash", 28, 8, 96);
        PARTY_LOST_DISTANCE = BUILDER
                .comment(
                        "If farther than this from the owner for several seconds, leave the party and head to base.",
                        "Default: 48."
                )
                .defineInRange("partyLostDistance", 48, 16, 128);
        PARTY_FORMATION_SPACING = BUILDER
                .comment("Formation spacing between followers (blocks).", "Default: 2.0.")
                .defineInRange("partyFormationSpacing", 2.0, 0.5, 6.0);
        SURVIVOR_SUPPLY_DAYS = BUILDER
                .comment(
                        "Settled survivors leave a supply bundle this often, in in-game days.",
                        "Default: 2."
                )
                .defineInRange("survivorSupplyDays", 2, 1, 30);
        BASE_ATTACK_MOB_COUNT = BUILDER
                .comment(
                        "Hostile count for the Base Under Attack broadcast wave.",
                        "Default: 8."
                )
                .defineInRange("baseAttackMobCount", 8, 1, 40);
        BASE_ATTACK_COOLDOWN_DAYS = BUILDER
                .comment(
                        "Minimum in-game days between Base Under Attack events per player.",
                        "Default: 5."
                )
                .defineInRange("baseAttackCooldownDays", 5, 1, 60);
        AUCTION_DESPAWN_DAYS = BUILDER
                .comment(
                        "Dead-channel auction trader packs up after this many in-game days.",
                        "Default: 3."
                )
                .defineInRange("auctionDespawnDays", 3, 1, 30);
        AUCTION_DESPAWN_DISTANCE = BUILDER
                .comment(
                        "Auction trader despawns if the owner is farther than this (blocks).",
                        "Default: 160."
                )
                .defineInRange("auctionDespawnDistance", 160, 48, 512);
        SIDE_EVENT_COOLDOWN_DAYS = BUILDER
                .comment(
                        "Cooldown days for auction / convoy / missing / downed-plane side events.",
                        "Default: 3."
                )
                .defineInRange("sideEventCooldownDays", 3, 1, 30);
        BUILDER.pop();

        // ========== SOFT COMPAT ==========
        BUILDER.comment(
                "============================================================",
                "SOFT COMPAT",
                "Optional integrations. Never required — if the mod is missing",
                "or its API changes, Dead Air soft-fails and continues.",
                "============================================================"
        ).push("compat");

        COMPAT_TACZ = BUILDER.comment("TaCZ: survivor guns + loot tag #dead_air:compat/guns.")
                .define("tacz", true);
        COMPAT_JOURNEYMAP = BUILDER.comment("JourneyMap: temporary waypoint at EB / proximity sites.")
                .define("journeymap", true);
        COMPAT_XAERO = BUILDER.comment("Xaero's Minimap / World Map: temporary waypoint at EB sites.")
                .define("xaero", true);
        COMPAT_FTB_CHUNKS = BUILDER.comment("FTB Chunks: best-effort base claim/highlight when panel base is set.")
                .define("ftbChunks", true);
        COMPAT_COLD_SWEAT = BUILDER.comment("Cold Sweat: temp items in medic supplies / auction.")
                .define("coldSweat", true);
        COMPAT_TOUGH_AS_NAILS = BUILDER.comment("Tough As Nails: water/purification in medic/survivor loot.")
                .define("toughAsNails", true);
        COMPAT_VEHICLE = BUILDER.comment("Vehicle / Vehicle Reborn: parts chest near downed-plane wreck.")
                .define("vehicle", true);
        COMPAT_IMMERSIVE_AIRCRAFT = BUILDER.comment("Immersive Aircraft: alternate crash loot when RT plane absent.")
                .define("immersiveAircraft", true);
        COMPAT_POINT_BLANK = BUILDER.comment("Point Blank: survivor gun equip + #dead_air:compat/guns loot.")
                .define("pointBlank", true);
        COMPAT_HORDE = BUILDER.comment("Zombie Awareness / Enhanced AI: bump Base Under Attack wave count.")
                .define("horde", true);
        COMPAT_APOCALYPSE_GEAR = BUILDER.comment(
                "Apocalypse Now / Survival Instinct / Zombie Survival Kit / Chovy / Food (zmodfood):",
                "mid-tier survivor apparel + fair food/medical/gear in EB chests & supplies.",
                "Never rolls exo / juggernaut / bombsquad kits."
        ).define("apocalypseGear", true);
        SURVIVOR_GUN_FULL_MODELS = BUILDER.comment(
                "When true (default), survivors render full TaCZ/Point Blank 3D gun models in hand.",
                "When false, NPC-held guns use distance LOD and a cheap silhouette proxy (cheaper on the render thread)."
        ).define("survivorGunFullModels", true);
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
    public static java.util.List<String> expansionStations = java.util.List.of();
    public static boolean radioAlwaysOn;
    public static boolean musicPlaysWithoutTower;
    public static String overlayCorner;
    public static int overlayOffsetX;
    public static int overlayOffsetY;
    public static String customMusicPath;
    public static boolean broadcastsEnabled = true;
    public static int broadcastMinDays = 1;
    public static int broadcastMaxDays = 1;
    public static boolean forceTuneOnStoryBroadcast = false;
    public static int storyRelaysRequired = 3;
    public static int relayMinDistance = 300;
    public static int relayMaxDistance = 700;
    public static int encounterDurationSeconds = 180;
    public static int proximityDetectRadius = 112;
    public static int proximityPromptCooldownSeconds = 120;
    public static int ebSiteMinSeparation = 96;
    public static int ebSiteMaxPerChunk = 1;
    public static boolean ebSiteClearTrees = true;
    public static boolean ebSiteAvoidClaims = true;
    public static boolean ebSiteAvoidStructures = true;
    public static boolean survivorsEnabled = true;
    public static int maxSurvivorsPerPlayer = 5;
    public static int maxPartySize = 5;
    public static boolean partyHudEnabled = true;
    public static String partyHudCorner = "top_left";
    public static int partyHudOffsetX = 0;
    public static int partyHudOffsetY = 0;
    public static int partyFollowLeash = 28;
    public static int partyLostDistance = 48;
    public static double partyFormationSpacing = 2.0;
    public static int survivorSupplyDays = 2;
    public static int baseAttackMobCount = 8;
    public static int baseAttackCooldownDays = 5;
    public static int auctionDespawnDays = 3;
    public static int auctionDespawnDistance = 160;
    public static int sideEventCooldownDays = 3;

    public static boolean compatTacz = true;
    public static boolean compatJourneymap = true;
    public static boolean compatXaero = true;
    public static boolean compatFtbChunks = true;
    public static boolean compatColdSweat = true;
    public static boolean compatToughAsNails = true;
    public static boolean compatVehicle = true;
    public static boolean compatImmersiveAircraft = true;
    public static boolean compatPointBlank = true;
    public static boolean compatHorde = true;
    public static boolean compatApocalypseGear = true;
    /** Client render: full TaCZ/PB gun BEWLRs on survivors. Default false = LOD proxies. */
    public static boolean survivorGunFullModels = true;

    /** Gameplay values received from a remote server; null when not connected to one. */
    private static int[] serverInts;
    private static boolean[] serverBools;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        reloadFromSpec();
        broadcastToPlayers();
    }

    /** Client: use the server's gameplay values until disconnect. */
    public static void applyServerValues(int emergencyRange, int musicRange, int spacing,
                                         boolean lineOfSight, boolean weather, boolean playsWithoutTower) {
        serverInts = new int[] {emergencyRange, musicRange, spacing};
        serverBools = new boolean[] {lineOfSight, weather, playsWithoutTower};
        applyServerOverride();
        Dead_air.LOGGER.info("[Dead Air] Using server config: music={} emergency={} spacing={} los={} weather={} playsWithoutTower={}",
            musicRange, emergencyRange, spacing, lineOfSight, weather, playsWithoutTower);
    }

    /** Client: drop server values and go back to the local toml. */
    public static void clearServerValues() {
        if (serverInts == null) return;
        serverInts = null;
        serverBools = null;
        reloadFromSpec();
    }

    private static void applyServerOverride() {
        if (serverInts == null) return;
        emergencyBroadcastRange = serverInts[0];
        musicStationRange = serverInts[1];
        minTowerSpacing = serverInts[2];
        enableLineOfSight = serverBools[0];
        enableWeatherEffects = serverBools[1];
        musicPlaysWithoutTower = serverBools[2];
    }

    private static void broadcastToPlayers() {
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        server.execute(() -> {
            uk.co.extraspecialstudio.dead_air.music.ExpansionPacks.applyConfigList();
            uk.co.extraspecialstudio.dead_air.net.DeadAirNet.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.ALL.noArg(),
                uk.co.extraspecialstudio.dead_air.net.ConfigSyncPacket.fromConfig());
        });
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
        expansionStations = java.util.List.copyOf(EXPANSION_STATIONS.get());
        radioAlwaysOn = RADIO_ALWAYS_ON.get();
        musicPlaysWithoutTower = MUSIC_PLAYS_WITHOUT_TOWER.get();
        overlayCorner = OVERLAY_CORNER.get();
        overlayOffsetX = OVERLAY_OFFSET_X.get();
        overlayOffsetY = OVERLAY_OFFSET_Y.get();
        customMusicPath = CUSTOM_MUSIC_PATH.get() != null ? CUSTOM_MUSIC_PATH.get().trim() : "";
        broadcastsEnabled = BROADCASTS_ENABLED.get();
        broadcastMinDays = BROADCAST_MIN_DAYS.get();
        broadcastMaxDays = Math.max(broadcastMinDays, BROADCAST_MAX_DAYS.get());
        forceTuneOnStoryBroadcast = FORCE_TUNE_ON_STORY_BROADCAST.get();
        storyRelaysRequired = STORY_RELAYS_REQUIRED.get();
        relayMinDistance = RELAY_MIN_DISTANCE.get();
        relayMaxDistance = Math.max(relayMinDistance + 32, RELAY_MAX_DISTANCE.get());
        encounterDurationSeconds = ENCOUNTER_DURATION_SECONDS.get();
        proximityDetectRadius = PROXIMITY_DETECT_RADIUS.get();
        proximityPromptCooldownSeconds = PROXIMITY_PROMPT_COOLDOWN_SECONDS.get();
        ebSiteMinSeparation = EB_SITE_MIN_SEPARATION.get();
        ebSiteMaxPerChunk = EB_SITE_MAX_PER_CHUNK.get();
        ebSiteClearTrees = EB_SITE_CLEAR_TREES.get();
        ebSiteAvoidClaims = EB_SITE_AVOID_CLAIMS.get();
        ebSiteAvoidStructures = EB_SITE_AVOID_STRUCTURES.get();
        survivorsEnabled = SURVIVORS_ENABLED.get();
        maxSurvivorsPerPlayer = MAX_SURVIVORS_PER_PLAYER.get();
        maxPartySize = MAX_PARTY_SIZE.get();
        partyHudEnabled = PARTY_HUD_ENABLED.get();
        partyHudCorner = PARTY_HUD_CORNER.get() != null ? PARTY_HUD_CORNER.get() : "top_left";
        partyHudOffsetX = PARTY_HUD_OFFSET_X.get();
        partyHudOffsetY = PARTY_HUD_OFFSET_Y.get();
        partyFollowLeash = PARTY_FOLLOW_LEASH.get();
        partyLostDistance = PARTY_LOST_DISTANCE.get();
        partyFormationSpacing = PARTY_FORMATION_SPACING.get();
        survivorSupplyDays = SURVIVOR_SUPPLY_DAYS.get();
        baseAttackMobCount = BASE_ATTACK_MOB_COUNT.get();
        baseAttackCooldownDays = BASE_ATTACK_COOLDOWN_DAYS.get();
        auctionDespawnDays = AUCTION_DESPAWN_DAYS.get();
        auctionDespawnDistance = AUCTION_DESPAWN_DISTANCE.get();
        sideEventCooldownDays = SIDE_EVENT_COOLDOWN_DAYS.get();
        compatTacz = COMPAT_TACZ.get();
        compatJourneymap = COMPAT_JOURNEYMAP.get();
        compatXaero = COMPAT_XAERO.get();
        compatFtbChunks = COMPAT_FTB_CHUNKS.get();
        compatColdSweat = COMPAT_COLD_SWEAT.get();
        compatToughAsNails = COMPAT_TOUGH_AS_NAILS.get();
        compatVehicle = COMPAT_VEHICLE.get();
        compatImmersiveAircraft = COMPAT_IMMERSIVE_AIRCRAFT.get();
        compatPointBlank = COMPAT_POINT_BLANK.get();
        compatHorde = COMPAT_HORDE.get();
        compatApocalypseGear = COMPAT_APOCALYPSE_GEAR.get();
        survivorGunFullModels = SURVIVOR_GUN_FULL_MODELS.get();
        applyServerOverride();
        uk.co.extraspecialstudio.dead_air.radio.RadioStation bedrock =
            uk.co.extraspecialstudio.dead_air.radio.StationRegistry.getStation(
                uk.co.extraspecialstudio.dead_air.radio.StationRegistry.BEDROCK_RADIO_ID);
        uk.co.extraspecialstudio.dead_air.radio.RadioStation emergency =
            uk.co.extraspecialstudio.dead_air.radio.StationRegistry.getStation(
                uk.co.extraspecialstudio.dead_air.radio.StationRegistry.EMERGENCY_BROADCAST_ID);
        if (bedrock != null || emergency != null) {
            Dead_air.LOGGER.info(
                "[Dead Air] Live signal range applied: musicConfig={} emergencyConfig={} bedrockStation={} emergencyStation={}",
                musicStationRange,
                emergencyBroadcastRange,
                bedrock == null ? -1 : bedrock.getBroadcastRange(),
                emergency == null ? -1 : emergency.getBroadcastRange());
        } else {
            Dead_air.LOGGER.info(
                "[Dead Air] Signal range config loaded: music={} emergency={}",
                musicStationRange,
                emergencyBroadcastRange);
        }
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
        BROADCASTS_ENABLED.set(true);
        BROADCAST_MIN_DAYS.set(1);
        BROADCAST_MAX_DAYS.set(2);
        FORCE_TUNE_ON_STORY_BROADCAST.set(true);
        STORY_RELAYS_REQUIRED.set(3);
        RELAY_MIN_DISTANCE.set(300);
        RELAY_MAX_DISTANCE.set(700);
        ENCOUNTER_DURATION_SECONDS.set(180);
        SURVIVORS_ENABLED.set(true);
        MAX_SURVIVORS_PER_PLAYER.set(5);
        MAX_PARTY_SIZE.set(5);
        PARTY_HUD_ENABLED.set(true);
        PARTY_HUD_CORNER.set("top_left");
        PARTY_HUD_OFFSET_X.set(0);
        PARTY_HUD_OFFSET_Y.set(0);
        PARTY_FOLLOW_LEASH.set(28);
        PARTY_LOST_DISTANCE.set(48);
        PARTY_FORMATION_SPACING.set(2.0);
        SURVIVOR_SUPPLY_DAYS.set(2);
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
        BROADCASTS_ENABLED.save();
        BROADCAST_MIN_DAYS.save();
        BROADCAST_MAX_DAYS.save();
        FORCE_TUNE_ON_STORY_BROADCAST.save();
        STORY_RELAYS_REQUIRED.save();
        RELAY_MIN_DISTANCE.save();
        RELAY_MAX_DISTANCE.save();
        ENCOUNTER_DURATION_SECONDS.save();
        SURVIVORS_ENABLED.save();
        MAX_SURVIVORS_PER_PLAYER.save();
        MAX_PARTY_SIZE.save();
        PARTY_HUD_ENABLED.save();
        PARTY_HUD_CORNER.save();
        PARTY_HUD_OFFSET_X.save();
        PARTY_HUD_OFFSET_Y.save();
        PARTY_FOLLOW_LEASH.save();
        PARTY_LOST_DISTANCE.save();
        PARTY_FORMATION_SPACING.save();
        SURVIVOR_SUPPLY_DAYS.save();
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

    public static void setPartyHudEnabled(boolean v) {
        partyHudEnabled = v;
        PARTY_HUD_ENABLED.set(v);
        PARTY_HUD_ENABLED.save();
    }

    public static void setPartyHudCorner(String v) {
        if (v == null || (!v.equals("top_left") && !v.equals("top_right")
            && !v.equals("bottom_left") && !v.equals("bottom_right"))) return;
        partyHudCorner = v;
        PARTY_HUD_CORNER.set(v);
        PARTY_HUD_CORNER.save();
    }

    public static void setPartyHudOffsetX(int v) {
        partyHudOffsetX = Math.max(-200, Math.min(200, v));
        PARTY_HUD_OFFSET_X.set(partyHudOffsetX);
        PARTY_HUD_OFFSET_X.save();
    }

    public static void setPartyHudOffsetY(int v) {
        partyHudOffsetY = Math.max(-200, Math.min(200, v));
        PARTY_HUD_OFFSET_Y.set(partyHudOffsetY);
        PARTY_HUD_OFFSET_Y.save();
    }
}
