package uk.co.extraspecialstudio.dead_air.client;

import com.mojang.blaze3d.vertex.PoseStack;
import javax.annotation.Nullable;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.entity.DeadAirEntities;
import uk.co.extraspecialstudio.dead_air.entity.ListenerEntity;
import uk.co.extraspecialstudio.dead_air.entity.SignalAnchorEntity;
import uk.co.extraspecialstudio.dead_air.survivor.SurvivorEntity;

@EventBusSubscriber(modid = Dead_air.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DeadAirEntityRenderers {
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DeadAirEntities.THE_LISTENER.get(), ListenerRenderer::new);
        event.registerEntityRenderer(DeadAirEntities.SIGNAL_ANCHOR.get(), AnchorRenderer::new);
        event.registerEntityRenderer(DeadAirEntities.SURVIVOR.get(), SurvivorRenderer::new);
    }

    static final class AnchorRenderer extends GeoEntityRenderer<SignalAnchorEntity> {
        AnchorRenderer(EntityRendererProvider.Context context) {
            super(context, new DefaultedEntityGeoModel<>(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "signal_anchor")));
            shadowRadius = 0.5f;
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }
    }

    static final class SurvivorRenderer extends HumanoidMobRenderer<SurvivorEntity, PlayerModel<SurvivorEntity>> {
        SurvivorRenderer(EntityRendererProvider.Context context) {
            super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        }

        @Override
        public ResourceLocation getTextureLocation(SurvivorEntity entity) {
            return ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "textures/entity/survivor/" + entity.getProfession().id + ".png");
        }
    }

    static final class ListenerRenderer extends GeoEntityRenderer<ListenerEntity> {
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
            float a = pulseAlpha(entity, partialTick);
            shadowRadius = 0.25f + 0.75f * a;
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        }

        private static float pulseAlpha(ListenerEntity entity, float partialTick) {
            float t = (entity.tickCount + partialTick) * PULSE_SPEED;
            float wave = 0.5f + 0.5f * Mth.sin(t);
            wave = 0.85f * wave + 0.15f * (0.5f + 0.5f * Mth.sin(t * 1.7f + 1.1f));
            return Mth.lerp(wave, ALPHA_MIN, ALPHA_MAX);
        }
    }

    /** Looks at its target through the neck so the head keeps its own tilt and idle sway. */
    static final class ListenerModel extends DefaultedEntityGeoModel<ListenerEntity> {
        ListenerModel() {
            super(ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "the_listener"));
        }

        @Override
        public void setCustomAnimations(ListenerEntity animatable, long instanceId, AnimationState<ListenerEntity> state) {
            super.setCustomAnimations(animatable, instanceId, state);
            GeoBone neck = getAnimationProcessor().getBone("neck");
            EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
            if (neck == null || data == null) return;
            neck.setRotX(data.headPitch() * Mth.DEG_TO_RAD * 0.6f);
            neck.setRotY(data.netHeadYaw() * Mth.DEG_TO_RAD * 0.8f);
        }
    }

    private DeadAirEntityRenderers() {}
}
