package uk.co.extraspecialstudio.dead_air.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.compat.SurvivorGunCompat;

/**
 * Survivor hand items with render-thread LOD for modded guns (TaCZ / Point Blank).
 * <p>
 * Full Bedrock gun models stay available via {@link Config#survivorGunFullModels}.
 * Default path skips those BEWLRs and draws a cheap silhouette (or nothing when far).
 * Combat / equip / ammo are untouched — this is client render only.
 */
public final class SurvivorGunLodHandLayer<T extends LivingEntity, M extends EntityModel<T> & ArmedModel>
        extends ItemInHandLayer<T, M> {

    /** Within this range: dark silhouette proxy instead of full gun models. */
    private static final double PROXY_DISTANCE_SQ = 28.0 * 28.0;
    /** Beyond this: skip held-gun rendering entirely. */
    private static final double SKIP_DISTANCE_SQ = 40.0 * 40.0;

    private static final ResourceLocation PROXY_TEX =
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/black_concrete.png");

    public SurvivorGunLodHandLayer(RenderLayerParent<T, M> parent,
                                   net.minecraft.client.renderer.ItemInHandRenderer itemInHandRenderer) {
        super(parent, itemInHandRenderer);
    }

    @Override
    protected void renderArmWithItem(LivingEntity entity, ItemStack stack, ItemDisplayContext transform,
                                     HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer,
                                     int packedLight) {
        if (stack.isEmpty()) return;

        if (!SurvivorGunCompat.isGun(stack) || Config.survivorGunFullModels) {
            super.renderArmWithItem(entity, stack, transform, arm, poseStack, buffer, packedLight);
            return;
        }

        double distSq = cameraDistanceSq(entity);
        if (distSq > SKIP_DISTANCE_SQ) {
            return;
        }
        if (distSq > PROXY_DISTANCE_SQ) {
            return;
        }

        poseStack.pushPose();
        this.getParentModel().translateToHand(arm, poseStack);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        boolean left = arm == HumanoidArm.LEFT;
        poseStack.translate((left ? -1.0F : 1.0F) / 16.0F, 0.125F, -0.625F);
        renderGunSilhouette(poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    private static double cameraDistanceSq(LivingEntity entity) {
        var cam = Minecraft.getInstance().getCameraEntity();
        if (cam == null) {
            cam = Minecraft.getInstance().player;
        }
        return cam != null ? entity.distanceToSqr(cam) : 0.0;
    }

    /** Flat dark body + barrel — avoids TaCZ/PB custom item renderers entirely. */
    private static void renderGunSilhouette(PoseStack pose, MultiBufferSource buffer, int light) {
        VertexConsumer vc = buffer.getBuffer(RenderType.entitySolid(PROXY_TEX));
        PoseStack.Pose last = pose.last();
        // Receiver / stock
        putBox(last, vc, light, -0.06f, -0.045f, -0.12f, 0.06f, 0.045f, 0.28f);
        // Barrel
        putBox(last, vc, light, -0.03f, -0.025f, -0.48f, 0.03f, 0.025f, -0.12f);
        // Grip
        putBox(last, vc, light, -0.035f, -0.14f, 0.02f, 0.035f, -0.045f, 0.12f);
    }

    private static void putBox(PoseStack.Pose pose, VertexConsumer vc, int light,
                               float x0, float y0, float z0, float x1, float y1, float z1) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        int overlay = OverlayTexture.NO_OVERLAY;
        float u0 = 0f, v0 = 0f, u1 = 1f, v1 = 1f;
        // -Z
        quad(m, n, vc, light, overlay, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1, u0, v0, u1, v1);
        // +Z
        quad(m, n, vc, light, overlay, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1, 0, 0, 1, u0, v0, u1, v1);
        // -Y
        quad(m, n, vc, light, overlay, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0, u0, v0, u1, v1);
        // +Y
        quad(m, n, vc, light, overlay, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, u0, v0, u1, v1);
        // -X
        quad(m, n, vc, light, overlay, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0, u0, v0, u1, v1);
        // +X
        quad(m, n, vc, light, overlay, x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1, 1, 0, 0, u0, v0, u1, v1);
    }

    private static void quad(Matrix4f m, Matrix3f n, VertexConsumer vc, int light, int overlay,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float nx, float ny, float nz,
                             float u0, float v0, float u1, float v1) {
        vert(m, n, vc, light, overlay, x0, y0, z0, u0, v0, nx, ny, nz);
        vert(m, n, vc, light, overlay, x1, y1, z1, u1, v0, nx, ny, nz);
        vert(m, n, vc, light, overlay, x2, y2, z2, u1, v1, nx, ny, nz);
        vert(m, n, vc, light, overlay, x3, y3, z3, u0, v1, nx, ny, nz);
    }

    private static void vert(Matrix4f m, Matrix3f n, VertexConsumer vc, int light, int overlay,
                             float x, float y, float z, float u, float v,
                             float nx, float ny, float nz) {
        vc.vertex(m, x, y, z)
            .color(255, 255, 255, 255)
            .uv(u, v)
            .overlayCoords(overlay)
            .uv2(light)
            .normal(n, nx, ny, nz)
            .endVertex();
    }
}
