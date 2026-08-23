package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscAnchor;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscLayoutSpec;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscPanel;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscRect;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscScreen;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscText;

import java.util.List;

/**
 * Dead Air config screen. Change options directly; values are saved to dead_air-common.toml.
 */
public class DeadAirConfigScreen extends EscScreen {
    private final Screen parent;
    private static final int SLIDER_W = 200;
    private static final int FIELD_COUNT = 6;

    public DeadAirConfigScreen(Screen parent) {
        super(Component.literal("Dead Air Config"));
        this.parent = parent;
    }

    @Override
    protected void buildLayout() {
        EscRect content = contentRect();
        int sliderW = Math.min(SLIDER_W, content.width() - 40);
        EscRect formBody = EscPanel.bodyBelowTitle(content, style);
        EscRect[] fieldRows = formBody.splitRows(equalWeights(FIELD_COUNT), 2);

        AbstractSliderButton maxVol = new AbstractSliderButton(0, 0, sliderW, 20,
            EscText.literal("Max volume: " + (int) (Config.maxVolume * 100) + "%"),
            Config.maxVolume) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                double v = Mth.clamp(this.value, 0.1, 1.0);
                Config.setMaxVolume(v);
                setMessage(EscText.literal("Max volume: " + (int) (v * 100) + "%"));
                uk.co.extraspecialstudio.dead_air.audio.AudioManager.updateVolumeForAllSounds();
            }

            @Override
            protected void applyValue() {
            }
        };
        layoutWidget(maxVol, resolve(fieldRows[0], EscLayoutSpec.of(EscAnchor.CENTER, 0, 0, sliderW, 20)));
        addRenderableWidget(maxVol);

        AbstractSliderButton minVol = new AbstractSliderButton(0, 0, sliderW, 20,
            EscText.literal("Min volume: " + (int) (Config.minVolume * 100) + "%"),
            (Config.minVolume - 0) / 0.5) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                double v = this.value * 0.5;
                Config.setMinVolume(v);
                setMessage(EscText.literal("Min volume: " + (int) (v * 100) + "%"));
            }

            @Override
            protected void applyValue() {
            }
        };
        layoutWidget(minVol, resolve(fieldRows[1], EscLayoutSpec.of(EscAnchor.CENTER, 0, 0, sliderW, 20)));
        addRenderableWidget(minVol);

        CycleButton<Boolean> radioBtn = CycleButton.<Boolean>builder(v -> EscText.literal(v ? "On" : "Off"))
            .withValues(List.of(true, false))
            .withInitialValue(Config.radioAlwaysOn)
            .create(0, 0, sliderW, 20,
                EscText.literal("Radio when walkie in inventory: " + (Config.radioAlwaysOn ? "On" : "Off")),
                (b, v) -> {
                    Config.setRadioAlwaysOn(v);
                    b.setMessage(EscText.literal("Radio when walkie in inventory: " + (v ? "On" : "Off")));
                });
        layoutWidget(radioBtn, resolve(fieldRows[2], EscLayoutSpec.of(EscAnchor.CENTER, 0, 0, sliderW, 20)));
        addRenderableWidget(radioBtn);

        List<String> corners = List.of("top_right", "top_left", "bottom_right", "bottom_left");
        String cur = Config.overlayCorner != null ? Config.overlayCorner : "top_right";
        int idx = corners.indexOf(cur);
        if (idx < 0) {
            idx = 0;
        }
        final int[] cornerIndex = {idx};
        CycleButton<String> cornerBtn = CycleButton.builder((String s) -> EscText.literal(formatCorner(s)))
            .withValues(corners).withInitialValue(corners.get(cornerIndex[0]))
            .create(0, 0, sliderW, 20,
                EscText.literal("Overlay corner: " + formatCorner(corners.get(cornerIndex[0]))),
                (b, s) -> {
                    cornerIndex[0] = corners.indexOf(s);
                    Config.setOverlayCorner(s);
                    b.setMessage(EscText.literal("Overlay corner: " + formatCorner(s)));
                });
        layoutWidget(cornerBtn, resolve(fieldRows[3], EscLayoutSpec.of(EscAnchor.CENTER, 0, 0, sliderW, 20)));
        addRenderableWidget(cornerBtn);

        AbstractSliderButton offsetX = new AbstractSliderButton(0, 0, sliderW, 20,
            EscText.literal("Overlay offset X: " + Config.overlayOffsetX),
            (Config.overlayOffsetX - (-200)) / 400.0) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                int v = (int) Math.round(-200 + this.value * 400);
                Config.setOverlayOffsetX(v);
                setMessage(EscText.literal("Overlay offset X: " + v));
            }

            @Override
            protected void applyValue() {
            }
        };
        layoutWidget(offsetX, resolve(fieldRows[4], EscLayoutSpec.of(EscAnchor.CENTER, 0, 0, sliderW, 20)));
        addRenderableWidget(offsetX);

        AbstractSliderButton offsetY = new AbstractSliderButton(0, 0, sliderW, 20,
            EscText.literal("Overlay offset Y: " + Config.overlayOffsetY),
            (Config.overlayOffsetY - (-200)) / 400.0) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                int v = (int) Math.round(-200 + this.value * 400);
                Config.setOverlayOffsetY(v);
                setMessage(EscText.literal("Overlay offset Y: " + v));
            }

            @Override
            protected void applyValue() {
            }
        };
        layoutWidget(offsetY, resolve(fieldRows[5], EscLayoutSpec.of(EscAnchor.CENTER, 0, 0, sliderW, 20)));
        addRenderableWidget(offsetY);

        EscRect footer = EscPanel.footer(content, style);
        int btnH = style.defaultButtonHeight();
        addAnchoredButton(Component.literal("Done"), footer,
            EscLayoutSpec.of(EscAnchor.BOTTOM_CENTER, 0, (btnH - footer.height()) / 2, 100, btnH),
            b -> onClose());
    }

    private static float[] equalWeights(int count) {
        float[] weights = new float[count];
        for (int i = 0; i < count; i++) {
            weights[i] = 1f;
        }
        return weights;
    }

    private static String formatCorner(String key) {
        if (key == null) {
            return "Top Right";
        }
        return switch (key) {
            case "top_left" -> "Top Left";
            case "bottom_right" -> "Bottom Right";
            case "bottom_left" -> "Bottom Left";
            default -> "Top Right";
        };
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        EscRect panel = contentRect();
        EscPanel.renderPanel(guiGraphics, panel, style);
        EscRect titleBar = EscPanel.titleBar(panel, style);
        int titleY = titleBar.y() + Math.max(6, (titleBar.height() - EscText.measureLineHeight(font)) / 2);
        EscText.drawCentered(guiGraphics, font, title, titleBar.x() + titleBar.width() / 2, titleY, 0xFFFFFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
