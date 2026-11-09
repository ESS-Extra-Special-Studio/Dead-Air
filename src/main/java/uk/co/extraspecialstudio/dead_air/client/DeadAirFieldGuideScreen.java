package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import uk.co.extraspecialstudio.extraspecial.esc.theme.EscFrameStyle;
import uk.co.extraspecialstudio.extraspecial.esc.theme.EscTheme;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscAnchor;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscLayoutSpec;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscRect;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscScreen;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscUiStyle;
import uk.co.extraspecialstudio.dead_air.client.guide.FieldGuideContent;
import uk.co.extraspecialstudio.dead_air.client.guide.FieldGuideContent.GuideSection;

import java.util.List;

/**
 * In-game guide UI for Dead Air: index has section shortcuts only; content pages use
 * bottom Back / Forward / Index only. Forward advances through all pages in order.
 */
@SuppressWarnings("null")
public class DeadAirFieldGuideScreen extends EscScreen {

    private static final int BODY_TOP_OFFSET = 34;
    private static final int FOOTER_HEIGHT = 28;
    private static final int PAGE_PADDING = 18;
    /** Line spacing for wrapped body text */
    private static final int LINE_STEP = 9;

    private static final int INDEX_ROW_HEIGHT = 22;
    private static final int INDEX_ROW_GAP = 4;
    /** Pixels above the footer nav reserved so list rows never overlap controls */
    private static final int INDEX_ABOVE_NAV_PADDING = 36;

    private static final int NAV_BUTTON_W = 70;
    private static final int NAV_BUTTON_H = 20;
    private static final int NAV_GAP = 6;

    private static final EscUiStyle GUIDE_STYLE = new EscUiStyle(
        PAGE_PADDING, 12, 28,
        0xAA101010, 0x44FFFFFF, 1,
        NAV_BUTTON_W, NAV_BUTTON_H, INDEX_ROW_HEIGHT,
        0xE8E8E8, 0xD6D6D6,
        FOOTER_HEIGHT, 10,
        0xFF66A0FF, 0xFFFFFFFF, 0xFF88B0FF, 0x909090, 0.88f, EscFrameStyle.SQUARE,
        0.4f, 0.15f, 0f, 1f, EscTheme.vanilla()
    );

    /** 0 = index with section shortcuts; 1..{@link FieldGuideContent#getMaxFlatIndex()} are content pages in order. */
    private int flatIndex;
    /** Pixel scroll for the index section list (mouse wheel). */
    private int indexScroll;
    private int previousFlatIndex = -1;

    private Button backButton;
    private Button forwardButton;
    private Button homeButton;
    private Button closeButton;

    private EscRect panelRect;
    private EscRect bodyRect;
    private EscRect footerRect;
    private EscRect indexListRect;

    public DeadAirFieldGuideScreen() {
        super(Component.translatable("screen.dead_air.field_guide.title"), GUIDE_STYLE);
    }

    @Override
    protected void init() {
        if (FieldGuideContent.isIndex(flatIndex) && previousFlatIndex > 0) {
            indexScroll = 0;
        }
        previousFlatIndex = flatIndex;
        super.init();
        refreshNavButtonState();
    }

    @Override
    protected void buildLayout() {
        recomputeLayoutRegions();

        int totalNavW = (NAV_BUTTON_W * 4) + (NAV_GAP * 3);
        EscRect navGroup = resolve(footerRect, EscLayoutSpec.of(EscAnchor.CENTER, 0, 4, totalNavW, NAV_BUTTON_H));
        EscRect[] navSlots = navGroup.splitColumns(new float[]{1f, 1f, 1f, 1f}, NAV_GAP);

        backButton = addButton(Component.translatable("screen.dead_air.field_guide.back"), navSlots[0],
            b -> goBack());
        forwardButton = addButton(Component.translatable("screen.dead_air.field_guide.forward"), navSlots[1],
            b -> goForward());
        homeButton = addButton(Component.translatable("screen.dead_air.field_guide.home"), navSlots[2],
            b -> goHome());
        closeButton = addButton(Component.translatable("screen.dead_air.field_guide.close"), navSlots[3],
            b -> onClose());
    }

    private void recomputeLayoutRegions() {
        panelRect = contentRect();
        footerRect = new EscRect(panelRect.x(), panelRect.bottom() - FOOTER_HEIGHT, panelRect.width(), FOOTER_HEIGHT);
        bodyRect = new EscRect(panelRect.x(), panelRect.y(), panelRect.width(),
            Math.max(0, footerRect.y() - panelRect.y() - 4));

        int listTop = topAfterIndexIntro() + 6;
        int listBottom = footerRect.y() - INDEX_ABOVE_NAV_PADDING;
        indexListRect = new EscRect(
            bodyRect.x(),
            listTop,
            bodyRect.width(),
            Math.max(0, listBottom - listTop)
        );
    }

    private int indexViewportTop() {
        return indexListRect != null ? indexListRect.y() : topAfterIndexIntro() + 6;
    }

    private int indexViewportBottom() {
        return indexListRect != null ? indexListRect.bottom() : height - FOOTER_HEIGHT - INDEX_ABOVE_NAV_PADDING;
    }

    private int indexRowStride() {
        return INDEX_ROW_HEIGHT + INDEX_ROW_GAP;
    }

    private int indexTotalListHeight() {
        int n = FieldGuideContent.sections().size();
        return n * indexRowStride() - INDEX_ROW_GAP;
    }

    private int maxIndexScroll() {
        int vh = indexViewportBottom() - indexViewportTop();
        return Math.max(0, indexTotalListHeight() - vh);
    }

    /** Minimum Y so index intro text ends before the scroll area (approximate). */
    private int topAfterIndexIntro() {
        int left = bodyRect != null ? bodyRect.x() : PAGE_PADDING;
        int contentWidth = bodyRect != null ? bodyRect.width() : width - PAGE_PADDING * 2;
        int lines = 0;
        for (String line : FieldGuideContent.getIndexIntroLines()) {
            lines += font.split(Component.literal(line), contentWidth).size();
        }
        int bodyTop = bodyRect != null ? bodyRect.y() : 12;
        return bodyTop + BODY_TOP_OFFSET + lines * LINE_STEP + 12;
    }

    private void goBack() {
        if (flatIndex > 0) {
            flatIndex--;
            init();
        }
    }

    private void goForward() {
        int maxFlat = FieldGuideContent.getMaxFlatIndex();
        if (flatIndex < maxFlat) {
            flatIndex++;
            init();
        }
    }

    private void goHome() {
        if (flatIndex != 0) {
            flatIndex = 0;
            init();
        }
    }

    private void refreshNavButtonState() {
        int maxFlat = FieldGuideContent.getMaxFlatIndex();

        if (backButton != null) {
            backButton.active = flatIndex > 0;
        }
        if (forwardButton != null) {
            forwardButton.active = flatIndex < maxFlat;
        }
        if (homeButton != null) {
            homeButton.active = flatIndex != 0;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && FieldGuideContent.isIndex(flatIndex)) {
            if (clickIndexSection(mouseX, mouseY)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickIndexSection(double mouseX, double mouseY) {
        int vpTop = indexViewportTop();
        int vpBottom = indexViewportBottom();
        if (mouseY < vpTop || mouseY > vpBottom) {
            return false;
        }

        int btnWidth = Math.min(280, bodyRect.width());
        int x = bodyRect.x() + (bodyRect.width() - btnWidth) / 2;
        if (mouseX < x || mouseX > x + btnWidth) {
            return false;
        }

        List<GuideSection> sections = FieldGuideContent.sections();
        int stride = indexRowStride();
        for (int i = 0; i < sections.size(); i++) {
            int rowY = vpTop + i * stride - indexScroll;
            int rowBottom = rowY + INDEX_ROW_HEIGHT;
            if (rowBottom < vpTop || rowY > vpBottom) {
                continue;
            }
            if (mouseY >= rowY && mouseY <= rowBottom) {
                flatIndex = FieldGuideContent.getFlatForSectionStart(sections.get(i).id());
                init();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (FieldGuideContent.isIndex(flatIndex)) {
            int vpTop = indexViewportTop();
            int vpBottom = indexViewportBottom();
            if (mouseY >= vpTop && mouseY <= vpBottom
                && mouseX >= bodyRect.x() && mouseX <= bodyRect.right()) {
                indexScroll = Mth.clamp(indexScroll - (int) (scrollY * indexRowStride()), 0, maxIndexScroll());
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int left = bodyRect.x();
        int right = bodyRect.right();
        int top = panelRect.y();
        int bottom = bodyRect.bottom() - 4;

        guiGraphics.fill(left - 8, top - 6, right + 8, bottom, 0xAA101010);
        guiGraphics.drawString(font, title, left, top - 2, 0xE8E8E8, false);

        int contentWidth = bodyRect.width();

        if (FieldGuideContent.isIndex(flatIndex)) {
            guiGraphics.drawString(font, Component.translatable("screen.dead_air.field_guide.index_subtitle"),
                left, top + 14, 0xFFD65A, false);

            int textY = top + BODY_TOP_OFFSET;
            for (String line : FieldGuideContent.getIndexIntroLines()) {
                for (net.minecraft.util.FormattedCharSequence seq : font.split(Component.literal(line), contentWidth)) {
                    guiGraphics.drawString(font, seq, left, textY, 0xD6D6D6);
                    textY += LINE_STEP;
                }
            }

            renderIndexSectionList(guiGraphics, mouseX, mouseY);
            return;
        }

        String subtitle = FieldGuideContent.getSectionTitleForFlat(flatIndex)
            + " (" + FieldGuideContent.getPageOneBasedForFlat(flatIndex)
            + "/" + FieldGuideContent.getPagesInSectionForFlat(flatIndex) + ")";
        guiGraphics.drawString(font, Component.literal(subtitle), left, top + 14, 0xFFD65A, false);

        int bodyY = top + BODY_TOP_OFFSET;
        for (String line : FieldGuideContent.getLinesForFlat(flatIndex)) {
            for (net.minecraft.util.FormattedCharSequence seq : font.split(Component.literal(line), contentWidth)) {
                guiGraphics.drawString(font, seq, left, bodyY, 0xD6D6D6);
                bodyY += LINE_STEP;
            }
        }
    }

    private void renderIndexSectionList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int vpTop = indexViewportTop();
        int vpBottom = indexViewportBottom();
        int vpLeft = bodyRect.x();
        int vpRight = bodyRect.right();

        int btnWidth = Math.min(280, bodyRect.width());
        int x = bodyRect.x() + (bodyRect.width() - btnWidth) / 2;

        guiGraphics.fill(vpLeft, vpTop - 2, vpRight, vpTop - 1, 0x44FFFFFF);
        guiGraphics.fill(vpLeft, vpBottom + 1, vpRight, vpBottom + 2, 0x44FFFFFF);

        List<GuideSection> sections = FieldGuideContent.sections();
        int stride = indexRowStride();

        for (int i = 0; i < sections.size(); i++) {
            int rowY = vpTop + i * stride - indexScroll;
            int rowBottom = rowY + INDEX_ROW_HEIGHT;
            if (rowBottom < vpTop || rowY > vpBottom) {
                continue;
            }

            boolean hovered = mouseX >= x && mouseX <= x + btnWidth
                && mouseY >= Math.max(rowY, vpTop) && mouseY <= Math.min(rowBottom, vpBottom);
            int bg = hovered ? 0x55222222 : 0x33101010;
            int drawTop = Math.max(rowY, vpTop);
            int drawBottom = Math.min(rowBottom, vpBottom);
            guiGraphics.fill(x, drawTop, x + btnWidth, drawBottom, bg);
            int innerMid = drawTop + (drawBottom - drawTop) / 2 - 4;
            guiGraphics.drawString(font, Component.literal(sections.get(i).title()), x + 6, innerMid,
                hovered ? 0xFFFFFF : 0xDDDDDD, false);
        }

        int max = maxIndexScroll();
        if (max > 0) {
            guiGraphics.drawString(
                font,
                Component.translatable("screen.dead_air.field_guide.scroll_hint"),
                vpLeft,
                vpBottom + 4,
                0x888888,
                false
            );
        }
    }
}
