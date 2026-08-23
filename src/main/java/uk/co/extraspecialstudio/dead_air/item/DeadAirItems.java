package uk.co.extraspecialstudio.dead_air.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.block.DeadAirBlocks;

/**
 * Items for Dead Air.
 */
public final class DeadAirItems {

    public static final DeferredRegister<Item> REGISTER = DeferredRegister.create(ForgeRegistries.ITEMS, Dead_air.MODID);

    @SuppressWarnings("null")
    public static final RegistryObject<Item> DISC_COMPENDIUM_A = REGISTER.register(
        "disc_compendium_a",
        () -> new BlockItem(DeadAirBlocks.DISC_COMPENDIUM_A.get(), new Item.Properties().stacksTo(1))
    );

    @SuppressWarnings("null")
    public static final RegistryObject<Item> DISC_COMPENDIUM_B = REGISTER.register(
        "disc_compendium_b",
        () -> new BlockItem(DeadAirBlocks.DISC_COMPENDIUM_B.get(), new Item.Properties().stacksTo(1))
    );

    @SuppressWarnings("null")
    public static final RegistryObject<Item> JUKEBOX_UPGRADE = REGISTER.register(
        "jukebox_upgrade",
        () -> new Item(new Item.Properties().stacksTo(1))
    );

    @SuppressWarnings("null")
    public static final RegistryObject<Item> DEAD_AIR_FIELD_GUIDE = REGISTER.register(
        "dead_air_field_guide",
        () -> new FieldGuideItem(new Item.Properties().stacksTo(1))
    );

    /** Tier-1 Dead Air walkie (local discovery). */
    @SuppressWarnings("null")
    public static final RegistryObject<WalkieItem> WALKIE_T1 = REGISTER.register(
        "walkie_t1",
        () -> new WalkieItem(new Item.Properties().stacksTo(1), WalkieItem.Tier.T1)
    );

    /** Tier-2 Walkie Link (cross-dim capable). */
    @SuppressWarnings("null")
    public static final RegistryObject<WalkieItem> WALKIE_T2 = REGISTER.register(
        "walkie_t2",
        () -> new WalkieItem(new Item.Properties().stacksTo(1), WalkieItem.Tier.T2)
    );

    /** Static Note — drops from kills while listening. */
    @SuppressWarnings("null")
    public static final RegistryObject<Item> STATIC_NOTE = REGISTER.register(
        "static_note",
        () -> new StaticNoteItem(new Item.Properties().stacksTo(36))
    );

    /** Resonant Chord — 9 Static Notes piled on the ground. */
    @SuppressWarnings("null")
    public static final RegistryObject<ResonantChordItem> RESONANT_CHORD = REGISTER.register(
        "resonant_chord",
        () -> new ResonantChordItem(new Item.Properties().stacksTo(16))
    );

    /** Dimensional Relay — T2 craft ingredient (chords + eye of ender + gold). */
    @SuppressWarnings("null")
    public static final RegistryObject<DimensionalRelayItem> DIMENSIONAL_RELAY = REGISTER.register(
        "dimensional_relay",
        () -> new DimensionalRelayItem(new Item.Properties().stacksTo(16))
    );

    /** Signal Upgrade / Tower Boost — install on radio panel for cross-dim linking. */
    @SuppressWarnings("null")
    public static final RegistryObject<Item> SIGNAL_UPGRADE = REGISTER.register(
        "signal_upgrade",
        () -> new Item(new Item.Properties().stacksTo(1))
    );

    private DeadAirItems() {}
}
