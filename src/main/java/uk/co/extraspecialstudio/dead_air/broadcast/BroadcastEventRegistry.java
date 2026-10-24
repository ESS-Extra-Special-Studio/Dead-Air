package uk.co.extraspecialstudio.dead_air.broadcast;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import uk.co.extraspecialstudio.dead_air.Dead_air;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All known broadcast events. Dead Air registers its own on common setup; addons use DeadAirAPI. */
public final class BroadcastEventRegistry {
    private static final Map<ResourceLocation, BroadcastEvent> EVENTS = new LinkedHashMap<>();

    public static synchronized void register(BroadcastEvent event) {
        if (event == null || event.id() == null) return;
        if (EVENTS.put(event.id(), event) != null) {
            Dead_air.LOGGER.info("[Dead Air] Broadcast event {} replaced", event.id());
        }
    }

    public static synchronized BroadcastEvent get(ResourceLocation id) {
        return EVENTS.get(id);
    }

    public static synchronized Collection<BroadcastEvent> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(EVENTS.values()));
    }

    /** Events of this category that can fire for the player, highest priority first. */
    public static synchronized List<BroadcastEvent> eligible(BroadcastEvent.Category category, ServerPlayer player,
                                                             BroadcastProgress.PlayerProgress progress) {
        List<BroadcastEvent> out = new ArrayList<>();
        for (BroadcastEvent e : EVENTS.values()) {
            if (e.category() != category) continue;
            try {
                if (e.canTrigger(player, progress)) out.add(e);
            } catch (Exception ex) {
                Dead_air.LOGGER.warn("[Dead Air] Broadcast event {} canTrigger failed", e.id(), ex);
            }
        }
        out.sort(Comparator.comparingInt(BroadcastEvent::priority).reversed());
        return out;
    }

    private BroadcastEventRegistry() {}
}
