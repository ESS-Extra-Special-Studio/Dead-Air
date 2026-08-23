package uk.co.extraspecialstudio.dead_air.api.events;

import net.neoforged.neoforge.common.NeoForge;
import uk.co.extraspecialstudio.dead_air.api.events.types.DeadAirAirdropRequestedEvent;
import uk.co.extraspecialstudio.dead_air.api.events.types.DeadAirBroadcastStartedEvent;
import uk.co.extraspecialstudio.dead_air.api.events.types.DeadAirEmergencySignalEvent;
import uk.co.extraspecialstudio.dead_air.api.events.types.DeadAirSignalStrengthChangedEvent;

/**
 * Public event entrypoints for Dead Air. Dead Air itself uses these to post events, and other
 * mods can subscribe via {@code NeoForge.EVENT_BUS}.
 */
public final class DeadAirEvents {

    private DeadAirEvents() {}

    public static boolean post(DeadAirBroadcastStartedEvent event) {
        NeoForge.EVENT_BUS.post(event);
        return false;
    }

    public static boolean post(DeadAirSignalStrengthChangedEvent event) {
        NeoForge.EVENT_BUS.post(event);
        return false;
    }

    public static boolean post(DeadAirEmergencySignalEvent event) {
        NeoForge.EVENT_BUS.post(event);
        return false;
    }

    public static boolean post(DeadAirAirdropRequestedEvent event) {
        NeoForge.EVENT_BUS.post(event);
        return false;
    }
}

