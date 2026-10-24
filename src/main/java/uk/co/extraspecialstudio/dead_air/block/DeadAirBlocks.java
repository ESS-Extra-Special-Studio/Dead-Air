package uk.co.extraspecialstudio.dead_air.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/**
 * Placeable blocks for Dead Air (disc compendiums as normal blocks).
 */
public final class DeadAirBlocks {

    public static final DeferredRegister<Block> REGISTER =
        DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK, Dead_air.MODID);

    private static BlockBehaviour.Properties discProps() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .strength(0.5f)
            .sound(SoundType.WOOD)
            .noOcclusion();
    }

    public static final DeferredHolder<Block, Block> DISC_COMPENDIUM_A = REGISTER.register(
        "disc_compendium_a",
        () -> new Block(discProps())
    );

    public static final DeferredHolder<Block, Block> DISC_COMPENDIUM_B = REGISTER.register(
        "disc_compendium_b",
        () -> new Block(discProps())
    );

    public static final DeferredHolder<Block, Block> BROKEN_RELAY = REGISTER.register(
        "broken_relay",
        () -> new BrokenRelayBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN)
            .strength(4.0f, 1200.0f)
            .sound(SoundType.COPPER)
            .requiresCorrectToolForDrops()
            .noLootTable()
            .lightLevel(s -> s.getValue(BrokenRelayBlock.ACTIVE) ? 10 : 3))
    );

    public static final DeferredHolder<Block, Block> SURVIVOR_TENT = REGISTER.register(
        "survivor_tent",
        () -> new SurvivorTentBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .strength(0.8f)
            .sound(SoundType.WOOL)
            .noOcclusion())
    );

    public static final DeferredHolder<Block, Block> SIGNAL_FIREPIT = REGISTER.register(
        "signal_firepit",
        () -> new SignalFirepitBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE)
            .strength(1.0f)
            .sound(SoundType.STONE)
            .lightLevel(s -> s.getValue(SignalFirepitBlock.LIT) ? 12 : 0)
            .noOcclusion())
    );

    private static BlockBehaviour.Properties decorWood() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(1.2f)
            .sound(SoundType.WOOD)
            .noOcclusion();
    }

    public static final DeferredHolder<Block, Block> FOLDING_CHAIR = REGISTER.register(
        "folding_chair",
        () -> new FoldingChairBlock(decorWood().mapColor(MapColor.COLOR_GREEN).sound(SoundType.WOOD))
    );

    public static final DeferredHolder<Block, Block> SURVIVOR_BACKPACK = REGISTER.register(
        "survivor_backpack",
        () -> EbDecorBlock.box(decorWood().mapColor(MapColor.COLOR_GREEN).sound(SoundType.WOOL),
            3, 0, 4.2, 14.2, 12, 11.5)
    );

    public static final DeferredHolder<Block, Block> CAMP_TABLE = REGISTER.register(
        "camp_table",
        // Matches camp_table.bbmodel (top ~13.5, tin_rim ~15).
        () -> EbDecorBlock.box(decorWood(), 0, 0, 3, 16, 15, 13.5)
    );

    public static final DeferredHolder<Block, Block> CAMP_LANTERN = REGISTER.register(
        "camp_lantern",
        // Matches camp_lantern.bbmodel (bail_top ~y12.15).
        () -> EbDecorBlock.box(
            decorWood().mapColor(MapColor.METAL).sound(SoundType.LANTERN).lightLevel(s -> 10),
            5.5, 0, 5.5, 10.5, 12.2, 10.5)
    );

    public static final DeferredHolder<Block, Block> JERRYCAN = REGISTER.register(
        "jerrycan",
        () -> EbDecorBlock.box(
            decorWood().mapColor(MapColor.COLOR_GREEN).sound(SoundType.METAL),
            2.8, 0, 5, 13.4, 14.5, 11.5)
    );

    public static final DeferredHolder<Block, Block> COOKING_POT = REGISTER.register(
        "cooking_pot",
        // Matches Downloads cooking_pot.bbmodel (legs/bail ~y0–12).
        () -> EbDecorBlock.box(
            decorWood().mapColor(MapColor.METAL).sound(SoundType.METAL),
            2.5, 0, 3.7, 13.5, 12, 12.3)
    );

    public static final DeferredHolder<Block, Block> MEDICAL_CASE = REGISTER.register(
        "medical_case",
        () -> EbDecorBlock.box(
            decorWood().mapColor(MapColor.WOOL).sound(SoundType.WOOD),
            2.8, 0, 4.7, 13.2, 9.8, 11.3)
    );

    public static final DeferredHolder<Block, Block> CLOTHESLINE = REGISTER.register(
        "clothesline",
        () -> new ClotheslineBlock(
            decorWood().mapColor(MapColor.WOOD).sound(SoundType.WOOD).noOcclusion())
    );

    public static final DeferredHolder<Block, Block> TRAFFIC_CONE = REGISTER.register(
        "traffic_cone",
        () -> EbDecorBlock.box(
            decorWood().mapColor(MapColor.COLOR_ORANGE).sound(SoundType.WOOD).noOcclusion(),
            3, 0, 3, 13, 15.2, 13)
    );

    private DeadAirBlocks() {}
}
