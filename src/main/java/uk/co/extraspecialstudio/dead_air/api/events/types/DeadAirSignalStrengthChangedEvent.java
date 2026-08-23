package uk.co.extraspecialstudio.dead_air.api.events.types;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

/**
 * Fired when signal strength for a station changes meaningfully on the client.
 */
public class DeadAirSignalStrengthChangedEvent extends Event {
    private final ResourceLocation stationId;
    private final float oldStrength;
    private final float newStrength;

    public DeadAirSignalStrengthChangedEvent(ResourceLocation stationId, float oldStrength, float newStrength) {
        this.stationId = stationId;
        this.oldStrength = oldStrength;
        this.newStrength = newStrength;
    }

    public ResourceLocation getStationId() {
        return stationId;
    }

    public float getOldStrength() {
        return oldStrength;
    }

    public float getNewStrength() {
        return newStrength;
    }
}

