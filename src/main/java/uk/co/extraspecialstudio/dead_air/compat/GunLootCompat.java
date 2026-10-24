package uk.co.extraspecialstudio.dead_air.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/**
 * Injects real TaCZ / Point Blank gun stacks (with GunId NBT) into auction, crash, and scavenger supplies.
 * Falls back to {@link CompatTags#GUNS} ammo packs when a full gun roll fails.
 */
public final class GunLootCompat {
    private GunLootCompat() {}

    public static boolean anyGunModEnabled() {
        return (Config.compatTacz && SurvivorTaczCompat.isLoaded())
            || (Config.compatPointBlank && PointBlankCompat.isLoaded());
    }

    public static void injectAuction(WanderingTrader trader) {
        if (!anyGunModEnabled()) return;
        try {
            ItemStack gun = SurvivorGunCompat.pickLootGun(trader.getRandom());
            if (!gun.isEmpty()) {
                int price = 4 + trader.getRandom().nextInt(6);
                trader.getOffers().add(new MerchantOffer(
                    new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, price),
                    java.util.Optional.empty(),
                    gun,
                    2, 8, 0.05f));
            } else {
                CompatLoot.addAuctionOffers(trader, CompatTags.GUNS, 1);
            }
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] Gun auction inject failed: {}", t.toString());
        }
    }

    public static void injectCrashChest(ServerLevel level, BlockPos chestPos) {
        if (!anyGunModEnabled()) return;
        try {
            ItemStack gun = SurvivorGunCompat.pickLootGun(level.getRandom());
            if (!gun.isEmpty()) {
                BlockEntity be = level.getBlockEntity(chestPos);
                if (be instanceof ChestBlockEntity chest) {
                    for (int i = 0; i < chest.getContainerSize(); i++) {
                        if (chest.getItem(i).isEmpty()) {
                            chest.setItem(i, gun);
                            break;
                        }
                    }
                }
            } else {
                CompatLoot.injectIntoChest(level, chestPos, CompatTags.GUNS, 1);
            }
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] Gun crash inject failed: {}", t.toString());
        }
    }

    public static void injectScavengerSupplies(ServerPlayer player) {
        if (!anyGunModEnabled()) return;
        try {
            ItemStack gun = SurvivorGunCompat.pickLootGun(player.getRandom());
            if (!gun.isEmpty()) {
                if (!player.getInventory().add(gun)) player.drop(gun, false);
            } else {
                CompatLoot.giveToPlayer(player, CompatTags.GUNS, 1);
            }
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] Gun supply inject failed: {}", t.toString());
        }
    }
}
