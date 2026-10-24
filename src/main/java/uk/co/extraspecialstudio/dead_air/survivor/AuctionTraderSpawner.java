package uk.co.extraspecialstudio.dead_air.survivor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.AABB;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.item.DeadAirItems;

import java.util.Optional;
import java.util.UUID;

/** Temporary dead-channel auction trader (vanilla WanderingTrader with scrap-flavoured offers). */
public final class AuctionTraderSpawner {

    private AuctionTraderSpawner() {}

    public static WanderingTrader spawn(ServerLevel level, BlockPos pos, UUID owner) {
        WanderingTrader trader = EntityType.WANDERING_TRADER.create(level);
        if (trader == null) return null;
        trader.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360f, 0);
        trader.setDespawnDelay(Math.max(1, Config.auctionDespawnDays) * 24000);
        trader.setPersistenceRequired();
        trader.getPersistentData().putBoolean("DeadAirAuctionTrader", true);
        trader.getPersistentData().putUUID("DeadAirAuctionOwner", owner);
        trader.getPersistentData().putLong("DeadAirAuctionDay", level.getDayTime() / 24000L);

        MerchantOffers offers = trader.getOffers();
        offers.clear();
        offers.add(offer(Items.EMERALD, 2, new ItemStack(Items.IRON_NUGGET, 12), 8));
        offers.add(offer(Items.EMERALD, 3, new ItemStack(Items.GUNPOWDER, 6), 6));
        offers.add(offer(Items.EMERALD, 4, new ItemStack(Items.ARROW, 16), 8));
        offers.add(offer(DeadAirItems.STATIC_NOTE.get(), 1, new ItemStack(Items.GOLDEN_APPLE), 2));
        offers.add(offer(Items.IRON_INGOT, 3, new ItemStack(Items.COPPER_INGOT, 6), 6));
        offers.add(offer(Items.EMERALD, 5, new ItemStack(DeadAirItems.STATIC_NOTE.get()), 4));
        try {
            uk.co.extraspecialstudio.dead_air.compat.GunLootCompat.injectAuction(trader);
            uk.co.extraspecialstudio.dead_air.compat.ColdSweatCompat.injectAuction(trader);
            uk.co.extraspecialstudio.dead_air.compat.ToughAsNailsCompat.injectAuction(trader);
        } catch (Throwable ignored) {
        }

        level.addFreshEntity(trader);
        return trader;
    }

    private static MerchantOffer offer(net.minecraft.world.item.Item costItem, int costCount, ItemStack result, int maxUses) {
        return new MerchantOffer(
            new ItemCost(costItem, costCount),
            Optional.empty(),
            result,
            maxUses,
            0,
            0.05f);
    }

    /** Despawn auction traders that timed out or whose owner wandered far away. */
    public static void tick(ServerLevel overworld) {
        if (overworld.getServer().getTickCount() % 100 != 0) return;
        long day = overworld.getDayTime() / 24000L;
        double maxDist = Config.auctionDespawnDistance;
        double maxDistSq = maxDist * maxDist;
        for (ServerPlayer player : overworld.players()) {
            AABB box = player.getBoundingBox().inflate(maxDist + 16);
            for (WanderingTrader trader : overworld.getEntitiesOfClass(WanderingTrader.class, box,
                t -> t.getPersistentData().getBoolean("DeadAirAuctionTrader"))) {
                var tag = trader.getPersistentData();
                long spawned = tag.getLong("DeadAirAuctionDay");
                if (day - spawned >= Config.auctionDespawnDays) {
                    trader.discard();
                    continue;
                }
                if (!tag.hasUUID("DeadAirAuctionOwner")) continue;
                UUID owner = tag.getUUID("DeadAirAuctionOwner");
                if (!owner.equals(player.getUUID())) continue;
                double dx = player.getX() - trader.getX();
                double dz = player.getZ() - trader.getZ();
                if (dx * dx + dz * dz > maxDistSq) {
                    trader.discard();
                }
            }
        }
    }
}
