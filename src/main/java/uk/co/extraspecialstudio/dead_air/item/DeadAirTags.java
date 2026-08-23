package uk.co.extraspecialstudio.dead_air.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/**
 * Item tags for built-in and companion walkie radios.
 * Companions (e.g. Pip-Boy) add their items via datapack under the {@code dead_air} namespace
 * so they work without subclassing {@link WalkieItem}.
 */
public final class DeadAirTags {
    /** Any radio that should participate in walkie tune / listen / note-drop logic. */
    public static final TagKey<Item> WALKIE_RADIOS = tag("walkie_radios");
    /** T1-tier companion (or stock) radios. */
    public static final TagKey<Item> WALKIE_T1 = tag("walkie_t1");
    /** T2-tier companion (or stock) radios — link-capable. */
    public static final TagKey<Item> WALKIE_T2 = tag("walkie_t2");

    private DeadAirTags() {}

    private static TagKey<Item> tag(String path) {
        return ItemTags.create(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, path));
    }
}
