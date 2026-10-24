package uk.co.extraspecialstudio.dead_air.menu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import uk.co.extraspecialstudio.dead_air.Dead_air;

public final class DeadAirMenus {
    public static final DeferredRegister<MenuType<?>> REGISTER =
        DeferredRegister.create(Registries.MENU, Dead_air.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<SurvivorEquipmentMenu>> SURVIVOR_EQUIPMENT =
        REGISTER.register("survivor_equipment",
            () -> IMenuTypeExtension.create(SurvivorEquipmentMenu::fromNetwork));

    private DeadAirMenus() {}
}
