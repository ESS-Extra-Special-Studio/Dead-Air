package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import uk.co.extraspecialstudio.dead_air.menu.SurvivorEquipmentMenu;

public class SurvivorEquipmentScreen extends AbstractContainerScreen<SurvivorEquipmentMenu> {
    public SurvivorEquipmentScreen(SurvivorEquipmentMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 184;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xE0101418);
        g.fill(x, y, x + imageWidth, y + 1, 0xFF6B8F6B);
        g.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, 0xFF6B8F6B);
        // slot frames
        for (var slot : menu.slots) {
            if (slot.index < 6) {
                int sx = x + slot.x - 1;
                int sy = y + slot.y - 1;
                g.fill(sx, sy, sx + 18, sy + 18, 0xFF2A3038);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
