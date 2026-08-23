package uk.co.extraspecialstudio.dead_air.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import uk.co.extraspecialstudio.dead_air.Dead_air;

@SuppressWarnings("null")
public final class DeadAirCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTER =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Dead_air.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DEAD_AIR_TAB = REGISTER.register(
        "dead_air",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.dead_air.main"))
            .icon(() -> new ItemStack(DeadAirItems.DEAD_AIR_FIELD_GUIDE.get()))
            .displayItems((parameters, output) -> {
                // Guide
                output.accept(DeadAirItems.DEAD_AIR_FIELD_GUIDE.get());
                // Radios
                output.accept(DeadAirItems.WALKIE_T1.get());
                output.accept(DeadAirItems.WALKIE_T2.get());
                // Resources / craft parts
                output.accept(DeadAirItems.STATIC_NOTE.get());
                output.accept(DeadAirItems.RESONANT_CHORD.get());
                output.accept(DeadAirItems.DIMENSIONAL_RELAY.get());
                output.accept(DeadAirItems.DISC_COMPENDIUM_A.get());
                output.accept(DeadAirItems.DISC_COMPENDIUM_B.get());
                // Station hardware (owned by RadioTowers; craft recipe is Dead Air's)
                acceptRadioPanelIfPresent(output);
                // Tower upgrades
                output.accept(DeadAirItems.SIGNAL_UPGRADE.get());
                output.accept(DeadAirItems.JUKEBOX_UPGRADE.get());
            })
            .build()
    );

    /** Radio panel lives in radiotowers; show it in Dead Air's tab when that mod is loaded. */
    private static void acceptRadioPanelIfPresent(CreativeModeTab.Output output) {
        if (!ModList.get().isLoaded("radiotowers")) return;
        Item panel = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("radiotowers", "radio_panel"));
        if (panel != null && panel != Items.AIR) {
            output.accept(panel);
        }
    }

    private DeadAirCreativeTabs() {}
}
