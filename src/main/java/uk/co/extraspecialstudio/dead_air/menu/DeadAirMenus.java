package uk.co.extraspecialstudio.dead_air.menu;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import uk.co.extraspecialstudio.dead_air.Dead_air;

public final class DeadAirMenus {
    public static final DeferredRegister<MenuType<?>> REGISTER =
        DeferredRegister.create(ForgeRegistries.MENU_TYPES, Dead_air.MODID);

    public static final RegistryObject<MenuType<SurvivorEquipmentMenu>> SURVIVOR_EQUIPMENT =
        REGISTER.register("survivor_equipment",
            () -> IForgeMenuType.create(SurvivorEquipmentMenu::fromNetwork));

    private DeadAirMenus() {}
}
