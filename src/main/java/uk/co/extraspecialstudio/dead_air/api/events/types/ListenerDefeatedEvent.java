package uk.co.extraspecialstudio.dead_air.api.events.types;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Fired on the server for each participant when The Listener is killed in the final encounter. */
public class ListenerDefeatedEvent extends Event {
    private final ServerPlayer player;
    private final boolean endlessWavesUnlocked;

    public ListenerDefeatedEvent(ServerPlayer player, boolean endlessWavesUnlocked) {
        this.player = player;
        this.endlessWavesUnlocked = endlessWavesUnlocked;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public boolean isEndlessWavesUnlocked() {
        return endlessWavesUnlocked;
    }
}
