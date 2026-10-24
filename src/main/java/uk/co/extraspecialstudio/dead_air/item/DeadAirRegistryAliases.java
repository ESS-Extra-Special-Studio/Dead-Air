package uk.co.extraspecialstudio.dead_air.item;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/**
 * Remaps legacy {@code dead_air:walkie_t*} item IDs to {@code dead_air:radio_t*} so existing worlds
 * and inventories keep their handheld radios after the rename.
 */
@Mod.EventBusSubscriber(modid = Dead_air.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DeadAirRegistryAliases {

    private DeadAirRegistryAliases() {}

    @SubscribeEvent
    public static void onMissingMappings(MissingMappingsEvent event) {
        for (MissingMappingsEvent.Mapping<Item> mapping : event.getMappings(ForgeRegistries.Keys.ITEMS, Dead_air.MODID)) {
            Item target = switch (mapping.getKey().getPath()) {
                case "walkie_t1" -> DeadAirItems.RADIO_T1.get();
                case "walkie_t2" -> DeadAirItems.RADIO_T2.get();
                case "walkie_t3" -> DeadAirItems.RADIO_T3.get();
                default -> null;
            };
            if (target != null) {
                mapping.remap(target);
            }
        }
    }
}
