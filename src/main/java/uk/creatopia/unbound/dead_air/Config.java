package uk.creatopia.unbound.dead_air;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Configuration for Dead Air mod.
 */
@Mod.EventBusSubscriber(modid = Dead_air.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
@SuppressWarnings("null")
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // Radio Tower Settings
    private static final ForgeConfigSpec.IntValue EMERGENCY_BROADCAST_RANGE = BUILDER
        .comment("Broadcast range for Emergency Broadcast stations (in blocks)")
        .defineInRange("emergencyBroadcastRange", 2000, 100, 10000);
    
    private static final ForgeConfigSpec.IntValue MUSIC_STATION_RANGE = BUILDER
        .comment("Broadcast range for Music stations (in blocks)")
        .defineInRange("musicStationRange", 1500, 100, 10000);

    private static final ForgeConfigSpec.IntValue MIN_TOWER_SPACING = BUILDER
        .comment("Minimum spacing between towers of the same type (in blocks)")
        .defineInRange("minTowerSpacing", 400, 50, 2000);
    
    private static final ForgeConfigSpec.IntValue POWER_CONSUMPTION = BUILDER
        .comment("Power consumption per tick for radio towers (FE)")
        .defineInRange("powerConsumption", 100, 1, 10000);
    
    // Signal Strength Settings
    private static final ForgeConfigSpec.BooleanValue ENABLE_LINE_OF_SIGHT = BUILDER
        .comment("Enable line-of-sight signal attenuation")
        .define("enableLineOfSight", true);
    
    private static final ForgeConfigSpec.BooleanValue ENABLE_WEATHER_EFFECTS = BUILDER
        .comment("Enable weather effects on signal strength")
        .define("enableWeatherEffects", true);
    
    // Audio Settings
    private static final ForgeConfigSpec.DoubleValue MAX_VOLUME = BUILDER
        .comment("Maximum volume for radio stations (0.0 to 1.0)")
        .defineInRange("maxVolume", 0.7, 0.1, 1.0);
    
    private static final ForgeConfigSpec.DoubleValue MIN_VOLUME = BUILDER
        .comment("Minimum volume for radio stations (0.0 to 1.0)")
        .defineInRange("minVolume", 0.1, 0.0, 0.5);

    private static final ForgeConfigSpec.BooleanValue ATTRACT_ZOMBIES = BUILDER
        .comment("Active broadcasts attract zombies")
        .define("attractZombies", true);
    
    // Music Station Settings
    private static final ForgeConfigSpec.BooleanValue AUTO_DISCOVER_MOD_MUSIC = BUILDER
        .comment("Automatically discover and create stations from mod music tracks")
        .define("autoDiscoverModMusic", true);
    
    private static final ForgeConfigSpec.BooleanValue ENABLE_CORRUPTED_STATIONS = BUILDER
        .comment("Enable corrupted radio stations with special effects")
        .define("enableCorruptedStations", true);
    
    // Walkie-Talkie Settings
    private static final ForgeConfigSpec.BooleanValue RADIO_ALWAYS_ON = BUILDER
        .comment("If true, radio plays as long as walkie-talkie is turned on (even when placed down or in hotbar). " +
                 "If false, radio only plays while holding the walkie-talkie in hand.")
        .define("radioAlwaysOn", true);

    private static final ForgeConfigSpec.BooleanValue SIMPLIFIED_MUSIC_DEBUG = BUILDER
        .comment("DEBUG: If true, bypass playlist/track logic and play a single known-good track (music_disc.13). " +
                 "Use this to narrow down music playback issues. Set to false for normal playlist behavior.")
        .define("simplifiedMusicDebug", true);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    // Config values
    public static int emergencyBroadcastRange;
    public static int musicStationRange;
    public static int minTowerSpacing;
    public static int powerConsumption;
    public static boolean enableLineOfSight;
    public static boolean enableWeatherEffects;
    public static double maxVolume;
    public static double minVolume;
    public static boolean attractZombies;
    public static boolean autoDiscoverModMusic;
    public static boolean enableCorruptedStations;
    public static boolean radioAlwaysOn;
    public static boolean simplifiedMusicDebug;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        emergencyBroadcastRange = EMERGENCY_BROADCAST_RANGE.get();
        musicStationRange = MUSIC_STATION_RANGE.get();
        minTowerSpacing = MIN_TOWER_SPACING.get();
        powerConsumption = POWER_CONSUMPTION.get();
        enableLineOfSight = ENABLE_LINE_OF_SIGHT.get();
        enableWeatherEffects = ENABLE_WEATHER_EFFECTS.get();
        maxVolume = MAX_VOLUME.get();
        minVolume = MIN_VOLUME.get();
        attractZombies = ATTRACT_ZOMBIES.get();
        autoDiscoverModMusic = AUTO_DISCOVER_MOD_MUSIC.get();
        enableCorruptedStations = ENABLE_CORRUPTED_STATIONS.get();
        radioAlwaysOn = RADIO_ALWAYS_ON.get();
        simplifiedMusicDebug = SIMPLIFIED_MUSIC_DEBUG.get();
    }
}
