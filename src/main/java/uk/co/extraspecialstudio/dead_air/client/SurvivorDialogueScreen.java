package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import uk.co.extraspecialstudio.dead_air.net.DeadAirNet;
import uk.co.extraspecialstudio.dead_air.net.SurvivorActionPacket;
import uk.co.extraspecialstudio.dead_air.net.SurvivorScreenPacket;
import uk.co.extraspecialstudio.dead_air.survivor.SurvivorEntity;
import uk.co.extraspecialstudio.extraspecial.esc.theme.EscFrameStyle;
import uk.co.extraspecialstudio.extraspecial.esc.theme.EscTheme;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscPanel;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscRect;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscScreen;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscText;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscUiStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Survivor dialogue â€” EscScreen shell matching Dead Air / ESC stack look
 * (theme-aware panel + EscButtons), same action grid as before.
 */
public class SurvivorDialogueScreen extends EscScreen {
    private static final int BOX_W = 300;
    private static final int BOX_H = 232;

    /** Compact dialogue chrome â€” olive radio aesthetic, still theme-linked accents. */
    private static final EscUiStyle DIALOGUE_STYLE = new EscUiStyle(
        0, 0, 22,
        0xE0101418, 0xFF6B8F6B, 1,
        90, 20, 20,
        0xE8E0C8, 0xC8C8C8,
        8, 0,
        0xFF6B8F6B, 0xFF9AB89A, 0xFF9AB89A, 0x909090,
        0.90f, EscFrameStyle.SQUARE,
        0.25f, 0.08f, 0f, 1f, EscTheme.vanilla()
    );

    private final SurvivorScreenPacket data;
    private List<FormattedCharSequence> lines = List.of();
    private EscRect boxRect = new EscRect(0, 0, BOX_W, BOX_H);

    public SurvivorDialogueScreen(SurvivorScreenPacket data) {
        super(Component.literal(data.name), DIALOGUE_STYLE);
        this.data = data;
    }

    @Override
    protected void buildLayout() {
        int x = (width - BOX_W) / 2;
        int y = (height - BOX_H) / 2;
        boxRect = new EscRect(x, y, BOX_W, BOX_H);
        lines = font.split(Component.translatable(data.line), BOX_W - 20);
        int bw = (BOX_W - 30) / 2;
        int tw = (BOX_W - 40) / 3;
        int row = y + BOX_H - 72;

        if (data.missing && data.ownedByYou) {
            addButton(Component.translatable("screen.dead_air.survivor.follow"),
                new EscRect(x + 10, row + 24, bw, 20), b -> act(SurvivorActionPacket.FOLLOW));
            addButton(Component.translatable("screen.dead_air.survivor.who"),
                new EscRect(x + 20 + bw, row + 24, bw, 20), b -> act(SurvivorActionPacket.WHO_ARE_YOU));
            return;
        }

        if (data.state == SurvivorEntity.STRANDED && !data.ownedByYou) {
            addButton(Component.translatable("screen.dead_air.survivor.recruit"),
                new EscRect(x + 10, row + 24, bw, 20), b -> act(SurvivorActionPacket.RECRUIT));
            addButton(Component.translatable("screen.dead_air.survivor.not_now"),
                new EscRect(x + 20 + bw, row + 24, bw, 20), b -> onClose());
            addButton(Component.translatable("screen.dead_air.survivor.who"),
                new EscRect(x + 10, row, BOX_W - 20, 20), b -> act(SurvivorActionPacket.WHO_ARE_YOU));
            return;
        }

        if (!data.ownedByYou) {
            addButton(Component.translatable("gui.done"),
                new EscRect(x + (BOX_W - bw) / 2, row + 24, bw, 20), b -> onClose());
            return;
        }

        List<Btn> buttons = new ArrayList<>();
        if (data.state == SurvivorEntity.FOLLOWING) {
            if (data.hasBase) {
                buttons.add(new Btn("screen.dead_air.survivor.send_base", SurvivorActionPacket.SEND_TO_BASE));
            }
            buttons.add(new Btn("screen.dead_air.survivor.settle", SurvivorActionPacket.SETTLE));
            buttons.add(new Btn("screen.dead_air.survivor.equipment", SurvivorActionPacket.OPEN_EQUIPMENT));
            buttons.add(new Btn("screen.dead_air.survivor.chat", SurvivorActionPacket.IDLE_CHAT));
            buttons.add(new Btn("screen.dead_air.survivor.defend", SurvivorActionPacket.DEFEND_HERE));
            buttons.add(new Btn("screen.dead_air.survivor.dismiss", SurvivorActionPacket.DISMISS));
        } else if (data.state == SurvivorEntity.SETTLED) {
            buttons.add(new Btn("screen.dead_air.survivor.follow", SurvivorActionPacket.FOLLOW));
            buttons.add(new Btn("screen.dead_air.survivor.equipment", SurvivorActionPacket.OPEN_EQUIPMENT));
            buttons.add(new Btn("screen.dead_air.survivor.chat", SurvivorActionPacket.IDLE_CHAT));
            buttons.add(new Btn("screen.dead_air.survivor.job", SurvivorActionPacket.CYCLE_JOB));
            buttons.add(new Btn("screen.dead_air.survivor.defend", SurvivorActionPacket.DEFEND_HERE));
            buttons.add(new Btn("screen.dead_air.survivor.patrol_add", SurvivorActionPacket.PATROL_ADD));
            buttons.add(new Btn("screen.dead_air.survivor.patrol_start", SurvivorActionPacket.PATROL_START));
            buttons.add(new Btn("screen.dead_air.survivor.patrol_clear", SurvivorActionPacket.PATROL_CLEAR));
            if (data.hasBase) {
                buttons.add(new Btn("screen.dead_air.survivor.send_base", SurvivorActionPacket.SEND_TO_BASE));
            }
            if (data.supplyReady) {
                buttons.add(new Btn("screen.dead_air.survivor.collect", SurvivorActionPacket.COLLECT));
            }
            buttons.add(new Btn("screen.dead_air.survivor.dismiss", SurvivorActionPacket.DISMISS));
        }

        buttons.add(new Btn("screen.dead_air.survivor.who", SurvivorActionPacket.WHO_ARE_YOU));

        int i = 0;
        int startY = y + 54;
        for (Btn b : buttons) {
            int col = i % 3;
            int r = i / 3;
            int bx = x + 10 + col * (tw + 10);
            int by = startY + r * 22;
            if (by + 20 > y + BOX_H - 8) break;
            addButton(Component.translatable(b.key), new EscRect(bx, by, tw, 20), btn -> act(b.action));
            i++;
        }
    }

    private void act(int action) {
        DeadAirNet.sendToServer(new SurvivorActionPacket(data.entityId, action));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        EscPanel.renderPanel(g, boxRect, style);
        EscText.drawString(g, font, data.name, boxRect.x() + 10, boxRect.y() + 8,
            opaque(style.textColorTitle()), false, uk.co.extraspecialstudio.extraspecial.esc.ui.EscFonts.DEFAULT);
        Component prof = Component.translatable("survivor.dead_air.profession." + data.profession);
        g.drawString(font, prof, boxRect.x() + BOX_W - 10 - font.width(prof), boxRect.y() + 8,
            opaque(style.mutedColor()), false);
        Component meta = Component.translatable("survivor.dead_air.personality." + data.personality)
            .append(" Â· ")
            .append(Component.translatable("survivor.dead_air.job." + data.jobId));
        g.drawString(font, meta, boxRect.x() + 10, boxRect.y() + 20,
            opaque(style.secondaryColor()), false);
        int ly = boxRect.y() + 34;
        for (FormattedCharSequence seq : lines) {
            g.drawString(font, seq, boxRect.x() + 10, ly, opaque(style.textColorBody()), false);
            ly += 10;
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private static int opaque(int rgbOrArgb) {
        return (rgbOrArgb & 0xFF000000) == 0 ? (0xFF000000 | (rgbOrArgb & 0xFFFFFF)) : rgbOrArgb;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Btn(String key, int action) {}
}
