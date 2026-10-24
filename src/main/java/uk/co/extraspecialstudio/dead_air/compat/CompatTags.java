package uk.co.extraspecialstudio.dead_air.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/** Item tags for soft-compat loot / gun detection. Entries resolve only when the item exists. */
public final class CompatTags {
    public static final TagKey<Item> GUNS = tag("compat/guns");
    public static final TagKey<Item> COLD_SWEAT = tag("compat/cold_sweat");
    public static final TagKey<Item> TOUGH_AS_NAILS = tag("compat/tough_as_nails");
    public static final TagKey<Item> VEHICLE_PARTS = tag("compat/vehicle_parts");
    public static final TagKey<Item> AIRCRAFT_PARTS = tag("compat/aircraft_parts");
    /** Canned goods / snacks / water from Apocalypse Now, Survival Instinct, Zombie Survival Kit. */
    public static final TagKey<Item> SCAVENGE_FOOD = tag("compat/scavenge_food");
    /** Bandages, antibiotics, basic kits — no morphine injectors / blood bags in EB chests. */
    public static final TagKey<Item> SCAVENGE_MEDICAL = tag("compat/scavenge_medical");
    /** Mid-tier apparel / scrap tools for chests (not exo / juggernaut). */
    public static final TagKey<Item> SCAVENGE_GEAR = tag("compat/scavenge_gear");

    private static TagKey<Item> tag(String path) {
        return ItemTags.create(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, path));
    }

    private CompatTags() {}
}
