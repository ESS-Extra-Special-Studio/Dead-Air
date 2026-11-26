package uk.co.extraspecialstudio.dead_air.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/**
 * Item tags for built-in and companion radios.
 * Companions (e.g. Pip-Boy) add their items via datapack under the {@code dead_air} namespace
 * so they work without subclassing {@link RadioItem}.
 */
public final class DeadAirTags {
    /** Any radio that should participate in radio tune / listen / note-drop logic. */
    public static final TagKey<Item> RADIOS = tag("radios");
    /** T1-tier companion (or stock) radios. */
    public static final TagKey<Item> RADIO_T1 = tag("radio_t1");
    /** T2-tier companion (or stock) radios — link-capable. */
    public static final TagKey<Item> RADIO_T2 = tag("radio_t2");
    /** T3-tier companion (or stock) radios. */
    public static final TagKey<Item> RADIO_T3 = tag("radio_t3");

    /** @deprecated Use {@link #RADIOS}. Alias tag {@code walkie_radios} still accepted for companion datapacks. */
    @Deprecated
    public static final TagKey<Item> WALKIE_RADIOS = tag("walkie_radios");
    /** @deprecated Use {@link #RADIO_T1}. */
    @Deprecated
    public static final TagKey<Item> WALKIE_T1 = tag("walkie_t1");
    /** @deprecated Use {@link #RADIO_T2}. */
    @Deprecated
    public static final TagKey<Item> WALKIE_T2 = tag("walkie_t2");

    private DeadAirTags() {}

    private static TagKey<Item> tag(String path) {
        return ItemTags.create(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, path));
    }
}
