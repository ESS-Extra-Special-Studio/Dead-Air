package uk.co.extraspecialstudio.dead_air.api.events.types;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * Fired on the server when a player survives the first encounter with The Listener.
 * Boss Mode is unlocked for this player from now on.
 */
public class ListenerFirstEncounterSurvivedEvent extends Event {
    private final ServerPlayer player;

    public ListenerFirstEncounterSurvivedEvent(ServerPlayer player) {
        this.player = player;
    }

    public ServerPlayer getPlayer() {
        return player;
    }
}
