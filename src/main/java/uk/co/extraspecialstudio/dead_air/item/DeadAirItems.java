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

    /** Tier-1 Dead Air radio (local discovery). */
    @SuppressWarnings("null")
    public static final RegistryObject<RadioItem> RADIO_T1 = REGISTER.register(
        "radio_t1",
        () -> new RadioItem(new Item.Properties().stacksTo(1), RadioItem.Tier.T1)
    );

    /** Tier-2 radio (cross-dim capable). */
    @SuppressWarnings("null")
    public static final RegistryObject<RadioItem> RADIO_T2 = REGISTER.register(
        "radio_t2",
        () -> new RadioItem(new Item.Properties().stacksTo(1), RadioItem.Tier.T2)
    );

    /** Tier-3 radio: T2 features plus Transmit and story broadcasts. */
    @SuppressWarnings("null")
    public static final RegistryObject<RadioItem> RADIO_T3 = REGISTER.register(
        "radio_t3",
        () -> new RadioItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE), RadioItem.Tier.T3)
    );

    /** Upgrade module: craft into or use on a T2 Radio to produce a T3 Radio. */
    @SuppressWarnings("null")
    public static final RegistryObject<Item> SIGNAL_DECODER = REGISTER.register(
        "signal_decoder",
        () -> new SignalDecoderItem(new Item.Properties().stacksTo(1))
    );

    @SuppressWarnings("null")
    public static final RegistryObject<Item> RELAY_LOG = REGISTER.register(
        "relay_log",
        () -> new RelayLogItem(new Item.Properties().stacksTo(1))
    );

    @SuppressWarnings("null")
    public static final RegistryObject<Item> BROKEN_RELAY = REGISTER.register(
        "broken_relay",
        () -> new BlockItem(DeadAirBlocks.BROKEN_RELAY.get(), new Item.Properties())
    );

    @SuppressWarnings("null")
    public static final RegistryObject<Item> SURVIVOR_TENT = REGISTER.register(
        "survivor_tent",
        () -> new BlockItem(DeadAirBlocks.SURVIVOR_TENT.get(), new Item.Properties())
    );

    @SuppressWarnings("null")
    public static final RegistryObject<Item> SIGNAL_FIREPIT = REGISTER.register(
        "signal_firepit",
        () -> new BlockItem(DeadAirBlocks.SIGNAL_FIREPIT.get(), new Item.Properties())
    );

    @SuppressWarnings("null")
    public static final RegistryObject<Item> FOLDING_CHAIR = REGISTER.register(
        "folding_chair",
        () -> new BlockItem(DeadAirBlocks.FOLDING_CHAIR.get(), new Item.Properties())
    );

    @SuppressWarnings("null")
    public static final RegistryObject<Item> SURVIVOR_BACKPACK = REGISTER.register(
        "survivor_backpack",
        () -> new BlockItem(DeadAirBlocks.SURVIVOR_BACKPACK.get(), new Item.Properties())
    );

    @SuppressWarnings("null")
    public static final RegistryObject<Item> CAMP_TABLE = REGISTER.register(
        "camp_table",
        () -> new BlockItem(DeadAirBlocks.CAMP_TABLE.get(), new Item.Properties())
    );

    @SuppressWarnings("null")
    public static final RegistryObject<Item> CAMP_LANTERN = REGISTER.register(
        "camp_lantern",
        () -> new BlockItem(DeadAirBlocks.CAMP_LANTERN.get(), new Item.Properties())
    );

    @SuppressWarnings("null")
    public static final RegistryObject<Item> JERRYCAN = REGISTER.register(
        "jerrycan",
        () -> new BlockItem(DeadAirBlocks.JERRYCAN.get(), new Item.Properties())
    );

    @SuppressWarnings("null")
    public static final RegistryObject<Item> COOKING_POT = REGISTER.register(
        "cooking_pot",
        () -> new BlockItem(DeadAirBlocks.COOKING_POT.get(), new Item.Properties())
    );

    @SuppressWarnings("null")
    public static final RegistryObject<Item> MEDICAL_CASE = REGISTER.register(
        "medical_case",
        () -> new BlockItem(DeadAirBlocks.MEDICAL_CASE.get(), new Item.Properties())
    );

    @SuppressWarnings("null")
    public static final RegistryObject<Item> CLOTHESLINE = REGISTER.register(
        "clothesline",
        () -> new BlockItem(DeadAirBlocks.CLOTHESLINE.get(), new Item.Properties())
    );

    @SuppressWarnings("null")
    public static final RegistryObject<Item> TRAFFIC_CONE = REGISTER.register(
        "traffic_cone",
        () -> new BlockItem(DeadAirBlocks.TRAFFIC_CONE.get(), new Item.Properties())
    );

    @SuppressWarnings("null")
    public static final RegistryObject<Item> SIGNAL_BREAKER = REGISTER.register(
        "signal_breaker",
        () -> new SignalBreakerItem(new Item.Properties().fireResistant().rarity(net.minecraft.world.item.Rarity.EPIC))
    );

    // Boss components for the Signal Breaker; only obtainable by command until those bosses exist.
    public static final RegistryObject<Item> COMPONENT_DISTORTION_CORE = component("component_distortion_core");
    public static final RegistryObject<Item> COMPONENT_BROADCASTER_COIL = component("component_broadcaster_coil");
    public static final RegistryObject<Item> COMPONENT_MAW_FANG = component("component_maw_fang");
    public static final RegistryObject<Item> COMPONENT_SCRAMBLER_CHIP = component("component_scrambler_chip");
    public static final RegistryObject<Item> COMPONENT_ECHO_CRYSTAL = component("component_echo_crystal");

    @SuppressWarnings("null")
    private static RegistryObject<Item> component(String name) {
        return REGISTER.register(name,
            () -> new TooltipItem(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.RARE)));
    }

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

    public static final RegistryObject<Item> THE_LISTENER_SPAWN_EGG = REGISTER.register(
        "the_listener_spawn_egg",
        () -> new net.minecraftforge.common.ForgeSpawnEggItem(
            uk.co.extraspecialstudio.dead_air.entity.DeadAirEntities.THE_LISTENER,
            0x4A1E7A, 0x2FA8FF, new Item.Properties())
    );

    public static final RegistryObject<Item> SIGNAL_ANCHOR_SPAWN_EGG = REGISTER.register(
        "signal_anchor_spawn_egg",
        () -> new net.minecraftforge.common.ForgeSpawnEggItem(
            uk.co.extraspecialstudio.dead_air.entity.DeadAirEntities.SIGNAL_ANCHOR,
            0x1B2A33, 0x2FA8FF, new Item.Properties())
    );

    public static final RegistryObject<Item> SURVIVOR_SPAWN_EGG = REGISTER.register(
        "survivor_spawn_egg",
        () -> new net.minecraftforge.common.ForgeSpawnEggItem(
            uk.co.extraspecialstudio.dead_air.entity.DeadAirEntities.SURVIVOR,
            0x6B5A3A, 0xC9B28A, new Item.Properties())
    );

    private DeadAirItems() {}
}
