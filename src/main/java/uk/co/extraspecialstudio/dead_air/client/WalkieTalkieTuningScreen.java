package uk.co.extraspecialstudio.dead_air.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscAnchor;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscButtons;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscInsets;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscLayoutSpec;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscRect;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscScreen;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscText;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.SignalStrength;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;
import uk.co.extraspecialstudio.dead_air.tower.KnownTowersClientCache;
import uk.co.extraspecialstudio.dead_air.walkie.WalkieTalkieManager;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI screen for Dead Air walkies: tune stations, power, Ping; T2 also has Link/Unlink.
 * Voice/channel tab removed with Flaton Walkie-Talkie dep (may return as a later upgrade).
 */
@SuppressWarnings("null")
public class WalkieTalkieTuningScreen extends EscScreen {
    private static final float MIN_FREQUENCY = 88.0f;
    private static final float MAX_FREQUENCY = 109.0f;
    
    // Pip-Boy color scheme (ZombieCraft reference - crisp greens, slight transparency)
    private static final int PIPBOY_HIGHLIGHT = 0xFFFF00; // Yellow highlight
    private static final int PIPBOY_TEXT = 0x00FF41; // Bright green text
    private static final int PIPBOY_LINKED = 0xFF8800; // Orange — T2 linked station
    private static final int BORDER_COLOR = 0xFF00E050; // Crisp neon green border
    private static final int PANEL_BG = 0x70001808; // Semi-transparent dark green (~44% opacity)
    /** Very subtle glow - minimal to keep UI crisp */
    private static final int GLOW_COLOR = 0x1000E050;   // Very faint green
    private static final int NEEDLE_GLOW = 0x15FF0000;  // Very faint red for needle
    
    protected List<RadioStation> availableStations;
    protected float currentFrequency = 88.0f;
    protected RadioStation nearestStation = null;
    /** Which hand opened this GUI — tune/power/link only write that walkie's NBT. */
    protected final net.minecraft.world.InteractionHand boundHand;
    /** Slider type that can be synced from a frequency (e.g. when clicking a station in the list). */
    protected FrequencySliderWidget frequencySlider;
    protected AbstractSliderButton volumeSlider;
    protected Button tuneButton;
    protected Button powerButton;
    protected Button pingLocationButton;
    protected Button syncButton;
    protected Button configCogButton;
    
    // Waveform animation
    private float waveformTime = 0.0f;
    private final List<Float> waveformData = new ArrayList<>();
    
    // Station list bounds for click detection (updated in buildLayout)
    private int listBoundsX, listBoundsY, listBoundsWidth, listContentStartY;
    private static final int LIST_LINE_HEIGHT = 12;

    // Anchor layout regions
    protected EscRect bodyRect;
    protected EscRect leftPanelRect;
    protected EscRect centerPanelRect;
    protected EscRect rightPanelRect;
    protected EscRect centerDialRect;
    protected EscRect centerSliderRow;
    protected EscRect centerTuneRow;
    protected EscRect rightSignalRect;
    protected EscRect rightVolumeRect;
    protected EscRect rightButtonsRect;
    protected int centerContentCenterX;
    protected int dialY;
    protected int dialRadius = 45;
    protected int freqTextY;
    protected int infoY;
    protected int signalSectionY;
    protected int volumeSectionY;
    protected int buttonsSectionY;
    
    public WalkieTalkieTuningScreen() {
        this(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    public WalkieTalkieTuningScreen(net.minecraft.world.InteractionHand hand) {
        super(Component.literal("Radio"));
        this.boundHand = hand != null ? hand : net.minecraft.world.InteractionHand.MAIN_HAND;

        // Initialize frequency to current station if tuned on the bound walkie
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ItemStack walkie = getBoundWalkie(mc);
            WalkieTalkieManager.WalkieTalkieState state = WalkieTalkieManager.getState(mc.player, walkie);
            if (state.getCurrentStation() != null) {
                currentFrequency = state.getCurrentStation().getFrequency();
            }
        }
        
        // Initialize waveform data
        for (int i = 0; i < 50; i++) {
            waveformData.add(0.0f);
        }
    }

    /** Walkie this screen is editing (the one that was right-clicked / held when opened). */
    private ItemStack getBoundWalkie(Minecraft mc) {
        if (mc == null || mc.player == null) return ItemStack.EMPTY;
        ItemStack held = mc.player.getItemInHand(boundHand);
        if (WalkieTalkieManager.isWalkieTalkieItem(held)) return held;
        return WalkieTalkieManager.getPrimaryWalkieStack(mc.player);
    }
    
    @Override
    protected void init() {
        this.clearWidgets();
        super.init();
    }

    @Override
    protected void buildLayout() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        recomputeLayoutRegions();

        refreshAvailableStations();
        updateNearestStation();

        float sliderValue = (currentFrequency - MIN_FREQUENCY) / (MAX_FREQUENCY - MIN_FREQUENCY);
        frequencySlider = new FrequencySliderWidget(0, 0, 240, 20, sliderValue, this);
        addRenderableWidget(frequencySlider);

        double currentVolume = Config.maxVolume;
        volumeSlider = new AbstractSliderButton(0, 0, 120, 20,
            EscText.literal(String.format("Volume: %.0f%%", currentVolume * 100)),
            currentVolume) {
            @Override
            protected void updateMessage() {
                double newVolume = this.value;
                Config.setMaxVolume(newVolume);
                this.setMessage(EscText.literal(String.format("Volume: %.0f%%", Config.maxVolume * 100)));
                uk.co.extraspecialstudio.dead_air.audio.AudioManager.updateVolumeForAllSounds();
            }

            @Override
            protected void applyValue() {
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (!this.active || !this.visible || button != 0) {
                    return false;
                }
                int handleWidth = 8;
                double handleX = this.getX() + (this.value * (this.width - handleWidth));
                double handleY = this.getY();
                double tolerance = 4.0;
                if (mouseX >= handleX - tolerance && mouseX <= handleX + handleWidth + tolerance
                    && mouseY >= handleY - tolerance && mouseY <= handleY + this.height + tolerance) {
                    return super.mouseClicked(mouseX, mouseY, button);
                }
                if (mouseX >= this.getX() && mouseX <= this.getX() + this.width
                    && mouseY >= this.getY() && mouseY <= this.getY() + this.height) {
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

        tuneButton = addButton(
            EscText.literal(nearestStation != null ? "TUNE: " + nearestStation.getName() : "TUNE: NO SIGNAL"),
            new EscRect(0, 0, 200, 20),
            button -> tuneToCurrentFrequency()
        );
        tuneButton.visible = true;
        tuneButton.active = true;

        pingLocationButton = addButton(Component.literal("PING LOCATION"), new EscRect(0, 0, 120, 20), button -> {
            if (mc.player != null) {
                String playerName = mc.player.getName().getString();
                int x = (int) mc.player.getX();
                int y = (int) mc.player.getY();
                int z = (int) mc.player.getZ();
                String dimension = mc.level != null ? mc.level.dimension().location().toString() : "unknown";
                Component locationMessage = Component.literal(
                    String.format("%s: Location: %d, %d, %d (%s)", playerName, x, y, z, dimension)
                );
                mc.player.sendSystemMessage(locationMessage);
            }
        });

        WalkieTalkieManager.WalkieTalkieState state = WalkieTalkieManager.getState(mc.player, getBoundWalkie(mc));
        powerButton = addButton(
            Component.literal(state.isOn() ? "[ON]" : "[OFF]"),
            new EscRect(0, 0, 120, 20),
            button -> {
                ItemStack walkie = getBoundWalkie(mc);
                WalkieTalkieManager.WalkieTalkieState currentState = WalkieTalkieManager.getState(mc.player, walkie);
                if (currentState.isOn()) {
                    WalkieTalkieManager.turnOff(mc.player, walkie);
                    uk.co.extraspecialstudio.dead_air.audio.AudioManager.stopAll();
                } else {
                    WalkieTalkieManager.turnOn(mc.player, walkie);
                }
                currentState = WalkieTalkieManager.getState(mc.player, walkie);
                button.setMessage(EscText.literal(currentState.isOn() ? "[ON]" : "[OFF]"));
            }
        );

        ItemStack primaryWalkie = getBoundWalkie(mc);
        // Link/Unlink is T2-only — T1 GUI has Power + Ping only.
        if (WalkieTalkieManager.canLinkCrossDim(primaryWalkie)) {
            syncButton = addButton(
                Component.translatable(WalkieTalkieManager.isUsingLinkedMode(primaryWalkie)
                    ? "screen.dead_air.walkie.unlink" : "screen.dead_air.walkie.sync"),
                new EscRect(0, 0, 120, 20),
                button -> {
                    if (mc.player == null) return;
                    ItemStack walkie = getBoundWalkie(mc);
                    if (!WalkieTalkieManager.canLinkCrossDim(walkie)) {
                        mc.player.sendSystemMessage(Component.translatable("message.dead_air.walkie.sync_need_t2"));
                        return;
                    }
                    if (WalkieTalkieManager.isUsingLinkedMode(walkie)) {
                        WalkieTalkieManager.clearLink(mc.player, walkie);
                        mc.player.sendSystemMessage(Component.translatable("message.dead_air.walkie.unlink_ok"));
                        refreshAvailableStations();
                        updateNearestStation();
                        refreshLinkButtonLabel();
                        return;
                    }
                    var boosted = findBoostedTowerForLink(mc);
                    if (boosted == null) {
                        mc.player.sendSystemMessage(Component.translatable("message.dead_air.walkie.sync_need_boost"));
                        return;
                    }
                    RadioStation linkedStation = StationRegistry.getStation(boosted.stationId);
                    if (linkedStation == null) {
                        linkedStation = nearestStation;
                    }
                    if (linkedStation == null) {
                        linkedStation = WalkieTalkieManager.getState(mc.player, walkie).getCurrentStation();
                    }
                    WalkieTalkieManager.syncToTower(mc.player, walkie, mc.level.dimension(), boosted.panelPos, linkedStation);
                    if (linkedStation != null) {
                        WalkieTalkieManager.tuneToStation(mc.player, linkedStation, walkie);
                    }
                    mc.player.sendSystemMessage(Component.translatable("message.dead_air.walkie.sync_ok"));
                    refreshAvailableStations();
                    updateNearestStation();
                    refreshLinkButtonLabel();
                }
            );
        }

        configCogButton = addAnchoredButton(Component.literal("\u2699"), rightPanelRect,
            EscLayoutSpec.of(EscAnchor.TOP_RIGHT, 0, 0, 20, 20),
            b -> mc.setScreen(new DeadAirConfigScreen(this)));

        layoutRadioTabWidgets();
    }

    private void recomputeLayoutRegions() {
        bodyRect = radioBodyRect();

        EscRect[] cols = bodyRect.splitColumns(new float[]{0.26f, 0.48f, 0.26f}, 12);
        if (cols.length >= 3) {
            leftPanelRect = cols[0];
            centerPanelRect = cols[1];
            rightPanelRect = cols[2];
        } else {
            leftPanelRect = bodyRect;
            centerPanelRect = bodyRect;
            rightPanelRect = bodyRect;
        }

        listBoundsX = leftPanelRect.x();
        listBoundsY = leftPanelRect.y();
        listBoundsWidth = leftPanelRect.width();
        listContentStartY = leftPanelRect.y() + 18;

        centerContentCenterX = centerPanelRect.x() + centerPanelRect.width() / 2;

        int sliderH = 20;
        int tuneH = 20;
        int centerGap = 4;
        centerTuneRow = new EscRect(centerPanelRect.x(), centerPanelRect.bottom() - tuneH,
            centerPanelRect.width(), tuneH);
        centerSliderRow = new EscRect(centerPanelRect.x(), centerTuneRow.y() - centerGap - sliderH,
            centerPanelRect.width(), sliderH);
        centerDialRect = new EscRect(centerPanelRect.x(), centerPanelRect.y(), centerPanelRect.width(),
            Math.max(0, centerSliderRow.y() - centerPanelRect.y() - centerGap));

        dialRadius = Math.min(45, Math.max(30, Math.min(centerDialRect.width(), centerDialRect.height()) / 6));
        dialY = centerDialRect.y() + 12 + dialRadius;
        freqTextY = dialY + 10;
        int dialBottom = dialY + dialRadius + 6;
        infoY = dialBottom + 5;

        int signalH = Math.min(70, rightPanelRect.height() / 3);
        int volumeSliderH = 20;
        int rightGap = 6;
        rightSignalRect = new EscRect(rightPanelRect.x(), rightPanelRect.y(), rightPanelRect.width(), signalH);
        rightVolumeRect = new EscRect(rightPanelRect.x(), rightSignalRect.bottom() + rightGap,
            rightPanelRect.width(), volumeSliderH);
        rightButtonsRect = new EscRect(rightPanelRect.x(), rightVolumeRect.bottom() + rightGap,
            rightPanelRect.width(), Math.max(52, rightPanelRect.bottom() - rightVolumeRect.bottom() - rightGap));
        signalSectionY = rightSignalRect.y();
        volumeSectionY = rightVolumeRect.y();
        buttonsSectionY = rightButtonsRect.y();
    }

    /**
     * Layout region for the three radio panels. Companions (Pip-Boy) inset this below a tab strip.
     */
    protected EscRect radioBodyRect() {
        return contentRect();
    }

    private void layoutRadioTabWidgets() {
        if (frequencySlider != null && centerSliderRow != null) {
            layoutWidget(frequencySlider, centerSliderRow.inset(EscInsets.of(24, 0, 24, 0)));
        }
        if (tuneButton != null && centerTuneRow != null) {
            int buttonWidth = Math.min(200, centerTuneRow.width() - 40);
            EscRect tuneBounds = resolve(centerTuneRow,
                EscLayoutSpec.of(EscAnchor.CENTER, 0, 0, buttonWidth, centerTuneRow.height()));
            layoutWidget(tuneButton, tuneBounds);
        }
        if (volumeSlider != null && rightVolumeRect != null) {
            layoutWidget(volumeSlider, rightVolumeRect.inset(EscInsets.of(5, 0, 5, 0)));
        }
        if (rightButtonsRect != null && rightButtonsRect.height() > 0) {
            boolean showLink = syncButton != null;
            int gaps = showLink ? 2 : 1;
            int rows = showLink ? 3 : 2;
            int gap = 3;
            int rowH = Math.max(18, (rightButtonsRect.height() - gap * gaps) / rows);
            int y = rightButtonsRect.y();
            EscRect powerRow = new EscRect(rightButtonsRect.x(), y, rightButtonsRect.width(), rowH);
            y = powerRow.bottom() + gap;
            EscRect syncRow = null;
            if (showLink) {
                syncRow = new EscRect(rightButtonsRect.x(), y, rightButtonsRect.width(), rowH);
                y = syncRow.bottom() + gap;
            }
            EscRect pingRow = new EscRect(rightButtonsRect.x(), y, rightButtonsRect.width(),
                Math.max(rowH, rightButtonsRect.bottom() - y));
            if (powerButton != null) {
                layoutWidget(powerButton, powerRow.inset(EscInsets.of(5, 0, 5, 0)));
            }
            if (syncButton != null && syncRow != null) {
                layoutWidget(syncButton, syncRow.inset(EscInsets.of(5, 0, 5, 0)));
            }
            if (pingLocationButton != null) {
                layoutWidget(pingLocationButton, pingRow.inset(EscInsets.of(5, 0, 5, 0)));
            }
        }
    }

    private void refreshLinkButtonLabel() {
        Minecraft mc = Minecraft.getInstance();
        if (syncButton == null || mc.player == null) return;
        ItemStack walkie = getBoundWalkie(mc);
        syncButton.setMessage(Component.translatable(
            WalkieTalkieManager.isUsingLinkedMode(walkie)
                ? "screen.dead_air.walkie.unlink" : "screen.dead_air.walkie.sync"));
    }

    /** Prefer a Signal Upgrade tower matching the selected in-range station; else any boosted tower in range. */
    private uk.co.extraspecialstudio.dead_air.net.KnownTowersSyncPacket.TowerEntry findBoostedTowerForLink(Minecraft mc) {
        if (mc.player == null || mc.level == null) return null;
        Vec3 pos = mc.player.position();
        if (nearestStation != null) {
            var match = KnownTowersClientCache.findBoostedTowerInRangeForStation(
                pos, nearestStation.getId(), mc.level);
            if (match != null) return match;
        }
        return KnownTowersClientCache.findBoostedTowerInRange(pos, mc.level);
    }

    /**
     * Refresh available stations list.
     * In-range towers only, plus Emergency. A T2's linked station is always kept on the list
     * while a link exists — even out of range or in another dimension.
     */
    private void refreshAvailableStations() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            availableStations = java.util.Collections.emptyList();
            return;
        }
        try {
            Vec3 pos = mc.player.position();
            var level = mc.level;
            List<ResourceLocation> inRange = KnownTowersClientCache.getStationsInRange(pos, level);
            availableStations = new ArrayList<>();
            // Emergency Broadcast is always available and first (full signal regardless of towers).
            RadioStation emergency = StationRegistry.getStation(StationRegistry.EMERGENCY_BROADCAST_ID);
            if (emergency != null) {
                availableStations.add(emergency);
            }
            if (inRange != null) {
                for (ResourceLocation id : inRange) {
                    if (id.equals(StationRegistry.EMERGENCY_BROADCAST_ID)) continue;
                    RadioStation s = StationRegistry.getStation(id);
                    if (s != null) availableStations.add(s);
                }
            }
            // T2 linked station stays visible permanently while linked (cross-dim / out of range).
            ItemStack walkie = getBoundWalkie(mc);
            if (WalkieTalkieManager.hasLinkedTower(walkie)) {
                ResourceLocation linkedId = WalkieTalkieManager.getLinkedStationId(walkie);
                if (linkedId != null && !linkedId.equals(StationRegistry.EMERGENCY_BROADCAST_ID)) {
                    boolean already = availableStations.stream().anyMatch(s -> s.getId().equals(linkedId));
                    if (!already) {
                        RadioStation linked = StationRegistry.getStation(linkedId);
                        if (linked != null) availableStations.add(linked);
                    }
                }
            }
            availableStations.sort((a, b) -> {
                if (a.getId().equals(StationRegistry.EMERGENCY_BROADCAST_ID)) return -1;
                if (b.getId().equals(StationRegistry.EMERGENCY_BROADCAST_ID)) return 1;
                return Float.compare(a.getFrequency(), b.getFrequency());
            });
            if (availableStations == null) availableStations = java.util.Collections.emptyList();
        } catch (Exception e) {
            availableStations = java.util.Collections.emptyList();
        }
    }
    
    /**
     * Update the nearest station based on current frequency.
     * Only considers stations that the player has unlocked.
     */
    private void updateNearestStation() {
        updateNearestStation(null);
    }

    /**
     * Update the nearest station based on current frequency.
     * When {@code preferred} is set (e.g. list click), tune resolution prefers that station.
     */
    private void updateNearestStation(RadioStation preferred) {
        refreshAvailableStations();
        
        if (availableStations == null || availableStations.isEmpty()) {
            nearestStation = null;
        } else if (preferred != null && availableStations.stream().anyMatch(s -> s.getId().equals(preferred.getId()))) {
            nearestStation = preferred;
            currentFrequency = preferred.getFrequency();
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
            
            if (nearestStation != null && nearestDistance > 0.2f) {
                nearestStation = null;
            }
        }
        
        if (tuneButton != null) {
            tuneButton.setMessage(EscText.literal(
                nearestStation != null ? "TUNE: " + nearestStation.getName() : "TUNE: NO SIGNAL"
            ));
        }
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        
        // Pip-Boy style background - slight transparency, dark green tint (ZombieCraft reference)
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        guiGraphics.fill(0, 0, width, height, getBackdropColor());
        renderOuterChrome(guiGraphics, mouseX, mouseY, partialTick);

        if (!shouldRenderRadioContent()) {
            renderNonRadioTab(guiGraphics, mouseX, mouseY, partialTick);
            setRadioWidgetsVisible(false);
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }
        setRadioWidgetsVisible(true);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }
        // Refresh station list when cache may have changed (e.g. after airdrop GUI changed panel station)
        refreshAvailableStations();
        updateNearestStation();

        if (leftPanelRect == null || centerPanelRect == null || rightPanelRect == null) {
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        int leftSectionX = leftPanelRect.x();
        int leftSectionY = leftPanelRect.y();
        int leftSectionWidth = leftPanelRect.width();
        int centerSectionX = centerPanelRect.x();
        int centerSectionY = centerPanelRect.y();
        int centerSectionWidth = centerPanelRect.width();
        int centerSectionHeight = centerPanelRect.height();
        int rightSectionX = rightPanelRect.x();
        int rightSectionWidth = rightPanelRect.width();
        int sectionHeight = bodyRect.height();
        int topMargin = bodyRect.y();
        int infoBoxWidth = Math.min(centerSectionWidth - 20, 240);
        int infoBoxLeft = centerContentCenterX - infoBoxWidth / 2;
        int infoBoxRight = centerContentCenterX + infoBoxWidth / 2;

        int borderThickness = 2;
        
        guiGraphics.fill(leftSectionX - borderThickness, leftSectionY - borderThickness,
            leftSectionX + leftSectionWidth + borderThickness, leftSectionY + sectionHeight + borderThickness, PANEL_BG);
        drawBorderWithGlow(guiGraphics, leftSectionX, leftSectionY, leftSectionWidth, sectionHeight, BORDER_COLOR, borderThickness);

        guiGraphics.fill(centerSectionX - borderThickness, centerSectionY - borderThickness,
            centerSectionX + centerSectionWidth + borderThickness, centerSectionY + centerSectionHeight + borderThickness, PANEL_BG);
        drawBorderWithGlow(guiGraphics, centerSectionX, centerSectionY, centerSectionWidth, centerSectionHeight, BORDER_COLOR, borderThickness);

        guiGraphics.fill(rightSectionX - borderThickness, topMargin - borderThickness,
            rightSectionX + rightSectionWidth + borderThickness, topMargin + sectionHeight + borderThickness, PANEL_BG);
        drawBorderWithGlow(guiGraphics, rightSectionX, topMargin, rightSectionWidth, sectionHeight, BORDER_COLOR, borderThickness);
        
        // Update waveform animation
        waveformTime += partialTick * 0.1f;
        updateWaveform();
        // Music is controlled only by ClientEvents client tick - opening/closing GUI must not affect playback
        
        // Layout variables are already calculated at the top of render() method
        // Use those variables here
        // borderThickness/borderColor/sectionHeight already computed above
        
        // LEFT SECTION: Station List
        drawStationList(guiGraphics, leftSectionX, leftSectionY, leftSectionWidth, sectionHeight);

        // RIGHT SECTIONS
        drawSignalContent(guiGraphics, rightSectionX, signalSectionY, rightSectionWidth, 70);

        drawRadioDial(guiGraphics, centerContentCenterX, dialY, dialRadius, mouseX, mouseY);
        if (frequencySlider != null && frequencySlider.visible) {
            String freqText = String.format("%.1f MHz", currentFrequency);
            int tw = font.width(freqText);
            int freqTextX = Mth.clamp(centerContentCenterX - tw / 2, centerSectionX + 10, centerSectionX + centerSectionWidth - tw - 10);
            drawStringWithGlow(guiGraphics, freqText, freqTextX, freqTextY, PIPBOY_TEXT);
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
                drawStringWithGlow(guiGraphics, stationName, nameX, infoY, PIPBOY_HIGHLIGHT);
                
                // Station frequency - ensure it fits
                String stationFreqText = String.format("%.1f MHz", stationFreq);
                int freqTextWidth = font.width(stationFreqText);
                int freqDisplayColor = distance < 0.1f ? PIPBOY_HIGHLIGHT : PIPBOY_TEXT;
                int freqX = Math.max(infoBoxLeft + 10, centerContentCenterX - freqTextWidth / 2);
                freqX = Math.min(freqX, infoBoxRight - freqTextWidth - 10);
                drawStringWithGlow(guiGraphics, stationFreqText, freqX, infoY + 10, freqDisplayColor);
                
                String genre = nearestStation.getGenre();
                if (genre == null) genre = "Unknown";
                if (font.width(genre) > maxNameWidth) genre = font.plainSubstrByWidth(genre, maxNameWidth - 3) + "...";
                int genreX = Math.max(infoBoxLeft + 10, centerContentCenterX - font.width(genre) / 2);
                genreX = Math.min(genreX, infoBoxRight - font.width(genre) - 10);
                drawStringWithGlow(guiGraphics, genre, genreX, infoY + 20, PIPBOY_TEXT);
                
                float signal = calculateSignalStrengthForStation(mc, nearestStation);
                int bars = SignalStrength.getSignalBars(signal);
                String signalText = "SIGNAL: " + bars + "/5";
                int signalColor = getSignalColor(signal);
                int signalTextWidth = font.width(signalText);
                int signalX = Math.max(infoBoxLeft + 10, centerContentCenterX - signalTextWidth / 2);
                signalX = Math.min(signalX, infoBoxRight - signalTextWidth - 10);
                drawStringWithGlow(guiGraphics, signalText, signalX, infoY + 30, signalColor);
                int nextY = infoY + 38;
                
                // Show "STATIC" if signal is weak - ensure it fits
                if (signal > 0.0f && signal < 0.3f) {
                    int staticX = Math.max(infoBoxLeft + 10, centerContentCenterX - font.width("STATIC") / 2);
                    staticX = Math.min(staticX, infoBoxRight - font.width("STATIC") - 10);
                    drawStringWithGlow(guiGraphics, "STATIC", staticX, nextY, PIPBOY_TEXT);
                }
            } catch (Exception e) {
                // Fallback: show error message
                drawCenteredStringWithGlow(guiGraphics, "Error", centerContentCenterX, infoY, 0xFF0000);
            }
        } else {
            int noSignalX = Math.max(infoBoxLeft + 10, centerContentCenterX - font.width("NO SIGNAL") / 2);
            noSignalX = Math.min(noSignalX, infoBoxRight - font.width("NO SIGNAL") - 10);
            drawStringWithGlow(guiGraphics, "NO SIGNAL", noSignalX, infoY, 0x666666);
            int staticX = Math.max(infoBoxLeft + 10, centerContentCenterX - font.width("STATIC") / 2);
            staticX = Math.min(staticX, infoBoxRight - font.width("STATIC") - 10);
            drawStringWithGlow(guiGraphics, "STATIC", staticX, infoY + 10, 0x888888);
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
        // Draw full circle border with glow
        for (int i = 0; i < 360; i++) {
            float angle1 = i;
            float angle2 = (i + 1) % 360;
            float rad1 = (float)Math.toRadians(angle1);
            float rad2 = (float)Math.toRadians(angle2);
            
            int x1 = dialCenterX + (int)(Math.cos(rad1) * borderRadius);
            int y1 = dialCenterY + (int)(Math.sin(rad1) * borderRadius);
            int x2 = dialCenterX + (int)(Math.cos(rad2) * borderRadius);
            int y2 = dialCenterY + (int)(Math.sin(rad2) * borderRadius);
            
            drawLineWithGlow(guiGraphics, x1, y1, x2, y2, BORDER_COLOR, GLOW_COLOR, borderThickness);
        }
        
        // Draw the arc outline (semi-circle) with glow
        drawArcWithGlow(guiGraphics, dialCenterX, dialCenterY, dialRadius, startAngle, endAngle, BORDER_COLOR, 3);
        
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
        
        // Draw red indicator line (needle) with minimal glow for crispness
        drawLineWithGlow(guiGraphics, x1, y1, x2, y2, 0xFFFF0000, NEEDLE_GLOW, 2);
        
        // Draw center pivot dot (no glow - crisp)
        guiGraphics.fill(dialCenterX - 3, dialCenterY - 3, dialCenterX + 3, dialCenterY + 3, dialTickColor);
        
        RenderSystem.disableBlend();
    }
    
    private void drawArcWithGlow(GuiGraphics guiGraphics, int centerX, int centerY, int radius, 
                         float startAngle, float endAngle, int color, int thickness) {
        drawArcInternal(guiGraphics, centerX, centerY, radius, startAngle, endAngle, color, thickness, true);
    }
    
    private void drawArcInternal(GuiGraphics guiGraphics, int centerX, int centerY, int radius, 
                         float startAngle, float endAngle, int color, int thickness, boolean withGlow) {
        int segments = 60;
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
            if (withGlow) {
                drawLineWithGlow(guiGraphics, x1, y1, x2, y2, color, GLOW_COLOR, thickness);
            } else {
                drawLine(guiGraphics, x1, y1, x2, y2, color, thickness);
            }
        }
    }
    
    /**
     * Draw a line between two points.
     */
    private void drawLine(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color, int thickness) {
        drawLineInternal(guiGraphics, x1, y1, x2, y2, color, thickness);
    }
    
    /** Internal line drawing - used by drawLine and drawLineWithGlow */
    private void drawLineInternal(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color, int thickness) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float)Math.sqrt(dx * dx + dy * dy);
        if (length == 0) return;
        float stepX = dx / length;
        float stepY = dy / length;
        int half = Math.max(1, thickness / 2);
        for (float i = 0; i <= length; i += 0.5f) {
            int x = (int)(x1 + stepX * i);
            int y = (int)(y1 + stepY * i);
            guiGraphics.fill(x - half, y - half, x + half, y + half, color);
        }
    }
    
    /** Draw a line with a faint glow (draws thicker dim line first). */
    private void drawLineWithGlow(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color, int glowColor, int thickness) {
        drawLineInternal(guiGraphics, x1, y1, x2, y2, glowColor, thickness + 1);
        drawLineInternal(guiGraphics, x1, y1, x2, y2, color, thickness);
    }
    
    /** Draw text with a very subtle outline (4 directions only for crisp look). */
    private void drawStringWithGlow(GuiGraphics guiGraphics, String text, int x, int y, int color) {
        int outlineColor = (color & 0x00FFFFFF) | 0x10000000;
        EscText.drawString(guiGraphics, font, text, x - 1, y, outlineColor, false, uk.co.extraspecialstudio.extraspecial.esc.ui.EscFonts.DEFAULT);
        EscText.drawString(guiGraphics, font, text, x + 1, y, outlineColor, false, uk.co.extraspecialstudio.extraspecial.esc.ui.EscFonts.DEFAULT);
        EscText.drawString(guiGraphics, font, text, x, y - 1, outlineColor, false, uk.co.extraspecialstudio.extraspecial.esc.ui.EscFonts.DEFAULT);
        EscText.drawString(guiGraphics, font, text, x, y + 1, outlineColor, false, uk.co.extraspecialstudio.extraspecial.esc.ui.EscFonts.DEFAULT);
        EscText.drawString(guiGraphics, font, text, x, y, color, false, uk.co.extraspecialstudio.extraspecial.esc.ui.EscFonts.DEFAULT);
    }

    /** Draw centered text with a faint glow. */
    private void drawCenteredStringWithGlow(GuiGraphics guiGraphics, String text, int centerX, int y, int color) {
        int x = centerX - font.width(text) / 2;
        drawStringWithGlow(guiGraphics, text, x, y, color);
    }
    
    /** Draw a border rectangle with subtle glow (draws expanded dim border first). */
    private void drawBorderWithGlow(GuiGraphics guiGraphics, int x, int y, int width, int height, int borderColor, int borderThickness) {
        int g = 1; // Glow expansion (minimal for crisp edges)
        guiGraphics.fill(x - borderThickness - g, y - borderThickness - g, x + width + borderThickness + g, y - borderThickness + g, GLOW_COLOR);
        guiGraphics.fill(x - borderThickness - g, y + height - g, x + width + borderThickness + g, y + height + borderThickness + g, GLOW_COLOR);
        guiGraphics.fill(x - borderThickness - g, y - borderThickness - g, x - borderThickness + g, y + height + borderThickness + g, GLOW_COLOR);
        guiGraphics.fill(x + width - g, y - borderThickness - g, x + width + borderThickness + g, y + height + borderThickness + g, GLOW_COLOR);
        guiGraphics.fill(x - borderThickness, y - borderThickness, x + width + borderThickness, y, borderColor);
        guiGraphics.fill(x - borderThickness, y + height, x + width + borderThickness, y + height + borderThickness, borderColor);
        guiGraphics.fill(x - borderThickness, y - borderThickness, x, y + height + borderThickness, borderColor);
        guiGraphics.fill(x + width, y - borderThickness, x + width + borderThickness, y + height + borderThickness, borderColor);
    }
    
    /**
     * Draw station list in Pip-Boy style (left side).
     * Clips each row to panel bounds so nothing sticks out in small windows.
     */
    private void drawStationList(GuiGraphics guiGraphics, int x, int y, int listWidth, int panelHeight) {
        int contentRight = x + listWidth - 5;
        int contentLeft = x + 9;

        drawStringWithGlow(guiGraphics, "RADIO STATIONS", contentLeft, y + 5, PIPBOY_TEXT);
        int listY = y + 18;

        if (availableStations == null || availableStations.isEmpty()) {
            drawStringWithGlow(guiGraphics, "No stations", contentLeft, listY, 0x666666);
            drawStringWithGlow(guiGraphics, "discovered", contentLeft, listY + 10, 0x666666);
            drawStringWithGlow(guiGraphics, "Find a tower!", contentLeft, listY + 20, 0x666666);
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
            boolean isLinked = false;
            Minecraft mcList = Minecraft.getInstance();
            if (mcList.player != null) {
                ResourceLocation linkedId = WalkieTalkieManager.getLinkedStationId(getBoundWalkie(mcList));
                isLinked = linkedId != null && linkedId.equals(station.getId());
            }
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
            } else if (isLinked) {
                guiGraphics.fill(x + 3, listY - 1, contentRight, listY + LIST_LINE_HEIGHT - 1, 0x55FF8800);
            }

            // Linked station is always orange; selected-but-not-linked stays yellow.
            int color = isLinked ? PIPBOY_LINKED : (isSelected ? PIPBOY_HIGHLIGHT : PIPBOY_TEXT);
            // Clip name to name area only so scrolling text never bleeds into the MHz column
            guiGraphics.enableScissor(x + 2, listY - 1, nameAreaRight + 2, listY + LIST_LINE_HEIGHT + 1);
            if (fullTextWidth <= nameAreaWidth) {
                drawStringWithGlow(guiGraphics, displayName, contentLeft, listY, color);
            } else {
                long t = System.currentTimeMillis();
                int scrollRange = Math.max(0, fullTextWidth - nameAreaWidth);
                int pause = 50;
                int cycle = scrollRange + pause * 2;
                int phase = (int) ((t / 40) % Math.max(1, cycle));
                int scrollOffset = phase < pause ? 0 : (phase >= pause + scrollRange ? scrollRange : phase - pause);
                scrollOffset = Mth.clamp(scrollOffset, 0, scrollRange);
                drawStringWithGlow(guiGraphics, displayName, contentLeft - scrollOffset, listY, color);
            }
            guiGraphics.disableScissor();

            if (freqX >= nameAreaRight + gap) {
                drawStringWithGlow(guiGraphics, freqText, freqX, listY, 0x888888);
            }
            listY += LIST_LINE_HEIGHT;
        }
    }
    
    /**
     * Draw signal, volume label, and button labels - NO borders/boxes (ZombieCraft: clean, no extra panels)
     */
    private void drawSignalContent(GuiGraphics guiGraphics, int x, int y, int boxWidth, int boxHeight) {
        drawStringWithGlow(guiGraphics, "SIGNAL", x + 5, y + 5, PIPBOY_TEXT);
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
     * Delegates to WalkieTalkieOverlay for consistency with music playback (includes fallback when no towers).
     */
    private float calculateSignalStrengthForStation(Minecraft mc, RadioStation station) {
        return WalkieTalkieOverlay.calculateSignalStrengthForStation(mc, station);
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
    
    private void tuneToStation(RadioStation station) {
        if (station == null) {
            tuneToCurrentFrequency();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        RadioStation validatedStation = StationRegistry.getStation(station.getId());
        if (validatedStation == null) {
            return;
        }

        currentFrequency = validatedStation.getFrequency();
        if (frequencySlider != null) {
            frequencySlider.setValueFromFrequency(currentFrequency);
        }
        updateNearestStation(validatedStation);

        try {
            WalkieTalkieManager.tuneToStation(mc.player, validatedStation, getBoundWalkie(mc));
            if (validatedStation.getType() == RadioStation.StationType.EMERGENCY_BROADCAST) {
                uk.co.extraspecialstudio.dead_air.audio.AudioManager.stopAll();
            } else {
                uk.co.extraspecialstudio.dead_air.audio.AudioManager.update(mc, validatedStation, mc.player.position());
            }

            mc.execute(() -> {
                if (mc.player != null && mc.gui != null) {
                    String stationName = validatedStation.getName() != null ? validatedStation.getName() : "Unknown";
                    mc.gui.getChat().addMessage(Component.literal("Tuned to: " + stationName +
                        " (" + String.format("%.1f", validatedStation.getFrequency()) + " MHz)"));
                }
            });
        } catch (Exception e) {
            Dead_air.LOGGER.error("Error tuning to station {}", validatedStation.getId(), e);
        }
    }

    /**
     * Tune to the current frequency (or nearest station if available).
     */
    private void tuneToCurrentFrequency() {
        if (nearestStation != null) {
            tuneToStation(nearestStation);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        mc.execute(() -> {
            if (mc.player != null && mc.gui != null) {
                mc.gui.getChat().addMessage(Component.literal("No station found at " +
                    String.format("%.1f", currentFrequency) + " MHz"));
            }
        });
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!shouldRenderRadioContent()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button == 0) {
            // Station list click - use bounds from last render
            if (mouseX >= listBoundsX && mouseX <= listBoundsX + listBoundsWidth && mouseY >= listContentStartY) {
                if (availableStations != null && !availableStations.isEmpty()) {
                    int clickedIndex = (int)((mouseY - listContentStartY) / LIST_LINE_HEIGHT);
                    if (clickedIndex >= 0 && clickedIndex < availableStations.size()) {
                        RadioStation clickedStation = availableStations.get(clickedIndex);
                        if (clickedStation != null) {
                            tuneToStation(clickedStation);
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    /** Backdrop fill behind panels. Companions may darken further or use 0 for chrome-only. */
    protected int getBackdropColor() {
        return 0xD8000810;
    }

    /** Drawn after backdrop, before radio panels (Pip bezel / tab strip). */
    protected void renderOuterChrome(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    /** When false, radio panels/widgets are hidden (stub tabs). */
    protected boolean shouldRenderRadioContent() {
        return true;
    }

    protected void renderNonRadioTab(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    protected void setRadioWidgetsVisible(boolean visible) {
        if (frequencySlider != null) {
            frequencySlider.visible = visible;
            frequencySlider.active = visible;
        }
        if (volumeSlider != null) {
            volumeSlider.visible = visible;
            volumeSlider.active = visible;
        }
        if (tuneButton != null) {
            tuneButton.visible = visible;
            tuneButton.active = visible;
        }
        if (powerButton != null) {
            powerButton.visible = visible;
            powerButton.active = visible;
        }
        if (pingLocationButton != null) {
            pingLocationButton.visible = visible;
            pingLocationButton.active = visible;
        }
        if (syncButton != null) {
            syncButton.visible = visible;
            syncButton.active = visible;
        }
        if (configCogButton != null) {
            configCogButton.visible = visible;
            configCogButton.active = visible;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    @Override
    public void onClose() {
        // Re-persist tuned station when closing GUI (preserve power on/off so closing doesn't turn radio back on)
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            WalkieTalkieManager.WalkieTalkieState state = WalkieTalkieManager.getState(mc.player);
            RadioStation toSave = state.getCurrentStation() != null ? state.getCurrentStation() : nearestStation;
            if (toSave != null) {
                WalkieTalkieManager.persistTunedStationPreservingPower(mc.player, toSave);
            }
        }
        super.onClose();
    }

    /**
     * Frequency slider that can be synced from code when the user clicks a station in the list
     * (so the slider thumb moves to match the needle).
     */
    private static final class FrequencySliderWidget extends AbstractSliderButton {
        private final WalkieTalkieTuningScreen screen;

        FrequencySliderWidget(int x, int y, int w, int h, double sliderValue, WalkieTalkieTuningScreen screen) {
            super(x, y, w, h, Component.empty(), sliderValue);
            this.screen = screen;
            this.visible = true;
            this.active = true;
        }

        @Override
        protected void updateMessage() {
            float freq = (float) Mth.lerp(this.value, MIN_FREQUENCY, MAX_FREQUENCY);
            freq = Math.round(freq * 10.0f) / 10.0f;
            screen.setCurrentFrequencyFromSlider(freq);
            screen.updateNearestStation();
        }

        @Override
        protected void applyValue() {}

        /** Set slider position from a frequency (e.g. when user clicks a station in the list). */
        void setValueFromFrequency(float freq) {
            this.value = Mth.clamp((double) (freq - MIN_FREQUENCY) / (MAX_FREQUENCY - MIN_FREQUENCY), 0.0, 1.0);
            updateMessage();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!this.active || !this.visible || button != 0) return false;
            int handleWidth = 8;
            double handleX = this.getX() + (this.value * (this.width - handleWidth));
            double handleY = this.getY();
            double tolerance = 4.0;
            if (mouseX >= handleX - tolerance && mouseX <= handleX + handleWidth + tolerance &&
                mouseY >= handleY - tolerance && mouseY <= handleY + this.height + tolerance) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            if (mouseX >= this.getX() && mouseX <= this.getX() + this.width &&
                mouseY >= this.getY() && mouseY <= this.getY() + this.height) {
                double relativeX = mouseX - this.getX();
                this.value = Mth.clamp(relativeX / this.width, 0.0, 1.0);
                updateMessage();
                return true;
            }
            return false;
        }
    }

    /** Called by the frequency slider when its value changes (so currentFrequency stays in sync). */
    void setCurrentFrequencyFromSlider(float freq) {
        this.currentFrequency = freq;
    }
}
