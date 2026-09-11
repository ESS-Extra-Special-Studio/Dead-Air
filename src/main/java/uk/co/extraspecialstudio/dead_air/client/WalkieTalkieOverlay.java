package uk.co.extraspecialstudio.dead_air.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.music.MusicStationManager;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;
import uk.co.extraspecialstudio.dead_air.radio.SignalStrength;
import uk.co.extraspecialstudio.dead_air.tower.KnownTowersClientCache;
import uk.co.extraspecialstudio.dead_air.walkie.WalkieTalkieManager;

/**
 * Client-side overlay for walkie-talkie UI and radio panel tooltips.
 */
@SuppressWarnings("null")
public class WalkieTalkieOverlay {
    /**
     * Render tooltip when looking at an activated radio panel showing which station it broadcasts.
     */
    public static void renderRadioPanelTooltip(GuiGraphics guiGraphics, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult hit = (BlockHitResult) mc.hitResult;
        BlockPos pos = hit.getBlockPos();
        if (!uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerDetector.isRadioPanel(mc.level, pos)) return;

        ResourceLocation stationId = uk.co.extraspecialstudio.dead_air.tower.KnownTowersClientCache.getStationForPanel(pos);
        if (stationId == null) return;

        RadioStation station = StationRegistry.getStation(stationId);
        if (station == null) return;

        // Emergency Broadcast is global (every tower); don't show it as "this panel's station" to avoid confusion with music stations
        if (station.getType() == RadioStation.StationType.EMERGENCY_BROADCAST) return;

        String text = station.getName() + " (" + String.format("%.1f", station.getFrequency()) + " MHz)";
        int textWidth = mc.font.width(text);
        int x = (width - textWidth) / 2;
        int y = height - 59; // Above hotbar, similar to vanilla block name
        guiGraphics.fill(x - 2, y - 2, x + textWidth + 2, y + mc.font.lineHeight + 2, 0x80000000);
        guiGraphics.drawString(mc.font, text, x, y, 0x55FF55, false); // Green to match radio theme
    }

    /** Debounce scroll line text to reduce jitter when "Now Playing" / "Tuned" flip. */
    private static String lastScrollText = "";
    private static long lastScrollTextMs = 0L;
    private static final long SCROLL_TEXT_DEBOUNCE_MS = 400L;

    /**
     * Render the walkie-talkie overlay.
     */
    public static void render(GuiGraphics guiGraphics, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // Music is driven by ClientEvents tick so it keeps playing when HUD is hidden
        // (scrolled off hotbar, pause menu, T2 LCD-only). Overlay is display-only.
        if (!WalkieTalkieManager.shouldPlayRadio(mc.player)) {
            return;
        }
        ItemStack listening = WalkieTalkieManager.findListeningWalkie(mc.player);
        ItemStack stateWalkie = !listening.isEmpty() ? listening : WalkieTalkieManager.getPrimaryWalkieStack(mc.player);
        WalkieTalkieManager.WalkieTalkieState state = WalkieTalkieManager.getState(mc.player, stateWalkie);
        if (!state.isOn()) {
            return;
        }
        
        RadioStation station = state.getCurrentStation();
        // Radios with a working live LCD (built-in T2, Pip-Boy, …) skip the corner panel.
        boolean useT2ScreenHud = stateWalkie.getItem() instanceof uk.co.extraspecialstudio.dead_air.item.WalkieItem walkieItem
            && walkieItem.supportsLiveLcd()
            && walkieItem.isLiveLcdSupported();

        if (station == null) {
            if (!useT2ScreenHud) {
                int[] box = overlayBox(width, height);
                int x = box[0], y = box[1], bgWidth = box[2], bgHeight = box[3];
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                guiGraphics.fill(x, y, x + bgWidth, y + bgHeight, 0x80000000);
                int textX = x + bgWidth - 4;
                int lineH = 9;
                drawStringWithGlow(guiGraphics, mc.font, "No station", textX - mc.font.width("No station"), y + 2, COLOR_MUTED, true);
                drawStringWithGlow(guiGraphics, mc.font, "Open tuning (N)", textX - mc.font.width("Open tuning (N)"), y + 2 + lineH, 0x888888, true);
                drawSignalDirection(guiGraphics, mc, width, height, null, 0f, x, y, bgWidth, bgHeight);
            }
            return;
        }
        
        // Calculate signal early for static overlay and display
        float signalStrength = calculateSignalStrengthForStation(mc, station);

        // Static visual/audio feedback for HUD (music itself is tick-driven)
        uk.co.extraspecialstudio.dead_air.audio.StaticSoundManager.update(mc, station, signalStrength,
            uk.co.extraspecialstudio.dead_air.audio.AudioManager.hasActiveSoundForStation(station.getId()));
        if (useT2ScreenHud) {
            return;
        }

        int[] box = overlayBox(width, height);
        int x = box[0], y = box[1], bgWidth = box[2], bgHeight = box[3];
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        guiGraphics.fill(x, y, x + bgWidth, y + bgHeight, 0x80000000);
        int textX = x + bgWidth - 4;
        int lineH = 9;
        int leftPad = 4;
        int signalBars = SignalStrength.getSignalBars(signalStrength);
        String frequencyText = String.format("%.1f MHz", station.getFrequency());
        
        // Signal bars on the left, aligned with frequency line
        int barWidth = 4;
        int barHeight = 5;
        int barSpacing = 2;
        int totalBarWidth = 5 * barWidth + 4 * barSpacing;
        int barXStart = x + leftPad;
        int barY = y + 2 + lineH + (lineH - barHeight) / 2 + barHeight;
        int bgX = barXStart - 1;
        int bgY = barY - barHeight - 1;
        guiGraphics.fill(bgX, bgY, barXStart + totalBarWidth + 1, barY + 1, 0x80000000);
        int[] gradientColors = {0xFFFF0000, 0xFFFF8800, 0xFFFFFF00, 0xFF88FF00, 0xFF00FF00};
        for (int i = 0; i < 5; i++) {
            boolean isActive = i < signalBars;
            int barColor = isActive ? gradientColors[i] : 0xFF444444;
            int barStartX = barXStart + i * (barWidth + barSpacing);
            int barTopY = barY - barHeight;
            int radius = barWidth / 2;
            for (int px = 0; px < barWidth; px++) {
                for (int py = 0; py <= radius; py++) {
                    int dx = px - radius;
                    int dy = py;
                    if (dx * dx + dy * dy <= radius * radius) {
                        guiGraphics.fill(barStartX + px, barTopY + py, barStartX + px + 1, barTopY + py + 1, barColor);
                    }
                }
            }
            guiGraphics.fill(barStartX, barTopY + radius, barStartX + barWidth, barY, barColor);
        }
        
        // Station name box (where STATIC used to be), next to signal bars - smaller, scrolling
        int stationBoxX = barXStart + totalBarWidth + 4;
        int stationBoxW = textX - stationBoxX - 4;
        int stationBoxH = lineH + 2;
        int stationBoxY = barY - barHeight - 1;
        guiGraphics.fill(stationBoxX, stationBoxY, stationBoxX + stationBoxW, stationBoxY + stationBoxH, 0x80000000);
        drawScrollingTextScaled(guiGraphics, mc, station.getName() + "      ", stationBoxX + 2, stationBoxY + 1, stationBoxW - 4, stationBoxH - 2, 0.75f, COLOR_STATION);
        
        // Side-scrolling info line: station • song • day • time (debounced to reduce jitter)
        String newScrollText = buildScrollText(mc, station, signalStrength);
        long now = System.currentTimeMillis();
        if (!newScrollText.equals(lastScrollText) && (now - lastScrollTextMs >= SCROLL_TEXT_DEBOUNCE_MS || lastScrollText.isEmpty())) {
            lastScrollText = newScrollText;
            lastScrollTextMs = now;
        }
        String scrollText = lastScrollText.isEmpty() ? newScrollText : lastScrollText;
        int scrollAreaY = y + 2;
        int scrollAreaH = lineH;
        int scrollAreaW = bgWidth - leftPad - 4;
        int scrollAreaX = x + leftPad;
        drawScrollingText(guiGraphics, mc, scrollText, scrollAreaX, scrollAreaY, scrollAreaW, scrollAreaH, COLOR_SCROLL, true);
        
        // Frequency centered between station name row and compass
        int stationRowBottom = stationBoxY + stationBoxH;
        int compassHeight = 14;
        int compassPadding = 2;
        int compassTop = y + bgHeight - compassHeight - compassPadding;
        int freqY = stationRowBottom + (compassTop - stationRowBottom - lineH) / 2;
        int freqCenterX = x + bgWidth / 2;
        int freqTextW = mc.font.width(frequencyText);
        drawStringWithGlow(guiGraphics, mc.font, frequencyText, freqCenterX - freqTextW / 2, freqY, COLOR_FREQ, true);
        
        drawSignalDirection(guiGraphics, mc, width, height, station, signalStrength, x, y, bgWidth, bgHeight);
    }
    
    /**
     * Build the side-scrolling info text: connected station, music status, current song, date, time.
     */
    private static String buildScrollText(Minecraft mc, RadioStation station, float signalStrength) {
        String stationName = station.getName();
        String songPart;
        // Use hasActiveSoundForStation so we show "Now Playing" when we actually have sound (avoids jitter from signal flicker around 0.1)
        boolean musicPlaying = uk.co.extraspecialstudio.dead_air.audio.AudioManager.hasActiveSoundForStation(station.getId())
            && signalStrength >= 0.05f;
        if (musicPlaying) {
            ResourceLocation trackId = MusicStationManager.getCurrentTrackId(station.getId(), mc.player);
            String trackName = trackId != null ? MusicStationManager.formatTrackDisplayName(trackId) : "?";
            songPart = "Now Playing: " + trackName;
        } else if (signalStrength < 0.05f) {
            songPart = "No signal";
        } else {
            // Tuned with signal but no active sound (backoff, or station not playing yet) - show "Tuned" not "Tuning..."
            songPart = "Tuned";
        }
        String dayPart = "";
        String timePart = "";
        if (mc.level != null) {
            long dayTime = mc.level.getDayTime();
            int day = (int) (dayTime / 24000) + 1;
            int tickOfDay = (int) (dayTime % 24000);
            int hour = (tickOfDay / 1000 + 6) % 24;
            int minute = (tickOfDay % 1000) * 60 / 1000;
            dayPart = "Day " + day;
            timePart = String.format("%02d:%02d", hour, minute);
        }
        return stationName + "  •  " + songPart + "  •  " + dayPart + "  •  " + timePart + "      ";
    }
    
    /** Text colors for overlay elements. */
    private static final int COLOR_STATION = 0x55DDFF;   // Cyan
    private static final int COLOR_FREQ = 0xFFFF88;      // Gold
    private static final int COLOR_SCROLL = 0x88FF88;    // Lime
    private static final int COLOR_COMPASS = 0xAAFFAA;   // Light green
    private static final int COLOR_MUTED = 0xAAAAAA;     // Gray
    
    /**
     * Draw text with a faint glow (dimmed outline layers).
     */
    private static void drawStringWithGlow(GuiGraphics guiGraphics, net.minecraft.client.gui.Font font, String text, int x, int y, int color, boolean glow) {
        if (glow) {
            int glowColor = (color & 0x00FFFFFF) | 0x30000000; // Faint glow
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx != 0 || dy != 0) {
                        guiGraphics.drawString(font, text, x + dx, y + dy, glowColor, false);
                    }
                }
            }
        }
        guiGraphics.drawString(font, text, x, y, color, false);
    }
    
    /** Scroll period in ms for smooth time-based scroll (avoids tick-step jitter). */
    private static final long SCROLL_PERIOD_MS = 14_000L;

    /**
     * Draw horizontally scrolling text (marquee style). Time-based offset for smooth scroll and less jitter.
     */
    private static void drawScrollingText(GuiGraphics guiGraphics, Minecraft mc, String text, int x, int y, int width, int height, int color, boolean glow) {
        int textWidth = mc.font.width(text);
        if (textWidth <= width) {
            drawStringWithGlow(guiGraphics, mc.font, text, x, y, color, glow);
            return;
        }
        int scrollLen = textWidth + 24;
        long t = System.currentTimeMillis() % SCROLL_PERIOD_MS;
        int offset = (int) (t * scrollLen / SCROLL_PERIOD_MS);
        guiGraphics.enableScissor(x, y, x + width, y + height);
        String scrolling = text + "  •  " + text;
        if (glow) {
            int glowColor = (color & 0x00FFFFFF) | 0x30000000;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx != 0 || dy != 0) {
                        guiGraphics.drawString(mc.font, scrolling, x - offset + dx, y + dy, glowColor, false);
                    }
                }
            }
        }
        guiGraphics.drawString(mc.font, scrolling, x - offset, y, color, false);
        guiGraphics.disableScissor();
    }
    
    /**
     * Draw horizontally scrolling text at reduced scale (for station name).
     * Centers text both horizontally and vertically within the bar.
     */
    private static void drawScrollingTextScaled(GuiGraphics guiGraphics, Minecraft mc, String text, int x, int y, int width, int height, float scale, int color) {
        int fullWidth = mc.font.width(text);
        int scaledWidth = (int) (fullWidth * scale);
        int scaledLineHeight = (int) (mc.font.lineHeight * scale);
        int centerY = (height - scaledLineHeight) / 2 + 1; // Vertically centered, +1 for visual nudge
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0);
        guiGraphics.pose().scale(scale, scale, 1f);
        int drawY = (int) (centerY / scale); // Y in scaled coords
        if (scaledWidth <= width) {
            int centerX = (width - scaledWidth) / 2;
            int drawX = (int) (centerX / scale);
            drawStringWithGlow(guiGraphics, mc.font, text, drawX, drawY, color, true);
        } else {
            int scrollLen = fullWidth + 16;
            long t = System.currentTimeMillis() % SCROLL_PERIOD_MS;
            int offset = (int) (t * scrollLen / SCROLL_PERIOD_MS);
            guiGraphics.enableScissor(x, y, x + width, y + height);
            String scrolling = text + "  •  " + text;
            int glowColor = (color & 0x00FFFFFF) | 0x30000000;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx != 0 || dy != 0) {
                        guiGraphics.drawString(mc.font, scrolling, -offset + dx, drawY + dy, glowColor, false);
                    }
                }
            }
            guiGraphics.drawString(mc.font, scrolling, -offset, drawY, color, false);
            guiGraphics.disableScissor();
        }
        guiGraphics.pose().popPose();
    }
    
    /** Cone angle (degrees): if player faces within this of the tower, compass flashes green. */
    private static final float FACING_TOWER_DEGREES = 35f;

    /**
     * Draw compass bar showing player's facing direction.
     * Flashes green when the player is facing the tower for the tuned station; red when looking elsewhere.
     */
    private static void drawSignalDirection(GuiGraphics guiGraphics, Minecraft mc, int width, int height, 
                                           RadioStation station, float signalStrength, int infoX, int infoY, int infoWidth, int infoHeight) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        
        // Get player's facing direction (yaw). Minecraft: 0=South (+Z), 90=West (-X), 180=North (-Z), 270=East (+X)
        float playerYaw = mc.player.getYRot();
        while (playerYaw < 0) playerYaw += 360;
        while (playerYaw >= 360) playerYaw -= 360;
        
        String facingDirection = compassDirectionFromYaw(playerYaw);
        
        // Facing hint from any detected/activated tower in range (music signal not required)
        boolean facingTower = false;
        if (station != null && uk.co.extraspecialstudio.dead_air.tower.KnownTowersClientCache.hasTowerInRangeForStation(
                station.getId(), mc.player.position(), mc.level)) {
            Vec3 playerPos = mc.player.position();
            Vec3 towerPos = uk.co.extraspecialstudio.dead_air.tower.KnownTowersClientCache.getBestTowerPositionForStation(
                station.getId(), playerPos, mc.level);
            if (towerPos != null) {
                double dx = towerPos.x - playerPos.x;
                double dz = towerPos.z - playerPos.z;
                if (dx * dx + dz * dz > 0.5) { // ignore if right on top of tower
                    // Yaw to face (dx, dz): Minecraft uses atan2(-dx, dz) so 0=South, 90=West, 180=North, 270=East
                    float yawToTower = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    if (yawToTower < 0) yawToTower += 360;
                    float diff = Math.abs(((playerYaw - yawToTower + 540) % 360) - 180);
                    facingTower = diff <= FACING_TOWER_DEGREES;
                }
            }
        }
        
        int compassWidth = 70;
        int compassHeight = 14;
        int paddingFromBottom = 2;
        int compassX = infoX + (infoWidth - compassWidth) / 2;
        int compassY = infoY + infoHeight - compassHeight - paddingFromBottom;
        
        guiGraphics.fill(compassX, compassY, compassX + compassWidth, compassY + compassHeight, 0x80000000);
        
        int borderColor = facingTower ? 0xFF88FF00 : 0xFFCC0000; // Green when facing tower, red when looking elsewhere
        guiGraphics.fill(compassX, compassY, compassX + compassWidth, compassY + 1, borderColor);
        guiGraphics.fill(compassX, compassY + compassHeight - 1, compassX + compassWidth, compassY + compassHeight, borderColor);
        guiGraphics.fill(compassX, compassY, compassX + 1, compassY + compassHeight, borderColor);
        guiGraphics.fill(compassX + compassWidth - 1, compassY, compassX + compassWidth, compassY + compassHeight, borderColor);
        
        int centerX = compassX + compassWidth / 2;
        int textWidth = mc.font.width(facingDirection);
        int textY = compassY + (compassHeight - mc.font.lineHeight) / 2 + 1;
        int textColor = facingTower ? blinkGreen() : 0xFFCC0000; // Flash green when facing tower, red when looking elsewhere
        drawStringWithGlow(guiGraphics, mc.font, facingDirection, centerX - textWidth / 2, textY, textColor, true);
    }
    
    /** Blinking green for "facing tower" (on/off every ~400ms). */
    private static int blinkGreen() {
        return (System.currentTimeMillis() / 400) % 2 == 0 ? 0xFF88FF00 : 0xFF55FF55;
    }
    
    /**
     * Calculate signal strength for a station (client-side).
     * Used by overlay and AudioManager.
     * Prefer LIVE (tower or cache from current player pos) when >= 0.1 so volume steps and stop work.
     * Otherwise use server so music can start when client has no tower/cache yet.
     */
    public static float calculateSignalStrengthForStation(Minecraft mc, RadioStation station) {
        if (mc.level == null || mc.player == null || station == null) {
            return 0.0f;
        }

        // Cross-dim Linked mode: full signal when a listening/held T2 is linked and tuned to the linked station
        try {
            ItemStack walkie = WalkieTalkieManager.findListeningWalkie(mc.player);
            if (walkie.isEmpty()) walkie = WalkieTalkieManager.getPrimaryWalkieStack(mc.player);
            if (WalkieTalkieManager.isUsingLinkedMode(walkie)) {
                ResourceLocation linkedId = WalkieTalkieManager.getLinkedStationId(walkie);
                if (linkedId != null && linkedId.equals(station.getId())) {
                    BlockPos panel = WalkieTalkieManager.getLinkedPanelPos(walkie);
                    if (panel != null && KnownTowersClientCache.isSignalBoostInstalledForPanel(panel)) {
                        return 1.0f;
                    }
                    // Linked tower may be in another dimension — still allow full signal if boost was synced when linked
                    if (WalkieTalkieManager.hasLinkedTower(walkie)) {
                        return 1.0f;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        
        try {
            if (station.getType() == RadioStation.StationType.EMERGENCY_BROADCAST) {
                return 1.0f;
            }

            Vec3 playerPos = mc.player.position();
            float liveSignal = 0.0f;
            
            // 1) TowerManager (singleplayer; live)
            try {
                var serverTowers = uk.co.extraspecialstudio.dead_air.radio.TowerManager.getAllTowers(mc.level);
                if (serverTowers != null && !serverTowers.isEmpty()) {
                    for (var tower : serverTowers) {
                        if (!tower.isPowered()) continue;
                        if (!tower.getStation().getId().equals(station.getId())) continue;
                        if (tower.isInRange(playerPos)) {
                            float signal = SignalStrength.getFinalSignalStrength(mc.level, tower, playerPos);
                            if (signal > liveSignal) liveSignal = signal;
                        }
                    }
                }
            } catch (Exception e) { /* TowerManager not available */ }
            
            // 2) KnownTowersClientCache (synced positions; uses player pos so live)
            if (uk.co.extraspecialstudio.dead_air.tower.KnownTowersClientCache.hasCachedTowers()) {
                float cacheSignal = uk.co.extraspecialstudio.dead_air.tower.KnownTowersClientCache.getSignalFromCache(
                    station.getId(), playerPos, mc.level);
                if (cacheSignal > liveSignal) liveSignal = cacheSignal;
            }
            
            Float serverSignal = uk.co.extraspecialstudio.dead_air.audio.AudioManager.getServerSignal(station.getId());
            boolean serverStale = uk.co.extraspecialstudio.dead_air.audio.AudioManager.isServerSignalStale(station.getId(), mc.player.tickCount);
            // When we have any local tower data (cache or TowerManager) and it says out of range, return 0 so signal and music stop.
            boolean haveLocalTowerData = uk.co.extraspecialstudio.dead_air.tower.KnownTowersClientCache.hasCachedTowers();
            if (liveSignal < 0.05f && haveLocalTowerData) {
                return 0.0f; // Out of range: 0 bars, music stops. No server/musicPlaysWithoutTower fallback.
            }
            // When client has tower cache and in range, use live signal
            if (haveLocalTowerData) {
                if (liveSignal >= 0.05f) return liveSignal;
                return 0.0f;
            }
            // No cache: rely on server. If server signal is stale (no response in a while), treat as 0 so music stops.
            if (serverStale) {
                return 0.0f;
            }
            // If we have no local signal but server says high (e.g. stale position), treat as 0 so signal doesn't jump to 3 bars
            if (liveSignal < 0.05f && serverSignal != null && serverSignal >= 0.4f) {
                return 0.0f;
            }
            if (liveSignal >= 0.1f && serverSignal != null) return Math.min(liveSignal, serverSignal);
            if (liveSignal >= 0.1f) return liveSignal;
            float result = serverSignal != null ? serverSignal : liveSignal;
            return result;
        } catch (Exception e) {
            return 0.0f;
        }
    }
    
    /**
     * Get compass direction name from yaw angle.
     * Returns: "North", "South", "East", "West", "Northeast", "Northwest", "Southeast", "Southwest"
     */
    public static String compassDirectionFromYaw(float yaw) {
        // Normalize to 0-360
        while (yaw < 0) yaw += 360;
        while (yaw >= 360) yaw -= 360;
        
        // Convert to compass direction
        // 0° = South, 45° = Southwest, 90° = West, 135° = Northwest, 
        // 180° = North, 225° = Northeast, 270° = East, 315° = Southeast
        if (yaw >= 337.5 || yaw < 22.5) return "South";
        if (yaw >= 22.5 && yaw < 67.5) return "Southeast";
        if (yaw >= 67.5 && yaw < 112.5) return "East";
        if (yaw >= 112.5 && yaw < 157.5) return "Northeast";
        if (yaw >= 157.5 && yaw < 202.5) return "North";
        if (yaw >= 202.5 && yaw < 247.5) return "Northwest";
        if (yaw >= 247.5 && yaw < 292.5) return "West";
        return "Southwest"; // 292.5 to 337.5
    }

    /** Corner HUD box: [x, y, width, height]. */
    private static int[] overlayBox(int screenWidth, int screenHeight) {
        int bgWidth = 120;
        int bgHeight = 58;
        int pad = 2;
        String corner = Config.overlayCorner != null ? Config.overlayCorner : "top_right";
        int ox = Config.overlayOffsetX;
        int oy = Config.overlayOffsetY;
        int x, y;
        switch (corner.toLowerCase()) {
            case "top_left":
                x = pad + ox;
                y = pad + oy;
                break;
            case "bottom_left":
                x = pad + ox;
                y = Math.round(screenHeight - bgHeight - pad + oy);
                break;
            case "bottom_right":
                x = Math.round(screenWidth - bgWidth - pad - ox);
                y = Math.round(screenHeight - bgHeight - pad + oy);
                break;
            default: // top_right
                x = Math.round(screenWidth - bgWidth - pad - ox);
                y = pad + oy;
                break;
        }
        return new int[]{x, y, bgWidth, bgHeight};
    }
}
