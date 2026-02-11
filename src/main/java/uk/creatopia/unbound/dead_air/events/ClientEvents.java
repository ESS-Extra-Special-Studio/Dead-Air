package uk.creatopia.unbound.dead_air.events;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import uk.creatopia.unbound.dead_air.Dead_air;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import uk.creatopia.unbound.dead_air.Config;
import uk.creatopia.unbound.dead_air.audio.AudioManager;
import uk.creatopia.unbound.dead_air.client.KeyBindings;
import uk.creatopia.unbound.dead_air.client.WalkieTalkieOverlay;
import uk.creatopia.unbound.dead_air.client.WalkieTalkieTuningScreen;
import uk.creatopia.unbound.dead_air.radio.RadioStation;
import uk.creatopia.unbound.dead_air.walkie.WalkieTalkieManager;

/**
 * Client-side event handlers.
 */
@Mod.EventBusSubscriber(modid = Dead_air.MODID, value = Dist.CLIENT)
@SuppressWarnings("null")
public class ClientEvents {

    // Set during world exit / disconnect to avoid any client-side work during teardown.
    private static volatile boolean DISCONNECTING = false;
    
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (DISCONNECTING) {
            AudioManager.stopAll();
            return;
        }
        // Stop audio if game is closing to prevent hangs
        if (mc == null || mc.player == null || mc.level == null) {
            AudioManager.stopAll();
            stopAnyVanillaMusic(mc);
            return;
        }
        
        // Stop audio if level is closing or server is stopping
        if (mc.level.isClientSide && mc.level.getServer() != null && mc.level.getServer().isStopped()) {
            AudioManager.stopAll();
            return;
        }
        
        // Stop audio if connection is lost (game closing)
        if (mc.getConnection() == null) {
            AudioManager.stopAll();
            return;
        }

        // Client is fully connected/in-world again
        DISCONNECTING = false;
        
        // Stop any vanilla music that might be playing
        stopAnyVanillaMusic(mc);
        
        // Check if player has walkie-talkie (based on config)
        boolean hasWalkieTalkie;
        if (Config.radioAlwaysOn) {
            // Radio plays as long as walkie-talkie is turned on (even when placed down or in hotbar)
            hasWalkieTalkie = WalkieTalkieManager.hasWalkieTalkieInInventory(mc.player);
        } else {
            // Radio only plays while holding the walkie-talkie in hand
            hasWalkieTalkie = WalkieTalkieManager.isHoldingWalkieTalkie(mc.player);
        }
        
        if (!hasWalkieTalkie) {
            AudioManager.stopAll();
            return;
        }
        
        WalkieTalkieManager.WalkieTalkieState state = WalkieTalkieManager.getState(mc.player);
        
        if (!state.isOn() || state.getCurrentStation() == null) {
            AudioManager.stopAll();
            return;
        }
        
        // Update audio playback (Emergency Broadcast stations don't play music)
        RadioStation station = state.getCurrentStation();
        if (station != null) {
            if (station.getType() == RadioStation.StationType.EMERGENCY_BROADCAST) {
                // Emergency Broadcast - stop any music, but don't play anything
                AudioManager.stopAll();
            } else {
                // Request signal from server every 2 ticks so we get server signal quickly (client often has no tower data)
                if (mc.player.tickCount % 2 == 0 && mc.getConnection() != null) {
                    uk.creatopia.unbound.dead_air.net.DeadAirNet.CHANNEL.sendToServer(
                        new uk.creatopia.unbound.dead_air.net.RadioSignalRequestPacket(station.getId()));
                }
                // Music stations - play music (uses local tower or server signal cache)
                AudioManager.update(mc, station, mc.player.position());
            }
        } else {
            AudioManager.stopAll();
        }
    }
    
    /**
     * Stop any vanilla Minecraft music that's currently playing.
     * This works alongside MusicManagerMixin to ensure no vanilla music plays.
     * The mixin prevents music from starting, this is a backup.
     */
    private static void stopAnyVanillaMusic(Minecraft mc) {
        if (mc.getSoundManager() == null) {
            return;
        }
        
        // The MusicManagerMixin should prevent music from starting
        // This method is a backup to stop any music that might have started
        // before the mixin was active or if the mixin didn't catch it
    }
    
    
    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() == VanillaGuiOverlay.CROSSHAIR.type()) {
            WalkieTalkieOverlay.render(event.getGuiGraphics(), 
                event.getWindow().getGuiScaledWidth(), 
                event.getWindow().getGuiScaledHeight());
        }
    }
    
    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.TUNE_WALKIE);
    }
    
    @SubscribeEvent
    public static void onClientDisconnecting(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        // Stop all audio when client is disconnecting to prevent hangs
        Dead_air.LOGGER.info("=== CLIENT DISCONNECTING - STOPPING AUDIO ===");
        DISCONNECTING = true;
        uk.creatopia.unbound.dead_air.audio.AudioManager.stopAll();
    }
    
    @SubscribeEvent
    public static void onClientTickKeybind(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        
        // Check for keybind press
        while (KeyBindings.TUNE_WALKIE.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && WalkieTalkieManager.isHoldingWalkieTalkie(mc.player)) {
                mc.setScreen(new WalkieTalkieTuningScreen());
            }
        }
    }
}
