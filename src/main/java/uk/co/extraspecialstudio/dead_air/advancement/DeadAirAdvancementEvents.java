package uk.co.extraspecialstudio.dead_air.advancement;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/** Forge event hooks for advancement grants that don't sit on a single DA call site. */
@Mod.EventBusSubscriber(modid = Dead_air.MODID)
public final class DeadAirAdvancementEvents {

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // AuctionBrokerEntity extends WanderingTrader; tag also covers legacy vanilla traders.
        if (!(event.getAbstractVillager() instanceof WanderingTrader trader)) return;
        if (!trader.getPersistentData().getBoolean("DeadAirAuctionTrader")) return;
        DeadAirAdvancements.award(player, DeadAirAdvancements.EB_AUCTION_TRADE);
    }

    @SubscribeEvent
    public static void onOpenCrashChest(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof ChestBlockEntity chest)) return;
        if (!chest.getPersistentData().getBoolean("DeadAirCrashLoot")) return;
        DeadAirAdvancements.award(player, DeadAirAdvancements.EB_PLANE);
    }

    private DeadAirAdvancementEvents() {}
}
