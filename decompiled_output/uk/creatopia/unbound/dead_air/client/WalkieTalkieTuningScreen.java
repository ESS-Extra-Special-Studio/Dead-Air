/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.gui.components.AbstractSliderButton
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Position
 *  net.minecraft.core.Vec3i
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.util.Mth
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.phys.Vec3
 */
package uk.creatopia.unbound.dead_air.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import uk.creatopia.unbound.dead_air.Config;
import uk.creatopia.unbound.dead_air.Dead_air;
import uk.creatopia.unbound.dead_air.audio.AudioManager;
import uk.creatopia.unbound.dead_air.radio.RadioStation;
import uk.creatopia.unbound.dead_air.radio.RadioTower;
import uk.creatopia.unbound.dead_air.radio.SignalStrength;
import uk.creatopia.unbound.dead_air.radio.StationRegistry;
import uk.creatopia.unbound.dead_air.radio.TowerManager;
import uk.creatopia.unbound.dead_air.station.StationUnlockManager;
import uk.creatopia.unbound.dead_air.tower.ApocalypseTowerDetector;
import uk.creatopia.unbound.dead_air.walkie.WalkieTalkieManager;

public class WalkieTalkieTuningScreen
extends Screen {
    private static final float MIN_FREQUENCY = 88.0f;
    private static final float MAX_FREQUENCY = 108.0f;
    private static final int PIPBOY_DARK_GREEN = 15636;
    private static final int PIPBOY_HIGHLIGHT = 0xFFFF00;
    private static final int PIPBOY_TEXT = 65345;
    private List<RadioStation> availableStations;
    private float currentFrequency = 88.0f;
    private RadioStation nearestStation = null;
    private AbstractSliderButton frequencySlider;
    private AbstractSliderButton volumeSlider;
    private Button tuneButton;
    private Button powerButton;
    private Button pingLocationButton;
    private float waveformTime = 0.0f;
    private final List<Float> waveformData = new ArrayList<Float>();

    public WalkieTalkieTuningScreen() {
        super((Component)Component.m_237113_((String)"Radio Tuning"));
        WalkieTalkieManager.WalkieTalkieState state;
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91074_ != null && (state = WalkieTalkieManager.getState((Player)mc.f_91074_)).getCurrentStation() != null) {
            this.currentFrequency = state.getCurrentStation().getFrequency();
        }
        for (int i = 0; i < 50; ++i) {
            this.waveformData.add(Float.valueOf(0.0f));
        }
    }

    protected void m_7856_() {
        super.m_7856_();
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91074_ == null) {
            return;
        }
        this.refreshAvailableStations();
        this.updateNearestStation();
        float sliderValue = (this.currentFrequency - 88.0f) / 20.0f;
        int sliderX = this.f_96543_ / 2 - 120;
        int sliderY = this.f_96544_ / 2 + 100;
        int sliderWidth = 240;
        int sliderHeight = 20;
        this.frequencySlider = new AbstractSliderButton(sliderX, sliderY, sliderWidth, sliderHeight, (Component)Component.m_237119_(), sliderValue){

            protected void m_5695_() {
                WalkieTalkieTuningScreen.this.currentFrequency = (float)Mth.m_14139_((double)this.f_93577_, (double)88.0, (double)108.0);
                WalkieTalkieTuningScreen.this.currentFrequency = (float)Math.round(WalkieTalkieTuningScreen.this.currentFrequency * 10.0f) / 10.0f;
                WalkieTalkieTuningScreen.this.updateNearestStation();
            }

            protected void m_5697_() {
            }

            public boolean m_6375_(double mouseX, double mouseY, int button) {
                if (!this.f_93623_ || !this.f_93624_ || button != 0) {
                    return false;
                }
                int handleWidth = 8;
                int handleHeight = this.f_93619_;
                double handleX = (double)this.m_252754_() + this.f_93577_ * (double)(this.f_93618_ - handleWidth);
                double handleY = this.m_252907_();
                double tolerance = 4.0;
                if (mouseX >= handleX - tolerance && mouseX <= handleX + (double)handleWidth + tolerance && mouseY >= handleY - tolerance && mouseY <= handleY + (double)handleHeight + tolerance) {
                    return super.m_6375_(mouseX, mouseY, button);
                }
                if (mouseX >= (double)this.m_252754_() && mouseX <= (double)(this.m_252754_() + this.f_93618_) && mouseY >= (double)this.m_252907_() && mouseY <= (double)(this.m_252907_() + this.f_93619_)) {
                    double newValue;
                    double relativeX = mouseX - (double)this.m_252754_();
                    this.f_93577_ = newValue = Mth.m_14008_((double)(relativeX / (double)this.f_93618_), (double)0.0, (double)1.0);
                    this.m_5695_();
                    this.m_7212_(mouseX, mouseY, 0.0, 0.0);
                    return true;
                }
                return false;
            }
        };
        this.m_142416_((GuiEventListener)this.frequencySlider);
        double currentVolume = Config.maxVolume;
        int volumeSliderX = this.f_96543_ - 130;
        int volumeSliderY = this.f_96544_ / 2 - 40;
        this.volumeSlider = new AbstractSliderButton(volumeSliderX, volumeSliderY, 120, 20, (Component)Component.m_237113_((String)String.format("Volume: %.0f%%", currentVolume * 100.0)), currentVolume){

            protected void m_5695_() {
                double newVolume;
                Config.maxVolume = newVolume = this.f_93577_;
                this.m_93666_((Component)Component.m_237113_((String)String.format("Volume: %.0f%%", newVolume * 100.0)));
                AudioManager.updateVolumeForAllSounds();
            }

            protected void m_5697_() {
            }

            public boolean m_6375_(double mouseX, double mouseY, int button) {
                if (!this.f_93623_ || !this.f_93624_ || button != 0) {
                    return false;
                }
                int handleWidth = 8;
                int handleHeight = this.f_93619_;
                double handleX = (double)this.m_252754_() + this.f_93577_ * (double)(this.f_93618_ - handleWidth);
                double handleY = this.m_252907_();
                double tolerance = 4.0;
                if (mouseX >= handleX - tolerance && mouseX <= handleX + (double)handleWidth + tolerance && mouseY >= handleY - tolerance && mouseY <= handleY + (double)handleHeight + tolerance) {
                    return super.m_6375_(mouseX, mouseY, button);
                }
                if (mouseX >= (double)this.m_252754_() && mouseX <= (double)(this.m_252754_() + this.f_93618_) && mouseY >= (double)this.m_252907_() && mouseY <= (double)(this.m_252907_() + this.f_93619_)) {
                    double newValue;
                    double relativeX = mouseX - (double)this.m_252754_();
                    this.f_93577_ = newValue = Mth.m_14008_((double)(relativeX / (double)this.f_93618_), (double)0.0, (double)1.0);
                    this.m_5695_();
                    this.m_7212_(mouseX, mouseY, 0.0, 0.0);
                    return true;
                }
                return false;
            }
        };
        this.m_142416_((GuiEventListener)this.volumeSlider);
        this.tuneButton = Button.m_253074_((Component)Component.m_237113_((String)(this.nearestStation != null ? "TUNE: " + this.nearestStation.getName() : "TUNE: NO SIGNAL")), button -> this.tuneToCurrentFrequency()).m_252987_(this.f_96543_ / 2 - 100, sliderY + 30, 200, 20).m_253136_();
        this.m_142416_((GuiEventListener)this.tuneButton);
        int rightPanelX = this.f_96543_ - 130;
        int rightPanelY = this.f_96544_ / 2 + 60;
        this.pingLocationButton = Button.m_253074_((Component)Component.m_237113_((String)"PING LOCATION"), button -> {
            if (mc.f_91074_ != null) {
                int x = (int)mc.f_91074_.m_20185_();
                int y = (int)mc.f_91074_.m_20186_();
                int z = (int)mc.f_91074_.m_20189_();
                String dimension = mc.f_91073_ != null ? mc.f_91073_.m_46472_().m_135782_().toString() : "unknown";
                MutableComponent locationMessage = Component.m_237113_((String)String.format("Location: %d, %d, %d (%s)", x, y, z, dimension));
                mc.f_91074_.m_213846_((Component)locationMessage);
            }
        }).m_252987_(rightPanelX, rightPanelY, 120, 20).m_253136_();
        this.m_142416_((GuiEventListener)this.pingLocationButton);
        WalkieTalkieManager.WalkieTalkieState state = WalkieTalkieManager.getState((Player)mc.f_91074_);
        this.powerButton = Button.m_253074_((Component)Component.m_237113_((String)(state.isOn() ? "[ON]" : "[OFF]")), button -> {
            WalkieTalkieManager.WalkieTalkieState currentState = WalkieTalkieManager.getState((Player)mc.f_91074_);
            if (currentState.isOn()) {
                WalkieTalkieManager.turnOff((Player)mc.f_91074_);
            } else {
                WalkieTalkieManager.turnOn((Player)mc.f_91074_);
            }
            currentState = WalkieTalkieManager.getState((Player)mc.f_91074_);
            button.m_93666_((Component)Component.m_237113_((String)(currentState.isOn() ? "[ON]" : "[OFF]")));
        }).m_252987_(rightPanelX, rightPanelY + 25, 120, 20).m_253136_();
        this.m_142416_((GuiEventListener)this.powerButton);
    }

    private void refreshAvailableStations() {
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91074_ == null) {
            this.availableStations = Collections.emptyList();
            return;
        }
        try {
            this.availableStations = StationUnlockManager.getAvailableStations((Player)mc.f_91074_);
            if (this.availableStations == null) {
                this.availableStations = Collections.emptyList();
            }
        }
        catch (Exception e) {
            this.availableStations = Collections.emptyList();
        }
    }

    private void updateNearestStation() {
        this.refreshAvailableStations();
        if (this.availableStations == null || this.availableStations.isEmpty()) {
            this.nearestStation = null;
        } else {
            this.nearestStation = null;
            float nearestDistance = Float.MAX_VALUE;
            for (RadioStation station : this.availableStations) {
                float distance = Math.abs(station.getFrequency() - this.currentFrequency);
                if (!(distance < nearestDistance)) continue;
                nearestDistance = distance;
                this.nearestStation = station;
            }
            if (this.nearestStation != null && nearestDistance > 0.2f) {
                this.nearestStation = null;
            }
        }
        if (this.tuneButton != null) {
            this.tuneButton.m_93666_((Component)Component.m_237113_((String)(this.nearestStation != null ? "TUNE: " + this.nearestStation.getName() : "TUNE: NO SIGNAL")));
        }
    }

    public void m_88315_(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        WalkieTalkieManager.WalkieTalkieState state;
        guiGraphics.m_280509_(0, 0, this.f_96543_, this.f_96544_, -16761580);
        super.m_88315_(guiGraphics, mouseX, mouseY, partialTick);
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91074_ == null) {
            return;
        }
        this.waveformTime += partialTick * 0.1f;
        this.updateWaveform();
        if (mc.f_91074_ != null && (state = WalkieTalkieManager.getState((Player)mc.f_91074_)).isOn() && state.getCurrentStation() != null) {
            AudioManager.update(mc, state.getCurrentStation(), mc.f_91074_.m_20182_());
        }
        int padding = Math.max(10, this.f_96543_ / 40);
        int topMargin = Math.max(20, this.f_96544_ / 20);
        int borderThickness = 2;
        int borderColor = -7799040;
        int sectionSpacing = 10;
        int leftSectionX = padding;
        int leftSectionY = topMargin;
        int leftSectionWidth = Math.min(180, this.f_96543_ / 4);
        int sectionHeight = this.f_96544_ - topMargin - padding;
        this.drawStationList(guiGraphics, leftSectionX, leftSectionY);
        int rightSectionWidth = leftSectionWidth;
        int rightSectionX = this.f_96543_ - rightSectionWidth - padding;
        int signalSectionY = topMargin;
        int signalSectionHeight = 100;
        this.drawWaveform(guiGraphics, rightSectionX, signalSectionY);
        int volumeSectionY = signalSectionY + signalSectionHeight + sectionSpacing;
        int volumeSectionHeight = 60;
        this.drawVolumeSection(guiGraphics, rightSectionX, volumeSectionY, rightSectionWidth, volumeSectionHeight, borderColor, borderThickness);
        if (this.volumeSlider != null) {
            this.volumeSlider.m_252865_(rightSectionX + 5);
            this.volumeSlider.m_253211_(volumeSectionY + 5);
            this.volumeSlider.m_93674_(rightSectionWidth - 10);
        }
        int buttonsSectionY = volumeSectionY + volumeSectionHeight + sectionSpacing;
        int buttonsSectionHeight = 60;
        this.drawButtonsSection(guiGraphics, rightSectionX, buttonsSectionY, rightSectionWidth, buttonsSectionHeight, borderColor, borderThickness);
        if (this.pingLocationButton != null) {
            this.pingLocationButton.m_252865_(rightSectionX + 5);
            this.pingLocationButton.m_253211_(buttonsSectionY + 5);
            this.pingLocationButton.m_93674_(rightSectionWidth - 10);
            this.pingLocationButton.m_93666_((Component)Component.m_237113_((String)"PING LOCATION"));
        }
        if (this.powerButton != null) {
            this.powerButton.m_252865_(rightSectionX + 5);
            this.powerButton.m_253211_(buttonsSectionY + 30);
            this.powerButton.m_93674_(rightSectionWidth - 10);
        }
        int centerSectionWidth = this.f_96543_ - leftSectionWidth - rightSectionWidth - padding * 2 - sectionSpacing * 2;
        int centerSectionX = leftSectionX + leftSectionWidth + sectionSpacing;
        int centerSectionY = topMargin;
        int centerSectionHeight = sectionHeight;
        guiGraphics.m_280509_(centerSectionX - borderThickness, centerSectionY - borderThickness, centerSectionX + centerSectionWidth + borderThickness, centerSectionY + centerSectionHeight + borderThickness, Integer.MIN_VALUE);
        guiGraphics.m_280509_(centerSectionX - borderThickness, centerSectionY - borderThickness, centerSectionX + centerSectionWidth + borderThickness, centerSectionY - borderThickness + borderThickness, borderColor);
        guiGraphics.m_280509_(centerSectionX - borderThickness, centerSectionY + centerSectionHeight, centerSectionX + centerSectionWidth + borderThickness, centerSectionY + centerSectionHeight + borderThickness, borderColor);
        guiGraphics.m_280509_(centerSectionX - borderThickness, centerSectionY - borderThickness, centerSectionX - borderThickness + borderThickness, centerSectionY + centerSectionHeight + borderThickness, borderColor);
        guiGraphics.m_280509_(centerSectionX + centerSectionWidth, centerSectionY - borderThickness, centerSectionX + centerSectionWidth + borderThickness, centerSectionY + centerSectionHeight + borderThickness, borderColor);
        int centerPadding = 15;
        int centerContentWidth = centerSectionWidth - centerPadding * 2;
        int centerContentCenterX = centerSectionX + centerSectionWidth / 2;
        int dialY = centerSectionY + centerPadding + 70;
        this.drawRadioDial(guiGraphics, centerContentCenterX, dialY, mouseX, mouseY);
        int freqY = dialY + 18;
        String currentFreqText = String.format("%.1f MHz", Float.valueOf(this.currentFrequency));
        int freqColor = this.nearestStation != null ? 0xFFFF00 : 65345;
        guiGraphics.m_280056_(this.f_96547_, currentFreqText, centerContentCenterX - this.f_96547_.m_92895_(currentFreqText) / 2, freqY, freqColor, false);
        int infoY = dialY + 80;
        int infoBoxWidth = Math.min(centerContentWidth - 20, 300);
        int infoBoxLeft = centerContentCenterX - infoBoxWidth / 2;
        int infoBoxRight = centerContentCenterX + infoBoxWidth / 2;
        int infoBoxHeight = 70;
        int sliderY = infoY + infoBoxHeight + 15;
        if (this.frequencySlider != null) {
            this.frequencySlider.m_252865_(centerContentCenterX - 120);
            this.frequencySlider.m_253211_(sliderY);
            this.frequencySlider.m_93674_(240);
            String freqText = String.format("%.1f MHz", Float.valueOf(this.currentFrequency));
            int freqTextX = centerContentCenterX - this.f_96547_.m_92895_(freqText) / 2;
            int freqTextY = sliderY - 12;
            guiGraphics.m_280056_(this.f_96547_, freqText, freqTextX, freqTextY, 65345, false);
        }
        if (this.tuneButton != null) {
            this.tuneButton.m_252865_(centerContentCenterX - 100);
            this.tuneButton.m_253211_(sliderY + 30);
            this.tuneButton.m_93674_(200);
        }
        if (this.nearestStation != null) {
            try {
                float stationFreq = this.nearestStation.getFrequency();
                float distance = Math.abs(stationFreq - this.currentFrequency);
                Object stationName = this.nearestStation.getName();
                if (stationName == null) {
                    stationName = "Unknown Station";
                }
                int maxNameWidth = infoBoxWidth - 20;
                if (this.f_96547_.m_92895_((String)stationName) > maxNameWidth) {
                    stationName = this.f_96547_.m_92834_((String)stationName, maxNameWidth - 3) + "...";
                }
                int nameX = Math.max(infoBoxLeft + 10, centerContentCenterX - this.f_96547_.m_92895_((String)stationName) / 2);
                nameX = Math.min(nameX, infoBoxRight - this.f_96547_.m_92895_((String)stationName) - 10);
                guiGraphics.m_280056_(this.f_96547_, (String)stationName, nameX, infoY, 0xFFFF00, false);
                String stationFreqText = String.format("%.1f MHz", Float.valueOf(stationFreq));
                int freqTextWidth = this.f_96547_.m_92895_(stationFreqText);
                int freqDisplayColor = distance < 0.1f ? 0xFFFF00 : 65345;
                int freqX = Math.max(infoBoxLeft + 10, centerContentCenterX - freqTextWidth / 2);
                freqX = Math.min(freqX, infoBoxRight - freqTextWidth - 10);
                guiGraphics.m_280056_(this.f_96547_, stationFreqText, freqX, infoY + 12, freqDisplayColor, false);
                Object genre = this.nearestStation.getGenre();
                if (genre == null) {
                    genre = "Unknown";
                }
                if (this.f_96547_.m_92895_((String)genre) > maxNameWidth) {
                    genre = this.f_96547_.m_92834_((String)genre, maxNameWidth - 3) + "...";
                }
                int genreX = Math.max(infoBoxLeft + 10, centerContentCenterX - this.f_96547_.m_92895_((String)genre) / 2);
                genreX = Math.min(genreX, infoBoxRight - this.f_96547_.m_92895_((String)genre) - 10);
                guiGraphics.m_280056_(this.f_96547_, (String)genre, genreX, infoY + 24, 65345, false);
                float signal = this.calculateSignalStrengthForStation(mc, this.nearestStation);
                int bars = SignalStrength.getSignalBars(signal);
                String signalText = "SIGNAL: " + bars + "/5";
                int signalColor = this.getSignalColor(signal);
                int signalTextWidth = this.f_96547_.m_92895_(signalText);
                int signalX = Math.max(infoBoxLeft + 10, centerContentCenterX - signalTextWidth / 2);
                signalX = Math.min(signalX, infoBoxRight - signalTextWidth - 10);
                guiGraphics.m_280056_(this.f_96547_, signalText, signalX, infoY + 40, signalColor, false);
                int nextY = infoY + 48;
                if (signal > 0.0f && signal < 0.3f) {
                    int staticX = Math.max(infoBoxLeft + 10, centerContentCenterX - this.f_96547_.m_92895_("STATIC") / 2);
                    staticX = Math.min(staticX, infoBoxRight - this.f_96547_.m_92895_("STATIC") - 10);
                    guiGraphics.m_280056_(this.f_96547_, "STATIC", staticX, nextY, 65345, false);
                }
            }
            catch (Exception e) {
                Dead_air.LOGGER.debug("Error drawing station info: {}", (Object)e.getMessage());
                guiGraphics.m_280056_(this.f_96547_, "Error", centerContentCenterX - this.f_96547_.m_92895_("Error") / 2, infoY, 0xFF0000, false);
            }
        } else {
            int noSignalX = Math.max(infoBoxLeft + 10, centerContentCenterX - this.f_96547_.m_92895_("NO SIGNAL") / 2);
            noSignalX = Math.min(noSignalX, infoBoxRight - this.f_96547_.m_92895_("NO SIGNAL") - 10);
            guiGraphics.m_280056_(this.f_96547_, "NO SIGNAL", noSignalX, infoY, 0x666666, false);
            int staticX = Math.max(infoBoxLeft + 10, centerContentCenterX - this.f_96547_.m_92895_("STATIC") / 2);
            staticX = Math.min(staticX, infoBoxRight - this.f_96547_.m_92895_("STATIC") - 10);
            guiGraphics.m_280056_(this.f_96547_, "STATIC", staticX, infoY + 12, 0x888888, false);
        }
    }

    private void drawRadioDial(GuiGraphics guiGraphics, int centerX, int centerY, int mouseX, int mouseY) {
        int y2;
        int x2;
        int y1;
        int x1;
        int y22;
        int x22;
        int y12;
        int dialRadius = 60;
        int dialCenterX = centerX;
        int dialCenterY = centerY;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float startAngle = 180.0f;
        float endAngle = 360.0f;
        float totalAngle = 180.0f;
        int borderColor = -7799040;
        int borderRadius = dialRadius + 5;
        int borderThickness = 3;
        int dialTickColor = -16711871;
        for (int i = 0; i < 360; ++i) {
            float angle1 = i;
            float angle2 = (i + 1) % 360;
            float rad1 = (float)Math.toRadians(angle1);
            float rad2 = (float)Math.toRadians(angle2);
            int x12 = dialCenterX + (int)(Math.cos(rad1) * (double)borderRadius);
            y12 = dialCenterY + (int)(Math.sin(rad1) * (double)borderRadius);
            x22 = dialCenterX + (int)(Math.cos(rad2) * (double)borderRadius);
            y22 = dialCenterY + (int)(Math.sin(rad2) * (double)borderRadius);
            this.drawLine(guiGraphics, x12, y12, x22, y22, borderColor, borderThickness);
        }
        this.drawArc(guiGraphics, dialCenterX, dialCenterY, dialRadius, startAngle, endAngle, -7799040, 3);
        int[] majorFrequencies = new int[]{88, 92, 96, 100, 104, 108};
        for (int i = 0; i < majorFrequencies.length; ++i) {
            int freq = majorFrequencies[i];
            float normalizedFreq = ((float)freq - 88.0f) / 20.0f;
            float angle = startAngle + normalizedFreq * totalAngle;
            float rad = (float)Math.toRadians(angle);
            x1 = dialCenterX + (int)(Math.cos(rad) * (double)(dialRadius - 10));
            y1 = dialCenterY + (int)(Math.sin(rad) * (double)(dialRadius - 10));
            x2 = dialCenterX + (int)(Math.cos(rad) * (double)(dialRadius - 2));
            y2 = dialCenterY + (int)(Math.sin(rad) * (double)(dialRadius - 2));
            this.drawLine(guiGraphics, x1, y1, x2, y2, dialTickColor, 2);
        }
        for (int freq = 88; freq <= 108; freq += 2) {
            boolean isMajor = false;
            for (int majorFreq : majorFrequencies) {
                if (freq != majorFreq) continue;
                isMajor = true;
                break;
            }
            if (isMajor) continue;
            float normalizedFreq = ((float)freq - 88.0f) / 20.0f;
            float angle = startAngle + normalizedFreq * totalAngle;
            float rad = (float)Math.toRadians(angle);
            x1 = dialCenterX + (int)(Math.cos(rad) * (double)(dialRadius - 7));
            y1 = dialCenterY + (int)(Math.sin(rad) * (double)(dialRadius - 7));
            x2 = dialCenterX + (int)(Math.cos(rad) * (double)(dialRadius - 3));
            y2 = dialCenterY + (int)(Math.sin(rad) * (double)(dialRadius - 3));
            this.drawLine(guiGraphics, x1, y1, x2, y2, dialTickColor, 1);
        }
        float normalizedFreq = (this.currentFrequency - 88.0f) / 20.0f;
        float angle = startAngle + normalizedFreq * totalAngle;
        float rad = (float)Math.toRadians(angle);
        int indicatorLength = dialRadius + 4;
        int x13 = dialCenterX;
        y12 = dialCenterY;
        x22 = dialCenterX + (int)(Math.cos(rad) * (double)indicatorLength);
        y22 = dialCenterY + (int)(Math.sin(rad) * (double)indicatorLength);
        int screenPadding = 20;
        x22 = Math.max(screenPadding, Math.min(this.f_96543_ - screenPadding, x22));
        y22 = Math.max(screenPadding, Math.min(this.f_96544_ - screenPadding, y22));
        this.drawLine(guiGraphics, x13, y12, x22, y22, -65536, 2);
        guiGraphics.m_280509_(dialCenterX - 3, dialCenterY - 3, dialCenterX + 3, dialCenterY + 3, dialTickColor);
        RenderSystem.disableBlend();
    }

    private void drawArc(GuiGraphics guiGraphics, int centerX, int centerY, int radius, float startAngle, float endAngle, int color, int thickness) {
        int segments = 60;
        float angleStep = (endAngle - startAngle) / (float)segments;
        for (int i = 0; i < segments; ++i) {
            float angle1 = startAngle + angleStep * (float)i;
            float angle2 = startAngle + angleStep * (float)(i + 1);
            float rad1 = (float)Math.toRadians(angle1);
            float rad2 = (float)Math.toRadians(angle2);
            int x1 = centerX + (int)(Math.cos(rad1) * (double)radius);
            int y1 = centerY + (int)(Math.sin(rad1) * (double)radius);
            int x2 = centerX + (int)(Math.cos(rad2) * (double)radius);
            int y2 = centerY + (int)(Math.sin(rad2) * (double)radius);
            this.drawLine(guiGraphics, x1, y1, x2, y2, color, thickness);
        }
    }

    private void drawLine(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color, int thickness) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float)Math.sqrt(dx * dx + dy * dy);
        if (length == 0.0f) {
            return;
        }
        float stepX = dx / length;
        float stepY = dy / length;
        for (float i = 0.0f; i <= length; i += 0.5f) {
            int x = (int)((float)x1 + stepX * i);
            int y = (int)((float)y1 + stepY * i);
            guiGraphics.m_280509_(x - thickness / 2, y - thickness / 2, x + thickness / 2, y + thickness / 2, color);
        }
    }

    private void drawStationList(GuiGraphics guiGraphics, int x, int y) {
        int listWidth = Math.min(180, this.f_96543_ / 4);
        int listHeight = this.f_96544_ - y - Math.max(10, this.f_96543_ / 40);
        int borderColor = -7799040;
        int borderThickness = 2;
        guiGraphics.m_280509_(x - borderThickness, y - borderThickness, x + listWidth + borderThickness, y + listHeight + borderThickness, Integer.MIN_VALUE);
        guiGraphics.m_280509_(x - borderThickness, y - borderThickness, x + listWidth + borderThickness, y - borderThickness + borderThickness, borderColor);
        guiGraphics.m_280509_(x - borderThickness, y + listHeight, x + listWidth + borderThickness, y + listHeight + borderThickness, borderColor);
        guiGraphics.m_280509_(x - borderThickness, y - borderThickness, x - borderThickness + borderThickness, y + listHeight + borderThickness, borderColor);
        guiGraphics.m_280509_(x + listWidth, y - borderThickness, x + listWidth + borderThickness, y + listHeight + borderThickness, borderColor);
        guiGraphics.m_280056_(this.f_96547_, "RADIO STATIONS", x + 5, y + 5, 65345, false);
        y += 18;
        if (this.availableStations == null || this.availableStations.isEmpty()) {
            guiGraphics.m_280056_(this.f_96547_, "No stations", x, y, 0x666666, false);
            guiGraphics.m_280056_(this.f_96547_, "discovered", x, y + 10, 0x666666, false);
            guiGraphics.m_280056_(this.f_96547_, "Find a tower!", x, y + 20, 0x666666, false);
            return;
        }
        int maxListHeight = this.f_96544_ - y - 20;
        int lineHeight = 12;
        int maxStations = Math.min(this.availableStations.size(), maxListHeight / lineHeight);
        maxStations = Math.min(maxStations, 10);
        if (x + listWidth > this.f_96543_ - 20) {
            listWidth = this.f_96543_ - x - 20;
        }
        int maxNameWidth = listWidth - 75;
        maxNameWidth = Math.max(maxNameWidth, 50);
        for (int i = 0; i < maxStations && y + lineHeight <= this.f_96544_ - 100; ++i) {
            int availableForName;
            String freq;
            String freqText;
            int freqTextWidth;
            int freqX;
            RadioStation station = this.availableStations.get(i);
            if (station == null) continue;
            boolean isSelected = this.nearestStation != null && this.nearestStation.getId().equals((Object)station.getId());
            Object stationName = station.getName();
            if (stationName == null) {
                stationName = "Unknown";
            }
            if (this.f_96547_.m_92895_((String)stationName) > maxNameWidth) {
                stationName = this.f_96547_.m_92834_((String)stationName, maxNameWidth - 3) + "...";
            }
            if ((freqX = x + listWidth - (freqTextWidth = this.f_96547_.m_92895_(freqText = (freq = String.format("%.1f", Float.valueOf(station.getFrequency()))) + " MHz")) - 2) < x + maxNameWidth + 10 && (availableForName = freqX - x - 15) > 20) {
                stationName = this.f_96547_.m_92834_(station.getName(), availableForName - 3) + "...";
            }
            if (isSelected) {
                int highlightRight = Math.min(x + listWidth, this.f_96543_ - 10);
                guiGraphics.m_280509_(x - 2, y - 1, highlightRight, y + 11, 0x44FFFFFF);
                guiGraphics.m_280056_(this.f_96547_, "> " + (String)stationName, x, y, 0xFFFF00, false);
            } else {
                guiGraphics.m_280056_(this.f_96547_, "  " + (String)stationName, x, y, 65345, false);
            }
            if (freqX >= x + maxNameWidth + 5 && freqX + freqTextWidth <= this.f_96543_ - 10) {
                guiGraphics.m_280056_(this.f_96547_, freqText, freqX, y, 0x888888, false);
            }
            y += lineHeight;
        }
    }

    private void drawVolumeSection(GuiGraphics guiGraphics, int x, int y, int width, int height, int borderColor, int borderThickness) {
        guiGraphics.m_280509_(x - borderThickness, y - borderThickness, x + width + borderThickness, y + height + borderThickness, Integer.MIN_VALUE);
        guiGraphics.m_280509_(x - borderThickness, y - borderThickness, x + width + borderThickness, y - borderThickness + borderThickness, borderColor);
        guiGraphics.m_280509_(x - borderThickness, y + height, x + width + borderThickness, y + height + borderThickness, borderColor);
        guiGraphics.m_280509_(x - borderThickness, y - borderThickness, x - borderThickness + borderThickness, y + height + borderThickness, borderColor);
        guiGraphics.m_280509_(x + width, y - borderThickness, x + width + borderThickness, y + height + borderThickness, borderColor);
    }

    private void drawButtonsSection(GuiGraphics guiGraphics, int x, int y, int width, int height, int borderColor, int borderThickness) {
        guiGraphics.m_280509_(x - borderThickness, y - borderThickness, x + width + borderThickness, y + height + borderThickness, Integer.MIN_VALUE);
        guiGraphics.m_280509_(x - borderThickness, y - borderThickness, x + width + borderThickness, y - borderThickness + borderThickness, borderColor);
        guiGraphics.m_280509_(x - borderThickness, y + height, x + width + borderThickness, y + height + borderThickness, borderColor);
        guiGraphics.m_280509_(x - borderThickness, y - borderThickness, x - borderThickness + borderThickness, y + height + borderThickness, borderColor);
        guiGraphics.m_280509_(x + width, y - borderThickness, x + width + borderThickness, y + height + borderThickness, borderColor);
    }

    private void drawWaveform(GuiGraphics guiGraphics, int x, int y) {
        int i;
        int startY = y;
        int waveformWidth = Math.min(110, this.f_96543_ / 8);
        int waveformHeight = Math.min(50, this.f_96544_ / 15);
        int sectionHeight = waveformHeight + 40;
        if (x + waveformWidth > this.f_96543_ - 10) {
            x = this.f_96543_ - waveformWidth - 10;
        }
        int borderColor = -7799040;
        int borderThickness = 2;
        guiGraphics.m_280509_(x - borderThickness, startY - borderThickness, x + waveformWidth + borderThickness, startY + sectionHeight + borderThickness, Integer.MIN_VALUE);
        guiGraphics.m_280509_(x - borderThickness, startY - borderThickness, x + waveformWidth + borderThickness, startY - borderThickness + borderThickness, borderColor);
        guiGraphics.m_280509_(x - borderThickness, startY + sectionHeight, x + waveformWidth + borderThickness, startY + sectionHeight + borderThickness, borderColor);
        guiGraphics.m_280509_(x - borderThickness, startY - borderThickness, x - borderThickness + borderThickness, startY + sectionHeight + borderThickness, borderColor);
        guiGraphics.m_280509_(x + waveformWidth, startY - borderThickness, x + waveformWidth + borderThickness, startY + sectionHeight + borderThickness, borderColor);
        guiGraphics.m_280056_(this.f_96547_, "SIGNAL", x + 5, y + 5, 65345, false);
        guiGraphics.m_280509_(x + 5, y += 18, x + waveformWidth - 5, y + waveformHeight, 0x44000000);
        int barsY = y + waveformHeight + 8;
        int barWidth = 5;
        int barHeight = 8;
        int barSpacing = 2;
        int barsStartX = x + 5;
        float signal = 0.0f;
        if (this.nearestStation != null) {
            Minecraft mc = Minecraft.m_91087_();
            if (mc.f_91073_ != null && mc.f_91074_ != null) {
                try {
                    signal = this.calculateSignalStrengthForStation(mc, this.nearestStation);
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
        }
        int signalBars = SignalStrength.getSignalBars(signal);
        int[] gradientColors = new int[]{-65536, -30720, -256, -7799040, -16711936};
        for (i = 0; i < 5; ++i) {
            int dy;
            int dx;
            int py;
            int px;
            int barX = barsStartX + i * (barWidth + barSpacing);
            boolean isActive = i < signalBars;
            int barColor = isActive ? gradientColors[i] : -12303292;
            int radius = barWidth / 2;
            int topY = barsY;
            int bodyTopY = topY + radius;
            int bodyBottomY = barsY + barHeight;
            for (px = 0; px < barWidth; ++px) {
                for (py = 0; py <= radius; ++py) {
                    dx = px - radius;
                    dy = py;
                    if (dx * dx + dy * dy > radius * radius) continue;
                    guiGraphics.m_280509_(barX + px, topY + py, barX + px + 1, topY + py + 1, barColor);
                }
            }
            guiGraphics.m_280509_(barX, bodyTopY, barX + barWidth, bodyBottomY, barColor);
            if (!isActive) continue;
            for (px = 0; px < barWidth; ++px) {
                for (py = 0; py <= radius; ++py) {
                    dx = px - radius;
                    dy = py;
                    if (dx * dx + dy * dy > radius * radius || dx * dx + dy * dy < (radius - 1) * (radius - 1)) continue;
                    guiGraphics.m_280509_(barX + px, topY + py, barX + px + 1, topY + py + 1, 0xFFFFFF);
                }
            }
            guiGraphics.m_280509_(barX, bodyBottomY - 1, barX + barWidth, bodyBottomY, 0xFFFFFF);
            guiGraphics.m_280509_(barX, bodyTopY, barX + 1, bodyBottomY, 0xFFFFFF);
            guiGraphics.m_280509_(barX + barWidth - 1, bodyTopY, barX + barWidth, bodyBottomY, 0xFFFFFF);
        }
        if (this.nearestStation != null && this.waveformData.size() > 0) {
            Minecraft mc = Minecraft.m_91087_();
            if (mc.f_91073_ != null && mc.f_91074_ != null) {
                try {
                    int waveformColor = signal > 0.1f ? -7799040 : -10066330;
                    for (int i2 = 0; i2 < this.waveformData.size() - 1; ++i2) {
                        float y1 = (float)(y + waveformHeight / 2) - this.waveformData.get(i2).floatValue() * (float)waveformHeight / 2.0f * signal;
                        float y2 = (float)(y + waveformHeight / 2) - this.waveformData.get(i2 + 1).floatValue() * (float)waveformHeight / 2.0f * signal;
                        int x1 = x + 5 + i2 * (waveformWidth - 10) / this.waveformData.size();
                        int x2 = x + 5 + (i2 + 1) * (waveformWidth - 10) / this.waveformData.size();
                        this.drawLine(guiGraphics, x1, (int)y1, x2, (int)y2, waveformColor, 2);
                    }
                }
                catch (Exception e) {
                    Dead_air.LOGGER.debug("Error drawing waveform: {}", (Object)e.getMessage());
                }
            }
        } else {
            for (i = 0; i < waveformWidth - 10; i += 2) {
                float noise = (float)(Math.random() * (double)waveformHeight);
                guiGraphics.m_280509_(x + 5 + i, y + (int)noise, x + 5 + i + 1, y + (int)noise + 1, 0x888888);
            }
        }
    }

    private void updateWaveform() {
        for (int i = 0; i < this.waveformData.size(); ++i) {
            float base = (float)(Math.sin(this.waveformTime + (float)i * 0.2f) * 0.5 + 0.5);
            float variation = (float)(Math.sin(this.waveformTime * 2.0f + (float)i * 0.3f) * 0.2);
            this.waveformData.set(i, Float.valueOf(base + variation));
        }
    }

    private float calculateSignalStrengthForStation(Minecraft mc, RadioStation station) {
        if (mc.f_91073_ == null || mc.f_91074_ == null || station == null) {
            return 0.0f;
        }
        try {
            Vec3 playerPos = mc.f_91074_.m_20182_();
            float bestSignal = 0.0f;
            try {
                List<RadioTower> serverTowers = TowerManager.getAllTowers((Level)mc.f_91073_);
                if (serverTowers != null && !serverTowers.isEmpty()) {
                    boolean checkAllTowers = station.getType() == RadioStation.StationType.EMERGENCY_BROADCAST;
                    for (RadioTower tower : serverTowers) {
                        float signal;
                        if (!tower.isPowered() || !checkAllTowers && !tower.getStation().getId().equals((Object)station.getId()) || !tower.isInRange(playerPos) || !((signal = SignalStrength.getFinalSignalStrength((Level)mc.f_91073_, tower, playerPos)) > bestSignal)) continue;
                        bestSignal = signal;
                    }
                    if (bestSignal > 0.0f) {
                        return bestSignal;
                    }
                }
            }
            catch (Exception serverTowers) {
                // empty catch block
            }
            int searchRadius = Math.min(500, station.getBroadcastRange());
            BlockPos playerBlockPos = BlockPos.m_274446_((Position)playerPos);
            boolean checkAllPanels = station.getType() == RadioStation.StationType.EMERGENCY_BROADCAST;
            for (int x = -searchRadius; x <= searchRadius; x += 8) {
                for (int z = -searchRadius; z <= searchRadius; z += 8) {
                    for (int y = -10; y <= 10; y += 5) {
                        Vec3 panelPos;
                        double distance;
                        CompoundTag nbt;
                        BlockPos checkPos = playerBlockPos.m_7918_(x, y, z);
                        if (!ApocalypseTowerDetector.isRadioPanel((Level)mc.f_91073_, checkPos)) continue;
                        boolean isActivated = false;
                        if (mc.f_91073_.m_7702_(checkPos) != null && (nbt = mc.f_91073_.m_7702_(checkPos).m_187480_()) != null) {
                            boolean bl = isActivated = nbt.m_128441_("activated") && nbt.m_128471_("activated") || nbt.m_128441_("active") && nbt.m_128471_("active") || nbt.m_128441_("powered") && nbt.m_128471_("powered") || nbt.m_128441_("Activated") && nbt.m_128471_("Activated") || nbt.m_128441_("Active") && nbt.m_128471_("Active") || nbt.m_128441_("Powered") && nbt.m_128471_("Powered");
                            if (!isActivated) {
                                boolean bl2 = isActivated = nbt.m_128441_("activated") && nbt.m_128451_("activated") > 0 || nbt.m_128441_("active") && nbt.m_128451_("active") > 0 || nbt.m_128441_("powered") && nbt.m_128451_("powered") > 0;
                            }
                        }
                        if (isActivated || !checkAllPanels) {
                            // empty if block
                        }
                        if ((distance = playerPos.m_82554_(panelPos = Vec3.m_82512_((Vec3i)checkPos))) > (double)station.getBroadcastRange()) continue;
                        double normalizedDistance = distance / (double)station.getBroadcastRange();
                        float signal = (float)(1.0 - normalizedDistance * 0.9);
                        signal = Math.max(0.0f, Math.min(1.0f, signal));
                        if (Config.enableWeatherEffects && (mc.f_91073_.m_46471_() || mc.f_91073_.m_46470_())) {
                            signal *= 0.7f;
                        }
                        if (!(signal > bestSignal)) continue;
                        bestSignal = signal;
                    }
                }
            }
            return bestSignal;
        }
        catch (Exception e) {
            Dead_air.LOGGER.debug("Could not calculate signal strength on client: {}", (Object)e.getMessage());
            return 0.0f;
        }
    }

    private int getSignalColor(float signal) {
        if (signal <= 0.0f) {
            return 0x666666;
        }
        if (signal < 0.2f) {
            return 0xFF0000;
        }
        if (signal < 0.4f) {
            return 0xFF8800;
        }
        if (signal < 0.6f) {
            return 0xFFFF00;
        }
        if (signal < 0.8f) {
            return -7799040;
        }
        return 0xFFFF00;
    }

    private void tuneToCurrentFrequency() {
        Minecraft mc = Minecraft.m_91087_();
        if (mc.f_91074_ == null || mc.f_91073_ == null) {
            Dead_air.LOGGER.warn("Cannot tune: player or level is null");
            return;
        }
        try {
            if (this.nearestStation != null && this.nearestStation.getId() != null) {
                if (this.nearestStation.getName() == null) {
                    Dead_air.LOGGER.warn("Station has null name, cannot tune");
                    return;
                }
                if (this.nearestStation.getId() == null) {
                    Dead_air.LOGGER.warn("Station has null ID, cannot tune");
                    return;
                }
                RadioStation validatedStation = StationRegistry.getStation(this.nearestStation.getId());
                if (validatedStation == null) {
                    Dead_air.LOGGER.warn("Station {} not found in registry, cannot tune", (Object)this.nearestStation.getId());
                    return;
                }
                WalkieTalkieManager.tuneToStation((Player)mc.f_91074_, validatedStation);
                mc.execute(() -> {
                    if (mc.f_91074_ != null && validatedStation != null) {
                        try {
                            String stationName = validatedStation.getName();
                            if (stationName == null) {
                                stationName = "Unknown";
                            }
                            mc.f_91074_.m_213846_((Component)Component.m_237113_((String)("Tuned to: " + stationName + " (" + String.format("%.1f", Float.valueOf(validatedStation.getFrequency())) + " MHz)")));
                        }
                        catch (Exception e) {
                            Dead_air.LOGGER.error("Error sending tune message", (Throwable)e);
                        }
                    }
                });
            } else {
                mc.execute(() -> {
                    if (mc.f_91074_ != null) {
                        mc.f_91074_.m_213846_((Component)Component.m_237113_((String)("No station found at " + String.format("%.1f", Float.valueOf(this.currentFrequency)) + " MHz")));
                    }
                });
            }
        }
        catch (Exception e) {
            Dead_air.LOGGER.error("Error tuning to station", (Throwable)e);
            Dead_air.LOGGER.error("Stack trace:", (Throwable)e);
            mc.execute(() -> {
                if (mc.f_91074_ != null) {
                    mc.f_91074_.m_213846_((Component)Component.m_237113_((String)("\u00a7cError tuning to station: " + e.getMessage())));
                }
            });
        }
    }

    public boolean m_6375_(double mouseX, double mouseY, int button) {
        if (button == 0) {
            RadioStation clickedStation;
            int clickedIndex;
            int listX = Math.max(10, this.f_96543_ / 40);
            int listY = Math.max(20, this.f_96544_ / 20) + 18;
            int listWidth = Math.min(180, this.f_96543_ / 4);
            int lineHeight = 12;
            if (mouseX >= (double)listX && mouseX <= (double)(listX + listWidth) && mouseY >= (double)listY && this.availableStations != null && !this.availableStations.isEmpty() && (clickedIndex = (int)((mouseY - (double)listY) / (double)lineHeight)) >= 0 && clickedIndex < this.availableStations.size() && (clickedStation = this.availableStations.get(clickedIndex)) != null) {
                this.currentFrequency = clickedStation.getFrequency();
                this.updateNearestStation();
                this.tuneToCurrentFrequency();
                return true;
            }
        }
        return super.m_6375_(mouseX, mouseY, button);
    }

    public boolean m_7043_() {
        return false;
    }
}
