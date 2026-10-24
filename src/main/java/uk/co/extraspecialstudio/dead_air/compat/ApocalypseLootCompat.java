package uk.co.extraspecialstudio.dead_air.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.WanderingTrader;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/**
 * Soft-fills Dead Air EB / crash chests and supplies with fair apocalypse food, meds, and scrap gear.
 * Uses {@link CompatTags} optional entries — missing mods contribute nothing.
 */
public final class ApocalypseLootCompat {

    private ApocalypseLootCompat() {
    }

    public static boolean enabled() {
        return Config.compatApocalypseGear && SurvivorApocalypseGearCompat.anyLoaded();
    }

    /** Camp / convoy / auction / relay chests — light scavenge, not a military cache. */
    public static void injectSiteChest(ServerLevel level, BlockPos chestPos) {
        if (!enabled()) return;
        try {
            RandomSource random = level.getRandom();
            CompatLoot.injectIntoChest(level, chestPos, CompatTags.SCAVENGE_FOOD, 1 + random.nextInt(2));
            if (random.nextFloat() < 0.55f) {
                CompatLoot.injectIntoChest(level, chestPos, CompatTags.SCAVENGE_MEDICAL, 1);
            }
            if (random.nextFloat() < 0.30f) {
                CompatLoot.injectIntoChest(level, chestPos, CompatTags.SCAVENGE_GEAR, 1);
            }
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] Apocalypse site loot failed: {}", t.toString());
        }
    }

    /** Downed-plane crates — a bit more gear, still no morphine bags / exo suits. */
    public static void injectCrashChest(ServerLevel level, BlockPos chestPos) {
        if (!enabled()) return;
        try {
            RandomSource random = level.getRandom();
            CompatLoot.injectIntoChest(level, chestPos, CompatTags.SCAVENGE_FOOD, 2);
            CompatLoot.injectIntoChest(level, chestPos, CompatTags.SCAVENGE_MEDICAL, 1 + random.nextInt(2));
            if (random.nextFloat() < 0.45f) {
                CompatLoot.injectIntoChest(level, chestPos, CompatTags.SCAVENGE_GEAR, 1);
            }
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] Apocalypse crash loot failed: {}", t.toString());
        }
    }

    public static void injectMedicSupplies(ServerPlayer player) {
        if (!enabled()) return;
        try {
            CompatLoot.giveToPlayer(player, CompatTags.SCAVENGE_MEDICAL, 2);
            if (player.getRandom().nextFloat() < 0.4f) {
                CompatLoot.giveToPlayer(player, CompatTags.SCAVENGE_FOOD, 1);
            }
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] Apocalypse medic supply failed: {}", t.toString());
        }
    }

    public static void injectScavengerSupplies(ServerPlayer player) {
        if (!enabled()) return;
        try {
            CompatLoot.giveToPlayer(player, CompatTags.SCAVENGE_FOOD, 2);
            if (player.getRandom().nextFloat() < 0.35f) {
                CompatLoot.giveToPlayer(player, CompatTags.SCAVENGE_GEAR, 1);
            }
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] Apocalypse scavenger supply failed: {}", t.toString());
        }
    }

    public static void injectAuction(WanderingTrader trader) {
        if (!enabled()) return;
        try {
            CompatLoot.addAuctionOffers(trader, CompatTags.SCAVENGE_FOOD, 1);
            if (trader.getRandom().nextFloat() < 0.5f) {
                CompatLoot.addAuctionOffers(trader, CompatTags.SCAVENGE_MEDICAL, 1);
            }
            if (trader.getRandom().nextFloat() < 0.25f) {
                CompatLoot.addAuctionOffers(trader, CompatTags.SCAVENGE_GEAR, 1);
            }
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] Apocalypse auction inject failed: {}", t.toString());
        }
    }
}
