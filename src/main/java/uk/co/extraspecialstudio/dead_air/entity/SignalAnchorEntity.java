package uk.co.extraspecialstudio.dead_air.entity;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import uk.co.extraspecialstudio.dead_air.sound.DeadAirSounds;
import uk.co.extraspecialstudio.dead_air.encounter.ListenerEncounter;

import java.util.UUID;

/** Glowing antenna shard that keeps The Listener tethered. Break all of them to end the first encounter. */
public class SignalAnchorEntity extends Mob implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.signal_anchor.idle");
    private static final RawAnimation HIT = RawAnimation.begin().thenPlay("animation.signal_anchor.hit");
    private static final RawAnimation BREAK = RawAnimation.begin().thenPlayAndHold("animation.signal_anchor.break");
    private static final DustParticleOptions GLOW = new DustParticleOptions(new Vector3f(0.18f, 0.66f, 1.0f), 1.0f);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID encounterId;

    public SignalAnchorEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        setNoAi(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 16.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    public void setEncounter(UUID id) {
        this.encounterId = id;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!(source.getEntity() instanceof Player) && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        boolean hit = super.hurt(source, Math.min(amount, 5f));
        if (hit && isAlive()) triggerAnim("hit", "hit");
        return hit;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (encounterId != null && !ListenerEncounter.isActive(encounterId)) {
            discard();
            return;
        }
        if (tickCount % 60 == 1) playSound(DeadAirSounds.ANCHOR_HUM.get(), 0.8f, 1.0f);
        if (tickCount % 5 == 0 && level() instanceof ServerLevel server) {
            server.sendParticles(GLOW, getX(), getY() + 1.4, getZ(), 1, 0.25, 0.5, 0.25, 0);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.0, getZ(), 30, 0.4, 0.8, 0.4, 0.3);
            server.sendParticles(GLOW, getX(), getY() + 1.0, getZ(), 30, 0.5, 0.9, 0.5, 0);
            triggerAnim("hit", "break");
            if (encounterId != null) ListenerEncounter.onAnchorBroken(encounterId);
        }
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (deathTime >= 12 && !level().isClientSide && !isRemoved()) remove(RemovalReason.KILLED);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return DeadAirSounds.LISTENER_DEFLECT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return DeadAirSounds.ANCHOR_BREAK.get();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public void setDeltaMovement(Vec3 motion) {
        super.setDeltaMovement(new Vec3(0, Math.min(0, motion.y), 0));
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (encounterId != null) tag.putUUID("EncounterId", encounterId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        encounterId = tag.hasUUID("EncounterId") ? tag.getUUID("EncounterId") : null;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, s -> s.setAndContinue(IDLE)));
        controllers.add(new AnimationController<>(this, "hit", 0, s -> PlayState.STOP)
            .triggerableAnim("hit", HIT)
            .triggerableAnim("break", BREAK));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
