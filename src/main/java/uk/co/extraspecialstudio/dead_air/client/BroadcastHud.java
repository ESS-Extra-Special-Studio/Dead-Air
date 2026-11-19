package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import uk.co.extraspecialstudio.extraspecial.esc.theme.EscFrameStyle;
import uk.co.extraspecialstudio.extraspecial.esc.theme.EscTheme;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscFonts;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscPanel;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscRect;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscText;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscUiStyle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Typewriter-style transmission box at the top of the screen. One transmission at a time. */
public final class BroadcastHud {
    private static final long CHAR_MS = 28L;
    private static final long HOLD_MS = 9000L;
    private static final long FADE_MS = 1000L;
    private static final int MAX_WIDTH = 300;

    private static final EscUiStyle STYLE_STORY = new EscUiStyle(
        0, 0, 18,
        0xB00A0A0E, 0xFFD03030, 1,
        90, 20, 20,
        0xD03030, 0x7FE7FF,
        8, 0,
        0xFFD03030, 0xFFFF6060, 0xFFE0A030, 0x9A9A9A,
        0.88f, EscFrameStyle.SQUARE,
        0.2f, 0.12f, 0f, 1f, EscTheme.vanilla()
    );

    private static final EscUiStyle STYLE_NORMAL = new EscUiStyle(
        0, 0, 18,
        0xB00A0A0E, 0xFFE0A030, 1,
        90, 20, 20,
        0xE0A030, 0x9A9A9A,
        8, 0,
        0xFFE0A030, 0xFFFFC060, 0xFFE0A030, 0x9A9A9A,
        0.88f, EscFrameStyle.SQUARE,
        0.2f, 0.1f, 0f, 1f, EscTheme.vanilla()
    );

    private record Transmission(String title, List<String> lines, boolean decoded, boolean story) {}

    private static final Deque<Transmission> QUEUE = new ArrayDeque<>();
    private static Transmission current;
    private static long startMs;

    public static void show(String title, List<String> lines, boolean decoded, boolean story) {
        QUEUE.add(new Transmission(title, List.copyOf(lines), decoded, story));
    }

    public static void clear() {
        QUEUE.clear();
        current = null;
    }

    public static void render(GuiGraphics g, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        long now = System.currentTimeMillis();
        if (current == null) {
            current = QUEUE.poll();
            if (current == null) return;
            startMs = now;
        }
        int totalChars = 0;
        for (String l : current.lines) totalChars += l.length();
        long typeMs = totalChars * CHAR_MS;
        long elapsed = now - startMs;
        if (elapsed > typeMs + HOLD_MS + FADE_MS) {
            current = null;
            return;
        }
        float alpha = elapsed > typeMs + HOLD_MS ? 1f - (elapsed - typeMs - HOLD_MS) / (float) FADE_MS : 1f;
        int a = Math.max(8, (int) (alpha * 255));

        Font font = mc.font;
        int revealed = (int) (elapsed / CHAR_MS);
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (String line : current.lines) {
            int take = Math.max(0, Math.min(line.length(), revealed));
            revealed -= line.length();
            if (take == 0) break;
            String shown = take < line.length() ? line.substring(0, take) + "_" : line;
            wrapped.addAll(font.split(Component.literal(shown), MAX_WIDTH));
        }

        EscUiStyle style = current.story ? STYLE_STORY : STYLE_NORMAL;
        int boxW = MAX_WIDTH + 16;
        int boxH = 22 + wrapped.size() * 10 + 6;
        int x = (width - boxW) / 2;
        int y = 28;
        EscRect panel = new EscRect(x, y, boxW, boxH);
        // Fade by temporarily lowering fill opacity via a translucent overlay after ESC chrome.
        EscPanel.renderPanel(g, panel, style);
        if (alpha < 0.99f) {
            EscPanel.fill(g, panel, ((int) ((1f - alpha) * 180) << 24));
        }

        boolean blink = (now / 400) % 2 == 0;
        int titleColor = (a << 24) | (style.textColorTitle() & 0xFFFFFF);
        EscText.drawString(g, font, (blink ? "\u25CF " : "  ") + current.title, x + 8, y + 6,
            titleColor, false, EscFonts.DEFAULT);
        int lineColor = (a << 24) | ((current.decoded ? 0x7FE7FF : style.mutedColor()) & 0xFFFFFF);
        int ly = y + 20;
        for (FormattedCharSequence seq : wrapped) {
            g.drawString(font, seq, x + 8, ly, lineColor, false);
            ly += 10;
        }
    }

    private BroadcastHud() {}
}
