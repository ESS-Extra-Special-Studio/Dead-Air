package uk.co.extraspecialstudio.dead_air.client;

import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.net.SurvivorPartySyncPacket;
import uk.co.extraspecialstudio.dead_air.survivor.SurvivorParty;
import uk.co.extraspecialstudio.extraspecial.esc.theme.EscFrameStyle;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscFonts;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscPanel;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscRect;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscText;
import uk.co.extraspecialstudio.extraspecial.esc.ui.EscUiStyle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Compact party roster: portrait + HP/XP bars. Squad orders live on the hold-key action wheel.
 * ESC chrome ({@link EscPanel} / {@link EscUiStyle#active()}) so hub themes apply.
 * Sized for a slim overlay (~half the old panel); Fira Code for crisp HUD text (not Monocraft).
 */
public final class SurvivorPartyHud {
    private static final int PANEL_W = 64;
    private static final int PAD = 3;
    private static final int PORTRAIT = 10;
    private static final int ROW_H = 18;
    private static final int TITLE_H = 9;
    private static final int HINT_H = 8;
    private static final int SLOT_DOT_H = 4;
    /** Crisp UI face — Fira Code; Monocraft (EscFonts.DEFAULT) reads chunky at HUD scale. */
    private static final ResourceLocation HUD_FONT = EscFonts.FIRA_CODE;
    private static final int HINT_VISIBLE_TICKS = 20 * 12;
    private static final int ORDER_FLASH_TICKS = 40;
    private static final int XP_FLASH_TICKS = 24;
    private static final int DOWNED_FLASH_TICKS = 50;

    private static List<SurvivorPartySyncPacket.Member> members = List.of();
    private static int syncedMaxParty = Config.maxPartySize;
    private static int syncedPartyCount = 0;
    private static int hintTicksLeft = HINT_VISIBLE_TICKS;
    private static int orderFlashTicks;
    private static int lastFlashedOrder = -1;

    private static final Int2IntOpenHashMap lastXp = new Int2IntOpenHashMap();
    private static final Int2IntOpenHashMap lastLevel = new Int2IntOpenHashMap();
    private static final Int2IntOpenHashMap xpFlash = new Int2IntOpenHashMap();
    private static final Int2BooleanOpenHashMap lastDowned = new Int2BooleanOpenHashMap();
    private static final Int2IntOpenHashMap downedFlash = new Int2IntOpenHashMap();

    private static EscRect lastPanel = new EscRect(0, 0, 0, 0);
    private static final List<EscRect> lastRowRects = new ArrayList<>();
    private static final List<Integer> lastRowEntityIds = new ArrayList<>();
    private static int hoveredRow = -1;

    private SurvivorPartyHud() {}

    public static void applySync(int maxPartySize, int partyCount, List<SurvivorPartySyncPacket.Member> list) {
        syncedMaxParty = Math.max(1, maxPartySize);
        syncedPartyCount = Math.max(0, partyCount);
        List<SurvivorPartySyncPacket.Member> next = list == null ? List.of() : List.copyOf(list);
        IntOpenHashSet alive = new IntOpenHashSet(next.size());
        for (SurvivorPartySyncPacket.Member m : next) {
            int id = m.entityId();
            alive.add(id);
            if (lastXp.containsKey(id)) {
                int prevXp = lastXp.get(id);
                int prevLv = lastLevel.getOrDefault(id, m.level());
                // Flash on XP gain or level-up (xp bar wrap). Ignore bare xp drops.
                if (m.xp() > prevXp || m.level() > prevLv) {
                    xpFlash.put(id, XP_FLASH_TICKS);
                }
            }
            lastXp.put(id, m.xp());
            lastLevel.put(id, m.level());
            // First sighting seeds state without alarming (join / chunk load).
            if (lastDowned.containsKey(id)) {
                boolean wasDown = lastDowned.get(id);
                if (m.downed() && !wasDown) {
                    downedFlash.put(id, DOWNED_FLASH_TICKS);
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.55f, 0.55f);
                    }
                }
            }
            lastDowned.put(id, m.downed());
        }
        lastXp.keySet().removeIf(id -> !alive.contains(id));
        lastLevel.keySet().removeIf(id -> !alive.contains(id));
        lastDowned.keySet().removeIf(id -> !alive.contains(id));
        xpFlash.keySet().removeIf(id -> !alive.contains(id));
        downedFlash.keySet().removeIf(id -> !alive.contains(id));
        members = next;
    }

    public static void clear() {
        members = List.of();
        syncedMaxParty = Config.maxPartySize;
        syncedPartyCount = 0;
        hintTicksLeft = HINT_VISIBLE_TICKS;
        orderFlashTicks = 0;
        lastFlashedOrder = -1;
        lastXp.clear();
        lastLevel.clear();
        xpFlash.clear();
        lastDowned.clear();
        downedFlash.clear();
        lastPanel = new EscRect(0, 0, 0, 0);
        lastRowRects.clear();
        lastRowEntityIds.clear();
        hoveredRow = -1;
        uk.co.extraspecialstudio.extraspecial.esc.ui.EscActionWheel.forceResetAll();
    }

    public static boolean hasParty() {
        return Config.partyHudEnabled && (syncedPartyCount > 0 || !members.isEmpty());
    }

    public static boolean hasLoadedMembers() {
        return !members.isEmpty();
    }

    public static int loadedMemberCount() {
        return members.size();
    }

    public static void notifyOrderApplied(int order) {
        lastFlashedOrder = order;
        orderFlashTicks = ORDER_FLASH_TICKS;
        hintTicksLeft = 0;
        // Optimistic local update so order letters don't wait for the next sync.
        // Skip REGROUP — partial CD skips mean letters would lie until server sync.
        if (members.isEmpty() || order == SurvivorParty.ORDER_REGROUP) return;
        List<SurvivorPartySyncPacket.Member> updated = new ArrayList<>(members.size());
        for (SurvivorPartySyncPacket.Member m : members) {
            if (m.downed()) {
                updated.add(m);
                continue;
            }
            updated.add(new SurvivorPartySyncPacket.Member(
                m.entityId(), m.name(), m.profession(), m.hp(), m.maxHp(), m.downed(),
                m.distBlocks(), m.gender(), m.skinVariant(), m.level(), m.xp(), m.xpToNext(),
                m.skillScavenge(), m.skillMedicine(), m.skillRadio(), m.skillCombat(),
                order));
        }
        members = List.copyOf(updated);
    }

    public static void clientTick() {
        if (hintTicksLeft > 0 && hasParty()) hintTicksLeft--;
        if (orderFlashTicks > 0) orderFlashTicks--;
        tickMap(xpFlash);
        tickMap(downedFlash);
        updateHoverRow();
    }

    private static void tickMap(Int2IntOpenHashMap map) {
        if (map.isEmpty()) return;
        var it = map.int2IntEntrySet().fastIterator();
        while (it.hasNext()) {
            var e = it.next();
            int v = e.getIntValue() - 1;
            if (v <= 0) it.remove();
            else e.setValue(v);
        }
    }

    private static void updateHoverRow() {
        hoveredRow = -1;
        Minecraft mc = Minecraft.getInstance();
        // Grabbed mouse coords don't map to HUD — only tip/hover when cursor is free.
        if (mc.player == null || mc.screen != null || lastRowRects.isEmpty()) return;
        if (mc.mouseHandler.isMouseGrabbed()) return;
        int guiW = mc.getWindow().getGuiScaledWidth();
        int screenW = mc.getWindow().getScreenWidth();
        int screenH = mc.getWindow().getScreenHeight();
        if (screenW <= 0 || screenH <= 0) return;
        double mx = mc.mouseHandler.xpos() * guiW / (double) screenW;
        double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / (double) screenH;
        for (int i = 0; i < lastRowRects.size(); i++) {
            EscRect r = lastRowRects.get(i);
            if (mx >= r.x() && mx < r.right() && my >= r.y() && my < r.bottom()) {
                hoveredRow = i;
                return;
            }
        }
    }

    /** Left-click a party row ? face that survivor (cursor must be free, not grabbed). */
    public static boolean click(double mouseX, double mouseY) {
        if (!hasLoadedMembers() || SurvivorPartyWheelBootstrap.isOpen()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return false;
        // With a grabbed mouse, xpos/ypos are not HUD-aligned — would false-hit rows on attack.
        if (mc.mouseHandler.isMouseGrabbed()) return false;
        for (int i = 0; i < lastRowRects.size(); i++) {
            EscRect r = lastRowRects.get(i);
            if (mouseX >= r.x() && mouseX < r.right() && mouseY >= r.y() && mouseY < r.bottom()) {
                int entityId = lastRowEntityIds.get(i);
                Entity e = mc.level.getEntity(entityId);
                if (e != null) {
                    faceEntity(mc, e);
                    mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3f, 1.4f);
                    return true;
                }
            }
        }
        return false;
    }

    private static void faceEntity(Minecraft mc, Entity e) {
        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 target = e.getEyePosition(1f);
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        mc.player.setYRot((float) (Mth.atan2(dz, dx) * (180f / Math.PI)) - 90f);
        mc.player.setXRot((float) (-(Mth.atan2(dy, horiz) * (180f / Math.PI))));
        mc.player.yRotO = mc.player.getYRot();
        mc.player.xRotO = mc.player.getXRot();
    }

    public static void render(GuiGraphics g, int screenWidth, int screenHeight) {
        if (!hasParty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (SurvivorPartyWheelBootstrap.isOpen()) return;

        EscUiStyle style = EscUiStyle.active();
        boolean showHint = hintTicksLeft > 0 && !members.isEmpty();
        int detailRows = members.isEmpty() && syncedPartyCount > 0 ? 1 : members.size();
        boolean showSlots = syncedMaxParty > 0 && !(members.isEmpty() && syncedPartyCount > 0);
        int panelH = PAD + TITLE_H + detailRows * ROW_H
            + (showSlots ? SLOT_DOT_H : 0)
            + (showHint ? HINT_H : 2) + PAD;
        EscRect panel = overlayBox(screenWidth, screenHeight, PANEL_W, panelH);
        lastPanel = panel;

        // Sprint soft-dim — less HUD glare while moving fast
        float panelAlphaBoost = mc.player.isSprinting() ? -0.18f : 0f;
        if (panelAlphaBoost < 0) {
            EscPanel.fill(g, panel, EscPanel.withOpacity(style.panelFillColor(),
                Math.max(0.35f, style.panelOpacity() + panelAlphaBoost)));
            EscPanel.renderFrame(g, panel, style.panelBorderColor(), 1, EscFrameStyle.SQUARE);
        } else {
            EscPanel.renderPanel(g, panel, style);
        }

        if (orderFlashTicks > 0) {
            float pulse = orderFlashTicks / (float) ORDER_FLASH_TICKS;
            int flash = EscPanel.withOpacity(style.focusBorderColor(), 0.25f + 0.45f * pulse);
            EscPanel.renderFrame(g, panel, flash, 2, EscFrameStyle.SQUARE);
        }

        int titleCount = Math.max(syncedPartyCount, members.size());
        String title = Component.translatable("hud.dead_air.party.title", titleCount, syncedMaxParty).getString();
        EscText.drawString(g, mc.font, title, panel.x() + PAD, panel.y() + PAD,
            opaque(style.textColorTitle()), false, HUD_FONT);

        String orderSummary = currentOrderSummary();
        if (orderSummary != null) {
            int ow = mc.font.width(orderSummary);
            EscText.drawString(g, mc.font, orderSummary, panel.right() - PAD - ow, panel.y() + PAD,
                opaque(style.accentColor()), false, HUD_FONT);
        }

        lastRowRects.clear();
        lastRowEntityIds.clear();
        int cy = panel.y() + PAD + TITLE_H;
        if (members.isEmpty() && syncedPartyCount > 0) {
            EscText.drawString(g, mc.font,
                Component.translatable("hud.dead_air.party.unloaded").getString(),
                panel.x() + PAD, cy + 6, opaque(style.textColorBody()), false, HUD_FONT);
            cy += ROW_H;
        } else {
            List<SurvivorPartySyncPacket.Member> rows = sortedMembers();
            for (int i = 0; i < rows.size(); i++) {
                SurvivorPartySyncPacket.Member m = rows.get(i);
                EscRect rowRect = new EscRect(panel.x() + PAD, cy, panel.width() - PAD * 2, ROW_H - 1);
                lastRowRects.add(rowRect);
                lastRowEntityIds.add(m.entityId());
                if (i == hoveredRow) {
                    EscPanel.fill(g, rowRect, EscPanel.withOpacity(style.accentColor(), 0.12f));
                }
                renderRow(g, mc, style, panel.x() + PAD, cy, panel.width() - PAD * 2, m);
                cy += ROW_H;
            }
        }

        if (showSlots) {
            drawCapacityDots(g, style, panel.x() + PAD, cy, syncedMaxParty, titleCount);
            cy += SLOT_DOT_H;
        }

        if (showHint) {
            String keyName = KeyBindings.PARTY_ORDERS.getTranslatedKeyMessage().getString();
            String hint = Component.translatable("hud.dead_air.party.orders_hint_key", keyName).getString();
            EscText.drawString(g, mc.font, hint, panel.x() + PAD, panel.bottom() - PAD - 8,
                opaque(style.mutedColor()), false, HUD_FONT);
        } else if (hoveredRow >= 0 && hoveredRow < lastRowEntityIds.size()) {
            SurvivorPartySyncPacket.Member hm = memberById(lastRowEntityIds.get(hoveredRow));
            if (hm != null) {
                String tip = Component.translatable("hud.dead_air.party.profession." + hm.profession()).getString()
                    + " · " + Component.translatable("hud.dead_air.party.click_face").getString();
                EscText.drawString(g, mc.font, truncate(mc, tip, panel.width() - PAD * 2),
                    panel.x() + PAD, panel.bottom() - PAD - 8,
                    opaque(style.mutedColor()), false, HUD_FONT);
            }
        }
    }

    private static SurvivorPartySyncPacket.Member memberById(int id) {
        for (SurvivorPartySyncPacket.Member m : members) {
            if (m.entityId() == id) return m;
        }
        return null;
    }

    private static void drawCapacityDots(GuiGraphics g, EscUiStyle style, int x, int y, int max, int filled) {
        int gap = 3;
        int size = 3;
        for (int i = 0; i < max; i++) {
            int dx = x + i * (size + gap);
            int color = i < filled ? opaque(style.accentColor()) : 0xFF3A3A48;
            EscPanel.fill(g, new EscRect(dx, y + 1, size, size), color);
        }
    }

    private static List<SurvivorPartySyncPacket.Member> sortedMembers() {
        if (members.size() <= 1) return members;
        List<SurvivorPartySyncPacket.Member> sorted = new ArrayList<>(members);
        sorted.sort(Comparator
            .comparing(SurvivorPartySyncPacket.Member::downed).reversed()
            .thenComparingDouble(m -> m.maxHp() <= 0 ? 1.0 : m.hp() / (double) m.maxHp())
            .thenComparing(SurvivorPartySyncPacket.Member::name, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    private static String currentOrderSummary() {
        if (members.isEmpty()) return null;
        int order = members.get(0).partyOrder();
        for (SurvivorPartySyncPacket.Member m : members) {
            if (m.partyOrder() != order) {
                return Component.translatable("hud.dead_air.party.order_mixed").getString();
            }
        }
        return orderLetter(order);
    }

    /** REGROUP is stored as FOLLOW server-side — still treat as a flash match. */
    private static boolean matchesFlashedOrder(int partyOrder) {
        if (partyOrder == lastFlashedOrder) return true;
        return lastFlashedOrder == SurvivorParty.ORDER_REGROUP
            && partyOrder == SurvivorParty.ORDER_FOLLOW;
    }

    private static void renderRow(GuiGraphics g, Minecraft mc, EscUiStyle style,
                                  int x, int y, int rowW, SurvivorPartySyncPacket.Member m) {
        int accent = m.downed() ? 0xFFAA3333 : professionColor(m.profession(), style);
        float hpPct = m.maxHp() <= 0 ? 0 : Math.min(1f, m.hp() / m.maxHp());
        boolean lowHp = !m.downed() && hpPct < 0.35f;
        int downFlash = downedFlash.getOrDefault(m.entityId(), 0);

        EscRect frame = new EscRect(x, y + 1, PORTRAIT + 2, PORTRAIT + 2);
        EscPanel.fill(g, frame, 0xFF1A1A22);
        int borderCol = accent;
        if (downFlash > 0) {
            float pulse = downFlash / (float) DOWNED_FLASH_TICKS;
            borderCol = EscPanel.withOpacity(0xFFFF3030, 0.5f + 0.5f * pulse);
            EscPanel.fill(g, new EscRect(x - 1, y, rowW + 2, ROW_H - 1),
                EscPanel.withOpacity(0xFFFF2020, 0.12f * pulse));
        } else if (lowHp) {
            float pulse = 0.55f + 0.45f * Mth.sin((mc.player.tickCount + m.entityId()) * 0.35f);
            borderCol = EscPanel.withOpacity(style.focusBorderColor(), pulse);
        }
        if (orderFlashTicks > 0 && matchesFlashedOrder(m.partyOrder())) {
            borderCol = opaque(style.focusBorderColor());
        }
        EscPanel.renderFrame(g, frame, borderCol, (lowHp || downFlash > 0) ? 2 : 1, EscFrameStyle.SQUARE);
        blitPortrait(g, m, x + 1, y + 2, m.downed());

        String prof = professionLetter(m.profession());
        if (prof != null) {
            int bx = x + PORTRAIT - 2;
            int by = y + PORTRAIT - 1;
            EscPanel.fill(g, new EscRect(bx, by, 5, 5), 0xEE101018);
            EscText.drawString(g, mc.font, prof, bx, by - 1, accent, false, HUD_FONT);
        }

        int textX = x + PORTRAIT + 4;
        int barsW = Math.max(16, rowW - (PORTRAIT + 4));

        String orderChip = orderLetter(m.partyOrder());
        String distStr = m.distBlocks() + "m";
        String lv = "L" + Math.max(1, m.level());
        int rightMetaW = mc.font.width(lv) + 2 + mc.font.width(distStr);
        int nameColor = m.downed() ? 0xFFFF6666 : opaque(style.textColorBody());
        String namePrefix = orderChip != null ? orderChip + " " : "";
        String name = truncate(mc, namePrefix + m.name(), Math.max(12, barsW - rightMetaW - 2));
        EscText.drawString(g, mc.font, name, textX, y + 1, nameColor, false, HUD_FONT);
        EscText.drawString(g, mc.font, lv, textX + barsW - rightMetaW, y + 1,
            opaque(style.accentColor()), false, HUD_FONT);
        EscText.drawString(g, mc.font, distStr, textX + barsW - mc.font.width(distStr), y + 1,
            distanceColor(m.distBlocks(), style), false, HUD_FONT);

        int barX = textX;
        int hpY = y + 11;
        int hpColor = m.downed() ? 0xFFAA3333
            : (hpPct < 0.35f ? 0xFFFF5544 : opaque(style.accentColor()));
        drawBar(g, barX, hpY, barsW, 2, hpPct, 0xFF303040, hpColor);

        int xpNeed = Math.max(1, m.xpToNext());
        float xpPct = Math.min(1f, m.xp() / (float) xpNeed);
        int xpFlashLeft = xpFlash.getOrDefault(m.entityId(), 0);
        int xpFill = xpFlashLeft > 0 ? 0xFF88FFAA : opaque(style.secondaryColor());
        drawBar(g, barX, hpY + 3, barsW, 1, xpPct, 0xFF282830, xpFill);
    }

    private static int distanceColor(int blocks, EscUiStyle style) {
        if (blocks >= 999) return 0xFFFF6666;
        if (blocks <= 12) return 0xFF88FFAA;
        if (blocks <= 32) return opaque(style.textColorBody());
        if (blocks <= 64) return 0xFFE8C060;
        return 0xFFFF8866;
    }

    private static void blitPortrait(GuiGraphics g, SurvivorPartySyncPacket.Member m, int x, int y, boolean dim) {
        ResourceLocation tex = ResourceLocation.fromNamespaceAndPath(Dead_air.MODID,
            "textures/entity/survivor/" + m.skinTextureKey() + ".png");
        g.blit(tex, x, y, PORTRAIT, PORTRAIT, 8f, 8f, 8, 8, 64, 64);
        g.blit(tex, x, y, PORTRAIT, PORTRAIT, 40f, 8f, 8, 8, 64, 64);
        if (dim) {
            EscPanel.fill(g, new EscRect(x, y, PORTRAIT, PORTRAIT), 0x88000000);
        }
    }

    private static void drawBar(GuiGraphics g, int x, int y, int w, int h, float pct, int track, int fill) {
        EscPanel.fill(g, new EscRect(x, y, w, h), track);
        int fillW = Math.max(0, (int) (w * Math.max(0f, Math.min(1f, pct))));
        if (fillW > 0) {
            EscPanel.fill(g, new EscRect(x, y, fillW, h), fill);
        }
    }

    private static String truncate(Minecraft mc, String s, int maxPx) {
        if (s == null) return "";
        if (mc.font.width(s) <= maxPx) return s;
        String cut = s;
        while (cut.length() > 1 && mc.font.width(cut + "…") > maxPx) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }

    private static String orderLetter(int order) {
        return switch (order) {
            case SurvivorParty.ORDER_FOLLOW -> "F";
            case SurvivorParty.ORDER_DEFEND -> "D";
            case SurvivorParty.ORDER_ATTACK -> "A";
            case SurvivorParty.ORDER_RETREAT -> "R";
            case SurvivorParty.ORDER_REGROUP -> "G";
            default -> null;
        };
    }

    private static String professionLetter(String profession) {
        if (profession == null) return null;
        return switch (profession) {
            case "medic" -> "M";
            case "radio_operator" -> "R";
            case "pilot" -> "P";
            case "military" -> "T";
            case "civilian" -> "C";
            case "scavenger" -> "S";
            default -> "?";
        };
    }

    private static int professionColor(String profession, EscUiStyle style) {
        if (profession == null) return opaque(style.textColorBody());
        return switch (profession) {
            case "medic" -> 0xFF88FFAA;
            case "radio_operator" -> opaque(style.accentColor());
            case "pilot" -> 0xFF88CCFF;
            case "military" -> 0xFFC8B070;
            case "civilian" -> 0xFFD0C8B0;
            case "scavenger" -> opaque(style.secondaryColor());
            default -> opaque(style.secondaryColor());
        };
    }

    private static int opaque(int rgbOrArgb) {
        return (rgbOrArgb & 0xFF000000) == 0 ? (0xFF000000 | (rgbOrArgb & 0xFFFFFF)) : rgbOrArgb;
    }

    private static EscRect overlayBox(int screenWidth, int screenHeight, int bgWidth, int bgHeight) {
        int pad = 4;
        String corner = Config.partyHudCorner != null ? Config.partyHudCorner : "top_left";
        int ox = Config.partyHudOffsetX;
        int oy = Config.partyHudOffsetY;
        int x;
        int y;
        switch (corner.toLowerCase()) {
            case "top_right" -> {
                x = Math.round(screenWidth - bgWidth - pad - ox);
                y = pad + oy;
            }
            case "bottom_left" -> {
                x = pad + ox;
                // Positive offsetY moves inward (up), matching top corners.
                y = Math.round(screenHeight - bgHeight - pad - oy);
            }
            case "bottom_right" -> {
                x = Math.round(screenWidth - bgWidth - pad - ox);
                y = Math.round(screenHeight - bgHeight - pad - oy);
            }
            default -> {
                x = pad + ox;
                y = pad + oy;
            }
        }
        return new EscRect(x, y, bgWidth, bgHeight);
    }
}
