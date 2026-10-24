package uk.co.extraspecialstudio.dead_air.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.neoforged.fml.ModList;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/**
 * Soft Cold Sweat â€” temp gear into medic supplies / auction when loaded.
 * Lit Signal Firepit heat is datapack-only: {@code data/dead_air/cold_sweat/block/block_temp/signal_firepit.json}
 * (campfire-matched Â°F / range; no Java API, no {@code #thermal_source} â€” that tag is for hearth/boiler/icebox).
 */
public final class ColdSweatCompat {
    private static final boolean LOADED = ModList.get().isLoaded("cold_sweat");

    private ColdSweatCompat() {}

    public static void probe() {
        if (LOADED) Dead_air.LOGGER.info("[Dead Air] Cold Sweat soft-compat ready");
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static boolean enabled() {
        return Config.compatColdSweat && LOADED;
    }

    public static void injectMedicSupplies(ServerPlayer player) {
        if (!enabled()) return;
        CompatLoot.giveToPlayer(player, CompatTags.COLD_SWEAT, 2);
    }

    public static void injectAuction(WanderingTrader trader) {
        if (!enabled()) return;
        CompatLoot.addAuctionOffers(trader, CompatTags.COLD_SWEAT, 2);
    }
}
