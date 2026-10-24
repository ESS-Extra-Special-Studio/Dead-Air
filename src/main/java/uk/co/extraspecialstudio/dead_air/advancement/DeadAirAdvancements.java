package uk.co.extraspecialstudio.dead_air.advancement;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.base.PlayerBaseManager;
import uk.co.extraspecialstudio.dead_air.broadcast.BroadcastProgress;
import uk.co.extraspecialstudio.dead_air.broadcast.BroadcastScheduler;
import uk.co.extraspecialstudio.dead_air.broadcast.StoryStage;
import uk.co.extraspecialstudio.dead_air.item.DeadAirItems;
import uk.co.extraspecialstudio.dead_air.radio.RadioManager;
import uk.co.extraspecialstudio.dead_air.survivor.SurvivorRoster;

/**
 * Code-granted Dead Air advancements. JSON under {@code data/dead_air/advancements/}
 * uses {@code impossible} criteria named {@code granted}; vanilla inventory criteria
 * for T3 / Signal Breaker do not need this helper — except login catch-up below.
 */
public final class DeadAirAdvancements {
    public static final ResourceLocation ROOT = id("root");

    public static final ResourceLocation RADIO_FIRST_TUNE = id("radio/first_tune");
    public static final ResourceLocation RADIO_THREE_STATIONS = id("radio/three_stations");
    public static final ResourceLocation RADIO_GET_T3 = id("radio/get_t3");
    public static final ResourceLocation RADIO_SIGNAL_BOOST = id("radio/signal_boost");
    public static final ResourceLocation RADIO_JUKEBOX = id("radio/jukebox");

    public static final ResourceLocation EB_FIRST = id("eb/first");
    public static final ResourceLocation EB_SURVIVOR_CALL = id("eb/survivor_call");
    public static final ResourceLocation EB_AUCTION_HEAR = id("eb/auction_hear");
    public static final ResourceLocation EB_AUCTION_TRADE = id("eb/auction_trade");
    public static final ResourceLocation EB_CONVOY = id("eb/convoy");
    public static final ResourceLocation EB_BASE_CLEAR = id("eb/base_clear");
    public static final ResourceLocation EB_PIRATE = id("eb/pirate");
    public static final ResourceLocation EB_PLANE = id("eb/plane");

    public static final ResourceLocation SURVIVOR_RECRUIT = id("survivor/recruit");
    public static final ResourceLocation SURVIVOR_THREE = id("survivor/three");
    public static final ResourceLocation SURVIVOR_FULL = id("survivor/full");
    public static final ResourceLocation SURVIVOR_FOLLOW = id("survivor/follow");
    public static final ResourceLocation SURVIVOR_SETTLE = id("survivor/settle");
    public static final ResourceLocation SURVIVOR_BASE = id("survivor/base");
    public static final ResourceLocation SURVIVOR_TRAIN = id("survivor/train");
    public static final ResourceLocation SURVIVOR_ARM = id("survivor/arm");
    public static final ResourceLocation SURVIVOR_DEFEND = id("survivor/defend");
    public static final ResourceLocation SURVIVOR_SUPPLIES = id("survivor/supplies");
    public static final ResourceLocation SURVIVOR_MISSING = id("survivor/missing");

    public static final ResourceLocation STORY_RELAY_BROADCAST = id("story/relay_broadcast");
    public static final ResourceLocation STORY_RELAY_FOUND = id("story/relay_found");
    public static final ResourceLocation STORY_ALL_RELAYS = id("story/all_relays");
    public static final ResourceLocation STORY_IT_HEARS_YOU = id("story/it_hears_you");
    public static final ResourceLocation STORY_SUMMON = id("story/summon");
    public static final ResourceLocation STORY_SURVIVE_TIMER = id("story/survive_timer");
    public static final ResourceLocation STORY_ANCHORS = id("story/anchors");
    public static final ResourceLocation STORY_BREAKER = id("story/breaker");
    public static final ResourceLocation STORY_FINAL = id("story/final");

    public static final ResourceLocation HIDDEN_WRONG_NUMBER = id("hidden/wrong_number");
    public static final ResourceLocation HIDDEN_RADIO_SILENCE = id("hidden/radio_silence");

    private static final String CRITERION = "granted";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, path);
    }

    /** Award a code-granted advancement if present and incomplete. */
    public static void award(ServerPlayer player, ResourceLocation advancementId) {
        if (player == null || advancementId == null || player.server == null) return;
        try {
            Advancement adv = player.server.getAdvancements().getAdvancement(advancementId);
            if (adv == null) return;
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(adv);
            if (progress.isDone()) return;
            for (String criterion : progress.getRemainingCriteria()) {
                player.getAdvancements().award(adv, criterion);
            }
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] Advancement award failed for {}: {}", advancementId, t.toString());
        }
    }

    /**
     * Existing saves may already own gear / story / roster progress before the
     * advancement tab existed — inventory_changed and action hooks never re-fire.
     */
    public static void catchUpOnLogin(ServerPlayer player) {
        if (player == null) return;
        if (BroadcastScheduler.hasT3(player)) {
            award(player, RADIO_GET_T3);
        }
        if (hasInInventory(player, new ItemStack(DeadAirItems.SIGNAL_BREAKER.get()))) {
            award(player, STORY_BREAKER);
        }
        if (hasTunedRadio(player)) {
            award(player, RADIO_FIRST_TUNE);
        }
        if (PlayerBaseManager.hasBase(player.server, player.getUUID())) {
            award(player, SURVIVOR_BASE);
        }
        catchUpRoster(player);
        catchUpStory(player);
    }

    private static void catchUpRoster(ServerPlayer player) {
        SurvivorRoster roster = SurvivorRoster.get(player.server);
        if (roster == null) return;
        int n = roster.count(player.getUUID());
        if (n >= 1) award(player, SURVIVOR_RECRUIT);
        if (n >= 3) award(player, SURVIVOR_THREE);
        if (n >= Config.maxSurvivorsPerPlayer) award(player, SURVIVOR_FULL);
        if (roster.countFollowing(player.getUUID()) >= 1) award(player, SURVIVOR_FOLLOW);
    }

    private static void catchUpStory(ServerPlayer player) {
        BroadcastProgress data = BroadcastProgress.get(player.server);
        if (data == null) return;
        BroadcastProgress.PlayerProgress p = data.get(player.getUUID());
        if (p == null) return;

        if (!p.relaysFound.isEmpty() || p.stage >= StoryStage.RELAYS) {
            award(player, EB_FIRST);
            award(player, STORY_RELAY_BROADCAST);
            award(player, STORY_RELAY_FOUND);
        }
        if (p.relaysFound.size() >= Config.storyRelaysRequired) {
            award(player, STORY_ALL_RELAYS);
        }
        if (p.stage >= StoryStage.FINAL_BROADCAST || p.firstEncounterSurvived || p.finalDefeated) {
            award(player, STORY_IT_HEARS_YOU);
        }
        if (p.firstEncounterSurvived || p.finalDefeated) {
            // Survived or finished the fight — they summoned at least once.
            award(player, STORY_SUMMON);
            award(player, HIDDEN_WRONG_NUMBER);
            // Do not catch-up STORY_SURVIVE_TIMER: live grant only when ticksLeft <= 0
            // (anchor clears must not get Three Minutes of Terror).
        }
        if (p.finalDefeated) {
            award(player, STORY_FINAL);
        }
    }

    private static boolean hasInInventory(ServerPlayer player, ItemStack needle) {
        return player.getInventory().contains(needle);
    }

    private static boolean hasTunedRadio(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (RadioManager.isRadioItem(stack) && RadioManager.getTunedStationId(stack) != null) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (RadioManager.isRadioItem(stack) && RadioManager.getTunedStationId(stack) != null) {
                return true;
            }
        }
        return false;
    }

    private DeadAirAdvancements() {}
}
