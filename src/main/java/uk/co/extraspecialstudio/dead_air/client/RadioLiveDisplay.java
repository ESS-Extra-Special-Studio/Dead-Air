package uk.co.extraspecialstudio.dead_air.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.music.MusicStationManager;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.SignalStrength;
import uk.co.extraspecialstudio.dead_air.radio.StationRegistry;
import uk.co.extraspecialstudio.dead_air.tower.KnownTowersClientCache;
import uk.co.extraspecialstudio.dead_air.walkie.WalkieTalkieManager;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paints the live radio HUD into an arbitrary item atlas screen rectangle.
 * Used by the built-in T2 walkie and by companion radios (e.g. Pip-Boy) with their own UVs.
 */
public final class RadioLiveDisplay {
    /**
     * Optional idle painter for companion radios (e.g. Pip wrist silhouette).
     * Invoked instead of {@link #paintHud} when the radio is not actively listening.
     * Stock T2 never registers one.
     */
    @FunctionalInterface
    public interface IdlePainter {
        /**
         * @return true if the buffer was painted (skip default HUD); false to fall back to {@code paintHud}
         */
        boolean paint(NativeImage buf, int logicalWidth, int logicalHeight, long nowMs);
    }

    public record LcdRect(int x0, int y0, int x1, int y1) {
        public int width() { return x1 - x0; }
        public int height() { return y1 - y0; }
        public int cx() { return (x0 + x1) / 2; }
    }

    public record Spec(
        ResourceLocation baseTexture,
        ResourceLocation liveId,
        LcdRect rect,
        /** If non-null, atlas must match this square size (built-in T2 = 1024). Null = any size that fits rect. */
        Integer expectedAtlasSize,
        /** When false, skip LINK/LOC badge (small Pip screens — overlaps compass). */
        boolean showModeBadge,
        /**
         * Paint rotation applied when copying the logical HUD into the atlas rect.
         * {@code 0} = none, {@code 90} = counter-clockwise, {@code -90} = clockwise,
         * {@code 180} = flip both axes, {@code 270} = clockwise 90 then 180 (Pip wrist).
         */
        int paintRotateDegrees
    ) {
        public Spec(ResourceLocation baseTexture, ResourceLocation liveId, LcdRect rect, Integer expectedAtlasSize) {
            this(baseTexture, liveId, rect, expectedAtlasSize, true, 0);
        }

        public Spec(
            ResourceLocation baseTexture,
            ResourceLocation liveId,
            LcdRect rect,
            Integer expectedAtlasSize,
            boolean showModeBadge
        ) {
            this(baseTexture, liveId, rect, expectedAtlasSize, showModeBadge, 0);
        }

        /** @deprecated use {@link #paintRotateDegrees()} */
        @Deprecated
        public boolean rotatePaintCcw90() {
            return paintRotateDegrees == 90;
        }
    }

    private static final Map<ResourceLocation, RadioLiveDisplay> BY_LIVE_ID = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, IdlePainter> IDLE_PAINTERS = new ConcurrentHashMap<>();

    private static final int LCD_BG = abgr(255, 159, 162, 57);
    /** Fallout idle CRT black (ABGR). */
    private static final int LCD_IDLE_BG = abgr(255, 0, 0, 0);
    private static final int PIXEL_DARK = abgr(255, 18, 22, 8);
    private static final int PIXEL_DIM = abgr(255, 70, 76, 28);
    private static final int PIXEL_CYAN = abgr(255, 12, 90, 120);
    private static final int PIXEL_GOLD = abgr(255, 55, 48, 10);
    private static final int PIXEL_LIME = abgr(255, 20, 90, 18);
    private static final int PIXEL_RED = abgr(255, 160, 16, 8);
    private static final int[] BAR_COLORS = {
        abgr(255, 200, 20, 10),
        abgr(255, 200, 90, 10),
        abgr(255, 200, 180, 10),
        abgr(255, 90, 180, 10),
        abgr(255, 20, 180, 10)
    };
    private static final int BAR_OFF = abgr(255, 90, 95, 40);
    private static final Map<Character, Integer> GLYPHS = createGlyphs();
    private static final long REDRAW_MS = 80L;

    private final Spec spec;
    private DynamicTexture liveTexture;
    private NativeImage baseSnapshot;
    private long lastDrawMs;
    private String lastFingerprint = "";
    private Boolean supported;

    private RadioLiveDisplay(Spec spec) {
        this.spec = spec;
    }

    public static RadioLiveDisplay get(Spec spec) {
        RadioLiveDisplay existing = BY_LIVE_ID.get(spec.liveId());
        if (existing == null) {
            RadioLiveDisplay created = new RadioLiveDisplay(spec);
            BY_LIVE_ID.put(spec.liveId(), created);
            return created;
        }
        // Spec is fixed at construction — replace if paint/atlas knobs changed (Pip iterate).
        if (existing.spec.paintRotateDegrees() != spec.paintRotateDegrees()
            || existing.spec.showModeBadge() != spec.showModeBadge()
            || !java.util.Objects.equals(existing.spec.expectedAtlasSize(), spec.expectedAtlasSize())
            || !existing.spec.rect().equals(spec.rect())
            || !existing.spec.baseTexture().equals(spec.baseTexture())) {
            existing.reset();
            RadioLiveDisplay created = new RadioLiveDisplay(spec);
            BY_LIVE_ID.put(spec.liveId(), created);
            return created;
        }
        return existing;
    }

    /** Register / clear an idle painter for a live texture id (Pip wrist / empty LCD). */
    public static void setIdlePainter(ResourceLocation liveId, IdlePainter painter) {
        if (liveId == null) {
            return;
        }
        if (painter == null) {
            IDLE_PAINTERS.remove(liveId);
        } else {
            IDLE_PAINTERS.put(liveId, painter);
        }
    }

    public static void resetAll() {
        for (RadioLiveDisplay d : BY_LIVE_ID.values()) {
            d.reset();
        }
        BY_LIVE_ID.clear();
        // Keep IDLE_PAINTERS — Pip (and other companions) register once at client setup.
    }

    public void reset() {
        supported = null;
        lastFingerprint = "";
        lastDrawMs = 0L;
        if (baseSnapshot != null) {
            baseSnapshot.close();
            baseSnapshot = null;
        }
        liveTexture = null;
    }

    public boolean isSupported() {
        if (supported == null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return true;
            try {
                probe(mc);
            } catch (Exception e) {
                supported = false;
                Dead_air.LOGGER.debug("Radio live display probe failed for {}: {}", spec.baseTexture(), e.toString());
            }
        }
        return supported == Boolean.TRUE;
    }

    public ResourceLocation getTexture(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return spec.baseTexture();
        }
        if (!isSupported()) {
            return spec.baseTexture();
        }
        Minecraft mc = Minecraft.getInstance();
        try {
            ensureLive(mc);
            HudState state = describe(stack, mc);
            long now = System.currentTimeMillis();
            int scrollPhase = (int) ((now / 90L) % 10_000);
            String fingerprint = state.fingerprint() + "|" + (scrollPhase / 2)
                + "|r" + spec.paintRotateDegrees();
            if (fingerprint.equals(lastFingerprint) && now - lastDrawMs < REDRAW_MS) {
                return spec.liveId();
            }
            redraw(state, scrollPhase);
            lastFingerprint = fingerprint;
            lastDrawMs = now;
            liveTexture.upload();
            return spec.liveId();
        } catch (Exception e) {
            Dead_air.LOGGER.debug("Could not build live radio display: {}", e.toString());
            return spec.baseTexture();
        }
    }

    private void probe(Minecraft mc) throws Exception {
        try (InputStream stream = mc.getResourceManager().getResource(spec.baseTexture()).orElseThrow().open()) {
            NativeImage loaded = NativeImage.read(stream);
            try {
                LcdRect r = spec.rect();
                boolean fits = loaded.getWidth() > r.x1() && loaded.getHeight() > r.y1();
                if (spec.expectedAtlasSize() != null) {
                    fits = fits && loaded.getWidth() == spec.expectedAtlasSize()
                        && loaded.getHeight() == spec.expectedAtlasSize();
                }
                supported = fits;
                if (!fits) {
                    Dead_air.LOGGER.info(
                        "Live LCD disabled for {}: atlas {}x{}, rect needs >{}x{}{}",
                        spec.baseTexture(), loaded.getWidth(), loaded.getHeight(), r.x1(), r.y1(),
                        spec.expectedAtlasSize() != null ? ", expected " + spec.expectedAtlasSize() : "");
                }
            } finally {
                loaded.close();
            }
        }
    }

    private void ensureLive(Minecraft mc) throws Exception {
        if (liveTexture != null && baseSnapshot != null) return;
        try (InputStream stream = mc.getResourceManager().getResource(spec.baseTexture()).orElseThrow().open()) {
            NativeImage loaded = NativeImage.read(stream);
            LcdRect r = spec.rect();
            if (loaded.getWidth() <= r.x1() || loaded.getHeight() <= r.y1()) {
                supported = false;
                loaded.close();
                throw new IllegalStateException("atlas too small for live LCD: " + spec.baseTexture());
            }
            supported = true;
            baseSnapshot = new NativeImage(loaded.getWidth(), loaded.getHeight(), false);
            baseSnapshot.copyFrom(loaded);
            liveTexture = new DynamicTexture(loaded);
            mc.getTextureManager().register(spec.liveId(), liveTexture);
        }
    }

    private void redraw(HudState state, int scrollPhase) {
        NativeImage image = liveTexture.getPixels();
        if (image == null || baseSnapshot == null) return;
        LcdRect atlas = spec.rect();
        image.copyFrom(baseSnapshot);

        boolean rot90 = spec.paintRotateDegrees() == 90 || spec.paintRotateDegrees() == -90
            || Math.abs(spec.paintRotateDegrees()) == 270;
        // Logical canvas: swap axes when we will rotate ±90° (or ±90 then 180) into the atlas rect.
        int lw = rot90 ? atlas.height() : atlas.width();
        int lh = rot90 ? atlas.width() : atlas.height();

        NativeImage buf = new NativeImage(lw, lh, false);
        try {
            IdlePainter idle = IDLE_PAINTERS.get(spec.liveId());
            boolean usedIdle = false;
            if (!state.listening() && idle != null) {
                fill(buf, 0, 0, lw, lh, LCD_IDLE_BG);
                usedIdle = idle.paint(buf, lw, lh, System.currentTimeMillis());
            }
            if (!usedIdle) {
                fill(buf, 0, 0, lw, lh, LCD_BG);
                paintHud(buf, lw, lh, state, scrollPhase);
            }
            int deg = spec.paintRotateDegrees();
            if (deg == 90) {
                blitCcw90(buf, image, atlas);
            } else if (deg == -90) {
                blitCw90(buf, image, atlas);
            } else if (deg == 270) {
                // CW 90 then 180 — one more quarter-turn past plain 180 for Pip wrist.
                blitCw90(buf, image, atlas);
                flipRect180(image, atlas);
            } else if (deg == -270) {
                blitCcw90(buf, image, atlas);
                flipRect180(image, atlas);
            } else if (deg == 180) {
                blitFlip180(buf, image, atlas);
            } else {
                blitCopy(buf, image, atlas);
            }
        } finally {
            buf.close();
        }
    }

    private void paintHud(NativeImage buf, int lw, int lh, HudState state, int scrollPhase) {
        if (!state.on) {
            drawCentered(buf, "RADIO OFF", lw / 2, lh / 2 - 3, 2, PIXEL_DIM);
            return;
        }

        int pad = Math.max(2, lw / 60);
        // Stock T2 atlas (~92px) uses scale 2; Pip stays at 1 so glyphs don't bloat when the 3D model is large.
        int scale = lw >= 200 ? 2 : 1;
        int line1 = pad + 2;
        drawMarquee(buf, state.scrollLine, pad, line1, lw - pad * 2, scale, PIXEL_LIME, scrollPhase);

        int barY = line1 + 8 * scale + pad;
        for (int i = 0; i < 5; i++) {
            int h = 2 + i;
            int color = i < state.bars ? BAR_COLORS[i] : BAR_OFF;
            int x = pad + i * (3 * scale + 2);
            fill(buf, x, barY + (6 - h) * scale, x + 3 * scale, barY + 6 * scale, color);
        }
        String station = fit(state.stationName, lw >= 200 ? 22 : 14);
        drawText(buf, station, pad + 5 * (3 * scale + 2), barY, scale, PIXEL_CYAN);

        int freqY = barY + 10 * scale;
        drawCentered(buf, state.frequency, lw / 2, freqY, Math.max(2, scale), PIXEL_GOLD);

        int boxY = Math.min(lh - 12 * scale, freqY + 12 * scale);
        int boxW = Math.max(textWidth(state.compass, scale) + 6 * scale, 28 * scale);
        int boxX = lw / 2 - boxW / 2;
        int border = state.facingTower ? PIXEL_LIME : PIXEL_RED;
        int fillCol = abgr(255, 40, 45, 18);
        fill(buf, boxX, boxY, boxX + boxW, boxY + 10 * scale, fillCol);
        hline(buf, boxX, boxX + boxW, boxY, border);
        hline(buf, boxX, boxX + boxW, boxY + 10 * scale - 1, border);
        vline(buf, boxX, boxY, boxY + 10 * scale, border);
        vline(buf, boxX + boxW - 1, boxY, boxY + 10 * scale, border);
        drawCentered(buf, state.compass, lw / 2, boxY + 2 * scale, scale, state.facingTower ? blinkLime() : PIXEL_RED);

        if (spec.showModeBadge()) {
            String mode = state.linked ? "LINK" : "LOC";
            drawText(buf, mode, lw - textWidth(mode, scale) - pad, boxY + 2 * scale, scale,
                state.linked ? PIXEL_DARK : PIXEL_DIM);
        }
    }

    /** Copy buffer 1:1 into atlas rect (no rotation). */
    private static void blitCopy(NativeImage src, NativeImage dest, LcdRect atlas) {
        int w = Math.min(src.getWidth(), atlas.width());
        int h = Math.min(src.getHeight(), atlas.height());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                dest.setPixelRGBA(atlas.x0() + x, atlas.y0() + y, src.getPixelRGBA(x, y));
            }
        }
    }

    /** 180° flip into atlas: {@code (lx, ly) → (w-1-lx, h-1-ly)}. */
    private static void blitFlip180(NativeImage src, NativeImage dest, LcdRect atlas) {
        int w = Math.min(src.getWidth(), atlas.width());
        int h = Math.min(src.getHeight(), atlas.height());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                dest.setPixelRGBA(atlas.x0() + (w - 1 - x), atlas.y0() + (h - 1 - y), src.getPixelRGBA(x, y));
            }
        }
    }

    /** In-place 180° flip of pixels already inside the atlas rect. */
    private static void flipRect180(NativeImage image, LcdRect atlas) {
        int w = atlas.width();
        int h = atlas.height();
        int[] copy = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                copy[y * w + x] = image.getPixelRGBA(atlas.x0() + x, atlas.y0() + y);
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setPixelRGBA(
                    atlas.x0() + (w - 1 - x),
                    atlas.y0() + (h - 1 - y),
                    copy[y * w + x]
                );
            }
        }
    }

    /**
     * Map logical (lx, ly) → atlas with 90° counter-clockwise:
     * {@code (lx, ly) → (ly, lw - 1 - lx)}.
     */
    private static void blitCcw90(NativeImage src, NativeImage dest, LcdRect atlas) {
        int lw = src.getWidth();
        int lh = src.getHeight();
        for (int ly = 0; ly < lh; ly++) {
            for (int lx = 0; lx < lw; lx++) {
                int ax = atlas.x0() + ly;
                int ay = atlas.y0() + (lw - 1 - lx);
                if (ax >= atlas.x0() && ax < atlas.x1() && ay >= atlas.y0() && ay < atlas.y1()) {
                    dest.setPixelRGBA(ax, ay, src.getPixelRGBA(lx, ly));
                }
            }
        }
    }

    /**
     * Map logical (lx, ly) → atlas with 90° clockwise:
     * {@code (lx, ly) → (lh - 1 - ly, lx)}.
     */
    private static void blitCw90(NativeImage src, NativeImage dest, LcdRect atlas) {
        int lw = src.getWidth();
        int lh = src.getHeight();
        for (int ly = 0; ly < lh; ly++) {
            for (int lx = 0; lx < lw; lx++) {
                int ax = atlas.x0() + (lh - 1 - ly);
                int ay = atlas.y0() + lx;
                if (ax >= atlas.x0() && ax < atlas.x1() && ay >= atlas.y0() && ay < atlas.y1()) {
                    dest.setPixelRGBA(ax, ay, src.getPixelRGBA(lx, ly));
                }
            }
        }
    }

    private HudState describe(ItemStack stack, Minecraft mc) {
        CompoundTag tag = stack.getTag();
        boolean on = tag == null || !tag.contains("DeadAirOn") || tag.getBoolean("DeadAirOn");
        ResourceLocation stationId = WalkieTalkieManager.getTunedStationId(stack);
        RadioStation station = stationId != null ? StationRegistry.getStation(stationId) : null;
        boolean linked = WalkieTalkieManager.hasLinkedTower(stack)
            && WalkieTalkieManager.getLinkMode(stack) == WalkieTalkieManager.LinkMode.LINKED;

        float signal = 0f;
        int bars = 0;
        String compass = "----";
        boolean facingTower = false;
        if (on && mc.player != null && mc.level != null) {
            float yaw = mc.player.getYRot();
            compass = WalkieTalkieOverlay.compassDirectionFromYaw(yaw);
            if (station != null) {
                try {
                    signal = WalkieTalkieOverlay.calculateSignalStrengthForStation(mc, station);
                    bars = SignalStrength.getSignalBars(signal);
                    facingTower = isFacingTower(mc, station, signal, yaw);
                } catch (Exception ignored) {
                }
            }
        }

        String stationName = station != null ? sanitize(station.getName()) : "NO STATION";
        String frequency = station != null
            ? String.format(Locale.ROOT, "%.1f MHZ", station.getFrequency())
            : "--.- MHZ";
        boolean listening = isListening(mc, station, signal, on);
        String scroll = buildScrollLine(mc, station, signal, on, listening);
        return new HudState(on, linked, bars, stationName, frequency, scroll, compass, facingTower, listening);
    }

    /**
     * True when radio is on, a station is tuned, and music/stream is actually playing
     * (same condition used for the NOW PLAYING scroll segment).
     */
    private static boolean isListening(Minecraft mc, RadioStation station, float signal, boolean on) {
        if (!on || station == null || signal < 0.05f) {
            return false;
        }
        return uk.co.extraspecialstudio.dead_air.audio.AudioManager.hasActiveSoundForStation(station.getId());
    }

    private static String buildScrollLine(Minecraft mc, RadioStation station, float signal, boolean on, boolean listening) {
        if (!on) return "RADIO OFF";
        String dayPart = "";
        String timePart = "";
        if (mc.level != null) {
            long dayTime = mc.level.getDayTime();
            int day = (int) (dayTime / 24000) + 1;
            int tickOfDay = (int) (dayTime % 24000);
            int hour = (tickOfDay / 1000 + 6) % 24;
            int minute = (tickOfDay % 1000) * 60 / 1000;
            dayPart = "DAY " + day;
            timePart = String.format(Locale.ROOT, "%02d:%02d", hour, minute);
        }
        if (station == null) {
            return dayPart + "  " + timePart + "  OPEN TUNING";
        }
        String songPart;
        if (listening) {
            ResourceLocation trackId = MusicStationManager.getCurrentTrackId(station.getId(), mc.player);
            String trackName = trackId != null
                ? sanitize(MusicStationManager.formatTrackDisplayName(trackId))
                : "?";
            songPart = "NOW PLAYING: " + trackName;
        } else if (signal < 0.05f) {
            songPart = "NO SIGNAL";
        } else {
            songPart = "TUNED";
        }
        return dayPart + "  " + timePart + "  " + songPart + "      ";
    }

    private static boolean isFacingTower(Minecraft mc, RadioStation station, float signal, float playerYaw) {
        if (station == null || signal < 0.05f || !KnownTowersClientCache.hasCachedTowers()) return false;
        Vec3 playerPos = mc.player.position();
        Vec3 towerPos = KnownTowersClientCache.getBestTowerPositionForStation(station.getId(), playerPos, mc.level);
        if (towerPos == null) return false;
        double dx = towerPos.x - playerPos.x;
        double dz = towerPos.z - playerPos.z;
        if (dx * dx + dz * dz <= 0.5) return false;
        while (playerYaw < 0) playerYaw += 360;
        while (playerYaw >= 360) playerYaw -= 360;
        float yawToTower = (float) Math.toDegrees(Math.atan2(-dx, dz));
        if (yawToTower < 0) yawToTower += 360;
        float diff = Math.abs(((playerYaw - yawToTower + 540) % 360) - 180);
        return diff <= 35f;
    }

    private static int blinkLime() {
        return (System.currentTimeMillis() / 400) % 2 == 0 ? PIXEL_LIME : abgr(255, 40, 140, 40);
    }

    private void drawMarquee(NativeImage image, String text, int x, int y, int width, int scale, int color, int phase) {
        if (text == null || text.isEmpty()) return;
        int full = textWidth(text, scale);
        if (full <= width) {
            drawText(image, text, x, y, scale, color);
            return;
        }
        String loop = text + "  *  ";
        int loopW = textWidth(loop, scale);
        int offset = (phase * 2) % Math.max(1, loopW);
        drawTextClipped(image, loop + loop, x - offset, y, scale, color, x, x + width);
    }

    private void drawTextClipped(NativeImage image, String text, int x, int y, int scale, int color, int clipX0, int clipX1) {
        int cursor = x;
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toUpperCase(text.charAt(i));
            int bits = GLYPHS.getOrDefault(c, GLYPHS.get('?'));
            for (int row = 0; row < 5; row++) {
                int rowBits = (bits >> ((4 - row) * 3)) & 0b111;
                for (int col = 0; col < 3; col++) {
                    if ((rowBits & (1 << (2 - col))) == 0) continue;
                    int px0 = cursor + col * scale;
                    int px1 = px0 + scale;
                    int py0 = y + row * scale;
                    int py1 = py0 + scale;
                    if (px1 <= clipX0 || px0 >= clipX1) continue;
                    fill(image, Math.max(px0, clipX0), py0, Math.min(px1, clipX1), py1, color);
                }
            }
            cursor += 4 * scale;
        }
    }

    private static void drawCentered(NativeImage image, String text, int cx, int y, int scale, int color) {
        int width = textWidth(text, scale);
        drawText(image, text, cx - width / 2, y, scale, color);
    }

    private static void drawText(NativeImage image, String text, int x, int y, int scale, int color) {
        int cursor = x;
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toUpperCase(text.charAt(i));
            int bits = GLYPHS.getOrDefault(c, GLYPHS.get('?'));
            for (int row = 0; row < 5; row++) {
                int rowBits = (bits >> ((4 - row) * 3)) & 0b111;
                for (int col = 0; col < 3; col++) {
                    if ((rowBits & (1 << (2 - col))) != 0) {
                        fill(image, cursor + col * scale, y + row * scale,
                            cursor + (col + 1) * scale, y + (row + 1) * scale, color);
                    }
                }
            }
            cursor += 4 * scale;
        }
    }

    private static int textWidth(String text, int scale) {
        return text.isEmpty() ? 0 : (text.length() * 4 - 1) * scale;
    }

    private static void fill(NativeImage image, int x0, int y0, int x1, int y1, int color) {
        int maxX = Math.min(image.getWidth(), x1);
        int maxY = Math.min(image.getHeight(), y1);
        for (int y = Math.max(0, y0); y < maxY; y++) {
            for (int x = Math.max(0, x0); x < maxX; x++) {
                image.setPixelRGBA(x, y, color);
            }
        }
    }

    private static void hline(NativeImage image, int x0, int x1, int y, int color) {
        fill(image, x0, y, x1, y + 1, color);
    }

    private static void vline(NativeImage image, int x, int y0, int y1, int color) {
        fill(image, x, y0, x + 1, y1, color);
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) return "NO STATION";
        return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 .:-]", "");
    }

    private static String fit(String value, int max) {
        if (value.length() <= max) return value;
        return value.substring(0, Math.max(1, max - 1)) + ".";
    }

    private static int abgr(int alpha, int red, int green, int blue) {
        return (alpha & 255) << 24 | (blue & 255) << 16 | (green & 255) << 8 | (red & 255);
    }

    private static Map<Character, Integer> createGlyphs() {
        Map<Character, Integer> out = new LinkedHashMap<>();
        put(out, ' ', "000", "000", "000", "000", "000");
        put(out, '?', "111", "001", "011", "000", "010");
        put(out, '.', "000", "000", "000", "000", "010");
        put(out, ':', "000", "010", "000", "010", "000");
        put(out, '-', "000", "000", "111", "000", "000");
        put(out, '*', "000", "101", "010", "101", "000");
        put(out, '0', "111", "101", "101", "101", "111");
        put(out, '1', "010", "110", "010", "010", "111");
        put(out, '2', "110", "001", "111", "100", "111");
        put(out, '3', "110", "001", "111", "001", "110");
        put(out, '4', "101", "101", "111", "001", "001");
        put(out, '5', "111", "100", "110", "001", "110");
        put(out, '6', "011", "100", "111", "101", "111");
        put(out, '7', "111", "001", "010", "010", "010");
        put(out, '8', "111", "101", "111", "101", "111");
        put(out, '9', "111", "101", "111", "001", "110");
        put(out, 'A', "010", "101", "111", "101", "101");
        put(out, 'B', "110", "101", "110", "101", "110");
        put(out, 'C', "011", "100", "100", "100", "011");
        put(out, 'D', "110", "101", "101", "101", "110");
        put(out, 'E', "111", "100", "110", "100", "111");
        put(out, 'F', "111", "100", "110", "100", "100");
        put(out, 'G', "011", "100", "101", "101", "011");
        put(out, 'H', "101", "101", "111", "101", "101");
        put(out, 'I', "111", "010", "010", "010", "111");
        put(out, 'J', "001", "001", "001", "101", "010");
        put(out, 'K', "101", "101", "110", "101", "101");
        put(out, 'L', "100", "100", "100", "100", "111");
        put(out, 'M', "101", "111", "111", "101", "101");
        put(out, 'N', "101", "111", "111", "111", "101");
        put(out, 'O', "010", "101", "101", "101", "010");
        put(out, 'P', "110", "101", "110", "100", "100");
        put(out, 'Q', "010", "101", "101", "111", "011");
        put(out, 'R', "110", "101", "110", "101", "101");
        put(out, 'S', "011", "100", "010", "001", "110");
        put(out, 'T', "111", "010", "010", "010", "010");
        put(out, 'U', "101", "101", "101", "101", "111");
        put(out, 'V', "101", "101", "101", "101", "010");
        put(out, 'W', "101", "101", "111", "111", "101");
        put(out, 'X', "101", "101", "010", "101", "101");
        put(out, 'Y', "101", "101", "010", "010", "010");
        put(out, 'Z', "111", "001", "010", "100", "111");
        return out;
    }

    private static void put(Map<Character, Integer> map, char c, String... rows) {
        int bits = 0;
        for (String row : rows) bits = bits << 3 | Integer.parseInt(row, 2);
        map.put(c, bits);
    }

    private record HudState(
        boolean on,
        boolean linked,
        int bars,
        String stationName,
        String frequency,
        String scrollLine,
        String compass,
        boolean facingTower,
        boolean listening
    ) {
        String fingerprint() {
            return on + "|" + linked + "|" + bars + "|" + stationName + "|" + frequency
                + "|" + scrollLine + "|" + compass + "|" + facingTower + "|" + listening;
        }
    }
}
