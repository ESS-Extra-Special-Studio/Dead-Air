package uk.co.extraspecialstudio.dead_air.reference.airdroploot;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * One configurable airdrop loot entry: item + difficulty points.
 * Matches the "item arrays in config" + "difficulty point" idea from the structures mod.
 * Your backend can load these from JSON and pass the list to {@link AirdropLootConfigScreen}.
 */
public class AirdropLootEntry {
    private final ResourceLocation itemId;
    private final int difficultyPoints;
    private final String category; // optional, e.g. "guns", "materials", "food"

    public AirdropLootEntry(ResourceLocation itemId, int difficultyPoints) {
        this(itemId, difficultyPoints, "");
    }

    public AirdropLootEntry(ResourceLocation itemId, int difficultyPoints, String category) {
        this.itemId = itemId;
        this.difficultyPoints = Math.max(0, difficultyPoints);
        this.category = category != null ? category : "";
    }

    public ResourceLocation getItemId() {
        return itemId;
    }

    public int getDifficultyPoints() {
        return difficultyPoints;
    }

    public String getCategory() {
        return category;
    }

    /** Build an ItemStack for this entry (for rendering). Returns empty stack if item not in registry. */
    public ItemStack createStack() {
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    /** Example JSON shape for your config (you can use Gson or your loader):
     * <pre>
     * { "itemId": "minecraft:iron_ingot", "difficultyPoints": 1, "category": "materials" }
     * </pre>
     */
}
