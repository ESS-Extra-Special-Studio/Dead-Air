package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import uk.co.extraspecialstudio.dead_air.net.DeadAirNet;
import uk.co.extraspecialstudio.dead_air.net.EbTunePromptResponsePacket;
import uk.co.extraspecialstudio.extraspecial.esc.theme.EscFrameStyle;
import uk.co.extraspecialstudio.extraspecial.esc.theme.EscTheme;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscFonts;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscPanel;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscRect;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscScreen;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscText;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscUiStyle;

/** Small Yes/No overlay: Emergency Broadcast signal detected near a story/distress site. */
public class EbTunePromptScreen extends EscScreen {
    private static final int BOX_W = 280;
    private static final int BOX_H = 90;

    private static final EscUiStyle STYLE = new EscUiStyle(
        0, 0, 22,
        0xE0001808, 0xFF00E050, 1,
        90, 20, 20,
        0x00FF41, 0xA0FFC0,
        8, 0,
        0xFF00E050, 0xFF40FF80, 0xFF40FF80, 0x70A080,
        0.92f, EscFrameStyle.SQUARE,
        0.2f, 0.15f, 0f, 1f, EscTheme.vanilla()
    );

    private final String siteKey;
    private boolean answered;
    private EscRect boxRect = new EscRect(0, 0, BOX_W, BOX_H);

    public EbTunePromptScreen(String siteKey, String siteKind) {
        super(Component.translatable("screen.dead_air.eb_prompt.title"), STYLE);
        this.siteKey = siteKey != null ? siteKey : "";
    }

    @Override
    protected void buildLayout() {
        int x = (width - BOX_W) / 2;
        int y = (height - BOX_H) / 2;
        boxRect = new EscRect(x, y, BOX_W, BOX_H);
        int by = y + BOX_H - 28;
        int bw = (BOX_W - 30) / 2;
        addButton(Component.translatable("screen.dead_air.eb_prompt.yes"),
            new EscRect(x + 10, by, bw, 20), b -> answer(true));
        addButton(Component.translatable("screen.dead_air.eb_prompt.no"),
            new EscRect(x + 20 + bw, by, bw, 20), b -> answer(false));
    }

    private void answer(boolean accept) {
        if (answered) return;
        answered = true;
        DeadAirNet.CHANNEL.sendToServer(new EbTunePromptResponsePacket(siteKey, accept));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        EscPanel.renderPanel(g, boxRect, style);
        EscText.drawCentered(g, font, Component.translatable("screen.dead_air.eb_prompt.title"),
            width / 2, boxRect.y() + 12, opaque(style.textColorTitle()), EscFonts.DEFAULT);
        EscText.drawCentered(g, font, Component.translatable("screen.dead_air.eb_prompt.body"),
            width / 2, boxRect.y() + 30, opaque(style.textColorBody()), EscFonts.DEFAULT);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private static int opaque(int rgbOrArgb) {
        return (rgbOrArgb & 0xFF000000) == 0 ? (0xFF000000 | (rgbOrArgb & 0xFFFFFF)) : rgbOrArgb;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        declineIfUnanswered();
        super.onClose();
    }

    /**
     * Vanilla {@code setScreen(other)} calls {@link #removed()} without {@link #onClose()}.
     * Decline so the server prompt does not stick until the 30s timeout.
     */
    @Override
    public void removed() {
        declineIfUnanswered();
        super.removed();
    }

    private void declineIfUnanswered() {
        if (answered) return;
        answered = true;
        DeadAirNet.CHANNEL.sendToServer(new EbTunePromptResponsePacket(siteKey, false));
    }

    /** Call before {@code setScreen} replaces this prompt so {@link #removed()} does not decline. */
    public void suppressDecline() {
        answered = true;
    }
}
