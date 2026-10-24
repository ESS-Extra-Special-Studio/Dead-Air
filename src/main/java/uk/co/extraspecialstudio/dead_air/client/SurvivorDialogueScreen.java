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
 * Survivor dialogue — EscScreen shell matching Dead Air / ESC stack look
 * (theme-aware panel + EscButtons), same action grid as before.
 */
public class SurvivorDialogueScreen extends EscScreen {
    private static final int BOX_W = 340;
    private static final int BOX_H_MIN = 140;
    private static final int BTN_H = 20;
    private static final int BTN_GAP = 2;
    private static final int ROW_STEP = BTN_H + BTN_GAP;

    /** Compact dialogue chrome — olive radio aesthetic, still theme-linked accents. */
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
    private EscRect boxRect = new EscRect(0, 0, BOX_W, BOX_H_MIN);
    private int boxH = BOX_H_MIN;
    /** Opening equipment keeps the survivor facing you until that menu closes. */
    private boolean keepTalkingAfterClose;

    public SurvivorDialogueScreen(SurvivorScreenPacket data) {
        super(Component.literal(data.name), DIALOGUE_STYLE);
        this.data = data;
    }

    @Override
    protected void buildLayout() {
        lines = font.split(Component.translatable(data.line), BOX_W - 20);
        int lineBlock = Math.max(10, lines.size() * 10);
        int header = 34 + lineBlock + 6;

        List<Btn> buttons = collectButtons();
        int simpleRows = buttons.isEmpty()
            ? (data.missing && data.ownedByYou ? 1
                : (data.state == SurvivorEntity.STRANDED && !data.ownedByYou ? 2 : 1))
            : 0;

        // Fit all action buttons: more columns + tighter rows on short screens (no off-panel buttons).
        int maxBox = Math.max(BOX_H_MIN, height - 16);
        int cols = 3;
        int rows;
        int rowStep = ROW_STEP;
        if (buttons.isEmpty()) {
            rows = simpleRows;
        } else {
            rows = (buttons.size() + cols - 1) / cols;
            if (header + rows * rowStep + 12 > maxBox) {
                cols = 4;
                rows = (buttons.size() + cols - 1) / cols;
            }
            if (header + rows * rowStep + 12 > maxBox) {
                cols = Math.min(5, Math.max(3, buttons.size()));
                rows = (buttons.size() + cols - 1) / cols;
            }
            int budget = maxBox - header - 12;
            if (rows > 0 && rows * rowStep > budget) {
                rowStep = Math.max(BTN_H, budget / rows);
            }
        }

        boxH = Math.min(maxBox, Math.max(BOX_H_MIN, header + rows * rowStep + 12));
        int x = (width - BOX_W) / 2;
        int y = (height - boxH) / 2;
        boxRect = new EscRect(x, y, BOX_W, boxH);

        int bw = (BOX_W - 30) / 2;
        int gap = 8;
        int tw = Math.max(48, (BOX_W - 20 - gap * (cols - 1)) / cols);
        int btnTop = y + header;

        if (data.missing && data.ownedByYou) {
            addButton(Component.translatable("screen.dead_air.survivor.follow"),
                new EscRect(x + 10, btnTop, bw, BTN_H), b -> act(SurvivorActionPacket.FOLLOW));
            addButton(Component.translatable("screen.dead_air.survivor.who"),
                new EscRect(x + 20 + bw, btnTop, bw, BTN_H), b -> act(SurvivorActionPacket.WHO_ARE_YOU));
            return;
        }

        if (data.state == SurvivorEntity.STRANDED && !data.ownedByYou) {
            addButton(Component.translatable("screen.dead_air.survivor.who"),
                new EscRect(x + 10, btnTop, BOX_W - 20, BTN_H), b -> act(SurvivorActionPacket.WHO_ARE_YOU));
            addButton(Component.translatable("screen.dead_air.survivor.recruit"),
                new EscRect(x + 10, btnTop + rowStep, bw, BTN_H), b -> act(SurvivorActionPacket.RECRUIT));
            addButton(Component.translatable("screen.dead_air.survivor.not_now"),
                new EscRect(x + 20 + bw, btnTop + rowStep, bw, BTN_H), b -> onClose());
            return;
        }

        if (!data.ownedByYou) {
            addButton(Component.translatable("gui.done"),
                new EscRect(x + (BOX_W - bw) / 2, btnTop, bw, BTN_H), b -> onClose());
            return;
        }

        int i = 0;
        for (Btn b : buttons) {
            int col = i % cols;
            int r = i / cols;
            int bx = x + 10 + col * (tw + gap);
            int by = btnTop + r * rowStep;
            addButton(Component.translatable(b.key), new EscRect(bx, by, tw, BTN_H), btn -> act(b.action));
            i++;
        }
    }

    private List<Btn> collectButtons() {
        List<Btn> buttons = new ArrayList<>();
        if (!data.ownedByYou || data.missing) return buttons;

        if (data.state == SurvivorEntity.FOLLOWING) {
            if (data.hasBase) {
                buttons.add(new Btn("screen.dead_air.survivor.send_base", SurvivorActionPacket.SEND_TO_BASE));
            }
            buttons.add(new Btn("screen.dead_air.survivor.settle", SurvivorActionPacket.SETTLE));
            buttons.add(new Btn("screen.dead_air.survivor.equipment", SurvivorActionPacket.OPEN_EQUIPMENT));
            buttons.add(new Btn("screen.dead_air.survivor.chat", SurvivorActionPacket.IDLE_CHAT));
            if ("medic".equals(data.profession)) {
                buttons.add(new Btn("screen.dead_air.survivor.heal", SurvivorActionPacket.HEAL));
            }
            buttons.add(new Btn("screen.dead_air.survivor.defend", SurvivorActionPacket.DEFEND_HERE));
            buttons.add(new Btn("screen.dead_air.survivor.dismiss", SurvivorActionPacket.DISMISS));
        } else if (data.state == SurvivorEntity.SETTLED) {
            if (!"pilot".equals(data.profession)) {
                buttons.add(new Btn("screen.dead_air.survivor.follow", SurvivorActionPacket.FOLLOW));
            }
            buttons.add(new Btn("screen.dead_air.survivor.equipment", SurvivorActionPacket.OPEN_EQUIPMENT));
            buttons.add(new Btn("screen.dead_air.survivor.chat", SurvivorActionPacket.IDLE_CHAT));
            if ("medic".equals(data.profession)) {
                buttons.add(new Btn("screen.dead_air.survivor.heal", SurvivorActionPacket.HEAL));
            }
            if ("civilian".equals(data.profession)) {
                buttons.add(new Btn("screen.dead_air.survivor.train_scavenger", SurvivorActionPacket.TRAIN_SCAVENGER));
                buttons.add(new Btn("screen.dead_air.survivor.train_medic", SurvivorActionPacket.TRAIN_MEDIC));
                buttons.add(new Btn("screen.dead_air.survivor.train_radio", SurvivorActionPacket.TRAIN_RADIO));
                buttons.add(new Btn("screen.dead_air.survivor.train_military", SurvivorActionPacket.TRAIN_MILITARY));
                buttons.add(new Btn("screen.dead_air.survivor.train_pilot", SurvivorActionPacket.TRAIN_PILOT));
            }
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
        return buttons;
    }

    private void act(int action) {
        keepTalkingAfterClose = action == SurvivorActionPacket.OPEN_EQUIPMENT;
        DeadAirNet.CHANNEL.sendToServer(new SurvivorActionPacket(data.entityId, action));
        onClose();
    }

    @Override
    public void onClose() {
        if (!keepTalkingAfterClose) {
            DeadAirNet.CHANNEL.sendToServer(
                new SurvivorActionPacket(data.entityId, SurvivorActionPacket.END_DIALOGUE));
        }
        super.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        EscPanel.renderPanel(g, boxRect, style);
        EscText.drawString(g, font, data.name, boxRect.x() + 10, boxRect.y() + 8,
            opaque(style.textColorTitle()), false, uk.co.extraspecialstudio.extraspecial.esc.ui.EscFonts.DEFAULT);
        Component prof = Component.translatable("survivor.dead_air.profession." + data.profession);
        g.drawString(font, prof, boxRect.x() + BOX_W - 10 - font.width(prof), boxRect.y() + 8,
            opaque(style.mutedColor()), false);
        Component meta = Component.translatable("survivor.dead_air.personality." + data.personality)
            .append(" · ")
            .append(Component.translatable("survivor.dead_air.duty." + data.jobId));
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
