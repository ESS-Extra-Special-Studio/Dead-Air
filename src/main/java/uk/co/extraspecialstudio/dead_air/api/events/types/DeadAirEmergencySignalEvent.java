package uk.co.extraspecialstudio.dead_air.api.events.types;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.Event;

/**
 * Fired when an emergency signal is broadcast (server-side or client-side depending on implementation).
 * This is an API event scaffold; current Dead Air builds may not post it yet.
 */
public class DeadAirEmergencySignalEvent extends Event {
    private final ResourceLocation sourceId;
    private final String eventType;

    public DeadAirEmergencySignalEvent(ResourceLocation sourceId, String eventType) {
        this.sourceId = sourceId;
        this.eventType = eventType;
    }

    public ResourceLocation getSourceId() {
        return sourceId;
    }

    public String getEventType() {
        return eventType;
    }
}

