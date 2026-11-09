package uk.co.extraspecialstudio.dead_air.reference.airdroploot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Consumer;

/**
 * Reference UI for airdrop loot config: JEI-style item list, select items, show difficulty points.
 * Give this to the structures mod: they supply the list of entries (from their JSON) and a callback
 * when the player clicks Done with the selected item IDs. They can then generate/apply loot tables from that.
 * <p>
 * Integration: copy this class (and AirdropLootEntry) into their mod, change package, and open this screen
 * with the list loaded from their config + a callback that saves selection and regenerates loot tables.
 */
@SuppressWarnings("null")
public class AirdropLootConfigScreen extends Screen {

    private static final int SLOT_SIZE = 24;
    private static final int COLS = 8;
    private static final int ROWS_VISIBLE = 8;
    private static final int HEADER_HEIGHT = 40;
    private static final int FOOTER_HEIGHT = 36;
    private static final int PADDING = 8;

    private final List<AirdropLootEntry> allEntries;
    private final Set<ResourceLocation> selectedIds = new HashSet<>();
    private final Consumer<Set<ResourceLocation>> onDone;

    private List<AirdropLootEntry> filteredEntries = new ArrayList<>();
    private EditBox searchBox;
    private int scrollOffset = 0;
    private int listAreaY, listAreaHeight, listAreaX, listAreaWidth;
    private int totalSlots;

    /**
     * @param entries  All items that can be chosen (from your JSON config with difficulty points).
     * @param initialSelected  Item IDs that are currently selected (e.g. from saved config).
     * @param onDone   Callback with the set of selected item IDs when the player clicks Done.
     */
    public AirdropLootConfigScreen(
        List<AirdropLootEntry> entries,
        Set<ResourceLocation> initialSelected,
        Consumer<Set<ResourceLocation>> onDone
    ) {
        super(Component.literal("Airdrop Loot"));
        this.allEntries = entries != null ? new ArrayList<>(entries) : new ArrayList<>();
        this.selectedIds.addAll(initialSelected != null ? initialSelected : Set.of());
        this.onDone = onDone != null ? onDone : (set) -> {};
        refreshFilter();
    }

    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;
        listAreaWidth = COLS * SLOT_SIZE + PADDING * 2;
        listAreaHeight = ROWS_VISIBLE * SLOT_SIZE;
        listAreaX = centerX - listAreaWidth / 2;
        listAreaY = HEADER_HEIGHT + PADDING;

        searchBox = new EditBox(font, centerX - 120, 18, 240, 18, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search items..."));
        searchBox.setMaxLength(64);
        searchBox.setResponder(s -> {
            refreshFilter();
            scrollOffset = 0;
        });
        addRenderableWidget(searchBox);

        totalSlots = filteredEntries.size();

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> {
            onDone.accept(new HashSet<>(selectedIds));
            onClose();
        }).bounds(centerX - 100, height - FOOTER_HEIGHT + 4, 80, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
            .bounds(centerX + 20, height - FOOTER_HEIGHT + 4, 80, 20).build());
    }

    private void refreshFilter() {
        String q = (searchBox == null ? "" : searchBox.getValue()).toLowerCase().trim();
        filteredEntries = allEntries.stream()
            .filter(e -> q.isEmpty()
                || e.getItemId().toString().toLowerCase().contains(q)
                || e.getCategory().toLowerCase().contains(q))
            .toList();
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui, mouseX, mouseY, partialTick);
        refreshFilter();
        totalSlots = filteredEntries.size();

        // Title
        gui.drawCenteredString(font, getTitle().getString(), width / 2, 6, 0xFFFFFF);

        // Total difficulty
        int total = filteredEntries.stream()
            .filter(e -> selectedIds.contains(e.getItemId()))
            .mapToInt(AirdropLootEntry::getDifficultyPoints)
            .sum();
        gui.drawString(font, "Total difficulty: " + total + " pts", listAreaX, listAreaY - 10, 0xAAAAAA);

        // List background
        gui.fill(listAreaX - 2, listAreaY - 2,
            listAreaX + listAreaWidth + 2, listAreaY + listAreaHeight + 2, 0xFF202020);
        gui.fill(listAreaX, listAreaY, listAreaX + listAreaWidth, listAreaY + listAreaHeight, 0xFF101010);

        // Slots
        int maxScroll = Math.max(0, (int) Math.ceil((double) totalSlots / COLS) - ROWS_VISIBLE);
        for (int row = 0; row < ROWS_VISIBLE; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = (scrollOffset + row) * COLS + col;
                if (index >= totalSlots) continue;
                AirdropLootEntry entry = filteredEntries.get(index);
                int x = listAreaX + PADDING + col * SLOT_SIZE;
                int y = listAreaY + PADDING + row * SLOT_SIZE;
                boolean selected = selectedIds.contains(entry.getItemId());
                boolean hover = mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE;

                if (selected) {
                    gui.fill(x - 1, y - 1, x + SLOT_SIZE + 1, y + SLOT_SIZE + 1, 0xFF4080FF);
                } else if (hover) {
                    gui.fill(x - 1, y - 1, x + SLOT_SIZE + 1, y + SLOT_SIZE + 1, 0xFF606060);
                }

                ItemStack stack = entry.createStack();
                if (!stack.isEmpty()) {
                    gui.renderItem(stack, x + 4, y + 4);
                    gui.renderItemDecorations(font, stack, x + 4, y + 4);
                }
                gui.drawString(font, "" + entry.getDifficultyPoints(), x + SLOT_SIZE - 10, y + SLOT_SIZE - 8, 0xFFFF00);
            }
        }

        // Scroll hint
        if (maxScroll > 0) {
            gui.drawString(font, "Scroll: mouse wheel (offset " + scrollOffset + ")", listAreaX, listAreaY + listAreaHeight + 4, 0x888888);
        }

        super.render(gui, mouseX, mouseY, partialTick);

        // Tooltip
        for (int row = 0; row < ROWS_VISIBLE; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = (scrollOffset + row) * COLS + col;
                if (index >= totalSlots) continue;
                int x = listAreaX + PADDING + col * SLOT_SIZE;
                int y = listAreaY + PADDING + row * SLOT_SIZE;
                if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                    AirdropLootEntry entry = filteredEntries.get(index);
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.literal(entry.getItemId().toString()));
                    tooltip.add(Component.literal("Difficulty: " + entry.getDifficultyPoints() + " pts"));
                    if (!entry.getCategory().isEmpty()) {
                        tooltip.add(Component.literal("Category: " + entry.getCategory()));
                    }
                    tooltip.add(Component.literal("Click to toggle"));
                    gui.renderComponentTooltip(font, tooltip, mouseX, mouseY);
                    return;
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int row = 0; row < ROWS_VISIBLE; row++) {
                for (int col = 0; col < COLS; col++) {
                    int index = (scrollOffset + row) * COLS + col;
                    if (index >= totalSlots) continue;
                    int x = listAreaX + PADDING + col * SLOT_SIZE;
                    int y = listAreaY + PADDING + row * SLOT_SIZE;
                    if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                        ResourceLocation id = filteredEntries.get(index).getItemId();
                        if (selectedIds.contains(id)) selectedIds.remove(id);
                        else selectedIds.add(id);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= listAreaX && mouseX <= listAreaX + listAreaWidth
            && mouseY >= listAreaY && mouseY <= listAreaY + listAreaHeight) {
            int maxScroll = Math.max(0, (int) Math.ceil((double) totalSlots / COLS) - ROWS_VISIBLE);
            scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(scrollY), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

}
