package uk.co.extraspecialstudio.dead_air.api.events;

import net.minecraftforge.common.MinecraftForge;
import uk.co.extraspecialstudio.dead_air.api.events.types.DeadAirAirdropRequestedEvent;
import uk.co.extraspecialstudio.dead_air.api.events.types.DeadAirBroadcastStartedEvent;
import uk.co.extraspecialstudio.dead_air.api.events.types.DeadAirEmergencySignalEvent;
import uk.co.extraspecialstudio.dead_air.api.events.types.DeadAirSignalStrengthChangedEvent;

/**
 * Public event entrypoints for Dead Air. Dead Air itself uses these to post events, and other
 * mods can subscribe via {@code MinecraftForge.EVENT_BUS}.
 */
public final class DeadAirEvents {

    private DeadAirEvents() {}

    public static boolean post(DeadAirBroadcastStartedEvent event) {
        return MinecraftForge.EVENT_BUS.post(event);
    }

    public static boolean post(DeadAirSignalStrengthChangedEvent event) {
        return MinecraftForge.EVENT_BUS.post(event);
    }

    public static boolean post(DeadAirEmergencySignalEvent event) {
        return MinecraftForge.EVENT_BUS.post(event);
    }

    public static boolean post(DeadAirAirdropRequestedEvent event) {
        return MinecraftForge.EVENT_BUS.post(event);
    }
}

