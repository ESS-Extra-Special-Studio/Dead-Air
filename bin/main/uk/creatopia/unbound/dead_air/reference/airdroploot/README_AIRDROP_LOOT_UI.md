# Airdrop Loot Config UI – Reference for Structures Mod

This folder contains **reference UI code** for the “item arrays in config + difficulty points” airdrop loot idea. You can copy it into your structures mod (or an addon) and wire it to your JSON config and loot-table generation.

## What’s here

- **`AirdropLootEntry.java`** – One entry: item ID + difficulty points (+ optional category). Matches the idea of “every item and its difficulty point” in JSON.
- **`AirdropLootConfigScreen.java`** – Screen with:
  - JEI-style scrollable grid of items
  - Search box to filter by item ID or category
  - Click to toggle selection (highlighted border)
  - Total difficulty sum for selected items
  - Done / Cancel; **Done** calls your callback with the set of selected item IDs

## JSON config shape (for your backend)

agrek’s idea fits something like:

```json
[
  { "itemId": "minecraft:iron_ingot", "difficultyPoints": 1, "category": "materials" },
  { "itemId": "pointblank:pistol", "difficultyPoints": 3, "category": "guns" },
  { "itemId": "tacz:assault_rifle", "difficultyPoints": 5, "category": "guns" }
]
```

Load that into `List<AirdropLootEntry>`, and optionally persist “selected item IDs” per world or per config so you can pass `initialSelected` into the screen.

## How to use in your mod

1. **Copy** `AirdropLootEntry.java` and `AirdropLootConfigScreen.java` into your mod (change package to your mod’s package).
2. **Load your config** into `List<AirdropLootEntry>` (e.g. from JSON with item ID + difficulty points + category).
3. **Open the screen** when the player opens the “airdrop config” (e.g. from a block or key):

   ```java
   List<AirdropLootEntry> entries = YourConfigLoader.getEntries();
   Set<ResourceLocation> currentSelection = YourConfigLoader.getSelectedIds(); // from disk or default
   Minecraft.getInstance().setScreen(new AirdropLootConfigScreen(entries, currentSelection, selectedIds -> {
       YourConfigLoader.saveSelectedIds(selectedIds);
       YourLootTableGenerator.regenerateLootTables(selectedIds); // your API
   }));
   ```

4. **In the callback** (`onDone`):
   - Save the selected item IDs (to your JSON or world data).
   - Call your loot table generation / apply logic so airdrop loot tables reflect the selection and difficulty (e.g. wave difficulty from total points).

## UI behaviour

- **Search**: Filters by item ID and category (case-insensitive).
- **Click slot**: Toggles that item in/out of the selection.
- **Scroll**: Mouse wheel over the list scrolls (more items than fit on screen).
- **Total difficulty**: Shown above the list; sum of difficulty points of currently selected items (you can use this for “harder wave” logic).

## Dependencies

- Forge 1.20.1 (same as Dead Air). Uses `Screen`, `GuiGraphics`, `EditBox`, `Button`, `ForgeRegistries.ITEMS`. No Dead Air code; only the package is under Dead Air for reference.

If you need another Minecraft/Forge version, the same pattern works; you may need to adjust `renderBackground` and `mouseScrolled` signatures for that version.

## Optional: REI / JEI plugin

agrek suggested a REI plugin to “add this stuff” – you could add a REI (or JEI) entry that opens this same screen or shows the same config, and still use this UI as the in-game config screen so you have one shared “item list + difficulty” model and one place that writes the selected IDs and triggers loot table generation.
