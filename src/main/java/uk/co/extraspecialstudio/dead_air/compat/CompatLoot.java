package uk.co.extraspecialstudio.dead_air.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.registries.ForgeRegistries;
import uk.co.extraspecialstudio.dead_air.Dead_air;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Soft loot fills: resolve optional mod item IDs / tags and inject into chests, player supplies, auction offers.
 * Never throws; missing registry entries are skipped.
 */
public final class CompatLoot {
    private CompatLoot() {}

    public static void injectIntoChest(ServerLevel level, BlockPos chestPos, TagKey<Item> tag, int maxItems) {
        try {
            BlockEntity be = level.getBlockEntity(chestPos);
            if (!(be instanceof ChestBlockEntity chest)) return;
            List<ItemStack> picks = pickFromTag(tag, level.getRandom(), maxItems);
            for (ItemStack stack : picks) {
                insert(chest, stack);
            }
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] Compat chest inject failed: {}", t.toString());
        }
    }

    public static void giveToPlayer(ServerPlayer player, TagKey<Item> tag, int maxItems) {
        try {
            for (ItemStack stack : pickFromTag(tag, player.getRandom(), maxItems)) {
                if (!player.getInventory().add(stack)) player.drop(stack, false);
            }
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] Compat supply inject failed: {}", t.toString());
        }
    }

    public static void addAuctionOffers(WanderingTrader trader, TagKey<Item> tag, int maxOffers) {
        try {
            MerchantOffers offers = trader.getOffers();
            List<ItemStack> picks = pickFromTag(tag, trader.getRandom(), maxOffers);
            for (ItemStack result : picks) {
                int price = 2 + trader.getRandom().nextInt(4);
                offers.add(new MerchantOffer(
                    new ItemStack(Items.EMERALD, price),
                    ItemStack.EMPTY,
                    result,
                    4, 5, 0.05f));
            }
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] Compat auction inject failed: {}", t.toString());
        }
    }

    /** Place a small chest beside {@code center} and fill from tag (abandoned vehicle / parts stash). */
    @Nullable
    public static BlockPos placePartsChest(ServerLevel level, BlockPos center, TagKey<Item> tag, int maxItems) {
        try {
            RandomSource random = level.getRandom();
            BlockPos chestPos = center.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
            if (!level.getBlockState(chestPos).canBeReplaced() && !level.getBlockState(chestPos).isAir()) {
                chestPos = center.above();
            }
            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
            injectIntoChest(level, chestPos, tag, maxItems);
            return chestPos;
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] Compat parts chest failed: {}", t.toString());
            return null;
        }
    }

    public static List<ItemStack> pickFromTag(TagKey<Item> tag, RandomSource random, int max) {
        List<ItemStack> out = new ArrayList<>();
        try {
            var opt = ForgeRegistries.ITEMS.tags();
            if (opt == null) return out;
            var tagged = opt.getTag(tag);
            if (tagged.isEmpty()) return out;
            List<Item> items = new ArrayList<>();
            tagged.forEach(items::add);
            if (items.isEmpty()) return out;
            int n = Math.min(max, 1 + random.nextInt(Math.max(1, max)));
            for (int i = 0; i < n; i++) {
                Item item = items.get(random.nextInt(items.size()));
                out.add(new ItemStack(item, 1 + random.nextInt(2)));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** Resolve known item IDs that exist in the registry (skip missing). */
    public static List<ItemStack> pickFromIds(String[] ids, RandomSource random, int max) {
        List<Item> found = new ArrayList<>();
        for (String id : ids) {
            try {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl == null) continue;
                Item item = ForgeRegistries.ITEMS.getValue(rl);
                if (item != null && item != Items.AIR) found.add(item);
            } catch (Throwable ignored) {
            }
        }
        List<ItemStack> out = new ArrayList<>();
        if (found.isEmpty()) return out;
        int n = Math.min(max, 1 + random.nextInt(Math.max(1, max)));
        for (int i = 0; i < n; i++) {
            out.add(new ItemStack(found.get(random.nextInt(found.size())), 1));
        }
        return out;
    }

    private static void insert(ChestBlockEntity chest, ItemStack stack) {
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).isEmpty()) {
                chest.setItem(i, stack);
                return;
            }
        }
    }
}
