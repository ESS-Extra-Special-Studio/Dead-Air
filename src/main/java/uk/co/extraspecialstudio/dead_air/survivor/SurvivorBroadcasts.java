package uk.co.extraspecialstudio.dead_air.survivor;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.broadcast.BroadcastEvent;
import uk.co.extraspecialstudio.dead_air.broadcast.BroadcastEventRegistry;
import uk.co.extraspecialstudio.dead_air.broadcast.BroadcastProgress;
import uk.co.extraspecialstudio.dead_air.broadcast.BroadcastScheduler;
import uk.co.extraspecialstudio.dead_air.broadcast.ExtraBroadcasts;
import uk.co.extraspecialstudio.dead_air.broadcast.PendingSites;

import java.util.List;

/** Distress Call: a survivor camp appears 150-350 blocks away with someone waiting to be recruited. */
public final class SurvivorBroadcasts {
    public static final ResourceLocation DISTRESS_CALL = ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "distress_call");

    public static void registerDefaults() {
        BroadcastEventRegistry.register(new DistressCall());
    }

    static final class DistressCall implements BroadcastEvent {
        private static final String[][] OPENERS = {
            {"IS ANYONE OUT THERE? WE HAVE ONE INJURED AND NO AMMO.", "WE CAN'T HOLD THE CAMP ANOTHER NIGHT."},
            {"THIS IS A CIVILIAN CAMP. THE FIRE IS ALL WE HAVE LEFT.", "PLEASE. ANYONE WITH A RADIO."},
            {"I FOUND A WORKING SET. I DON'T KNOW IF THIS REACHES ANYONE.", "I'M ALONE. I CAN STILL BE USEFUL."},
        };

        @Override public ResourceLocation id() { return DISTRESS_CALL; }
        @Override public Category category() { return Category.SURVIVOR; }

        @Override
        public boolean canTrigger(ServerPlayer player, BroadcastProgress.PlayerProgress progress) {
            if (!Config.survivorsEnabled) return false;
            if (!BroadcastScheduler.serverHasEbAudience(player.server)) return false;
            if (!BroadcastScheduler.canHearEmergency(player)) return false;
            if (ExtraBroadcasts.hasBlockingStory(player, progress)) return false;
            SurvivorRoster roster = SurvivorRoster.get(player.server);
            if (roster != null && roster.count(player.getUUID()) >= Config.maxSurvivorsPerPlayer) return false;
            for (PendingSites.Site s : PendingSites.forOwner(player.server, player.getUUID())) {
                if (PendingSites.KIND_CAMP.equals(s.kind) && !s.placed) return false;
            }
            return true;
        }

        @Override
        public List<String> start(ServerPlayer player, BroadcastProgress.PlayerProgress progress) {
            PendingSites.Site site = PendingSites.create(player, PendingSites.KIND_CAMP, "survivor_camp/camp_v2", 150, 350);
            if (site == null) return List.of();
            String[] opener = OPENERS[player.getRandom().nextInt(OPENERS.length)];
            return List.of(opener[0], opener[1],
                "OUR CAMP IS AT X " + site.center.getX() + "  Z " + site.center.getZ() + ".");
        }
    }

    private SurvivorBroadcasts() {}
}
