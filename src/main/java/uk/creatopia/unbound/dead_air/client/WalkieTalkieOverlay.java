package uk.creatopia.unbound.dead_air.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import uk.creatopia.unbound.dead_air.radio.RadioStation;
import uk.creatopia.unbound.dead_air.radio.SignalStrength;
import uk.creatopia.unbound.dead_air.walkie.WalkieTalkieManager;

/**
 * Client-side overlay for walkie-talkie UI.
 */
@SuppressWarnings("null")
public class WalkieTalkieOverlay {
    /**
     * Render the walkie-talkie overlay.
     */
    public static void render(GuiGraphics guiGraphics, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        
        // Check if player is holding walkie-talkie
        if (!WalkieTalkieManager.isHoldingWalkieTalkie(mc.player)) {
            return;
        }
        
        WalkieTalkieManager.WalkieTalkieState state = WalkieTalkieManager.getState(mc.player);
        
        if (!state.isOn()) {
            return;
        }
        
        RadioStation station = state.getCurrentStation();
        if (station == null) {
            return;
        }
        
        // Calculate signal strength directly (don't rely on AudioManager, especially for Emergency Broadcast)
        float signalStrength = calculateSignalStrengthForStation(mc, station);
        int signalBars = SignalStrength.getSignalBars(signalStrength);
        
        // Render UI elements
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        
        // Position overlay in top-right corner to avoid chat overlap
        // Leave space for FPS counter, difficulty, and other top-right UI elements
        int startY = 30; // Start below typical top UI elements
        int x = width - 150; // Right side with padding
        int y = startY;
        
        // Draw semi-transparent background for better readability
        int bgWidth = 140;
        int bgHeight = 80; // Increased to accommodate signal bars with proper spacing
        guiGraphics.fill(x - 5, y - 5, x + bgWidth, y + bgHeight, 0x80000000); // Semi-transparent black
        
        String stationText = station.getName();
        String frequencyText = String.format("%.1f MHz", station.getFrequency());
        String genreText = station.getGenre();
        
        // Draw text right-aligned
        int textX = x + bgWidth - 5;
        guiGraphics.drawString(mc.font, stationText, textX - mc.font.width(stationText), y, 0xFFFFFF, false);
        guiGraphics.drawString(mc.font, frequencyText, textX - mc.font.width(frequencyText), y + 10, 0xCCCCCC, false);
        guiGraphics.drawString(mc.font, genreText, textX - mc.font.width(genreText), y + 20, 0xAAAAAA, false);
        
        // Draw signal bars (right-aligned) - brighter and more visible, with more spacing to avoid overlap
        // Genre text is at y + 20, font line height is ~9px, so text extends to ~y + 29
        // Signal bars are 8px tall, so we need to start them at least at y + 30, but add more padding
        int barX = textX - 5;
        int barY = y + 45; // Increased significantly to ensure no overlap with genre text (which ends at ~y + 29)
        int barWidth = 6; // Wider bars
        int barHeight = 8; // Taller bars
        int barSpacing = 3; // More spacing
        int totalBarWidth = 5 * barWidth + 4 * barSpacing; // Total width of all bars
        
        // Draw background for signal bars area
        int bgX = barX - totalBarWidth - 2;
        int bgY = barY - barHeight - 2;
        guiGraphics.fill(bgX, bgY, barX + 2, barY + 2, 0x80000000); // Semi-transparent black background
        
        // Draw signal bars with gradient colors and rounded tops (like image 3)
        // Colors: red -> orange -> yellow -> light green -> green
        int[] gradientColors = {0xFFFF0000, 0xFFFF8800, 0xFFFFFF00, 0xFF88FF00, 0xFF00FF00}; // Red, Orange, Yellow, Light Green, Green
        for (int i = 0; i < 5; i++) {
            boolean isActive = i < signalBars;
            int barColor = isActive ? gradientColors[i] : 0xFF444444;
            int barStartX = barX - totalBarWidth + i * (barWidth + barSpacing);
            int barTopY = barY - barHeight;
            
            // Draw bar with rounded top (like image 3)
            int radius = barWidth / 2;
            int topY = barTopY;
            
            // Draw rounded top (semi-circle) - fill pixels in a circle
            for (int px = 0; px < barWidth; px++) {
                for (int py = 0; py <= radius; py++) {
                    int dx = px - radius;
                    int dy = py;
                    // Check if pixel is inside the circle (rounded top)
                    if (dx * dx + dy * dy <= radius * radius) {
                        guiGraphics.fill(barStartX + px, topY + py, barStartX + px + 1, topY + py + 1, barColor);
                    }
                }
            }
            
            // Draw main rectangle body (below rounded top)
            guiGraphics.fill(barStartX, topY + radius, barStartX + barWidth, barY, barColor);
            
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
                            guiGraphics.fill(barStartX + px, topY + py, barStartX + px + 1, topY + py + 1, 0xFFFFFF);
                        }
                    }
                }
                // Bottom edge
                guiGraphics.fill(barStartX, barY - 1, barStartX + barWidth, barY, 0xFFFFFF);
                // Left edge
                guiGraphics.fill(barStartX, topY + radius, barStartX + 1, barY, 0xFFFFFF);
                // Right edge
                guiGraphics.fill(barStartX + barWidth - 1, topY + radius, barStartX + barWidth, barY, 0xFFFFFF);
            }
        }
        
        // Draw static effect if signal is weak (right-aligned) - below signal bars
        if (signalStrength < 0.3f) {
            String staticText = "STATIC";
            int alpha = Mth.floor((0.3f - signalStrength) * 255 / 0.3f);
            guiGraphics.drawString(mc.font, staticText, textX - mc.font.width(staticText), barY + 5, 
                0xFFFFFF | (alpha << 24), false);
        }
        
        // Draw compass bar inside the info window at the bottom middle
        drawSignalDirection(guiGraphics, mc, width, height, station, signalStrength, x, y, bgWidth, bgHeight);
    }
    
    /**
     * Draw compass bar showing player's facing direction.
     * Positioned inside the info window at the bottom middle.
     */
    private static void drawSignalDirection(GuiGraphics guiGraphics, Minecraft mc, int width, int height, 
                                           RadioStation station, float signalStrength, int infoX, int infoY, int infoWidth, int infoHeight) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        
        // Get player's facing direction (yaw)
        float playerYaw = mc.player.getYRot();
        
        // Convert yaw to compass direction (0 = South, 90 = West, 180 = North, 270 = East)
        // Minecraft yaw: 0 = South, increases clockwise
        // Normalize to 0-360
        while (playerYaw < 0) playerYaw += 360;
        while (playerYaw >= 360) playerYaw -= 360;
        
        // Get cardinal direction name
        String facingDirection = getCompassDirection(playerYaw);
        
        // Position compass bar inside the info window at the bottom middle
        int compassWidth = 100; // Width of compass bar
        int compassHeight = 18; // Height of compass bar
        int padding = 5; // Padding from bottom edge
        
        // Center horizontally within the info window
        int compassX = infoX + (infoWidth - compassWidth) / 2;
        // Position at bottom of info window with padding
        int compassY = infoY + infoHeight - compassHeight - padding;
        
        // Draw compass background (semi-transparent)
        guiGraphics.fill(compassX, compassY, compassX + compassWidth, compassY + compassHeight, 0x80000000);
        
        // Draw compass bar outline (green theme)
        int borderColor = 0xFF88FF00; // Bright green border
        guiGraphics.fill(compassX, compassY, compassX + compassWidth, compassY + 1, borderColor); // Top
        guiGraphics.fill(compassX, compassY + compassHeight - 1, compassX + compassWidth, compassY + compassHeight, borderColor); // Bottom
        guiGraphics.fill(compassX, compassY, compassX + 1, compassY + compassHeight, borderColor); // Left
        guiGraphics.fill(compassX + compassWidth - 1, compassY, compassX + compassWidth, compassY + compassHeight, borderColor); // Right
        
        // Draw facing direction text in center (no pink bar, no N/W/S/E labels)
        int centerX = compassX + compassWidth / 2;
        int textWidth = mc.font.width(facingDirection);
        guiGraphics.drawString(mc.font, facingDirection, centerX - textWidth / 2, 
                             compassY + (compassHeight - mc.font.lineHeight) / 2, 0xFFFF00, false);
    }
    
    /**
     * Calculate signal strength for a station (client-side).
     * Always recalculates to ensure accurate signal display.
     * On client side, we scan for Radio Panels directly since tower data isn't synced.
     */
    private static float calculateSignalStrengthForStation(Minecraft mc, RadioStation station) {
        if (mc.level == null || mc.player == null || station == null) {
            return 0.0f;
        }
        
        try {
            // Emergency Broadcast is never tied to towers - always full signal, always available
            if (station.getType() == RadioStation.StationType.EMERGENCY_BROADCAST) {
                return 1.0f;
            }
            
            Vec3 playerPos = mc.player.position();
            float bestSignal = 0.0f;
            
            // On client side, try to use server-side tower data first (more accurate)
            // If that fails, fall back to scanning for Radio Panels
            boolean useServerData = false;
            
            try {
                var serverTowers = uk.creatopia.unbound.dead_air.radio.TowerManager.getAllTowers(mc.level);
                if (serverTowers != null && !serverTowers.isEmpty()) {
                    useServerData = true;
                    
                    for (var tower : serverTowers) {
                        if (!tower.isPowered()) continue;
                        if (!tower.getStation().getId().equals(station.getId())) {
                            continue;
                        }
                        
                        if (tower.isInRange(playerPos)) {
                            float signal = SignalStrength.getFinalSignalStrength(mc.level, tower, playerPos);
                            if (signal > bestSignal) {
                                bestSignal = signal;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Server-side data not available, will use client-side scan
                useServerData = false;
            }
            
            // If server data wasn't available or didn't find anything, scan for Radio Panels
            if (!useServerData || bestSignal < 0.1f) {
                // Check a reasonable area around the player (within max broadcast range)
                int searchRadius = (int) Math.min(500, station.getBroadcastRange()); // Limit to 500 blocks for performance
                net.minecraft.core.BlockPos playerBlockPos = net.minecraft.core.BlockPos.containing(playerPos);
                
                // Scan area around player for Radio Panels (check every 8 blocks for better coverage)
                for (int x = -searchRadius; x <= searchRadius; x += 8) {
                    for (int z = -searchRadius; z <= searchRadius; z += 8) {
                        // Also check Y axis (towers can be at different heights)
                        for (int y = -10; y <= 10; y += 5) {
                            net.minecraft.core.BlockPos checkPos = playerBlockPos.offset(x, y, z);
                            
                            // Check if this is a Radio Panel
                            if (uk.creatopia.unbound.dead_air.tower.ApocalypseTowerDetector.isRadioPanel(mc.level, checkPos)) {
                                // Check if panel is activated (powered) - use NBT check
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
            }
            
            return bestSignal;
        } catch (Exception e) {
            uk.creatopia.unbound.dead_air.Dead_air.LOGGER.debug("Could not calculate signal strength on client: {}", e.getMessage());
            return 0.0f;
        }
    }
    
    /**
     * Get compass direction name from yaw angle.
     * Returns: "North", "South", "East", "West", "Northeast", "Northwest", "Southeast", "Southwest"
     */
    private static String getCompassDirection(float yaw) {
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
}
