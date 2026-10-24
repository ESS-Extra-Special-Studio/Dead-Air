package uk.co.extraspecialstudio.dead_air.broadcast;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * One kind of emergency broadcast. Register through
 * {@link uk.co.extraspecialstudio.dead_air.api.DeadAirAPI#registerBroadcastEvent(BroadcastEvent)}.
 * All methods run on the server thread.
 */
public interface BroadcastEvent {
    /**
     * STORY — Listener / relay progression (and soft expansion hooks).<br>
     * SURVIVOR — distress, convoy, missing recruit, crash sites.<br>
     * TRADERS — temporary auction / scrap traders.<br>
     * DEFENSE — base-under-attack and similar base defense events.
     */
    enum Category { STORY, SURVIVOR, TRADERS, DEFENSE }

    ResourceLocation id();

    Category category();

    /** Whether this event may fire for this player right now. */
    boolean canTrigger(ServerPlayer player, BroadcastProgress.PlayerProgress progress);

    /**
     * Run side effects (spawn sites, advance stages) and return the transmission lines in plain text.
     * Players without a Signal Decoder receive a scrambled copy; digits stay readable.
     * Return an empty list to cancel this broadcast.
     */
    List<String> start(ServerPlayer player, BroadcastProgress.PlayerProgress progress);

    /** Higher fires first when several events of the same category can trigger. */
    default int priority() {
        return 0;
    }

    /** Header shown above the transmission. */
    default String title() {
        return switch (category()) {
            case STORY -> "EMERGENCY BROADCAST";
            case DEFENSE -> "BASE ALERT";
            case TRADERS -> "DEAD CHANNEL";
            case SURVIVOR -> "DISTRESS CALL";
        };
    }
}
