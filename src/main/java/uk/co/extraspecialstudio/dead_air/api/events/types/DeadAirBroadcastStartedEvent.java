package uk.co.extraspecialstudio.dead_air.api.events.types;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.Event;

/**
 * Fired when a broadcast starts playing for a station (client-side audio start).
 */
public class DeadAirBroadcastStartedEvent extends Event {
    private final ResourceLocation stationId;
    private final ResourceLocation trackId;
    private final float signalStrength;
    private final Vec3 listenerPos;

    public DeadAirBroadcastStartedEvent(ResourceLocation stationId, ResourceLocation trackId, float signalStrength, Vec3 listenerPos) {
        this.stationId = stationId;
        this.trackId = trackId;
        this.signalStrength = signalStrength;
        this.listenerPos = listenerPos;
    }

    public ResourceLocation getStationId() {
        return stationId;
    }

    public ResourceLocation getTrackId() {
        return trackId;
    }

    public float getSignalStrength() {
        return signalStrength;
    }

    public Vec3 getListenerPos() {
        return listenerPos;
    }
}

