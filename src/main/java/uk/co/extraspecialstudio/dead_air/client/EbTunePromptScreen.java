package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import uk.co.extraspecialstudio.dead_air.net.DeadAirNet;
import uk.co.extraspecialstudio.dead_air.net.EbTunePromptResponsePacket;

/** Small Yes/No overlay: Emergency Broadcast signal detected near a story/distress site. */
public class EbTunePromptScreen extends Screen {
    private static final int BOX_W = 280;
    private static final int BOX_H = 90;
    private final String siteKey;
    private boolean answered;

    public EbTunePromptScreen(String siteKey, String siteKind) {
        super(Component.translatable("screen.dead_air.eb_prompt.title"));
        this.siteKey = siteKey != null ? siteKey : "";
    }

    @Override
    protected void init() {
        int x = (width - BOX_W) / 2;
        int y = (height - BOX_H) / 2;
        int by = y + BOX_H - 28;
        int bw = (BOX_W - 30) / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.dead_air.eb_prompt.yes"),
            b -> answer(true)).bounds(x + 10, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.dead_air.eb_prompt.no"),
            b -> answer(false)).bounds(x + 20 + bw, by, bw, 20).build());
    }

    private void answer(boolean accept) {
        if (answered) return;
        answered = true;
        DeadAirNet.sendToServer(new EbTunePromptResponsePacket(siteKey, accept));
        onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        int x = (width - BOX_W) / 2;
        int y = (height - BOX_H) / 2;
        g.fill(x, y, x + BOX_W, y + BOX_H, 0xE0001808);
        g.fill(x, y, x + BOX_W, y + 1, 0xFF00E050);
        g.fill(x, y + BOX_H - 1, x + BOX_W, y + BOX_H, 0xFF00E050);
        g.fill(x, y, x + 1, y + BOX_H, 0xFF00E050);
        g.fill(x + BOX_W - 1, y, x + BOX_W, y + BOX_H, 0xFF00E050);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int y = (height - BOX_H) / 2;
        g.drawCenteredString(font, Component.translatable("screen.dead_air.eb_prompt.title"), width / 2, y + 12, 0x00FF41);
        g.drawCenteredString(font, Component.translatable("screen.dead_air.eb_prompt.body"), width / 2, y + 30, 0xA0FFC0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (!answered) {
            answered = true;
            DeadAirNet.sendToServer(new EbTunePromptResponsePacket(siteKey, false));
        }
        super.onClose();
    }
}
