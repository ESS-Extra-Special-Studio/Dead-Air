package uk.co.extraspecialstudio.dead_air.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import javax.annotation.Nullable;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.entity.DeadAirEntities;
import uk.co.extraspecialstudio.dead_air.entity.ListenerEntity;
import uk.co.extraspecialstudio.dead_air.entity.SeatEntity;
import uk.co.extraspecialstudio.dead_air.entity.SignalAnchorEntity;
import uk.co.extraspecialstudio.dead_air.survivor.AuctionBrokerEntity;
import uk.co.extraspecialstudio.dead_air.survivor.SurvivorEntity;

@Mod.EventBusSubscriber(modid = Dead_air.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DeadAirEntityRenderers {
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DeadAirEntities.THE_LISTENER.get(), ListenerRenderer::new);
        event.registerEntityRenderer(DeadAirEntities.SIGNAL_ANCHOR.get(), AnchorRenderer::new);
        event.registerEntityRenderer(DeadAirEntities.SURVIVOR.get(), SurvivorRenderer::new);
        event.registerEntityRenderer(DeadAirEntities.AUCTION_BROKER.get(), AuctionBrokerRenderer::new);
        event.registerEntityRenderer(DeadAirEntities.SEAT.get(), SeatRenderer::new);
    }

    /** Invisible mount for chair sitting — no model, no shadow. */
    static final class SeatRenderer extends EntityRenderer<SeatEntity> {
        private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");

        SeatRenderer(EntityRendererProvider.Context context) {
            super(context);
            this.shadowRadius = 0f;
        }

        @Override
        public ResourceLocation getTextureLocation(SeatEntity entity) {
            return TEX;
        }
    }

    static final class AnchorRenderer extends GeoEntityRenderer<SignalAnchorEntity> {
        AnchorRenderer(EntityRendererProvider.Context context) {
            super(context, new DefaultedEntityGeoModel<>(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "signal_anchor")));
            // No ground shadow: flat base plate still z-fights a decal even when lifted.
            shadowRadius = 0f;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }
    }

    static final class SurvivorRenderer extends HumanoidMobRenderer<SurvivorEntity, PlayerModel<SurvivorEntity>> {
        private final PlayerModel<SurvivorEntity> wideModel;
        private final PlayerModel<SurvivorEntity> slimModel;

        SurvivorRenderer(EntityRendererProvider.Context context) {
            super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
            wideModel = this.model;
            slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
            this.layers.removeIf(layer -> layer instanceof ItemInHandLayer);
            this.addLayer(new SurvivorGunLodHandLayer<>(this, context.getItemInHandRenderer()));
        }

        @Override
        public void render(SurvivorEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                           MultiBufferSource buffer, int packedLight) {
            this.model = entity.isFemale() ? slimModel : wideModel;
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        }

        /**
         * Downed = supine on the ground. Do not use Pose.SWIMMING (crawl/swim twist).
         * Yaw, then tip onto the back, then pull the body down onto the block.
         */
        @Override
        protected void setupRotations(SurvivorEntity entity, PoseStack poseStack, float ageInTicks,
                                      float rotationYaw, float partialTicks) {
            if (entity.isDowned()) {
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - rotationYaw));
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
                poseStack.translate(0.0D, -1.0D, 0.125D);
                return;
            }
            super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks);
        }

        @Override
        public ResourceLocation getTextureLocation(SurvivorEntity entity) {
            return ResourceLocation.fromNamespaceAndPath(Dead_air.MODID,
                "textures/entity/survivor/" + entity.getSkinTextureKey() + ".png");
        }
    }

    /** Survivor-skinned auction broker (no villager robes). */
    static final class AuctionBrokerRenderer
        extends HumanoidMobRenderer<AuctionBrokerEntity, PlayerModel<AuctionBrokerEntity>> {
        private final PlayerModel<AuctionBrokerEntity> wideModel;
        private final PlayerModel<AuctionBrokerEntity> slimModel;

        AuctionBrokerRenderer(EntityRendererProvider.Context context) {
            super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
            wideModel = this.model;
            slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        }

        @Override
        public void render(AuctionBrokerEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                           MultiBufferSource buffer, int packedLight) {
            this.model = entity.isFemaleSkin() ? slimModel : wideModel;
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        }

        @Override
        public ResourceLocation getTextureLocation(AuctionBrokerEntity entity) {
            return ResourceLocation.fromNamespaceAndPath(Dead_air.MODID,
                "textures/entity/survivor/" + entity.getSkinTextureKey() + ".png");
        }
    }

    static final class ListenerRenderer extends GeoEntityRenderer<ListenerEntity> {
        /** Radians per tick for the primary fade sine (slow, ~3.5s full cycle). */
        private static final float PULSE_SPEED = 0.045f;
        private static final float ALPHA_MIN = 0.38f;
        private static final float ALPHA_MAX = 0.92f;

        ListenerRenderer(EntityRendererProvider.Context context) {
            super(context, new ListenerModel());
            shadowRadius = 1.0f;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }

        @Override
        public RenderType getRenderType(ListenerEntity animatable, ResourceLocation texture,
                                        @Nullable MultiBufferSource bufferSource, float partialTick) {
            return RenderType.entityTranslucent(texture);
        }

        @Override
        public Color getRenderColor(ListenerEntity animatable, float partialTick, int packedLight) {
            return Color.ofRGBA(1f, 1f, 1f, pulseAlpha(animatable, partialTick));
        }

        @Override
        public void render(ListenerEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                           MultiBufferSource bufferSource, int packedLight) {
            float emerge = entity.getSpawnEmergeScale(partialTick);
            if (emerge <= 0.001f) {
                // Crack / rift only — body stays hidden until the assemble boom.
                shadowRadius = 0f;
                return;
            }
            poseStack.pushPose();
            float scale = 0.15f + 0.85f * emerge;
            // Grow upward out of the rift instead of scaling from feet alone.
            poseStack.translate(0.0, entity.getBbHeight() * (1.0 - scale) * 0.35, 0.0);
            poseStack.scale(scale, scale, scale);
            float a = pulseAlpha(entity, partialTick);
            shadowRadius = (0.25f + 0.75f * a) * emerge;
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            poseStack.popPose();
        }

        private static float pulseAlpha(ListenerEntity entity, float partialTick) {
            float emerge = entity.getSpawnRenderAlpha(partialTick);
            float t = (entity.tickCount + partialTick) * PULSE_SPEED;
            // Smooth primary wave + slight second harmonic so it never feels metronomic.
            float wave = 0.5f + 0.5f * Mth.sin(t);
            wave = 0.85f * wave + 0.15f * (0.5f + 0.5f * Mth.sin(t * 1.7f + 1.1f));
            float pulse = Mth.lerp(wave, ALPHA_MIN, ALPHA_MAX);
            return pulse * emerge;
        }
    }

    /**
     * Vanilla head look only ({@code turnsHead=true}). No custom setCustomAnimations, no neck-chain
     * yaw accumulation — those caused multi-revolution spins.
     */
    static final class ListenerModel extends DefaultedEntityGeoModel<ListenerEntity> {
        ListenerModel() {
            super(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "the_listener"), true);
        }
    }

    private DeadAirEntityRenderers() {}
}
