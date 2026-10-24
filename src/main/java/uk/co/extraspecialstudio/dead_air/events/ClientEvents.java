package uk.co.extraspecialstudio.dead_air.events;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.event.sound.PlayStreamingSourceEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.audio.AudioManager;
import uk.co.extraspecialstudio.dead_air.client.KeyBindings;
import uk.co.extraspecialstudio.dead_air.client.RadioTuningScreen;
import uk.co.extraspecialstudio.dead_air.client.SurvivorPartyHud;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.RadioManager;

/**
 * Client-side event handlers. Registered via @EventBusSubscriber on FORGE bus so tick handler receives ClientTickEvent.
 */
@Mod.EventBusSubscriber(modid = Dead_air.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
@SuppressWarnings("null")
public class ClientEvents {

    // Set during world exit / disconnect to avoid any client-side work during teardown.
    private static volatile boolean DISCONNECTING = false;

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != 0 || event.getAction() != 1) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        // Wheel LMB/RMB/scroll swallow lives in ESC EscActionWheelClient.
        if (uk.co.extraspecialstudio.extraspecial.esc.ui.EscActionWheel.anyOpen()) return;
        int guiW = mc.getWindow().getGuiScaledWidth();
        int guiH = mc.getWindow().getGuiScaledHeight();
        int screenW = mc.getWindow().getScreenWidth();
        int screenH = mc.getWindow().getScreenHeight();
        if (screenW <= 0 || screenH <= 0) return;
        double mx = mc.mouseHandler.xpos() * guiW / (double) screenW;
        double my = mc.mouseHandler.ypos() * guiH / (double) screenH;
        if (SurvivorPartyHud.click(mx, my)) {
            event.setCanceled(true);
        }
    }

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
        SurvivorPartyHud.clientTick();
        uk.co.extraspecialstudio.dead_air.client.EbHuntClient.clientTick();
        // stopAnyVanillaMusic removed - empty; MusicManagerMixin disables vanilla music

        // Sole music driver (was overlay-only; tick keeps inventory radio alive when HUD hidden).
        RadioManager.ensureSingleActiveListening(mc.player);
        RadioManager.refreshLinkedStation(mc.player);
        if (!RadioManager.shouldPlayRadio(mc.player)) {
            AudioManager.stopAll();
            return;
        }

        ItemStack listening = RadioManager.findListeningRadio(mc.player);
        RadioManager.RadioState state = RadioManager.getState(mc.player, listening);
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
        // Send packets + drive music once per tick from the single active listening radio.
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

    /* Radio HUD overlay is registered via RegisterGuiOverlaysEvent in Dead_air.ClientModEvents (reliable; not tied to CROSSHAIR/HOTBAR). */

    // KeyMappings register in Dead_air.ClientModEvents (MOD bus) — not here.

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
    public static void onClientLoggingIn(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        java.util.List<String> packs = uk.co.extraspecialstudio.dead_air.music.ExpansionPacks.installedKnownPacks();
        if (packs.isEmpty()) return;
        try {
            uk.co.extraspecialstudio.dead_air.net.DeadAirNet.CHANNEL.sendToServer(
                new uk.co.extraspecialstudio.dead_air.net.ExpansionPacksAnnouncePacket(packs));
        } catch (Exception e) {
            Dead_air.LOGGER.debug("[Dead Air] Could not announce expansion packs: {}", e.toString());
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
        uk.co.extraspecialstudio.dead_air.Config.clearServerValues();
        SurvivorPartyHud.clear();
        uk.co.extraspecialstudio.dead_air.client.EbHuntClient.clear();
        if (net.minecraft.client.Minecraft.getInstance().player != null) {
            uk.co.extraspecialstudio.dead_air.station.StationUnlockManager.clearForPlayer(net.minecraft.client.Minecraft.getInstance().player.getUUID());
        }
    }
    
    @SubscribeEvent
    public static void onClientTickKeybind(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        
        // Check for keybind press (N = open our GUI; right-click radio also opens it)
        while (KeyBindings.TUNE_RADIO.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && RadioManager.isHoldingRadio(mc.player)) {
                // Prefer off-hand when it holds a radio and main does not — Pip-Boys equip there.
                net.minecraft.world.InteractionHand hand;
                boolean mainRadio = RadioManager.isRadioItem(mc.player.getMainHandItem());
                boolean offRadio = RadioManager.isRadioItem(mc.player.getOffhandItem());
                if (offRadio && !mainRadio) {
                    hand = net.minecraft.world.InteractionHand.OFF_HAND;
                } else if (mainRadio) {
                    hand = net.minecraft.world.InteractionHand.MAIN_HAND;
                } else {
                    hand = net.minecraft.world.InteractionHand.OFF_HAND;
                }
                net.minecraft.world.item.ItemStack held = mc.player.getItemInHand(hand);
                if (held.getItem() instanceof uk.co.extraspecialstudio.dead_air.item.RadioItem radio) {
                    radio.openTuningScreen(hand);
                } else if (uk.co.extraspecialstudio.dead_air.api.DeadAirAPI.tryOpenCompanionRadioGui(hand, held)) {
                    // Companion radio (e.g. Pip-Boy) opened its own screen.
                } else {
                    mc.setScreen(new RadioTuningScreen(hand));
                }
            }
        }
    }

    // radio right-click GUI: handled by RadioItem.use() (no longer cancel — that blocked our own use()).
}
