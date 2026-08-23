package uk.co.extraspecialstudio.dead_air.events;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.event.sound.PlayStreamingSourceEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import uk.co.extraspecialstudio.dead_air.audio.AudioManager;
import uk.co.extraspecialstudio.dead_air.client.KeyBindings;
import uk.co.extraspecialstudio.dead_air.client.WalkieTalkieTuningScreen;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.walkie.WalkieTalkieManager;

/**
 * Client-side event handlers. Registered via @EventBusSubscriber on FORGE bus so tick handler receives ClientTickEvent.
 */
@Mod.EventBusSubscriber(modid = Dead_air.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
@SuppressWarnings("null")
public class ClientEvents {

    // Set during world exit / disconnect to avoid any client-side work during teardown.
    private static volatile boolean DISCONNECTING = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END) return;
        if (mc != null && mc.player != null && mc.level != null) {
            DISCONNECTING = false;
        }
        if (DISCONNECTING) {
            return;
        }
        if (mc == null || mc.player == null || mc.level == null) {
            return;
        }
        if (mc.level.isClientSide && mc.level.getServer() != null && mc.level.getServer().isStopped()) {
            AudioManager.stopAll();
            return;
        }
        // stopAnyVanillaMusic removed - empty; MusicManagerMixin disables vanilla music

        // Sole music driver (was overlay-only; tick keeps inventory radio alive when HUD hidden).
        WalkieTalkieManager.ensureSingleActiveListening(mc.player);
        WalkieTalkieManager.refreshLinkedStation(mc.player);
        if (!WalkieTalkieManager.shouldPlayRadio(mc.player)) {
            AudioManager.stopAll();
            return;
        }

        ItemStack listening = WalkieTalkieManager.findListeningWalkie(mc.player);
        WalkieTalkieManager.WalkieTalkieState state = WalkieTalkieManager.getState(mc.player, listening);
        int tick = mc.player.tickCount;
        RadioStation station = state.getCurrentStation();
        if (station == null || !state.isOn()) {
            AudioManager.stopAll();
            return;
        }
        if (station.getType() == RadioStation.StationType.EMERGENCY_BROADCAST) {
            AudioManager.stopAllRadioSounds(); // stop music only; overlay plays static
            return;
        }
        // Send packets + drive music once per tick from the single active listening walkie.
        if (mc.getConnection() != null) {
            if (tick % 2 == 0) {
                uk.co.extraspecialstudio.dead_air.net.DeadAirNet.CHANNEL.sendToServer(
                    new uk.co.extraspecialstudio.dead_air.net.RadioSignalRequestPacket(station.getId()));
            }
            if (!uk.co.extraspecialstudio.dead_air.tower.KnownTowersClientCache.hasCachedTowers() && tick % 10 == 0) {
                uk.co.extraspecialstudio.dead_air.net.DeadAirNet.CHANNEL.sendToServer(
                    new uk.co.extraspecialstudio.dead_air.net.RequestTowerSyncPacket());
            }
        }
        try {
            uk.co.extraspecialstudio.dead_air.music.MusicStationManager.advanceBackgroundPlaylists(
                station.getId(), System.currentTimeMillis(), mc.player);
            AudioManager.update(mc, station, mc.player.position());
        } catch (NoClassDefFoundError | Exception ignored) {
        }
    }
    
    private static final String RADIO_SOUND_CLASS = "uk.co.extraspecialstudio.dead_air.audio.RadioSoundInstance";

    private static boolean isOurRadioSound(Object sound) {
        return sound != null && sound.getClass().getName().equals(RADIO_SOUND_CLASS);
    }

    /**
     * Block vanilla background music so only Dead Air radio plays.
     * Allow our RadioSoundInstance through; cancel other MUSIC category sounds.
     * Uses class name check to avoid loading RadioSoundInstance (prevents NoClassDefFoundError if classloader fails).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlaySound(PlaySoundEvent event) {
        try {
            if (event.getSound() == null && isOurRadioSound(event.getOriginalSound())) {
                event.setSound(event.getOriginalSound());
                return;
            }
            if (isOurRadioSound(event.getSound())) {
                return;
            }
            if (event.getSound() != null && event.getSound().getSource() == SoundSource.MUSIC) {
                event.setSound(null);
            }
        } catch (NoClassDefFoundError | Exception ignored) {
            // Audio classes missing or loader issue: don't crash, just skip our logic
        }
    }

    /** When a streaming radio sound starts, track its channel so we can update volume on the audio thread. */
    @SubscribeEvent
    public static void onPlayStreamingSource(PlayStreamingSourceEvent event) {
        try {
            if (!isOurRadioSound(event.getSound())) return;
            Class<?> tracker = Class.forName("uk.co.extraspecialstudio.dead_air.audio.RadioChannelTracker");
            tracker.getMethod("register", event.getChannel().getClass(), event.getSound().getClass()).invoke(null, event.getChannel(), event.getSound());
        } catch (NoClassDefFoundError | Exception ignored) {
            // Skip if audio classes not loadable
        }
    }

    /* Walkie HUD overlay is registered via RegisterGuiOverlaysEvent in Dead_air.ClientModEvents (reliable; not tied to CROSSHAIR/HOTBAR). */

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.TUNE_WALKIE);
    }
    
    /** Clear tower cache when the client loads a (new) level so we don't use stale towers from a previous world (phantom signal). */
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel().isClientSide()) {
            if (AudioManager.LOG_PLAYBACK) {
                uk.co.extraspecialstudio.dead_air.Dead_air.LOGGER.info("[radio] client level load");
            }
            uk.co.extraspecialstudio.dead_air.tower.KnownTowersClientCache.clear();
            uk.co.extraspecialstudio.dead_air.music.SoundDurationChecker.clearCache();
            try {
                uk.co.extraspecialstudio.dead_air.client.RadioLiveDisplay.resetAll();
            } catch (NoClassDefFoundError | Exception ignored) {
            }
        }
    }

    /**
     * Before the old dimension's level is torn down, snapshot radio playback so music can resume
     * at the same track/position after the sound engine wipes streaming sources.
     */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            if (AudioManager.LOG_PLAYBACK) {
                uk.co.extraspecialstudio.dead_air.Dead_air.LOGGER.info("[radio] client level unload");
            }
            try {
                AudioManager.prepareInterruptResume();
            } catch (NoClassDefFoundError | Exception ignored) {
            }
        }
    }

    @SubscribeEvent
    public static void onClientDisconnecting(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        DISCONNECTING = true;
        try {
            AudioManager.stopAll();
            AudioManager.clearResumeSnapshots();
        } catch (NoClassDefFoundError | Exception ignored) {
            // Audio classes may be missing in some classloader contexts
        }
        uk.co.extraspecialstudio.dead_air.tower.KnownTowersClientCache.clear();
        if (net.minecraft.client.Minecraft.getInstance().player != null) {
            uk.co.extraspecialstudio.dead_air.station.StationUnlockManager.clearForPlayer(net.minecraft.client.Minecraft.getInstance().player.getUUID());
        }
    }
    
    @SubscribeEvent
    public static void onClientTickKeybind(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        
        // Check for keybind press (N = open our GUI; right-click walkie also opens it)
        while (KeyBindings.TUNE_WALKIE.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && WalkieTalkieManager.isHoldingWalkieTalkie(mc.player)) {
                // Prefer off-hand when it holds a walkie and main does not — Pip-Boys equip there.
                net.minecraft.world.InteractionHand hand;
                boolean mainWalkie = WalkieTalkieManager.isWalkieTalkieItem(mc.player.getMainHandItem());
                boolean offWalkie = WalkieTalkieManager.isWalkieTalkieItem(mc.player.getOffhandItem());
                if (offWalkie && !mainWalkie) {
                    hand = net.minecraft.world.InteractionHand.OFF_HAND;
                } else if (mainWalkie) {
                    hand = net.minecraft.world.InteractionHand.MAIN_HAND;
                } else {
                    hand = net.minecraft.world.InteractionHand.OFF_HAND;
                }
                net.minecraft.world.item.ItemStack held = mc.player.getItemInHand(hand);
                if (held.getItem() instanceof uk.co.extraspecialstudio.dead_air.item.WalkieItem walkie) {
                    walkie.openTuningScreen(hand);
                } else if (uk.co.extraspecialstudio.dead_air.api.DeadAirAPI.tryOpenCompanionWalkieGui(hand, held)) {
                    // Companion radio (e.g. Pip-Boy) opened its own screen.
                } else {
                    mc.setScreen(new WalkieTalkieTuningScreen(hand));
                }
            }
        }
    }

    // Walkie right-click GUI: handled by WalkieItem.use() (no longer cancel — that blocked our own use()).
}
