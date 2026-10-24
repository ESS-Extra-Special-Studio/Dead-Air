package uk.co.extraspecialstudio.dead_air.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import uk.co.extraspecialstudio.dead_air.client.DeadAirSounds;
import uk.co.extraspecialstudio.dead_air.damage.DeadAirDamageTypes;
import uk.co.extraspecialstudio.dead_air.encounter.ListenerEncounter;
import uk.co.extraspecialstudio.dead_air.net.DeadAirNet;
import uk.co.extraspecialstudio.dead_air.net.EncounterStatePacket;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Listener. Four attacks (Claw Rake, Feedback Scream, Signal Jump, Listening Silence) driven by a
 * server-side state machine. Only a player holding the Signal Breaker can hurt him, and in the first encounter
 * nothing can.
 */
public class ListenerEntity extends Monster implements GeoEntity {
    public enum Mode { WILD, FIRST, FINAL }

    private enum State { IDLE, CLAW, SCREAM, JUMP_OUT, JUMP_IN, SILENCE, PULSE }

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.listener.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.listener.walk");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.listener.death");
    private static final RawAnimation CLAW = RawAnimation.begin().thenPlay("animation.listener.claw");
    private static final RawAnimation SCREAM = RawAnimation.begin().thenPlay("animation.listener.scream");
    private static final RawAnimation JUMP_OUT = RawAnimation.begin().thenPlayAndHold("animation.listener.jump_out");
    private static final RawAnimation JUMP_IN = RawAnimation.begin().thenPlay("animation.listener.jump_in");
    private static final RawAnimation SILENCE = RawAnimation.begin().thenPlay("animation.listener.silence");

    private static final int CLAW_TICKS = 18;
    private static final int CLAW_HIT_TICK = 7;
    private static final int SCREAM_TICKS = 32;
    private static final int SCREAM_BURST_TICK = 20;
    private static final int JUMP_TICKS = 10;
    private static final int SILENCE_TICKS = 60;
    private static final int PULSE_TICKS = 12;
    private static final DustParticleOptions STATIC_DUST = new DustParticleOptions(new Vector3f(0.18f, 0.66f, 1.0f), 1.4f);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent = new ServerBossEvent(
        getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);

    private Mode mode = Mode.WILD;
    private UUID encounterId;
    private State state = State.IDLE;
    private int stateTicks;
    private int clawCooldown;
    private int screamCooldown = 140;
    private int jumpCooldown = 100;
    private int silenceCooldown = 260;
    private int outOfReachTicks;
    private boolean chainClaw;
    private LivingEntity pulseTarget;
    private Vec3 pulsePos;
    private final Map<UUID, Vec3> silenceLastPos = new HashMap<>();
    /** Set only by {@link #killFromCommand()} so admin remove bypasses Acoustic gate. */
    private boolean forceCommandKill;

    public ListenerEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 100;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 300.0)
            .add(Attributes.MOVEMENT_SPEED, 0.19)
            .add(Attributes.ATTACK_DAMAGE, 12.0)
            .add(Attributes.ATTACK_KNOCKBACK, 1.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.ARMOR, 8.0)
            .add(Attributes.FOLLOW_RANGE, 64.0)
            .add(Attributes.STEP_HEIGHT, 1.0);
    }

    public void setMode(Mode mode, UUID encounterId) {
        this.mode = mode;
        this.encounterId = encounterId;
        bossEvent.setColor(mode == Mode.FINAL ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PURPLE);
        // FIRST uses a countdown bar; FINAL keeps notched health segments.
        bossEvent.setOverlay(mode == Mode.FIRST
            ? BossEvent.BossBarOverlay.PROGRESS
            : BossEvent.BossBarOverlay.NOTCHED_10);
    }

    public Mode getMode() {
        return mode;
    }

    public ServerBossEvent getBossEvent() {
        return bossEvent;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new ChaseGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 24.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    /** WILD (egg/summon) engages creative for testing; story modes keep vanilla creative-ignore. */
    @Override
    public boolean canAttack(LivingEntity target) {
        if (mode == Mode.WILD && target instanceof Player p && !p.isSpectator()) {
            return true;
        }
        return super.canAttack(target);
    }

    private boolean phaseTwo() {
        return mode == Mode.FINAL && getHealth() < getMaxHealth() * 0.5f;
    }

    private int cd(int ticks) {
        return phaseTwo() ? (int) (ticks * 0.6f) : ticks;
    }

    // ---------- damage rules ----------

    /**
     * Only {@code dead_air:acoustic} damages FINAL/WILD. Void, {@code /kill}, and every other type
     * are rejected. FIRST stays story-unwinnable. Admin: {@link #killFromCommand()}.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        if (forceCommandKill) {
            return super.hurt(source, amount);
        }
        LivingEntity attacker = source.getEntity() instanceof LivingEntity le ? le : null;
        if (mode != Mode.FIRST && DeadAirDamageTypes.isAcoustic(source)) {
            boolean accepted = super.hurt(source, amount);
            if (accepted && attacker != null) {
                setLastHurtByMob(attacker);
                setTarget(attacker);
            }
            return accepted;
        }
        if (source.getEntity() instanceof Player && level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 2.5, getZ(), 12, 0.6, 1.2, 0.6, 0.1);
            playSound(DeadAirSounds.LISTENER_DEFLECT.get(), 1.0f, 0.9f + random.nextFloat() * 0.2f);
            if (attacker != null) {
                setLastHurtByMob(attacker);
                setTarget(attacker);
            }
        }
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (forceCommandKill) return false;
        if (mode != Mode.FIRST && DeadAirDamageTypes.isAcoustic(source)) return false;
        return true;
    }

    /**
     * Admin remove via {@code /deadair listener kill} / {@code purge}.
     * Does not go through {@link #die} â€” FINAL admin cleanup must not award victory loot
     * or {@code finalDefeated}. Clears boss bar, aborts any linked encounter, then discards.
     */
    public void killFromCommand() {
        if (level().isClientSide() || isRemoved()) return;
        bossEvent.removeAllPlayers();
        ListenerEncounter.abortForListener(this);
        if (!isRemoved()) {
            bossEvent.removeAllPlayers();
            discard();
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        bossEvent.removeAllPlayers();
        super.remove(reason);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    // ---------- tick ----------

    @Override
    public void tick() {
        super.tick();
        // Delay orphan cleanup so spawn-tick race cannot self-discard.
        if (!level().isClientSide && mode != Mode.WILD && tickCount > 40
            && !ListenerEncounter.isActive(encounterId)) {
            discard();
        }
    }

    /** Encounter Listeners must not vanish on Peaceful (Mob.checkDespawn â†’ discard). */
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return mode == Mode.WILD;
    }

    @Override
    public void checkDespawn() {
        if (mode != Mode.WILD) return;
        super.checkDespawn();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (mode != Mode.FIRST) bossEvent.setProgress(getHealth() / getMaxHealth());
        if (clawCooldown > 0) clawCooldown--;
        if (screamCooldown > 0) screamCooldown--;
        if (jumpCooldown > 0) jumpCooldown--;
        if (silenceCooldown > 0) silenceCooldown--;
        if (mode == Mode.WILD && (getTarget() == null || !getTarget().isAlive()) && tickCount % 20 == 0) {
            Player nearest = level().getNearestPlayer(this, getAttributeValue(Attributes.FOLLOW_RANGE));
            if (nearest != null && canAttack(nearest)) {
                setTarget(nearest);
            }
        }
        if (random.nextInt(3) == 0 && level() instanceof ServerLevel server) {
            server.sendParticles(STATIC_DUST, getX(), getY() + 3.0 + random.nextDouble() * 2.0, getZ(), 1, 0.5, 0.6, 0.5, 0);
        }
        tickAttacks();
    }

    private void tickAttacks() {
        LivingEntity target = getTarget();
        if (state != State.IDLE) {
            stateTicks++;
            switch (state) {
                case CLAW -> tickClaw(target);
                case SCREAM -> tickScream();
                case JUMP_OUT -> tickJumpOut(target);
                case JUMP_IN -> tickJumpIn(target);
                case SILENCE -> tickSilence();
                case PULSE -> tickPulse();
                default -> {}
            }
            return;
        }
        if (target == null || !target.isAlive()) return;
        double dist = distanceTo(target);
        outOfReachTicks = dist > 6 ? outOfReachTicks + 1 : 0;

        if (dist < 4.5 && clawCooldown <= 0) {
            begin(State.CLAW, "claw");
            playSound(DeadAirSounds.LISTENER_CLAW.get(), 1.2f, 1.0f);
        } else if (dist < 10 && screamCooldown <= 0) {
            begin(State.SCREAM, "scream");
            playSound(DeadAirSounds.LISTENER_SCREAM.get(), 3.0f, 1.0f);
        } else if ((dist > 14 || outOfReachTicks > 80) && jumpCooldown <= 0) {
            begin(State.JUMP_OUT, "jump_out");
            playSound(DeadAirSounds.LISTENER_JUMP_OUT.get(), 2.0f, 1.0f);
        } else if (dist < 24 && silenceCooldown <= 0) {
            begin(State.SILENCE, "silence");
            playSound(DeadAirSounds.LISTENER_SILENCE.get(), 2.5f, 1.0f);
            silenceLastPos.clear();
            for (Player p : nearbyPlayers(24)) {
                silenceLastPos.put(p.getUUID(), p.position());
                if (p instanceof ServerPlayer sp) DeadAirNet.sendTo(sp, new EncounterStatePacket(isEncounter(), SILENCE_TICKS, 0));
            }
        }
    }

    private boolean isEncounter() {
        return mode != Mode.WILD;
    }

    private void begin(State next, String anim) {
        state = next;
        stateTicks = 0;
        getNavigation().stop();
        triggerAnim("action", anim);
    }

    private void finish() {
        state = State.IDLE;
        stateTicks = 0;
    }

    private void tickClaw(LivingEntity target) {
        getNavigation().stop();
        if (target != null) getLookControl().setLookAt(target, 30f, 30f);
        if (stateTicks == CLAW_HIT_TICK && target != null && target.isAlive() && distanceTo(target) < 5.5) {
            Vec3 toTarget = target.position().subtract(position()).normalize();
            Vec3 facing = Vec3.directionFromRotation(0, getYRot());
            if (toTarget.dot(facing) > 0.2) {
                float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
                if (mode == Mode.FIRST) damage *= 0.6f;
                if (target.hurt(damageSources().mobAttack(this), damage)) {
                    target.knockback(1.2, getX() - target.getX(), getZ() - target.getZ());
                }
            }
        }
        if (stateTicks >= CLAW_TICKS) {
            clawCooldown = cd(30);
            finish();
        }
    }

    private void tickScream() {
        getNavigation().stop();
        if (stateTicks < SCREAM_BURST_TICK && level() instanceof ServerLevel server && stateTicks % 4 == 0) {
            server.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 4.4, getZ(), 1, 0.2, 0.2, 0.2, 0);
        }
        if (stateTicks == SCREAM_BURST_TICK) {
            if (level() instanceof ServerLevel server) {
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI * 2 / 24;
                    server.sendParticles(STATIC_DUST, getX() + Math.cos(a) * 4, getY() + 1.0, getZ() + Math.sin(a) * 4, 4, 0.8, 0.3, 0.8, 0);
                }
                server.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 2.0, getZ(), 1, 0, 0, 0, 0);
            }
            for (Player p : nearbyPlayers(8)) {
                float damage = mode == Mode.FIRST ? 5f : 9f;
                p.hurt(damageSources().sonicBoom(this), damage);
                Vec3 push = p.position().subtract(position()).normalize().scale(1.6);
                p.push(push.x, 0.5, push.z);
                p.hurtMarked = true;
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
                p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
                if (p instanceof ServerPlayer sp) {
                    DeadAirNet.sendTo(sp, new EncounterStatePacket(isEncounter(), 0, 12));
                    if (!isEncounter()) ListenerEncounter.jamBriefly(sp);
                }
            }
        }
        if (stateTicks >= SCREAM_TICKS) {
            screamCooldown = cd(220);
            finish();
        }
    }

    private void tickJumpOut(LivingEntity target) {
        getNavigation().stop();
        if (level() instanceof ServerLevel server) {
            server.sendParticles(STATIC_DUST, getX(), getY() + 2.0, getZ(), 10, 0.7, 1.8, 0.7, 0);
        }
        if (stateTicks >= JUMP_TICKS) {
            Vec3 dest = target != null ? findSpotBehind(target) : null;
            if (dest != null) {
                teleportTo(dest.x, dest.y, dest.z);
                if (target != null) lookAt(target, 360f, 360f);
            }
            playSound(DeadAirSounds.LISTENER_JUMP_IN.get(), 2.0f, 1.0f);
            begin(State.JUMP_IN, "jump_in");
        }
    }

    private void tickJumpIn(LivingEntity target) {
        getNavigation().stop();
        if (stateTicks == 1 && level() instanceof ServerLevel server) {
            server.sendParticles(STATIC_DUST, getX(), getY() + 2.0, getZ(), 30, 0.8, 2.0, 0.8, 0);
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.0, getZ(), 20, 1.0, 1.0, 1.0, 0.2);
        }
        if (stateTicks >= JUMP_TICKS) {
            jumpCooldown = cd(180);
            outOfReachTicks = 0;
            finish();
            if (phaseTwo() && target != null && distanceTo(target) < 5.5) {
                clawCooldown = 0;
                begin(State.CLAW, "claw");
                playSound(DeadAirSounds.LISTENER_CLAW.get(), 1.2f, 1.0f);
            }
        }
    }

    private Vec3 findSpotBehind(LivingEntity target) {
        Vec3 look = Vec3.directionFromRotation(0, target.getYRot());
        for (int attempt = 0; attempt < 8; attempt++) {
            double back = 3.0 + attempt * 0.5;
            double side = (attempt % 2 == 0 ? 1 : -1) * attempt * 0.6;
            Vec3 perp = new Vec3(-look.z, 0, look.x);
            Vec3 base = target.position().subtract(look.scale(back)).add(perp.scale(side));
            BlockPos column = BlockPos.containing(base);
            int y = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
            if (Math.abs(y - target.getY()) > 6) y = (int) target.getY();
            Vec3 spot = new Vec3(column.getX() + 0.5, y, column.getZ() + 0.5);
            AABB box = getDimensions(getPose()).makeBoundingBox(spot);
            if (level().noCollision(this, box) && !level().containsAnyLiquid(box)) {
                return spot;
            }
        }
        return null;
    }

    private void tickSilence() {
        getNavigation().stop();
        if (stateTicks > 4) {
            for (Player p : nearbyPlayers(24)) {
                Vec3 last = silenceLastPos.put(p.getUUID(), p.position());
                if (p.isCreative() || p.isSpectator()) continue;
                double moved = last == null ? 0 : p.position().subtract(last).horizontalDistance();
                boolean loud = p.isSprinting() || p.isUsingItem() || p.swinging
                    || (moved > 0.09 && !p.isCrouching());
                if (loud) {
                    pulseTarget = p;
                    pulsePos = position().add(0, 4.4, 0);
                    playSound(DeadAirSounds.LISTENER_PULSE.get(), 2.5f, 1.0f);
                    state = State.PULSE;
                    stateTicks = 0;
                    clearSilenceCue();
                    return;
                }
            }
        }
        if (stateTicks >= SILENCE_TICKS) {
            silenceCooldown = cd(420);
            clearSilenceCue();
            finish();
        }
    }

    private void clearSilenceCue() {
        for (Player p : nearbyPlayers(32)) {
            if (p instanceof ServerPlayer sp) DeadAirNet.sendTo(sp, new EncounterStatePacket(isEncounter(), 0, 0));
        }
    }

    private void tickPulse() {
        getNavigation().stop();
        LivingEntity t = pulseTarget;
        if (t == null || !t.isAlive() || pulsePos == null) {
            silenceCooldown = cd(420);
            finish();
            return;
        }
        Vec3 aim = t.position().add(0, t.getBbHeight() * 0.6, 0);
        double remaining = Math.max(1, PULSE_TICKS - stateTicks + 1);
        pulsePos = pulsePos.add(aim.subtract(pulsePos).scale(1.0 / remaining));
        if (level() instanceof ServerLevel server) {
            server.sendParticles(STATIC_DUST, pulsePos.x, pulsePos.y, pulsePos.z, 6, 0.15, 0.15, 0.15, 0);
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, pulsePos.x, pulsePos.y, pulsePos.z, 2, 0.1, 0.1, 0.1, 0.05);
        }
        if (stateTicks >= PULSE_TICKS) {
            t.hurt(damageSources().indirectMagic(this, this), mode == Mode.FIRST ? 6f : 10f);
            t.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            setTarget(t);
            silenceCooldown = cd(420);
            pulseTarget = null;
            finish();
        }
    }

    private List<Player> nearbyPlayers(double radius) {
        return level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(radius),
            p -> p.isAlive() && !p.isSpectator() && p.distanceTo(this) <= radius);
    }

    // ---------- chase ----------

    /** Walks toward the target between attacks; attacks themselves are started in {@link #tickAttacks()}. */
    private class ChaseGoal extends Goal {
        private int repath;

        ChaseGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) return;
            getLookControl().setLookAt(t, 30f, 30f);
            if (state != State.IDLE) return;
            if (distanceTo(t) < 3.0) {
                getNavigation().stop();
                return;
            }
            if (--repath <= 0) {
                repath = 8;
                getNavigation().moveTo(t, phaseTwo() ? 1.25 : 1.05);
            }
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    // ---------- lifecycle ----------

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide && mode != Mode.WILD) {
            ListenerEncounter.onListenerDied(this, source);
        }
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (deathTime >= 30 && !level().isClientSide && !isRemoved()) {
            if (level() instanceof ServerLevel server) {
                server.sendParticles(STATIC_DUST, getX(), getY() + 2.0, getZ(), 60, 1.0, 2.2, 1.0, 0);
            }
            remove(Entity.RemovalReason.KILLED);
        }
    }

    /** Dissolve into static without dying (encounter over). */
    public void dissolve() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(STATIC_DUST, getX(), getY() + 2.0, getZ(), 80, 1.0, 2.4, 1.0, 0);
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 2.0, getZ(), 30, 1.0, 2.0, 1.0, 0.2);
        }
        playSound(DeadAirSounds.LISTENER_JUMP_OUT.get(), 2.0f, 0.7f);
        bossEvent.removeAllPlayers();
        discard();
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(net.minecraft.network.chat.Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("ListenerMode", mode.name());
        if (encounterId != null) tag.putUUID("EncounterId", encounterId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        try {
            mode = Mode.valueOf(tag.getString("ListenerMode"));
        } catch (IllegalArgumentException e) {
            mode = Mode.WILD;
        }
        encounterId = tag.hasUUID("EncounterId") ? tag.getUUID("EncounterId") : null;
        if (hasCustomName()) bossEvent.setName(getDisplayName());
    }

    // ---------- sounds ----------

    @Override
    protected SoundEvent getAmbientSound() {
        return DeadAirSounds.LISTENER_AMBIENT.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 140;
    }

    @Override
    protected float getSoundVolume() {
        return 2.0f;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return DeadAirSounds.LISTENER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return DeadAirSounds.LISTENER_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(DeadAirSounds.LISTENER_STEP.get(), 0.9f, 0.9f + random.nextFloat() * 0.2f);
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    // ---------- animation ----------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 5, s -> {
            if (isDeadOrDying()) return s.setAndContinue(DEATH);
            return s.setAndContinue(s.isMoving() ? WALK : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "action", 2, s -> PlayState.STOP)
            .triggerableAnim("claw", CLAW)
            .triggerableAnim("scream", SCREAM)
            .triggerableAnim("jump_out", JUMP_OUT)
            .triggerableAnim("jump_in", JUMP_IN)
            .triggerableAnim("silence", SILENCE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
