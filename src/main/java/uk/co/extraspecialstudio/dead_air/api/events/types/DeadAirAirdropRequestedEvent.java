package uk.co.extraspecialstudio.dead_air.api.events.types;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

/**
 * Fired when a player/tower requests an airdrop via Dead Air.
 * This is an API event scaffold; current Dead Air builds may not post it yet.
 */
public class DeadAirAirdropRequestedEvent extends Event {
    private final ResourceLocation stationId;
    private final BlockPos towerPos;

    public DeadAirAirdropRequestedEvent(ResourceLocation stationId, BlockPos towerPos) {
        this.stationId = stationId;
        this.towerPos = towerPos;
    }

    public ResourceLocation getStationId() {
        return stationId;
    }

    public BlockPos getTowerPos() {
        return towerPos;
    }
}

