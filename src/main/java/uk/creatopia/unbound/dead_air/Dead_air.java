package uk.creatopia.unbound.dead_air;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import uk.creatopia.unbound.dead_air.commands.SpawnTowerCommand;
import uk.creatopia.unbound.dead_air.music.MusicStationManager;
import uk.creatopia.unbound.dead_air.radio.StationRegistry;
import uk.creatopia.unbound.dead_air.tower.ApocalypseTowerDetector;
import uk.creatopia.unbound.dead_air.walkie.WalkieTalkieManager;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * Dead Air - A survival-driven radio network mod for Zombiecraft.
 * Integrates radio towers and walkie-talkies for
 * exploration, tension, and ambient storytelling. Airdrop integration planned for a later update.
 */
@Mod(Dead_air.MODID)
public class Dead_air {
    public static final String MODID = "dead_air";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Set true when server is stopping/saving so mod code skips work and storage access (avoids save/exit hang). */
    private static volatile boolean shutdownRequested = false;

    public static boolean isShutdownRequested() {
        return shutdownRequested;
    }

    public static void setShutdownRequested(boolean value) {
        shutdownRequested = value;
    }

    @SuppressWarnings({"removal", "deprecation"}) // 1.20.1 Forge still uses these; removal is for 1.21.1+
    public Dead_air() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        
        LOGGER.info("Dead Air mod initialized");
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        // Register on every fire so the server's command tree gets our command (server tree is built when
        // this fires on the server side). runSpawn bails out if not ServerLevel so client-side run is safe.
        try {
            SpawnTowerCommand.register(event.getDispatcher());
            LOGGER.info("Dead Air: registered commands (RegisterCommandsEvent, thread={})", Thread.currentThread().getName());
        } catch (Exception e) {
            LOGGER.error("Dead Air: failed to register commands", e);
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Initializing Dead Air systems...");

        // Initialize radio stations
        StationRegistry.registerDefaultStations();
        LOGGER.info("Registered default radio stations");

        // Initialize walkie-talkie integration
        WalkieTalkieManager.initialize();
        LOGGER.info("Initialized walkie-talkie integration");

        // Initialize Apocalypse Structures tower detection
        ApocalypseTowerDetector.initialize();
        LOGGER.info("Initialized Apocalypse Structures tower detection");

        // Register network channel for server->client radio signal (so music plays when client has no tower data)
        uk.creatopia.unbound.dead_air.net.DeadAirNet.register();
        LOGGER.info("Dead Air common setup complete");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        setShutdownRequested(false);
        LOGGER.info("Dead Air server starting - will scan for existing radio towers when worlds load...");
        try {
            SpawnTowerCommand.register(event.getServer().getCommands().getDispatcher());
            LOGGER.info("Dead Air: registered commands on server (ServerStartingEvent)");
        } catch (Exception e) {
            LOGGER.error("Dead Air: failed to register commands on server start", e);
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("Dead Air client setup - initializing music stations...");
            
            // Initialize music station manager on client
            event.enqueueWork(() -> {
                MusicStationManager.initialize();
                LOGGER.info("Music stations initialized");
            });
        }
    }
}
