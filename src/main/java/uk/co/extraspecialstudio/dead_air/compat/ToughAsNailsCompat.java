package uk.co.extraspecialstudio.dead_air.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.neoforged.fml.ModList;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/** Soft Tough As Nails â€” water / purification / thermos into medic & scavenger supplies. */
public final class ToughAsNailsCompat {
    private static final boolean LOADED = ModList.get().isLoaded("toughasnails");

    private ToughAsNailsCompat() {}

    public static void probe() {
        if (LOADED) Dead_air.LOGGER.info("[Dead Air] Tough As Nails soft-compat ready");
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static boolean enabled() {
        return Config.compatToughAsNails && LOADED;
    }

    public static void injectMedicOrSurvivorSupplies(ServerPlayer player) {
        if (!enabled()) return;
        CompatLoot.giveToPlayer(player, CompatTags.TOUGH_AS_NAILS, 2);
    }

    public static void injectAuction(WanderingTrader trader) {
        if (!enabled()) return;
        CompatLoot.addAuctionOffers(trader, CompatTags.TOUGH_AS_NAILS, 1);
    }
}
