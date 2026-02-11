package uk.creatopia.unbound.dead_air.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import uk.creatopia.unbound.dead_air.Config;
import uk.creatopia.unbound.dead_air.Dead_air;
import uk.creatopia.unbound.dead_air.radio.RadioStation;
import uk.creatopia.unbound.dead_air.radio.SignalStrength;
import uk.creatopia.unbound.dead_air.radio.StationRegistry;
import uk.creatopia.unbound.dead_air.station.StationUnlockManager;
import uk.creatopia.unbound.dead_air.walkie.WalkieTalkieManager;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI screen for tuning walkie-talkie to different stations using a Pip-Boy style interface.
 * Features a retro radio tuner dial and Fallout 4-inspired radio selection.
 */
@SuppressWarnings("null")
public class WalkieTalkieTuningScreen extends Screen {
    private static final float MIN_FREQUENCY = 88.0f;
    private static final float MAX_FREQUENCY = 108.0f;
    
    // Pip-Boy color scheme (ZombieCraft reference - crisp greens, slight transparency)
    private static final int PIPBOY_DARK_GREEN = 0x001408; // Dark green tint
    private static final int PIPBOY_HIGHLIGHT = 0xFFFF00; // Yellow highlight
    private static final int PIPBOY_TEXT = 0x00FF41; // Bright green text
    private static final int BORDER_COLOR = 0xFF00E050; // Crisp neon green border
    private static final int PANEL_BG = 0x70001808; // Semi-transparent dark green (~44% opacity)
    
    private List<RadioStation> availableStations;
    private float currentFrequency = 88.0f;
    private RadioStation nearestStation = null;
    private AbstractSliderButton frequencySlider;
    private AbstractSliderButton volumeSlider;
    private Button tuneButton;
    private Button powerButton;
    private Button pingLocationButton;
    
    // Waveform animation
    private float waveformTime = 0.0f;
    private final List<Float> waveformData = new ArrayList<>();
    
    // Station list bounds for click detection (updated each render)
    private int listBoundsX, listBoundsY, listBoundsWidth, listContentStartY;
    private static final int LIST_LINE_HEIGHT = 12;
    
    public WalkieTalkieTuningScreen() {
        super(Component.literal("Radio Tuning"));
        
        // Initialize frequency to current station if tuned
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            WalkieTalkieManager.WalkieTalkieState state = WalkieTalkieManager.getState(mc.player);
            if (state.getCurrentStation() != null) {
                currentFrequency = state.getCurrentStation().getFrequency();
            }
        }
        
        // Initialize waveform data
        for (int i = 0; i < 50; i++) {
            waveformData.add(0.0f);
        }
    }
    
    @Override
    protected void init() {
        super.init();
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        
        this.clearWidgets();
        listBoundsX = Math.max(12, width / 50);
        listBoundsY = Math.max(15, height / 25);
        listBoundsWidth = Math.min(180, width / 4);
        listContentStartY = listBoundsY + 18;
        
        // Ensure widgets list is ready
        if (this.renderables == null) {
            Dead_air.LOGGER.warn("WalkieTalkieTuningScreen renderables is null!");
        }
        
        // Refresh available stations (may have discovered new towers)
        refreshAvailableStations();
        
        // Update nearest station based on current frequency
        updateNearestStation();
        
        // Frequency slider - positioned below the dial, separate from it
        // Make the clickable area match the handle size (not the full track)
        float sliderValue = (currentFrequency - MIN_FREQUENCY) / (MAX_FREQUENCY - MIN_FREQUENCY);
        // Don't set positions in init() - they'll be set in render() when width/height are correct
        // Use temporary positions that will be updated in render()
        frequencySlider = new AbstractSliderButton(
            0, 0, 240, 20,
            Component.empty(), // Empty message - we'll draw the text separately to avoid overflow
            sliderValue
        ) {
            {
                this.visible = true;
                this.active = true;
            }
            @Override
            protected void updateMessage() {
                currentFrequency = (float) Mth.lerp(this.value, MIN_FREQUENCY, MAX_FREQUENCY);
                // Round to 0.1 MHz increments
                currentFrequency = Math.round(currentFrequency * 10.0f) / 10.0f;
                // Don't set message here - we'll draw it separately
                updateNearestStation();
            }
            
            @Override
            protected void applyValue() {
                // Value is already applied in updateMessage
            }
            
            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (!this.active || !this.visible || button != 0) {
                    return false;
                }
                
                // Calculate handle position based on current value
                int handleWidth = 8; // Width of the draggable handle
                int handleHeight = this.height;
                double handleX = this.getX() + (this.value * (this.width - handleWidth));
                double handleY = this.getY();
                
                // Only respond to clicks on or very near the handle (with some tolerance)
                double tolerance = 4.0; // Pixels of tolerance around the handle
                if (mouseX >= handleX - tolerance && mouseX <= handleX + handleWidth + tolerance &&
                    mouseY >= handleY - tolerance && mouseY <= handleY + handleHeight + tolerance) {
                    // Click is on the handle - allow dragging
                    return super.mouseClicked(mouseX, mouseY, button);
                }
                
                // Click is on the track but not the handle - snap to that position
                if (mouseX >= this.getX() && mouseX <= this.getX() + this.width &&
                    mouseY >= this.getY() && mouseY <= this.getY() + this.height) {
                    // Calculate new value based on click position
                    double relativeX = mouseX - this.getX();
                    double newValue = Mth.clamp(relativeX / this.width, 0.0, 1.0);
                    this.value = newValue;
                    this.updateMessage();
                    this.onDrag(mouseX, mouseY, 0, 0); // Trigger drag to update
                    return true;
                }
                
                return false;
            }
        };
        addRenderableWidget(frequencySlider);
        
        // Volume slider - will be positioned in volume section (right panel)
        double currentVolume = Config.maxVolume;
        int volumeSliderX = width - 130; // Will be repositioned in render
        int volumeSliderY = height / 2 - 40; // Will be repositioned in render
        volumeSlider = new AbstractSliderButton(
            volumeSliderX, volumeSliderY, 120, 20, // Narrower width
            Component.literal(String.format("Volume: %.0f%%", currentVolume * 100)),
            currentVolume
        ) {
            @Override
            protected void updateMessage() {
                double newVolume = this.value;
                Config.maxVolume = newVolume;
                this.setMessage(Component.literal(String.format("Volume: %.0f%%", newVolume * 100)));
                
                // Update volume of currently playing sounds
                uk.creatopia.unbound.dead_air.audio.AudioManager.updateVolumeForAllSounds();
            }
            
            @Override
            protected void applyValue() {
                // Value is already applied in updateMessage
            }
            
            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (!this.active || !this.visible || button != 0) {
                    return false;
                }
                
                // Calculate handle position based on current value
                int handleWidth = 8;
                int handleHeight = this.height;
                double handleX = this.getX() + (this.value * (this.width - handleWidth));
                double handleY = this.getY();
                
                // Only respond to clicks on or very near the handle
                double tolerance = 4.0;
                if (mouseX >= handleX - tolerance && mouseX <= handleX + handleWidth + tolerance &&
                    mouseY >= handleY - tolerance && mouseY <= handleY + handleHeight + tolerance) {
                    return super.mouseClicked(mouseX, mouseY, button);
                }
                
                // Click is on the track but not the handle - snap to that position
                if (mouseX >= this.getX() && mouseX <= this.getX() + this.width &&
                    mouseY >= this.getY() && mouseY <= this.getY() + this.height) {
                    double relativeX = mouseX - this.getX();
                    double newValue = Mth.clamp(relativeX / this.width, 0.0, 1.0);
                    this.value = newValue;
                    this.updateMessage();
                    this.onDrag(mouseX, mouseY, 0, 0);
                    return true;
                }
                
                return false;
            }
        };
        addRenderableWidget(volumeSlider);
        
        // Tune button - tune to the nearest station at current frequency (below slider)
        // Don't set positions in init() - they'll be set in render() when width/height are correct
        tuneButton = Button.builder(
            Component.literal(nearestStation != null ? "TUNE: " + nearestStation.getName() : "TUNE: NO SIGNAL"),
            button -> tuneToCurrentFrequency()
        ).bounds(0, 0, 200, 20).build(); // Positioned below slider - will be updated in render()
        tuneButton.visible = true;
        tuneButton.active = true;
        addRenderableWidget(tuneButton);
        
        // Ping location and Power buttons - will be positioned in buttons section (right panel)
        int rightPanelX = width - 130; // Will be repositioned in render
        int rightPanelY = height / 2 + 60; // Will be repositioned in render
        
        // Ping location button
        pingLocationButton = Button.builder(
            Component.literal("PING LOCATION"),
            button -> {
                if (mc.player != null) {
                    int x = (int) mc.player.getX();
                    int y = (int) mc.player.getY();
                    int z = (int) mc.player.getZ();
                    String dimension = mc.level != null ? mc.level.dimension().location().toString() : "unknown";
                    
                    // Format: "Location: X, Y, Z (Dimension)"
                    Component locationMessage = Component.literal(
                        String.format("Location: %d, %d, %d (%s)", x, y, z, dimension)
                    );
                    
                    // Send to chat
                    mc.player.sendSystemMessage(locationMessage);
                }
            }
        ).bounds(rightPanelX, rightPanelY, 120, 20).build(); // Narrower width
        addRenderableWidget(pingLocationButton);
        
        // Power on/off button (below ping button)
        WalkieTalkieManager.WalkieTalkieState state = WalkieTalkieManager.getState(mc.player);
        powerButton = Button.builder(
            Component.literal(state.isOn() ? "[ON]" : "[OFF]"),
            button -> {
                WalkieTalkieManager.WalkieTalkieState currentState = WalkieTalkieManager.getState(mc.player);
                if (currentState.isOn()) {
                    WalkieTalkieManager.turnOff(mc.player);
                } else {
                    WalkieTalkieManager.turnOn(mc.player);
                }
                // Refresh state and update button
                currentState = WalkieTalkieManager.getState(mc.player);
                button.setMessage(Component.literal(currentState.isOn() ? "[ON]" : "[OFF]"));
            }
        ).bounds(rightPanelX, rightPanelY + 25, 120, 20).build(); // Narrower width
        addRenderableWidget(powerButton);
    }
    
    /**
     * Refresh available stations list.
     */
    private void refreshAvailableStations() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            availableStations = java.util.Collections.emptyList();
            return;
        }
        
        try {
            availableStations = StationUnlockManager.getAvailableStations(mc.player);
            if (availableStations == null) {
                availableStations = java.util.Collections.emptyList();
            }
        } catch (Exception e) {
            availableStations = java.util.Collections.emptyList();
        }
    }
    
    /**
     * Update the nearest station based on current frequency.
     * Only considers stations that the player has unlocked.
     */
    private void updateNearestStation() {
        refreshAvailableStations();
        
        if (availableStations == null || availableStations.isEmpty()) {
            nearestStation = null;
        } else {
            nearestStation = null;
            float nearestDistance = Float.MAX_VALUE;
            
            for (RadioStation station : availableStations) {
                float distance = Math.abs(station.getFrequency() - currentFrequency);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestStation = station;
                }
            }
            
            // Check if we're close enough to consider it "tuned" (within 0.2 MHz)
            if (nearestStation != null && nearestDistance > 0.2f) {
                nearestStation = null;
            }
        }
        
        // Update tune button text
        if (tuneButton != null) {
            tuneButton.setMessage(Component.literal(
                nearestStation != null ? "TUNE: " + nearestStation.getName() : "TUNE: NO SIGNAL"
            ));
        }
    }
    
    /**
     * Position all widgets before rendering.
     */
    private void positionWidgets() {
        // Calculate positions (must match render method layout - ZombieCraft reference)
        int padding = Math.max(10, width / 50);
        int topMargin = Math.max(15, height / 25);
        int leftSectionWidth = Math.min(180, width / 4);
        int rightSectionWidth = leftSectionWidth;
        int sectionSpacing = 8;
        int centerSectionWidth = width - leftSectionWidth - rightSectionWidth - padding * 2 - sectionSpacing * 2;
        int centerSectionX = leftSectionWidth + sectionSpacing;
        int centerSectionY = topMargin;
        int centerContentCenterX = centerSectionX + centerSectionWidth / 2;
        int dialRadius = 60;
        int dialY = centerSectionY + 10 + dialRadius;
        int infoY = dialY + dialRadius / 2 + 6 + 9 + 6;
        int infoBoxHeight = 48;
        int sliderY = infoY + infoBoxHeight + 10;
        
        // Position frequency slider
        if (frequencySlider != null) {
            frequencySlider.setX(centerContentCenterX - 120);
            frequencySlider.setY(sliderY);
            frequencySlider.setWidth(240);
            frequencySlider.visible = true;
            frequencySlider.active = true;
        }
        
        // Position tune button
        if (tuneButton != null) {
            tuneButton.setX(centerContentCenterX - 100);
            tuneButton.setY(sliderY + 24);
            tuneButton.setWidth(200);
            tuneButton.visible = true;
            tuneButton.active = true;
        }
        
        // Position volume slider (right panel) - stacked from top
        int rightSectionX = width - rightSectionWidth - padding;
        int rightContentTop = topMargin + 8;
        int signalSectionY = rightContentTop;
        int signalSectionHeight = 85;
        int volumeSectionHeight = 55;
        int volumeSectionY = signalSectionY + signalSectionHeight + sectionSpacing;
        if (volumeSlider != null) {
            volumeSlider.setX(rightSectionX + 5);
            volumeSlider.setY(volumeSectionY + 5);
            volumeSlider.setWidth(rightSectionWidth - 10);
            volumeSlider.visible = true;
            volumeSlider.active = true;
        }
        
        // Position ping and power buttons (right panel)
        int buttonsSectionY = volumeSectionY + volumeSectionHeight + sectionSpacing;
        if (pingLocationButton != null) {
            pingLocationButton.setX(rightSectionX + 5);
            pingLocationButton.setY(buttonsSectionY + 5);
            pingLocationButton.setWidth(rightSectionWidth - 10);
            pingLocationButton.visible = true;
            pingLocationButton.active = true;
        }
        if (powerButton != null) {
            powerButton.setX(rightSectionX + 5);
            powerButton.setY(buttonsSectionY + 25);
            powerButton.setWidth(rightSectionWidth - 10);
            powerButton.visible = true;
            powerButton.active = true;
        }
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        
        // Pip-Boy style background - slight transparency, dark green tint (ZombieCraft reference)
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        guiGraphics.fill(0, 0, width, height, 0xD8000810); // ~85% opacity, dark green
        
        // Layout: center panel central, equal gaps between left-center and center-right
        int padding = Math.max(12, width / 50);
        int topMargin = Math.max(15, height / 25);
        int gap = 12;  // Equal gap between panels
        int sidePanelWidth = Math.min(180, (width - 2 * padding - 2 * gap) / 4);  // Left and right same width
        int centerSectionWidth = width - 2 * padding - 2 * sidePanelWidth - 2 * gap;
        int leftSectionX = padding;
        int leftSectionY = topMargin;
        int leftSectionWidth = sidePanelWidth;
        int centerSectionX = padding + sidePanelWidth + gap;
        int centerSectionY = topMargin;
        int rightSectionWidth = sidePanelWidth;
        int centerContentCenterX = centerSectionX + centerSectionWidth / 2;
        int sectionHeight = height - topMargin - padding;
        
        // Slider and button anchored in LOWER middle of center panel (reference)
        int centerBottom = centerSectionY + sectionHeight - padding;
        int buttonY = centerBottom - 20;
        int sliderY = buttonY - 24 - 12;  // 24px gap + 12px for freq text above slider
        
        // Smaller gauge (reference: "gauge is better and smaller in image 2")
        int dialRadius = 45;
        int dialY = centerSectionY + 12 + dialRadius;
        // Frequency counter anchored just under the gauge needle (user request)
        int freqTextY = dialY + 10;  // Just below gauge center/needle pivot
        // Info BELOW gauge - clear space so text never overlaps (reference: "space for all info to fit")
        int dialBottom = dialY + dialRadius + 8;  // Below full circle border
        int infoY = dialBottom + 8;
        int infoBoxHeight = Math.max(32, Math.min(44, sliderY - infoY - 12));
        
        // Slider: fixed left edge, never extends past panel (ZombieCraft reference)
        int sliderInset = 24;  // Clearance - vanilla slider may add internal padding
        int sliderWidth = Math.max(100, centerSectionWidth - sliderInset * 2);
        int sliderX = centerSectionX + sliderInset;
        int sliderYFinal = Mth.clamp(sliderY, centerSectionY + 5, centerSectionY + sectionHeight - 25);
        
        if (frequencySlider != null) {
            frequencySlider.setX(sliderX);
            frequencySlider.setY(sliderYFinal);
            frequencySlider.setWidth(sliderWidth);
            frequencySlider.setHeight(20);
            frequencySlider.visible = true;
            frequencySlider.active = true;
        }
        
        if (tuneButton != null) {
            int buttonWidth = Math.min(200, centerSectionWidth - 40);
            int buttonX = centerSectionX + (centerSectionWidth - buttonWidth) / 2;  // Centered
            buttonY = Mth.clamp(buttonY, centerSectionY + 5, centerSectionY + sectionHeight - 25);
            
            tuneButton.setX(buttonX);
            tuneButton.setY(buttonY);
            tuneButton.setWidth(buttonWidth);
            tuneButton.setHeight(20);
            tuneButton.visible = true;
            tuneButton.active = true;
        }
        
        // Right panel: SIGNAL in its own box at top, Volume and Buttons smaller below (reference)
        int rightSectionX = centerSectionX + centerSectionWidth + gap;
        int rightContentTop = topMargin + 8;
        int sectionSpacing = 6;
        int signalSectionY = rightContentTop;
        int signalSectionHeight = 70;   // Signal box - self-contained
        int volumeSectionHeight = 42;   // Smaller lower panels
        int buttonsSectionHeight = 48;
        int volumeSectionY = signalSectionY + signalSectionHeight + sectionSpacing;
        int buttonsSectionY = volumeSectionY + volumeSectionHeight + sectionSpacing;
        
        if (volumeSlider != null) {
            volumeSlider.setX(rightSectionX + 5);
            volumeSlider.setY(volumeSectionY + 5);
            volumeSlider.setWidth(rightSectionWidth - 10);
            volumeSlider.visible = true;
            volumeSlider.active = true;
        }
        
        if (pingLocationButton != null) {
            pingLocationButton.setX(rightSectionX + 5);
            pingLocationButton.setY(buttonsSectionY + 5);
            pingLocationButton.setWidth(rightSectionWidth - 10);
            pingLocationButton.visible = true;
            pingLocationButton.active = true;
        }
        
        if (powerButton != null) {
            powerButton.setX(rightSectionX + 5);
            powerButton.setY(buttonsSectionY + 25);
            powerButton.setWidth(rightSectionWidth - 10);
            powerButton.visible = true;
            powerButton.active = true;
        }

        // Draw ALL panel backgrounds/borders - semi-transparent, crisp borders (ZombieCraft style)
        int borderThickness = 2;
        int centerSectionHeight = sectionHeight;
        int leftSectionHeight = sectionHeight;
        
        // LEFT section - transparent background, crisp green border
        guiGraphics.fill(leftSectionX - borderThickness, leftSectionY - borderThickness,
            leftSectionX + leftSectionWidth + borderThickness, leftSectionY + leftSectionHeight + borderThickness, PANEL_BG);
        guiGraphics.fill(leftSectionX - borderThickness, leftSectionY - borderThickness,
            leftSectionX + leftSectionWidth + borderThickness, leftSectionY, BORDER_COLOR);
        guiGraphics.fill(leftSectionX - borderThickness, leftSectionY + leftSectionHeight,
            leftSectionX + leftSectionWidth + borderThickness, leftSectionY + leftSectionHeight + borderThickness, BORDER_COLOR);
        guiGraphics.fill(leftSectionX - borderThickness, leftSectionY - borderThickness,
            leftSectionX, leftSectionY + leftSectionHeight + borderThickness, BORDER_COLOR);
        guiGraphics.fill(leftSectionX + leftSectionWidth, leftSectionY - borderThickness,
            leftSectionX + leftSectionWidth + borderThickness, leftSectionY + leftSectionHeight + borderThickness, BORDER_COLOR);
        
        // CENTER section
        guiGraphics.fill(centerSectionX - borderThickness, centerSectionY - borderThickness,
            centerSectionX + centerSectionWidth + borderThickness, centerSectionY + centerSectionHeight + borderThickness, PANEL_BG);
        guiGraphics.fill(centerSectionX - borderThickness, centerSectionY - borderThickness,
            centerSectionX + centerSectionWidth + borderThickness, centerSectionY, BORDER_COLOR);
        guiGraphics.fill(centerSectionX - borderThickness, centerSectionY + centerSectionHeight,
            centerSectionX + centerSectionWidth + borderThickness, centerSectionY + centerSectionHeight + borderThickness, BORDER_COLOR);
        guiGraphics.fill(centerSectionX - borderThickness, centerSectionY - borderThickness,
            centerSectionX, centerSectionY + centerSectionHeight + borderThickness, BORDER_COLOR);
        guiGraphics.fill(centerSectionX + centerSectionWidth, centerSectionY - borderThickness,
            centerSectionX + centerSectionWidth + borderThickness, centerSectionY + centerSectionHeight + borderThickness, BORDER_COLOR);
        
        // RIGHT section
        guiGraphics.fill(rightSectionX - borderThickness, topMargin - borderThickness,
            rightSectionX + rightSectionWidth + borderThickness, topMargin + sectionHeight + borderThickness, PANEL_BG);
        guiGraphics.fill(rightSectionX - borderThickness, topMargin - borderThickness,
            rightSectionX + rightSectionWidth + borderThickness, topMargin, BORDER_COLOR);
        guiGraphics.fill(rightSectionX - borderThickness, topMargin + sectionHeight,
            rightSectionX + rightSectionWidth + borderThickness, topMargin + sectionHeight + borderThickness, BORDER_COLOR);
        guiGraphics.fill(rightSectionX - borderThickness, topMargin - borderThickness,
            rightSectionX, topMargin + sectionHeight + borderThickness, BORDER_COLOR);
        guiGraphics.fill(rightSectionX + rightSectionWidth, topMargin - borderThickness,
            rightSectionX + rightSectionWidth + borderThickness, topMargin + sectionHeight + borderThickness, BORDER_COLOR);
        
        // We intentionally call super.render() AFTER drawing backgrounds so widgets (sliders/buttons)
        // always render on top of our custom drawing.
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            // Still render widgets (e.g. close button) even if player is null
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }
        
        // Update waveform animation
        waveformTime += partialTick * 0.1f;
        updateWaveform();
        // Note: don't call AudioManager.update() from GUI rendering; client tick already updates audio.
        // Doing audio work from render can contribute to shutdown / disconnect hangs.
        
        // Layout variables are already calculated at the top of render() method
        // Use those variables here
        // borderThickness/borderColor/sectionHeight already computed above
        
        // LEFT SECTION: Station List - update bounds for click detection
        listBoundsX = leftSectionX;
        listBoundsY = leftSectionY;
        listBoundsWidth = leftSectionWidth;
        listContentStartY = leftSectionY + 18;  // After "RADIO STATIONS" header
        drawStationList(guiGraphics, leftSectionX, leftSectionY, leftSectionWidth, sectionHeight);
        
        // RIGHT SECTIONS: Content only, NO inner borders (ZombieCraft: one clean right panel, no extra boxes)
        drawSignalContent(guiGraphics, rightSectionX, signalSectionY, rightSectionWidth, signalSectionHeight);
        
        // CENTER SECTION: Dial + info (border/background already drawn BEFORE super.render())
        // Center section content (with internal padding)
        int centerPadding = 15;
        int centerContentWidth = centerSectionWidth - centerPadding * 2;
        
        drawRadioDial(guiGraphics, centerContentCenterX, dialY, dialRadius, mouseX, mouseY);
        
        // Info box (below dial) - rescaled to match reference, smaller
        int infoBoxWidth = Math.min(centerContentWidth - 20, 240);
        int infoBoxLeft = centerContentCenterX - infoBoxWidth / 2;
        int infoBoxRight = centerContentCenterX + infoBoxWidth / 2;
        
        // Frequency slider and tune button are already positioned at the top of render() method
        // Debug rectangle already drawn at the top of render() method
        
        // Frequency text above slider (within center panel bounds)
        if (frequencySlider != null && frequencySlider.visible) {
            String freqText = String.format("%.1f MHz", currentFrequency);
            int tw = font.width(freqText);
            int freqTextX = Mth.clamp(centerContentCenterX - tw / 2, centerSectionX + 10, centerSectionX + centerSectionWidth - tw - 10);
            guiGraphics.drawString(font, freqText, freqTextX, freqTextY, PIPBOY_TEXT, false);
        }
        
        if (nearestStation != null) {
            try {
                float stationFreq = nearestStation.getFrequency();
                float distance = Math.abs(stationFreq - currentFrequency);
                
                // Station name (highlighted) - truncate if too long, ensure it fits in box
                String stationName = nearestStation.getName();
                if (stationName == null) {
                    stationName = "Unknown Station";
                }
                int maxNameWidth = infoBoxWidth - 20; // Leave margins
                if (font.width(stationName) > maxNameWidth) {
                    stationName = font.plainSubstrByWidth(stationName, maxNameWidth - 3) + "...";
                }
                int nameX = Math.max(infoBoxLeft + 10, centerContentCenterX - font.width(stationName) / 2);
                nameX = Math.min(nameX, infoBoxRight - font.width(stationName) - 10);
                guiGraphics.drawString(font, stationName, nameX, infoY, PIPBOY_HIGHLIGHT, false);
                
                // Station frequency - ensure it fits
                String stationFreqText = String.format("%.1f MHz", stationFreq);
                int freqTextWidth = font.width(stationFreqText);
                int freqDisplayColor = distance < 0.1f ? PIPBOY_HIGHLIGHT : PIPBOY_TEXT;
                int freqX = Math.max(infoBoxLeft + 10, centerContentCenterX - freqTextWidth / 2);
                freqX = Math.min(freqX, infoBoxRight - freqTextWidth - 10);
                guiGraphics.drawString(font, stationFreqText, freqX, infoY + 10, freqDisplayColor, false);
                
                String genre = nearestStation.getGenre();
                if (genre == null) genre = "Unknown";
                if (font.width(genre) > maxNameWidth) genre = font.plainSubstrByWidth(genre, maxNameWidth - 3) + "...";
                int genreX = Math.max(infoBoxLeft + 10, centerContentCenterX - font.width(genre) / 2);
                genreX = Math.min(genreX, infoBoxRight - font.width(genre) - 10);
                guiGraphics.drawString(font, genre, genreX, infoY + 20, PIPBOY_TEXT, false);
                
                float signal = calculateSignalStrengthForStation(mc, nearestStation);
                int bars = SignalStrength.getSignalBars(signal);
                String signalText = "SIGNAL: " + bars + "/5";
                int signalColor = getSignalColor(signal);
                int signalTextWidth = font.width(signalText);
                int signalX = Math.max(infoBoxLeft + 10, centerContentCenterX - signalTextWidth / 2);
                signalX = Math.min(signalX, infoBoxRight - signalTextWidth - 10);
                guiGraphics.drawString(font, signalText, signalX, infoY + 30, signalColor, false);
                int nextY = infoY + 38;
                
                // Show "STATIC" if signal is weak - ensure it fits
                if (signal > 0.0f && signal < 0.3f) {
                    int staticX = Math.max(infoBoxLeft + 10, centerContentCenterX - font.width("STATIC") / 2);
                    staticX = Math.min(staticX, infoBoxRight - font.width("STATIC") - 10);
                    guiGraphics.drawString(font, "STATIC", staticX, nextY, PIPBOY_TEXT, false);
                }
            } catch (Exception e) {
                Dead_air.LOGGER.debug("Error drawing station info: {}", e.getMessage());
                // Fallback: show error message
                guiGraphics.drawString(font, "Error", 
                    centerContentCenterX - font.width("Error") / 2, infoY, 0xFF0000, false);
            }
        } else {
            int noSignalX = Math.max(infoBoxLeft + 10, centerContentCenterX - font.width("NO SIGNAL") / 2);
            noSignalX = Math.min(noSignalX, infoBoxRight - font.width("NO SIGNAL") - 10);
            guiGraphics.drawString(font, "NO SIGNAL", noSignalX, infoY, 0x666666, false);
            int staticX = Math.max(infoBoxLeft + 10, centerContentCenterX - font.width("STATIC") / 2);
            staticX = Math.min(staticX, infoBoxRight - font.width("STATIC") - 10);
            guiGraphics.drawString(font, "STATIC", staticX, infoY + 10, 0x888888, false);
        }

        // Draw widgets LAST so they can't be painted over by any guiGraphics.fill/draw calls above.
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    /**
     * Draw a retro radio tuner dial with frequency markers.
     * Semi-circle design (180 degrees) from 9 o'clock to 3 o'clock.
     */
    private void drawRadioDial(GuiGraphics guiGraphics, int centerX, int centerY, int dialRadius, int mouseX, int mouseY) {
        int dialCenterX = centerX;
        int dialCenterY = centerY;
        
        // Draw dial background - semi-circle arc
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        // Semi-circle: from 9 o'clock (180 degrees) to 3 o'clock (360/0 degrees)
        // This creates a horizontal 180-degree arc ABOVE the center (like a clock face)
        // Going counter-clockwise from 9 o'clock to 3 o'clock through 12 o'clock
        float startAngle = 180.0f; // 9 o'clock (left)
        float endAngle = 360.0f;   // 3 o'clock (right, same as 0°)
        float totalAngle = 180.0f;  // 180 degrees (half circle)
        
        // Draw circular border - crisp green (ZombieCraft reference)
        int borderRadius = dialRadius + 5;
        int borderThickness = 3;
        int dialTickColor = PIPBOY_TEXT; // Crisp green ticks
        // Draw full circle border using arc from 0 to 360 degrees - this creates a proper circle
        // Use more segments for smoother circle
        for (int i = 0; i < 360; i++) {
            float angle1 = i;
            float angle2 = (i + 1) % 360;
            float rad1 = (float)Math.toRadians(angle1);
            float rad2 = (float)Math.toRadians(angle2);
            
            int x1 = dialCenterX + (int)(Math.cos(rad1) * borderRadius);
            int y1 = dialCenterY + (int)(Math.sin(rad1) * borderRadius);
            int x2 = dialCenterX + (int)(Math.cos(rad2) * borderRadius);
            int y2 = dialCenterY + (int)(Math.sin(rad2) * borderRadius);
            
            drawLine(guiGraphics, x1, y1, x2, y2, BORDER_COLOR, borderThickness);
        }
        
        // Draw the arc outline (semi-circle)
        drawArc(guiGraphics, dialCenterX, dialCenterY, dialRadius, startAngle, endAngle, BORDER_COLOR, 3);
        
        // Draw major frequency markers (every 4 MHz) - evenly distributed across the arc
        // 88 MHz at 9 o'clock (180°), 108 MHz at 3 o'clock (0°)
        int[] majorFrequencies = {88, 92, 96, 100, 104, 108};
        
        for (int i = 0; i < majorFrequencies.length; i++) {
            int freq = majorFrequencies[i];
            // Calculate angle based on frequency position in range (88-108 MHz)
            // 88 MHz = 180° (9 o'clock), 108 MHz = 360° (3 o'clock)
            float normalizedFreq = (freq - MIN_FREQUENCY) / (MAX_FREQUENCY - MIN_FREQUENCY);
            float angle = startAngle + (normalizedFreq * totalAngle); // Go from 180° up to 360° (counter-clockwise, above)
            float rad = (float)Math.toRadians(angle);
            
            // Draw major notch line (small tick, not a square)
            int x1 = dialCenterX + (int)(Math.cos(rad) * (dialRadius - 10));
            int y1 = dialCenterY + (int)(Math.sin(rad) * (dialRadius - 10));
            int x2 = dialCenterX + (int)(Math.cos(rad) * (dialRadius - 2));
            int y2 = dialCenterY + (int)(Math.sin(rad) * (dialRadius - 2));
            drawLine(guiGraphics, x1, y1, x2, y2, dialTickColor, 2);
            
            // DO NOT draw frequency labels - user doesn't want numbers above the dial
        }
        
        // Draw minor tick marks (every 2 MHz, but not where major marks are)
        for (int freq = (int)MIN_FREQUENCY; freq <= (int)MAX_FREQUENCY; freq += 2) {
            // Skip major frequency positions
            boolean isMajor = false;
            for (int majorFreq : majorFrequencies) {
                if (freq == majorFreq) {
                    isMajor = true;
                    break;
                }
            }
            if (isMajor) continue;
            
            float normalizedFreq = (freq - MIN_FREQUENCY) / (MAX_FREQUENCY - MIN_FREQUENCY);
            float angle = startAngle + (normalizedFreq * totalAngle); // Go from 180° up to 360° (counter-clockwise, above)
            float rad = (float)Math.toRadians(angle);
            
            int x1 = dialCenterX + (int)(Math.cos(rad) * (dialRadius - 7));
            int y1 = dialCenterY + (int)(Math.sin(rad) * (dialRadius - 7));
            int x2 = dialCenterX + (int)(Math.cos(rad) * (dialRadius - 3));
            int y2 = dialCenterY + (int)(Math.sin(rad) * (dialRadius - 3));
            drawLine(guiGraphics, x1, y1, x2, y2, dialTickColor, 1);
        }
        
        // Draw current frequency indicator (red line/needle)
        // 88 MHz = 180° (9 o'clock), 108 MHz = 360° (3 o'clock)
        float normalizedFreq = (currentFrequency - MIN_FREQUENCY) / (MAX_FREQUENCY - MIN_FREQUENCY);
        float angle = startAngle + (normalizedFreq * totalAngle); // Go from 180° up to 360° (counter-clockwise, above)
        float rad = (float)Math.toRadians(angle);
        
        // Limit indicator length to stay on screen - ensure it doesn't go beyond screen bounds
        int indicatorLength = dialRadius + 4; // Slightly shorter needle
        int x1 = dialCenterX;
        int y1 = dialCenterY;
        int x2 = dialCenterX + (int)(Math.cos(rad) * indicatorLength);
        int y2 = dialCenterY + (int)(Math.sin(rad) * indicatorLength);
        
        // Clamp to screen bounds to prevent needle from going off screen
        int screenPadding = 20;
        x2 = Math.max(screenPadding, Math.min(width - screenPadding, x2));
        y2 = Math.max(screenPadding, Math.min(height - screenPadding, y2));
        
        // Draw red indicator line (needle)
        drawLine(guiGraphics, x1, y1, x2, y2, 0xFFFF0000, 2);
        
        // Draw center pivot dot
        guiGraphics.fill(dialCenterX - 3, dialCenterY - 3, dialCenterX + 3, dialCenterY + 3, dialTickColor);
        
        RenderSystem.disableBlend();
    }
    
    /**
     * Draw an arc (semi-circle) for the dial.
     */
    private void drawArc(GuiGraphics guiGraphics, int centerX, int centerY, int radius, 
                         float startAngle, float endAngle, int color, int thickness) {
        // Draw arc by drawing many small line segments
        int segments = 60; // Number of segments for smooth arc
        float angleStep = (endAngle - startAngle) / segments;
        
        for (int i = 0; i < segments; i++) {
            float angle1 = startAngle + (angleStep * i);
            float angle2 = startAngle + (angleStep * (i + 1));
            
            float rad1 = (float)Math.toRadians(angle1);
            float rad2 = (float)Math.toRadians(angle2);
            
            int x1 = centerX + (int)(Math.cos(rad1) * radius);
            int y1 = centerY + (int)(Math.sin(rad1) * radius);
            int x2 = centerX + (int)(Math.cos(rad2) * radius);
            int y2 = centerY + (int)(Math.sin(rad2) * radius);
            
            drawLine(guiGraphics, x1, y1, x2, y2, color, thickness);
        }
    }
    
    /**
     * Draw a line between two points.
     */
    private void drawLine(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color, int thickness) {
        // Simple line drawing using fill
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float)Math.sqrt(dx * dx + dy * dy);
        
        if (length == 0) return;
        
        float stepX = dx / length;
        float stepY = dy / length;
        
        for (float i = 0; i <= length; i += 0.5f) {
            int x = (int)(x1 + stepX * i);
            int y = (int)(y1 + stepY * i);
            guiGraphics.fill(x - thickness / 2, y - thickness / 2, 
                           x + thickness / 2, y + thickness / 2, color);
        }
    }
    
    /**
     * Draw station list in Pip-Boy style (left side).
     * Clips each row to panel bounds so nothing sticks out in small windows.
     */
    private void drawStationList(GuiGraphics guiGraphics, int x, int y, int listWidth, int panelHeight) {
        int contentRight = x + listWidth - 5;
        int contentLeft = x + 5;

        guiGraphics.drawString(font, "RADIO STATIONS", contentLeft, y + 5, PIPBOY_TEXT, false);
        int listY = y + 18;

        if (availableStations == null || availableStations.isEmpty()) {
            guiGraphics.drawString(font, "No stations", contentLeft, listY, 0x666666, false);
            guiGraphics.drawString(font, "discovered", contentLeft, listY + 10, 0x666666, false);
            guiGraphics.drawString(font, "Find a tower!", contentLeft, listY + 20, 0x666666, false);
            return;
        }

        int maxListHeight = panelHeight - 25;
        int maxStations = Math.min(availableStations.size(), maxListHeight / LIST_LINE_HEIGHT);
        maxStations = Math.min(maxStations, 10);

        int gap = 6;
        int minFreqWidth = font.width("108.0 MHz");
        int freqAreaWidth = Math.max(minFreqWidth + 4, Math.min(listWidth / 3, 60));
        int nameAreaWidth = listWidth - 10 - freqAreaWidth - gap;
        nameAreaWidth = Math.max(nameAreaWidth, 24);

        for (int i = 0; i < maxStations; i++) {
            if (listY + LIST_LINE_HEIGHT > y + panelHeight - 5) break;

            RadioStation station = availableStations.get(i);
            if (station == null) continue;

            boolean isSelected = (nearestStation != null && nearestStation.getId().equals(station.getId()));
            String stationName = station.getName() != null ? station.getName() : "Unknown";
            String prefix = isSelected ? "> " : "  ";
            String displayName = prefix + stationName;
            int fullTextWidth = font.width(displayName);
            // When name overflows, add " - " so the scroll goes further and the full name is visible
            if (fullTextWidth > nameAreaWidth) {
                displayName = displayName + " - ";
                fullTextWidth = font.width(displayName);
            }

            String freq = String.format("%.1f", station.getFrequency());
            String freqText = (listWidth >= 100) ? (freq + " MHz") : freq;
            int freqTextWidth = font.width(freqText);
            int freqX = contentRight - freqTextWidth;
            int nameAreaRight = contentLeft + nameAreaWidth;

            if (isSelected) {
                guiGraphics.fill(x + 3, listY - 1, contentRight, listY + LIST_LINE_HEIGHT - 1, 0x5500FF41);
            }

            int color = isSelected ? PIPBOY_HIGHLIGHT : PIPBOY_TEXT;
            // Clip name to name area only so scrolling text never bleeds into the MHz column
            guiGraphics.enableScissor(x + 2, listY - 1, nameAreaRight + 2, listY + LIST_LINE_HEIGHT + 1);
            if (fullTextWidth <= nameAreaWidth) {
                guiGraphics.drawString(font, displayName, contentLeft, listY, color, false);
            } else {
                long t = System.currentTimeMillis();
                int scrollRange = Math.max(0, fullTextWidth - nameAreaWidth);
                int pause = 50;
                int cycle = scrollRange + pause * 2;
                int phase = (int) ((t / 40) % Math.max(1, cycle));
                int scrollOffset = phase < pause ? 0 : (phase >= pause + scrollRange ? scrollRange : phase - pause);
                scrollOffset = Mth.clamp(scrollOffset, 0, scrollRange);
                guiGraphics.drawString(font, displayName, contentLeft - scrollOffset, listY, color, false);
            }
            guiGraphics.disableScissor();

            if (freqX >= nameAreaRight + gap) {
                guiGraphics.drawString(font, freqText, freqX, listY, 0x888888, false);
            }
            listY += LIST_LINE_HEIGHT;
        }
    }
    
    /**
     * Draw volume section with border.
     */
    private void drawVolumeSection(GuiGraphics guiGraphics, int x, int y, int width, int height, int borderColor, int borderThickness) {
        // Border only - no extra background box (reference)
        guiGraphics.fill(x - borderThickness, y - borderThickness, 
                        x + width + borderThickness, y - borderThickness + borderThickness, borderColor); // Top
        guiGraphics.fill(x - borderThickness, y + height, 
                        x + width + borderThickness, y + height + borderThickness, borderColor); // Bottom
        guiGraphics.fill(x - borderThickness, y - borderThickness, 
                        x - borderThickness + borderThickness, y + height + borderThickness, borderColor); // Left
        guiGraphics.fill(x + width, y - borderThickness, 
                        x + width + borderThickness, y + height + borderThickness, borderColor); // Right
    }
    
    /**
     * Draw buttons section with border.
     */
    private void drawButtonsSection(GuiGraphics guiGraphics, int x, int y, int width, int height, int borderColor, int borderThickness) {
        // Border only - no extra background box (reference)
        guiGraphics.fill(x - borderThickness, y - borderThickness, 
                        x + width + borderThickness, y - borderThickness + borderThickness, borderColor); // Top
        guiGraphics.fill(x - borderThickness, y + height, 
                        x + width + borderThickness, y + height + borderThickness, borderColor); // Bottom
        guiGraphics.fill(x - borderThickness, y - borderThickness, 
                        x - borderThickness + borderThickness, y + height + borderThickness, borderColor); // Left
        guiGraphics.fill(x + width, y - borderThickness, 
                        x + width + borderThickness, y + height + borderThickness, borderColor); // Right
    }
    
    /**
     * Draw waveform display in Pip-Boy style (right side).
     */
    /**
     * Draw signal, volume label, and button labels - NO borders/boxes (ZombieCraft: clean, no extra panels)
     */
    private void drawSignalContent(GuiGraphics guiGraphics, int x, int y, int boxWidth, int boxHeight) {
        guiGraphics.drawString(font, "SIGNAL", x + 5, y + 5, PIPBOY_TEXT, false);
        y += 18;
        int waveformWidth = Math.min(boxWidth - 10, 100);
        int waveformHeight = Math.min(35, boxHeight - 35);
        int barsY = y + waveformHeight + 8;
        int barWidth = 5;
        int barHeight = 8;
        int barSpacing = 2;
        int barsStartX = x + 5; // Align with waveform
        
        float signal = 0.0f;
        if (nearestStation != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                try {
                    signal = calculateSignalStrengthForStation(mc, nearestStation);
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
        
        int signalBars = SignalStrength.getSignalBars(signal);
        
        // Draw signal bars with gradient colors and rounded tops (like image 3)
        // Colors: red -> orange -> yellow -> light green -> green
        int[] gradientColors = {0xFFFF0000, 0xFFFF8800, 0xFFFFFF00, 0xFF88FF00, 0xFF00FF00}; // Red, Orange, Yellow, Light Green, Green
        for (int i = 0; i < 5; i++) {
            int barX = barsStartX + i * (barWidth + barSpacing);
            boolean isActive = i < signalBars;
            int barColor = isActive ? gradientColors[i] : 0xFF444444;
            
            // Draw bar with rounded top (like image 3) - gradient colors
            int radius = barWidth / 2;
            int topY = barsY; // Top of the bar (where rounded top starts)
            int bodyTopY = topY + radius;
            int bodyBottomY = barsY + barHeight;
            
            // Draw rounded top (semi-circle) - fill pixels in a circle at the TOP
            for (int px = 0; px < barWidth; px++) {
                for (int py = 0; py <= radius; py++) {
                    int dx = px - radius;
                    int dy = py;
                    // Check if pixel is inside the circle (rounded top)
                    if (dx * dx + dy * dy <= radius * radius) {
                        guiGraphics.fill(barX + px, topY + py, barX + px + 1, topY + py + 1, barColor);
                    }
                }
            }
            
            // Draw main rectangle body (below rounded top)
            guiGraphics.fill(barX, bodyTopY, barX + barWidth, bodyBottomY, barColor);
            
            // Add white outline for visibility
            if (isActive) {
                // Draw outline around the rounded top and body
                // Top rounded edge outline
                for (int px = 0; px < barWidth; px++) {
                    for (int py = 0; py <= radius; py++) {
                        int dx = px - radius;
                        int dy = py;
                        // Draw outline pixels (on the edge of the circle)
                        if (dx * dx + dy * dy <= radius * radius && 
                            (dx * dx + dy * dy >= (radius - 1) * (radius - 1))) {
                            guiGraphics.fill(barX + px, topY + py, barX + px + 1, topY + py + 1, 0xFFFFFF);
                        }
                    }
                }
                // Bottom edge
                guiGraphics.fill(barX, bodyBottomY - 1, barX + barWidth, bodyBottomY, 0xFFFFFF);
                // Left edge
                guiGraphics.fill(barX, bodyTopY, barX + 1, bodyBottomY, 0xFFFFFF);
                // Right edge
                guiGraphics.fill(barX + barWidth - 1, bodyTopY, barX + barWidth, bodyBottomY, 0xFFFFFF);
            }
        }
        
        // Draw waveform
        if (nearestStation != null && waveformData.size() > 0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                try {
                    // Generate waveform based on signal strength - use brighter color
                    int waveformColor = signal > 0.1f ? 0xFF00FF41 : 0xFF666666; // Crisp green for signal, gray for no signal
                    for (int i = 0; i < waveformData.size() - 1; i++) {
                        float y1 = y + waveformHeight / 2 - waveformData.get(i) * waveformHeight / 2 * signal;
                        float y2 = y + waveformHeight / 2 - waveformData.get(i + 1) * waveformHeight / 2 * signal;
                        int x1 = x + 5 + (i * (waveformWidth - 10) / waveformData.size());
                        int x2 = x + 5 + ((i + 1) * (waveformWidth - 10) / waveformData.size());
                        
                        drawLine(guiGraphics, x1, (int)y1, x2, (int)y2, waveformColor, 2); // Thicker line
                    }
                } catch (Exception e) {
                    // Safe fallback - draw static
                    Dead_air.LOGGER.debug("Error drawing waveform: {}", e.getMessage());
                }
            }
        } else {
            // Draw static/no signal - brighter
            for (int i = 0; i < waveformWidth - 10; i += 2) {
                float noise = (float)(Math.random() * waveformHeight);
                guiGraphics.fill(x + 5 + i, y + (int)noise, x + 5 + i + 1, y + (int)noise + 1, 0x888888); // Brighter static
            }
        }
    }
    
    /**
     * Update waveform data for animation.
     */
    private void updateWaveform() {
        for (int i = 0; i < waveformData.size(); i++) {
            // Generate sine wave with some variation
            float base = (float)(Math.sin(waveformTime + i * 0.2f) * 0.5 + 0.5);
            float variation = (float)(Math.sin(waveformTime * 2 + i * 0.3f) * 0.2);
            waveformData.set(i, base + variation);
        }
    }
    
    /**
     * Calculate signal strength for a specific station.
     * Safe for client-side - handles null cases gracefully.
     * Always recalculates to ensure accurate signal display.
     * On client side, we scan for Radio Panels directly since tower data isn't synced.
     */
    private float calculateSignalStrengthForStation(Minecraft mc, RadioStation station) {
        if (mc.level == null || mc.player == null || station == null) {
            return 0.0f;
        }
        
        try {
            Vec3 playerPos = mc.player.position();
            float bestSignal = 0.0f;
            
            // First, try to use server-side tower data (more accurate)
            try {
                var serverTowers = uk.creatopia.unbound.dead_air.radio.TowerManager.getAllTowers(mc.level);
                if (serverTowers != null && !serverTowers.isEmpty()) {
                    // For Emergency Broadcast, check ALL towers
                    // For regular stations, only check towers broadcasting this station
                    boolean checkAllTowers = (station.getType() == RadioStation.StationType.EMERGENCY_BROADCAST);
                    
                    for (var tower : serverTowers) {
                        if (!tower.isPowered()) continue;
                        
                        // For Emergency Broadcast, check all towers
                        // For regular stations, only check towers broadcasting this station
                        if (!checkAllTowers && !tower.getStation().getId().equals(station.getId())) {
                            continue;
                        }
                        
                        if (tower.isInRange(playerPos)) {
                            float signal = uk.creatopia.unbound.dead_air.radio.SignalStrength.getFinalSignalStrength(mc.level, tower, playerPos);
                            if (signal > bestSignal) {
                                bestSignal = signal;
                            }
                        }
                    }
                    
                    // If we found signal from server data, return it
                    if (bestSignal > 0.0f) {
                        return bestSignal;
                    }
                }
            } catch (Exception e) {
                // Server-side data not available, will use client-side scan
            }
            
            // Fallback: On client side, scan for Radio Panels directly
            // Check a reasonable area around the player (within max broadcast range)
            int searchRadius = (int) Math.min(500, station.getBroadcastRange()); // Limit to 500 blocks for performance
            BlockPos playerBlockPos = BlockPos.containing(playerPos);
            
            // For Emergency Broadcast, check ALL Radio Panels in range
            // For regular stations, check Radio Panels that might broadcast this station
            boolean checkAllPanels = (station.getType() == RadioStation.StationType.EMERGENCY_BROADCAST);
            
            // Scan area around player for Radio Panels (step 8 for coverage)
            // Also do a fine-grained scan within 16 blocks to catch "right next to tower" cases (e.g. Medieval FM)
            int fineRadius = 16;
            for (int x = -searchRadius; x <= searchRadius; x += 8) {
                for (int z = -searchRadius; z <= searchRadius; z += 8) {
                    for (int y = -10; y <= 10; y += 5) {
                        BlockPos checkPos = playerBlockPos.offset(x, y, z);
                        
                        // Check if this is a Radio Panel (client-side check)
                        if (uk.creatopia.unbound.dead_air.tower.ApocalypseTowerDetector.isRadioPanel(mc.level, checkPos)) {
                            // Check if panel is activated (powered) - check NBT directly on client
                            boolean isActivated = false;
                            if (mc.level.getBlockEntity(checkPos) != null) {
                                net.minecraft.nbt.CompoundTag nbt = mc.level.getBlockEntity(checkPos).saveWithFullMetadata();
                                if (nbt != null) {
                                    // Check multiple possible NBT keys
                                    isActivated = nbt.contains("activated") && nbt.getBoolean("activated") ||
                                                 nbt.contains("active") && nbt.getBoolean("active") ||
                                                 nbt.contains("powered") && nbt.getBoolean("powered") ||
                                                 nbt.contains("Activated") && nbt.getBoolean("Activated") ||
                                                 nbt.contains("Active") && nbt.getBoolean("Active") ||
                                                 nbt.contains("Powered") && nbt.getBoolean("Powered");
                                    
                                    // Also check as int/byte (some mods store booleans as 0/1)
                                    if (!isActivated) {
                                        isActivated = (nbt.contains("activated") && nbt.getInt("activated") > 0) ||
                                                      (nbt.contains("active") && nbt.getInt("active") > 0) ||
                                                      (nbt.contains("powered") && nbt.getInt("powered") > 0);
                                    }
                                }
                            }
                            
                            // For regular stations: if panel is not activated, assume it might be a STANDARD tower (always powered)
                            // We'll show signal if the panel exists and is in range, even if activation check fails
                            // This is a best-effort approach since we can't determine tower type on client side
                            if (!isActivated && !checkAllPanels) {
                                // For regular stations, still check if panel exists in range
                                // STANDARD towers are always powered, so we'll show signal anyway
                                // This might show signal for wrong stations, but it's better than showing 0/5
                            }
                            
                            // Calculate distance and signal strength
                            Vec3 panelPos = Vec3.atCenterOf(checkPos);
                            double distance = playerPos.distanceTo(panelPos);
                            
                            if (distance > station.getBroadcastRange()) {
                                continue; // Out of range
                            }
                            
                            // Calculate signal strength based on distance
                            double normalizedDistance = distance / station.getBroadcastRange();
                            float signal = (float) (1.0 - (normalizedDistance * 0.9)); // Same formula as SignalStrength
                            signal = Math.max(0.0f, Math.min(1.0f, signal));
                            
                            // Apply weather penalty if enabled
                            if (uk.creatopia.unbound.dead_air.Config.enableWeatherEffects) {
                                if (mc.level.isRaining() || mc.level.isThundering()) {
                                    signal *= 0.7f;
                                }
                            }
                            
                            if (signal > bestSignal) {
                                bestSignal = signal;
                            }
                        }
                    }
                }
            }
            // Fine-grained scan within 16 blocks to catch "right next to tower" cases (e.g. Medieval FM)
            for (int x = -fineRadius; x <= fineRadius; x += 2) {
                for (int z = -fineRadius; z <= fineRadius; z += 2) {
                    for (int y = -8; y <= 8; y += 2) {
                        BlockPos checkPos = playerBlockPos.offset(x, y, z);
                        if (uk.creatopia.unbound.dead_air.tower.ApocalypseTowerDetector.isRadioPanel(mc.level, checkPos)) {
                            Vec3 panelPos = Vec3.atCenterOf(checkPos);
                            double distance = playerPos.distanceTo(panelPos);
                            if (distance <= station.getBroadcastRange()) {
                                double normalizedDistance = distance / station.getBroadcastRange();
                                float signal = (float) (1.0 - (normalizedDistance * 0.9));
                                signal = Math.max(0.0f, Math.min(1.0f, signal));
                                if (uk.creatopia.unbound.dead_air.Config.enableWeatherEffects &&
                                    (mc.level.isRaining() || mc.level.isThundering())) {
                                    signal *= 0.7f;
                                }
                                if (signal > bestSignal) bestSignal = signal;
                            }
                        }
                    }
                }
            }
            
            return bestSignal;
        } catch (Exception e) {
            // Client-side might not have full tower data - return 0 safely
            Dead_air.LOGGER.debug("Could not calculate signal strength on client: {}", e.getMessage());
            return 0.0f;
        }
    }
    
    /**
     * Get color for signal strength display.
     */
    private int getSignalColor(float signal) {
        if (signal <= 0.0f) return 0x666666; // Gray - no signal
        if (signal < 0.2f) return 0xFF0000;  // Red - very weak
        if (signal < 0.4f) return 0xFF8800;  // Orange - weak
        if (signal < 0.6f) return 0xFFFF00;  // Yellow - moderate
        if (signal < 0.8f) return 0xFF88FF00;  // Yellow-green - good
        return PIPBOY_HIGHLIGHT; // Yellow - excellent
    }
    
    /**
     * Tune to the current frequency (or nearest station if available).
     */
    private void tuneToCurrentFrequency() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            Dead_air.LOGGER.warn("Cannot tune: player or level is null");
            return;
        }
        
        try {
            if (nearestStation != null && nearestStation.getId() != null) {
                // Validate station before tuning
                if (nearestStation.getName() == null) {
                    Dead_air.LOGGER.warn("Station has null name, cannot tune");
                    return;
                }
                
                // Validate station ID
                if (nearestStation.getId() == null) {
                    Dead_air.LOGGER.warn("Station has null ID, cannot tune");
                    return;
                }
                
                // Double-check station is valid in registry
                RadioStation validatedStation = StationRegistry.getStation(nearestStation.getId());
                if (validatedStation == null) {
                    Dead_air.LOGGER.warn("Station {} not found in registry, cannot tune", nearestStation.getId());
                    return;
                }
                
                // Tune to the nearest station - this is safe on client
                // Use validated station to ensure it's the same object
                WalkieTalkieManager.tuneToStation(mc.player, validatedStation);
                
                // Send message on next tick to avoid issues
                mc.execute(() -> {
                    if (mc.player != null && validatedStation != null) {
                        try {
                            String stationName = validatedStation.getName();
                            if (stationName == null) {
                                stationName = "Unknown";
                            }
                            mc.player.sendSystemMessage(Component.literal("Tuned to: " + stationName + 
                                " (" + String.format("%.1f", validatedStation.getFrequency()) + " MHz)"));
                        } catch (Exception e) {
                            Dead_air.LOGGER.error("Error sending tune message", e);
                        }
                    }
                });
            } else {
                // No station at this frequency
                mc.execute(() -> {
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(Component.literal("No station found at " + 
                            String.format("%.1f", currentFrequency) + " MHz"));
                    }
                });
            }
        } catch (Exception e) {
            Dead_air.LOGGER.error("Error tuning to station", e);
            // Log full stack trace for debugging
            Dead_air.LOGGER.error("Stack trace:", e);
            mc.execute(() -> {
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal("§cError tuning to station: " + e.getMessage()));
                }
            });
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Station list click - use bounds from last render
            if (mouseX >= listBoundsX && mouseX <= listBoundsX + listBoundsWidth && mouseY >= listContentStartY) {
                if (availableStations != null && !availableStations.isEmpty()) {
                    int clickedIndex = (int)((mouseY - listContentStartY) / LIST_LINE_HEIGHT);
                    if (clickedIndex >= 0 && clickedIndex < availableStations.size()) {
                        RadioStation clickedStation = availableStations.get(clickedIndex);
                        if (clickedStation != null) {
                            currentFrequency = clickedStation.getFrequency();
                            updateNearestStation();
                            tuneToCurrentFrequency();
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
