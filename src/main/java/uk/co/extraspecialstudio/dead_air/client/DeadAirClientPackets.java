package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import uk.co.extraspecialstudio.dead_air.net.BroadcastMessagePacket;
import uk.co.extraspecialstudio.dead_air.net.EncounterStatePacket;
import uk.co.extraspecialstudio.dead_air.net.ForceEmergencyTunePacket;
import uk.co.extraspecialstudio.dead_air.net.SurvivorScreenPacket;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;
import uk.co.extraspecialstudio.dead_air.walkie.WalkieTalkieManager;

/** Client handlers for Dead Air 3.0 packets; only loaded on the physical client. */
public final class DeadAirClientPackets {

    public static void onBroadcast(BroadcastMessagePacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        BroadcastHud.show(msg.title, msg.lines, msg.decoded, msg.story);
        ChatFormatting head = msg.story ? ChatFormatting.RED : ChatFormatting.GOLD;
        mc.player.sendSystemMessage(Component.literal("[" + msg.title + "]").withStyle(head, ChatFormatting.BOLD));
        for (String line : msg.lines) {
            mc.player.sendSystemMessage(Component.literal(line)
                .withStyle(msg.decoded ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
        }
        if (!msg.decoded) {
            mc.player.sendSystemMessage(Component.translatable("message.dead_air.broadcast.scrambled")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    public static void onForceTune(int mode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        switch (mode) {
            case ForceEmergencyTunePacket.TUNE_EMERGENCY -> {
                RadioStation emergency = StationRegistry.getStation(StationRegistry.EMERGENCY_BROADCAST_ID);
                ItemStack walkie = WalkieTalkieManager.getPrimaryWalkieStack(mc.player);
                if (emergency != null && walkie != null && !walkie.isEmpty()) {
                    WalkieTalkieManager.tuneToStation(mc.player, emergency, walkie);
                }
            }
            case ForceEmergencyTunePacket.JAM_ON -> ListenerClientEffects.setJammed(true);
            case ForceEmergencyTunePacket.JAM_OFF -> ListenerClientEffects.setJammed(false);
            default -> {}
        }
    }

    public static void onEncounterState(EncounterStatePacket msg) {
        ListenerClientEffects.apply(msg.active, msg.silenceTicks, msg.screamFlashTicks);
    }

    public static void onSurvivorScreen(SurvivorScreenPacket msg) {
        Minecraft.getInstance().setScreen(new SurvivorDialogueScreen(msg));
    }

    public static void onEbSignalPrompt(String siteKey, String siteKind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen instanceof EbTunePromptScreen) return;
        mc.setScreen(new EbTunePromptScreen(siteKey, siteKind));
    }

    private DeadAirClientPackets() {}
}
