package uk.co.extraspecialstudio.dead_air;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import uk.co.extraspecialstudio.dead_air.commands.SpawnTowerCommand;
import uk.co.extraspecialstudio.dead_air.block.DeadAirBlocks;
import uk.co.extraspecialstudio.dead_air.item.DeadAirCreativeTabs;
import uk.co.extraspecialstudio.dead_air.item.DeadAirItems;
import uk.co.extraspecialstudio.dead_air.music.MusicStationManager;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;
import uk.co.extraspecialstudio.dead_air.station.config.ConfigStationLoader;
import uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerDetector;
import uk.co.extraspecialstudio.dead_air.walkie.WalkieTalkieManager;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Dead Air - A survival-driven radio network mod for Zombiecraft.
 * Integrates radio towers and walkie-talkies for
 * exploration, tension, and ambient storytelling. Airdrop integration planned for a later update.
 */
@SuppressWarnings({"removal", "deprecation"}) // ModLoadingContext / FMLJavaModLoadingContext: 1.20.1 Forge still uses these
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

    public Dead_air() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(uk.co.extraspecialstudio.dead_air.client.CustomMusicLoader::registerCustomSounds);
        uk.co.extraspecialstudio.dead_air.client.DeadAirSounds.REGISTER.register(modEventBus);
        DeadAirBlocks.REGISTER.register(modEventBus);
        DeadAirItems.REGISTER.register(modEventBus);
        DeadAirCreativeTabs.REGISTER.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        try {
            SpawnTowerCommand.register(event.getDispatcher());
        } catch (Exception e) {
            LOGGER.error("Dead Air: failed to register commands", e);
        }
    }

    /** Force server-side load of tower classes so right-click on panel doesn't get NoClassDefFoundError (ModuleClassLoader). */
    private static void ensureTowerClassesLoaded() {
        try {
            Class.forName("uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerType");
            Class.forName("uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerDetector");
            Class.forName("uk.co.extraspecialstudio.dead_air.tower.RadioPanelManager");
            Class.forName("uk.co.extraspecialstudio.dead_air.tower.PanelActivationStorage");
            Class.forName("uk.co.extraspecialstudio.dead_air.tower.KnownTowerStorage");
            Class.forName("uk.co.extraspecialstudio.dead_air.radio.TowerManager");
            Class.forName("uk.co.extraspecialstudio.dead_air.radio.RadioTower");
            // Touch enum constants so the class is fully initialized before RadioTowers reflects into DeadAirAPI
            uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerType.values();
            LOGGER.info("Dead Air: tower classes ready");
        } catch (Throwable e) {
            LOGGER.error("Dead Air: tower classes not loadable — panel activation will fail", e);
        }
    }

    /** Force-load client music/audio helpers so tune/playback does not hit ModuleClassLoader NoClassDefFoundError. */
    private static void ensureMusicClassesLoaded() {
        ClassLoader cl = Dead_air.class.getClassLoader();
        try {
            Class.forName("uk.co.extraspecialstudio.dead_air.music.OggDurationReader", true, cl);
            Class.forName("uk.co.extraspecialstudio.dead_air.music.SoundDurationChecker", true, cl);
            Class.forName("uk.co.extraspecialstudio.dead_air.music.MusicStationManager", true, cl);
            Class.forName("uk.co.extraspecialstudio.dead_air.audio.AudioManager", true, cl);
            Class.forName("uk.co.extraspecialstudio.dead_air.audio.RadioSoundInstance", true, cl);
            Class.forName("uk.co.extraspecialstudio.dead_air.audio.StaticSoundManager", true, cl);
            Class.forName("uk.co.extraspecialstudio.dead_air.audio.StaticSoundManager$LoopingStaticSound", true, cl);
            Class.forName("uk.co.extraspecialstudio.dead_air.audio.InternetStreamManager", true, cl);
            Class.forName("uk.co.extraspecialstudio.dead_air.client.DeadAirSounds", true, cl);
            LOGGER.info("Dead Air: music/audio classes ready");
        } catch (Throwable e) {
            LOGGER.error("Dead Air: music/audio classes not loadable — some tune features may degrade", e);
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        StationRegistry.registerDefaultStations();
        ConfigStationLoader.loadAndRegisterStations();
        uk.co.extraspecialstudio.dead_air.music.CustomStationFolders.registerStationsIntoRegistry();
        WalkieTalkieManager.initialize();
        ApocalypseTowerDetector.initialize();
        ensureTowerClassesLoaded();
        uk.co.extraspecialstudio.dead_air.net.DeadAirNet.register();
        event.enqueueWork(() -> {
            try {
                if (net.minecraftforge.fml.ModList.get().isLoaded("radiotowers")) {
                    Class<?> catalog = Class.forName("net.mcreator.radiotowers.airdrop.AirdropCatalog");
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> addons = (java.util.List<Object>) catalog.getField("ADDON_ENTRIES").get(null);
                    Class<?> entryCl = Class.forName("net.mcreator.radiotowers.airdrop.AirdropCatalog$Entry");
                    java.lang.reflect.Constructor<?> entryCtor =
                        entryCl.getConstructor(ResourceLocation.class, int.class, int.class);
                    addons.add(entryCtor.newInstance(new ResourceLocation(MODID, "walkie_t1"), 5, 1));
                    // The catalog caches itself on first use; drop that cache in case it built before we registered.
                    catalog.getMethod("invalidate").invoke(null);
                    LOGGER.info("Dead Air: registered airdrop catalog entry (T1.Radio 1 / 5 points)");
                }
            } catch (Throwable t) {
                LOGGER.debug("Dead Air: could not register airdrop addon entry: {}", t.toString());
            }
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        setShutdownRequested(false);
        try {
            SpawnTowerCommand.register(event.getServer().getCommands().getDispatcher());
        } catch (Exception e) {
            LOGGER.error("Dead Air: failed to register commands on server start", e);
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            uk.co.extraspecialstudio.dead_air.net.DeadAirNet.setOpenWalkieGuiCallback(() -> {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    net.minecraft.world.InteractionHand hand =
                        uk.co.extraspecialstudio.dead_air.walkie.WalkieTalkieManager.isWalkieTalkieItem(mc.player.getMainHandItem())
                            ? net.minecraft.world.InteractionHand.MAIN_HAND
                            : net.minecraft.world.InteractionHand.OFF_HAND;
                    net.minecraft.world.item.ItemStack held = mc.player.getItemInHand(hand);
                    if (held.getItem() instanceof uk.co.extraspecialstudio.dead_air.item.WalkieItem walkie) {
                        walkie.openTuningScreen(hand);
                    } else {
                        mc.setScreen(new uk.co.extraspecialstudio.dead_air.client.WalkieTalkieTuningScreen(hand));
                    }
                }
            });
            // ClientEvents registered via @EventBusSubscriber on FORGE bus
            event.enqueueWork(MusicStationManager::initialize);
            ensureMusicClassesLoaded();
        }

        /** Reset T2 LCD probe after resource packs change (e.g. Pip-Boy conversion enabled). */
        @SubscribeEvent
        public static void onRegisterReloadListeners(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) resourceManager -> {
                try {
                    uk.co.extraspecialstudio.dead_air.client.RadioLiveDisplay.resetAll();
                } catch (Throwable ignored) {
                }
            });
        }

        /** Add custom music / custom stations pack so .ogg files are loadable. */
        @SubscribeEvent
        public static void onAddPackFinders(AddPackFindersEvent event) {
            if (event.getPackType() != net.minecraft.server.packs.PackType.CLIENT_RESOURCES) return;
            try {
                String pathStr = uk.co.extraspecialstudio.dead_air.client.CustomMusicLoader.getCustomMusicPathForPack();
                java.nio.file.Path customRoot = (pathStr == null || pathStr.isEmpty())
                    ? null
                    : java.nio.file.Path.of(pathStr);
                java.nio.file.Path packRoot = uk.co.extraspecialstudio.dead_air.client.CustomMusicLoader.preparePackCache(
                    customRoot != null ? customRoot : java.nio.file.Path.of(""));
                if (packRoot == null) return;
                java.nio.file.Path path = packRoot;
                event.addRepositorySource(consumer -> {
                    try {
                        net.minecraft.server.packs.repository.Pack pack = net.minecraft.server.packs.repository.Pack.readMetaAndCreate(
                            Dead_air.MODID + "/custom_music",
                            net.minecraft.network.chat.Component.literal("Dead Air Custom Music"),
                            true,
                            id -> new net.minecraft.server.packs.PathPackResources("Dead Air Custom Music", path, false),
                            net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
                            net.minecraft.server.packs.repository.Pack.Position.TOP,
                            net.minecraft.server.packs.repository.PackSource.DEFAULT
                        );
                        if (pack != null) consumer.accept(pack);
                    } catch (Exception e) {
                        Dead_air.LOGGER.warn("[Dead Air] Could not add custom music pack from {}", pathStr, e);
                    }
                });
            } catch (Exception e) {
                Dead_air.LOGGER.warn("[Dead Air] Could not add custom music pack", e);
            }
        }

        /** Register walkie HUD overlay so it always renders (not dependent on CROSSHAIR/HOTBAR events). */
        @SubscribeEvent
        public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("walkie_hud", (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
                uk.co.extraspecialstudio.dead_air.client.WalkieTalkieOverlay.renderRadioPanelTooltip(guiGraphics, screenWidth, screenHeight);
                uk.co.extraspecialstudio.dead_air.client.WalkieTalkieOverlay.render(guiGraphics, screenWidth, screenHeight);
            });
        }
    }
}
