package uk.co.extraspecialstudio.dead_air.broadcast;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;

import java.util.List;

/** Dead Air's built-in story broadcasts leading to The Listener. */
public final class StoryBroadcasts {
    public static final ResourceLocation RELAY_TRANSMISSION = ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "relay_transmission");
    public static final ResourceLocation IT_HEARS_YOU = ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "it_hears_you");
    public static final ResourceLocation BREAKER_HINT = ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "breaker_hint");

    private static final String[] RELAY_TEMPLATES = {"broken_relay/small", "broken_relay/medium", "broken_relay/large"};

    public static void registerDefaults() {
        BroadcastEventRegistry.register(new RelayTransmission());
        BroadcastEventRegistry.register(new ItHearsYou());
        BroadcastEventRegistry.register(new BreakerHint());
    }

    /** Points the player at the next Broken Relay (or repeats the current one). */
    static final class RelayTransmission implements BroadcastEvent {
        @Override public ResourceLocation id() { return RELAY_TRANSMISSION; }
        @Override public Category category() { return Category.STORY; }

        @Override
        public boolean canTrigger(ServerPlayer player, BroadcastProgress.PlayerProgress p) {
            return p.stage <= StoryStage.RELAYS && p.relaysFound.size() < Config.storyRelaysRequired;
        }

        @Override
        public List<String> start(ServerPlayer player, BroadcastProgress.PlayerProgress p) {
            p.stage = StoryStage.RELAYS;
            int index = p.relaysFound.size();
            if (p.targetRelay == null) {
                String template = RELAY_TEMPLATES[Math.min(index, RELAY_TEMPLATES.length - 1)];
                PendingSites.Site site = PendingSites.create(player, PendingSites.KIND_RELAY, template,
                    Config.relayMinDistance, Config.relayMaxDistance);
                if (site == null) return List.of();
                p.targetRelay = site.center;
            }
            return relayLines(index, p.targetRelay);
        }
    }

    public static List<String> relayLines(int index, BlockPos target) {
        String coords = "X " + target.getX() + "  Z " + target.getZ();
        return switch (index) {
            case 0 -> List.of(
                "THIS IS RELAY STATION KILO-SEVEN. THE NETWORK IS DOWN.",
                "SOMETHING IS ANSWERING ON OUR FREQUENCIES. IT COPIES OUR VOICES.",
                "OUR LAST RELAY LOG IS AT " + coords + ". FIND IT.");
            case 1 -> List.of(
                "KILO-SEVEN AGAIN. WE LOST THE SECOND MAST LAST NIGHT.",
                "THE BELLS RANG BEFORE THE STATIC CAME. DO NOT RING BACK.",
                "SECOND RELAY: " + coords + ".");
            default -> List.of(
                "IF YOU ARE HEARING THIS, WE ARE GONE.",
                "THE LAST RELAY HOLDS THE FREQUENCY IT LISTENS ON.",
                "FINAL RELAY: " + coords + ". DO NOT TRANSMIT NEAR IT AFTER DARK.");
        };
    }

    /** Final story broadcast: reveals the hidden frequency. */
    static final class ItHearsYou implements BroadcastEvent {
        @Override public ResourceLocation id() { return IT_HEARS_YOU; }
        @Override public Category category() { return Category.STORY; }
        @Override public int priority() { return 10; }
        @Override public String title() { return "IT HEARS YOU"; }

        @Override
        public boolean canTrigger(ServerPlayer player, BroadcastProgress.PlayerProgress p) {
            // One-shot: once FINAL_BROADCAST is set, stop winning every night over side events.
            return p.stage == StoryStage.RELAYS && p.relaysFound.size() >= Config.storyRelaysRequired;
        }

        @Override
        public List<String> start(ServerPlayer player, BroadcastProgress.PlayerProgress p) {
            p.stage = StoryStage.FINAL_BROADCAST;
            BroadcastProgress data = BroadcastProgress.get(player.server);
            float freq = data.getHiddenFrequency(player.server.overworld().getRandom());
            return List.of(
                "ALL RELAYS ARE DARK.",
                "IT HEARS YOU.",
                "IT LISTENS ON " + String.format("%.1f", freq) + " MHZ.",
                "TRANSMIT ON IT, AT NIGHT, NEAR A RELAY, AND IT WILL ANSWER.");
        }
    }

    /** After the first encounter: tells the player about the Signal Breaker. */
    static final class BreakerHint implements BroadcastEvent {
        @Override public ResourceLocation id() { return BREAKER_HINT; }
        @Override public Category category() { return Category.STORY; }

        @Override
        public boolean canTrigger(ServerPlayer player, BroadcastProgress.PlayerProgress p) {
            return p.stage == StoryStage.FIRST_SURVIVED && !p.breakerHinted;
        }

        @Override
        public List<String> start(ServerPlayer player, BroadcastProgress.PlayerProgress p) {
            p.breakerHinted = true;
            return List.of(
                "YOU WALKED AWAY. NOBODY ELSE HAS.",
                "NOTHING WE MAKE CAN HURT IT. ONLY A BROKEN SIGNAL CAN.",
                "BUILD A SIGNAL BREAKER FROM WHAT THE OTHERS LEFT BEHIND.");
        }
    }

    private StoryBroadcasts() {}
}
