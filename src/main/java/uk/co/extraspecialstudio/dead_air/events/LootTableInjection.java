package uk.co.extraspecialstudio.dead_air.events;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.item.DeadAirItems;

/**
 * Injects T1.Radio into RadioTowers <em>structure</em> tower crates (guaranteed).
 * Does <strong>not</strong> inject into airdrop delivery loot — those crates only contain
 * what the player selected in the Call Airdrop GUI (plus catalog purchases).
 */
@Mod.EventBusSubscriber(modid = Dead_air.MODID)
public final class LootTableInjection {
    private static final ResourceLocation TOWER_STRUCTURE =
        ResourceLocation.fromNamespaceAndPath("radiotowers", "radio_tower_overrun_loot");

    private LootTableInjection() {}

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        if (!TOWER_STRUCTURE.equals(event.getName())) {
            return;
        }
        event.getTable().addPool(LootPool.lootPool()
            .name("dead_air_guaranteed_t1")
            .setRolls(ConstantValue.exactly(1))
            .add(LootItem.lootTableItem(DeadAirItems.WALKIE_T1.get()))
            .build());
    }
}
