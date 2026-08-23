package uk.co.extraspecialstudio.dead_air.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/**
 * Placeable blocks for Dead Air (disc compendiums as normal blocks).
 */
public final class DeadAirBlocks {

    public static final DeferredRegister<Block> REGISTER =
        DeferredRegister.create(ForgeRegistries.BLOCKS, Dead_air.MODID);

    private static BlockBehaviour.Properties discProps() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .strength(0.5f)
            .sound(SoundType.WOOD)
            .noOcclusion();
    }

    public static final RegistryObject<Block> DISC_COMPENDIUM_A = REGISTER.register(
        "disc_compendium_a",
        () -> new Block(discProps())
    );

    public static final RegistryObject<Block> DISC_COMPENDIUM_B = REGISTER.register(
        "disc_compendium_b",
        () -> new Block(discProps())
    );

    private DeadAirBlocks() {}
}
