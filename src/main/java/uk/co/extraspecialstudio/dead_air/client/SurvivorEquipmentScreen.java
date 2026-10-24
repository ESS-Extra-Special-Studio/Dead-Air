package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import uk.co.extraspecialstudio.dead_air.menu.SurvivorEquipmentMenu;
import uk.co.extraspecialstudio.extraspecial.esc.theme.EscFrameStyle;
import uk.co.extraspecialstudio.extraspecial.esc.theme.EscTheme;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscFonts;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscPanel;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscRect;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscText;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscUiStyle;

/** Survivor gear grid — ESC panel chrome; scavengers also show a 3×3 satchel. */
public class SurvivorEquipmentScreen extends AbstractContainerScreen<SurvivorEquipmentMenu> {
    private static final EscUiStyle STYLE = new EscUiStyle(
        0, 0, 18,
        0xE0101418, 0xFF6B8F6B, 1,
        90, 20, 20,
        0xE8E0C8, 0xC8C8C8,
        8, 0,
        0xFF6B8F6B, 0xFF9AB89A, 0xFF9AB89A, 0x909090,
        0.90f, EscFrameStyle.SQUARE,
        0.25f, 0.08f, 0f, 1f, EscTheme.vanilla()
    );

    public SurvivorEquipmentScreen(SurvivorEquipmentMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = menu.hasLootPack() ? 222 : 184;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        EscRect panel = new EscRect(leftPos, topPos, imageWidth, imageHeight);
        EscPanel.renderPanel(g, panel, STYLE);
        int equipEnd = menu.hasLootPack() ? 6 + 9 : 6;
        for (var slot : menu.slots) {
            if (slot.index < equipEnd) {
                EscRect frame = new EscRect(leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18);
                EscPanel.fill(g, frame, EscPanel.withOpacity(0xFF2A3038, 0.95f));
                EscPanel.renderFrame(g, frame, STYLE.panelBorderColor(), 1, EscFrameStyle.SQUARE);
            }
        }
        if (menu.hasLootPack()) {
            EscText.drawString(g, font,
                Component.translatable("screen.dead_air.survivor.loot_pack").getString(),
                leftPos + 116, topPos + 6,
                opaque(STYLE.mutedColor()), false, EscFonts.DEFAULT);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        EscText.drawString(g, font, title.getString(), titleLabelX, titleLabelY,
            opaque(STYLE.textColorTitle()), false, EscFonts.DEFAULT);
        EscText.drawString(g, font, playerInventoryTitle.getString(), inventoryLabelX, inventoryLabelY,
            opaque(STYLE.mutedColor()), false, EscFonts.DEFAULT);
    }

    private static int opaque(int rgbOrArgb) {
        return (rgbOrArgb & 0xFF000000) == 0 ? (0xFF000000 | (rgbOrArgb & 0xFFFFFF)) : rgbOrArgb;
    }
}
