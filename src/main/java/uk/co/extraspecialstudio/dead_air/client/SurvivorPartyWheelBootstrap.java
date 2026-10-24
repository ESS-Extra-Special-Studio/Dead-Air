package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.network.chat.Component;
import uk.co.extraspecialstudio.dead_air.net.DeadAirNet;
import uk.co.extraspecialstudio.dead_air.net.SurvivorPartyOrderPacket;
import uk.co.extraspecialstudio.dead_air.survivor.SurvivorParty;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscActionWheel;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscActionWheelOption;

import java.util.List;

/**
 * Wires Dead Air squad orders into ESC's reusable {@link EscActionWheel}.
 * Wheel chrome / tick / overlay live in ESC — this mod only supplies options + confirm.
 */
public final class SurvivorPartyWheelBootstrap {
    public static final String WHEEL_ID = "dead_air.party_orders";

    private static final List<EscActionWheelOption> OPTIONS = List.of(
        new EscActionWheelOption("hud.dead_air.party.follow", "hud.dead_air.party.blurb.follow"),
        new EscActionWheelOption("hud.dead_air.party.defend", "hud.dead_air.party.blurb.defend"),
        new EscActionWheelOption("hud.dead_air.party.attack", "hud.dead_air.party.blurb.attack"),
        new EscActionWheelOption("hud.dead_air.party.retreat", "hud.dead_air.party.blurb.retreat"),
        new EscActionWheelOption("hud.dead_air.party.regroup", "hud.dead_air.party.blurb.regroup")
    );

    private SurvivorPartyWheelBootstrap() {}

    public static void register() {
        EscActionWheel.register(WHEEL_ID, KeyBindings.PARTY_ORDERS)
            .options(OPTIONS)
            .canOpen(SurvivorPartyHud::hasLoadedMembers)
            .hubSubtitle(() -> Component.translatable(
                "hud.dead_air.party.wheel_squad", SurvivorPartyHud.loadedMemberCount()))
            .idleHint(() -> Component.translatable("hud.dead_air.party.wheel_hint"))
            .cancelLabel(() -> Component.translatable("hud.dead_air.party.wheel_cancel"))
            .actionBarOnConfirm(false)
            .doubleTapRepeat(true)
            .releaseMouse(true)
            .onConfirm(SurvivorPartyWheelBootstrap::confirmOrder);
    }

    private static void confirmOrder(int index) {
        if (index < SurvivorParty.ORDER_FOLLOW || index > SurvivorParty.ORDER_REGROUP) return;
        if (!SurvivorPartyHud.hasLoadedMembers()) return;
        DeadAirNet.CHANNEL.sendToServer(new SurvivorPartyOrderPacket(index));
        SurvivorPartyHud.notifyOrderApplied(index);
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            int n = SurvivorPartyHud.loadedMemberCount();
            mc.player.displayClientMessage(
                Component.translatable("hud.dead_air.party.order_sent",
                    OPTIONS.get(index).label(), n), true);
        }
    }

    public static boolean isOpen() {
        EscActionWheel w = EscActionWheel.get(WHEEL_ID);
        return w != null && w.isOpen();
    }
}
