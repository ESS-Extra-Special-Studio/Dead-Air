package uk.co.extraspecialstudio.dead_air.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.item.DeadAirItems;
import uk.co.extraspecialstudio.dead_air.net.KnownTowersSyncPacket;
import uk.co.extraspecialstudio.dead_air.tower.ApocalypseTowerDetector;
import uk.co.extraspecialstudio.dead_air.tower.KnownTowersClientCache;

import java.util.List;

/**
 * Draws installed upgrade items as small icons on the flat left plate of radio panels.
 * Uses existing item models (no new geometry) and only walks the synced tower cache.
 * Four equally spaced slots (top → bottom); unused slots stay empty for future upgrades.
 */
@Mod.EventBusSubscriber(modid = Dead_air.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RadioPanelUpgradeRenderer {
    private static final double MAX_RENDER_DIST_SQ = 48.0 * 48.0;
    private static final float ICON_SCALE = 0.26f;
    /** Four equal slots from near the top of the plate to near the bottom. */
    private static final int SLOT_COUNT = 4;
    private static final double SLOT_TOP_Y = 0.78;
    private static final double SLOT_BOTTOM_Y = 0.30;
    private static final double LEFT_OFFSET = 0.27;
    /** Panel sits near the back of the cell; front face is ~0.375 from center opposite facing. */
    private static final double FRONT_FROM_CENTER = 0.375;
    /**
     * Nudge icon centers just past the plate face. Item models are thin cards —
     * keep this small so they sit flush (0.06 floated; -0.02 buried).
     */
    private static final double OUTWARD = 0.018;

    private RadioPanelUpgradeRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;

        List<KnownTowersSyncPacket.TowerEntry> panels = KnownTowersClientCache.getPanelsWithUpgrades();
        if (panels.isEmpty()) return;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        ItemRenderer items = mc.getItemRenderer();
        Level level = mc.level;
        int packedLightFallback = LightTexture.FULL_BRIGHT;

        for (KnownTowersSyncPacket.TowerEntry entry : panels) {
            BlockPos panelPos = entry.panelPos;
            if (panelPos == null) continue;
            double dx = panelPos.getX() + 0.5 - cam.x;
            double dy = panelPos.getY() + 0.5 - cam.y;
            double dz = panelPos.getZ() + 0.5 - cam.z;
            if (dx * dx + dy * dy + dz * dz > MAX_RENDER_DIST_SQ) continue;
            if (!ApocalypseTowerDetector.isRadioPanel(level, panelPos)) continue;

            BlockState state = level.getBlockState(panelPos);
            Direction facing = getHorizontalFacing(state);
            if (facing == null) facing = Direction.NORTH;

            // Slot order top→bottom: Signal Upgrade, then Jukebox (room for 2 more later)
            ItemStack[] slots = new ItemStack[SLOT_COUNT];
            int next = 0;
            if (entry.signalBoostInstalled && next < SLOT_COUNT) {
                slots[next++] = new ItemStack(DeadAirItems.SIGNAL_UPGRADE.get());
            }
            if (entry.jukeboxModuleInstalled && next < SLOT_COUNT) {
                slots[next++] = new ItemStack(DeadAirItems.JUKEBOX_UPGRADE.get());
            }
            if (next == 0) continue;

            Direction left = facing.getClockWise();
            int light = packedLightFallback;
            try {
                light = net.minecraft.client.renderer.LevelRenderer.getLightColor(level, panelPos.relative(facing));
            } catch (Exception ignored) {
            }

            for (int i = 0; i < SLOT_COUNT; i++) {
                if (slots[i] == null || slots[i].isEmpty()) continue;
                double y = slotY(i);
                Vec3 at = iconWorldPos(panelPos, facing, left, y);
                renderIcon(pose, buffers, items, slots[i], at, cam, facing, light);
            }
        }
        buffers.endBatch();
    }

    /** Equally spaced Y for slot index 0 (top) … SLOT_COUNT-1 (bottom). */
    private static double slotY(int index) {
        if (SLOT_COUNT <= 1) return SLOT_TOP_Y;
        return SLOT_TOP_Y - index * (SLOT_TOP_Y - SLOT_BOTTOM_Y) / (SLOT_COUNT - 1);
    }

    private static Vec3 iconWorldPos(BlockPos pos, Direction facing, Direction left, double localY) {
        double cx = pos.getX() + 0.5 - facing.getStepX() * FRONT_FROM_CENTER + facing.getStepX() * OUTWARD;
        double cy = pos.getY() + localY;
        double cz = pos.getZ() + 0.5 - facing.getStepZ() * FRONT_FROM_CENTER + facing.getStepZ() * OUTWARD;
        cx += left.getStepX() * LEFT_OFFSET;
        cz += left.getStepZ() * LEFT_OFFSET;
        return new Vec3(cx, cy, cz);
    }

    private static void renderIcon(PoseStack pose, MultiBufferSource buffers, ItemRenderer items,
                                   ItemStack stack, Vec3 world, Vec3 cam, Direction facing, int light) {
        pose.pushPose();
        pose.translate(world.x - cam.x, world.y - cam.y, world.z - cam.z);
        // Face the panel front, then flip 180° so the item front (not PL Corp back) faces the player
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot() + 180f));
        pose.scale(ICON_SCALE, ICON_SCALE, ICON_SCALE);
        items.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
            pose, buffers, Minecraft.getInstance().level, 0);
        pose.popPose();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Direction getHorizontalFacing(BlockState state) {
        for (Property<?> prop : state.getProperties()) {
            if (prop instanceof DirectionProperty dirProp && prop.getName().equals("facing")) {
                Comparable<?> v = state.getValue((Property) dirProp);
                if (v instanceof Direction d && d.getAxis().isHorizontal()) return d;
            }
        }
        return null;
    }
}
