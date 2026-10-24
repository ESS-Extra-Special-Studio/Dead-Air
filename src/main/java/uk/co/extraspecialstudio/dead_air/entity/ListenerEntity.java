package uk.co.extraspecialstudio.dead_air.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.damage.DeadAirDamageTypes;
import uk.co.extraspecialstudio.dead_air.item.DeadAirItems;
import uk.co.extraspecialstudio.dead_air.sound.DeadAirSounds;
import uk.co.extraspecialstudio.dead_air.encounter.ListenerEncounter;
import uk.co.extraspecialstudio.dead_air.net.DeadAirNet;
import uk.co.extraspecialstudio.dead_air.net.EncounterStatePacket;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Listener. Four attacks (Claw Rake, Feedback Scream, Signal Jump, Listening Silence) driven by a
 * server-side state machine. Only Acoustic damage from the Signal Breaker can hurt him (all modes).
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
    /**
     * Spawn assemble length — matches {@code listener/spawn.ogg} (~3.2s).
     * Crack → expanding static rift → emerge → seal.
     */
    public static final int SPAWN_INTRO_TICKS = 64;
    private static final EntityDataAccessor<Integer> DATA_SPAWN_INTRO =
        SynchedEntityData.defineId(ListenerEntity.class, EntityDataSerializers.INT);
    /** Pale grey / near-white dust â€” TV static on the floating spectre body (not cyan). */
    private static final DustParticleOptions STATIC_DUST = new DustParticleOptions(new Vector3f(0.78f, 0.78f, 0.82f), 1.4f);
    /** Pale grey / near-white dust â€” TV static under the floating spectre. */
    private static final DustParticleOptions STATIC_UNDER_DARK = new DustParticleOptions(new Vector3f(0.45f, 0.45f, 0.48f), 1.6f);
    private static final DustParticleOptions STATIC_UNDER_MID = new DustParticleOptions(new Vector3f(0.72f, 0.72f, 0.75f), 1.9f);
    private static final DustParticleOptions STATIC_UNDER_BRIGHT = new DustParticleOptions(new Vector3f(0.92f, 0.92f, 0.95f), 2.1f);

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
    /** After a quiet Listening Silence, briefly struggle to re-lock crouched players. */
    private int locateBlindTicks;
    /**
     * Players who landed Acoustic during Listening Silence / locate-blind — keep aggro on them
     * even if they stay crouched (quiet crouch clears would otherwise wipe the hit).
     * A set so co-op revealers do not overwrite each other.
     */
    private final Set<UUID> stealthBrokenBy = new HashSet<>();
    /** Set only by {@link #killFromCommand()} so admin remove bypasses Acoustic gate. */
    private boolean forceCommandKill;
    /** One-shot assemble cue when first added to a level. */
    private boolean spawnSoundPlayed;
    /**
     * Creepy notice line ({@code listener.agro}) — once per life when first locking a player,
     * so the “I hear you” VO does not spam every re-acquire.
     */
    private boolean agroAnnounced;

    public ListenerEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setMaxUpStep(1.0f);
        xpReward = 100;
        setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_SPAWN_INTRO, 0);
    }

    /** True while the atmospheric rift / emerge cue is playing (egg, summon, encounter). */
    public boolean isAssembling() {
        return entityData.get(DATA_SPAWN_INTRO) > 0;
    }

    public int getSpawnIntroTicksRemaining() {
        return entityData.get(DATA_SPAWN_INTRO);
    }

    /** 0 = just started, 1 = finished. */
    public float getSpawnIntroProgress(float partialTick) {
        int left = getSpawnIntroTicksRemaining();
        if (left <= 0) return 1f;
        float elapsed = SPAWN_INTRO_TICKS - left + partialTick;
        return Mth.clamp(elapsed / SPAWN_INTRO_TICKS, 0f, 1f);
    }

    /**
     * Model scale during assemble: invisible through crack/rift, then rises as he emerges
     * (aligned with the loud middle of the spawn sting).
     */
    public float getSpawnEmergeScale(float partialTick) {
        if (!isAssembling()) return 1f;
        float p = getSpawnIntroProgress(partialTick);
        // Crack 0–0.18, rift swell 0.18–0.40, emerge 0.34–0.68, seal 0.68–1.0
        if (p < 0.34f) return 0f;
        if (p < 0.68f) return Mth.clamp((p - 0.34f) / 0.34f, 0f, 1f);
        return 1f;
    }

    public float getSpawnRenderAlpha(float partialTick) {
        float scale = getSpawnEmergeScale(partialTick);
        if (scale <= 0f) return 0f;
        // Soft fade-in with the scale so the cloak does not pop opaque.
        return Mth.clamp(scale * scale, 0f, 1f);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 300.0)
            // Slow walker — gap close is Signal Jump, not a sprint.
            .add(Attributes.MOVEMENT_SPEED, 0.19)
            .add(Attributes.ATTACK_DAMAGE, 12.0)
            .add(Attributes.ATTACK_KNOCKBACK, 1.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.ARMOR, 8.0)
            .add(Attributes.FOLLOW_RANGE, 64.0);
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
        // No idle wander during Listening Silence / locate-blind — that walked him back onto crouchers.
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.55) {
            @Override
            public boolean canUse() {
                if (isAssembling() || state != State.IDLE || locateBlindTicks > 0) return false;
                return super.canUse();
            }
        });
        // Look-at only — no RandomLookAround (idle thrash). Skip during Listening Silence / locate-blind
        // so crouch stealth is not instantly undone by vanilla stare-lock.
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 24.0f) {
            @Override
            public boolean canUse() {
                if (isAssembling() || state == State.SILENCE || locateBlindTicks > 0) return false;
                return super.canUse();
            }
        });
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                if (isAssembling()) return false;
                // Don't snap onto a quiet croucher during Listening Silence or the post-silence blind
                // (unless they revealed themselves with Acoustic).
                if (isStealthWindow()) {
                    LivingEntity hurtBy = getLastHurtByMob();
                    if (hurtBy instanceof Player p && isStealthHidden(p)) return false;
                }
                return super.canUse();
            }
        });
        // Quiet crouchers are not re-acquired during Listening Silence or the post-silence blind.
        // mustSee=false: spectre should lock players through brief line-of-sight breaks.
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 5, false, false, living -> {
            if (isAssembling()) return false;
            if (!(living instanceof Player p) || p.isSpectator()) return false;
            return !isStealthHidden(p);
        }));
    }

    /**
     * Vanilla {@link Mob#canAttack} rejects creative ({@code invulnerable}) players, so egg /summon
     * WILD bosses stand around while you hit them in creative. WILD still engages creative for testing;
     * story encounters keep the normal creative-ignore rule.
     */
    @Override
    public boolean canAttack(LivingEntity target) {
        if (mode == Mode.WILD && target instanceof Player p && !p.isSpectator()) {
            return true;
        }
        return super.canAttack(target);
    }

    /** Vanilla-ish pitch clamp â€” no wild crane that reads as thrash. */
    @Override
    public int getMaxHeadXRot() {
        return 40;
    }

    private boolean phaseTwo() {
        return mode == Mode.FINAL && getHealth() < getMaxHealth() * 0.5f;
    }

    private int cd(int ticks) {
        return phaseTwo() ? (int) (ticks * 0.6f) : ticks;
    }

    // ---------- damage rules ----------

    /**
     * Only {@code dead_air:acoustic} (Signal Breaker) can damage The Listener.
     * FIRST encounters accept Acoustic hits but cannot be killed (survive via timer / anchors).
     * FINAL / WILD can be slain with Acoustic. Vanilla / void / {@code /kill} are rejected.
     * Admin: {@link #killFromCommand()}.
     * <p>
     * Signal Breaker melee is converted here — Listener never reaches {@code LivingHurtEvent}
     * on a deflect, so the global acoustic converter cannot run.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        if (forceCommandKill) {
            return super.hurt(source, amount);
        }
        LivingEntity attacker = source.getEntity() instanceof LivingEntity le ? le : null;
        // Radio Silence: any player hit, or an owned survivor swinging for them, counts.
        if (encounterId != null && attacker != null) {
            if (attacker instanceof Player
                || (attacker instanceof uk.co.extraspecialstudio.dead_air.survivor.SurvivorEntity s
                    && s.getOwnerUuid() != null)) {
                uk.co.extraspecialstudio.dead_air.encounter.ListenerEncounter.markPlayerAttacked(encounterId);
            }
        }
        DamageSource deal = source;
        if (!DeadAirDamageTypes.isAcoustic(deal) && isSignalBreakerMelee(deal)) {
            deal = DeadAirDamageTypes.acoustic(level(), attacker);
        }
        if (DeadAirDamageTypes.isAcoustic(deal)) {
            float dealAmount = amount;
            // FIRST is unwinnable by killing him — hits still land for feedback.
            if (mode == Mode.FIRST) {
                float maxDeal = Math.max(0f, getHealth() - 1f);
                if (maxDeal <= 0f) {
                    if (level() instanceof ServerLevel server) {
                        server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 2.5, getZ(),
                            8, 0.5, 1.0, 0.5, 0.08);
                    }
                    playSound(DeadAirSounds.LISTENER_DEFLECT.get(), 0.85f, 1.15f + random.nextFloat() * 0.1f);
                    aggroFromHit(attacker, true);
                    // Not a successful hurt — returning true burned Signal Breaker durability every swing.
                    return false;
                }
                dealAmount = Math.min(dealAmount, maxDeal);
            }
            boolean accepted = super.hurt(deal, dealAmount);
            if (accepted) {
                aggroFromHit(attacker, true);
                // Breaker hit FX (global acoustic converter skips Listener).
                uk.co.extraspecialstudio.dead_air.events.SignalBreakerAcousticEvents.playHitSounds(this);
            }
            return accepted;
        }
        if (source.getEntity() instanceof Player && level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 2.5, getZ(), 12, 0.6, 1.2, 0.6, 0.1);
            playSound(DeadAirSounds.LISTENER_DEFLECT.get(), 1.0f, 0.9f + random.nextFloat() * 0.2f);
            // Deflects must not snap onto quiet crouchers during Listening Silence / locate-blind.
            aggroFromHit(attacker, false);
        }
        return false;
    }

    /** Acoustic hits break stealth; plain deflects respect Listening Silence / locate-blind. */
    private void aggroFromHit(LivingEntity attacker, boolean breakStealth) {
        if (attacker == null) return;
        setLastHurtByMob(attacker);
        if (breakStealth && attacker instanceof Player) {
            stealthBrokenBy.add(attacker.getUUID());
            setTarget(attacker);
            return;
        }
        if (isStealthHidden(attacker instanceof Player p ? p : null)) {
            return;
        }
        setTarget(attacker);
    }

    /** True during Listening Silence or the post-silence locate-blind. */
    public boolean isStealthWindow() {
        return state == State.SILENCE || locateBlindTicks > 0;
    }

    /**
     * Quiet croucher during stealth window who has not revealed themselves with Acoustic.
     * Used by AI clears / target goals / encounter target keeper.
     */
    public boolean isStealthHidden(Player p) {
        if (p == null || !isStealthWindow() || !isQuietCrouch(p)) return false;
        return !stealthBrokenBy.contains(p.getUUID());
    }

    /** Remaining Listening Silence cue ticks for late-joining clients (0 if not silencing). */
    public int listeningSilenceCueTicksRemaining() {
        if (state != State.SILENCE) return 0;
        return Math.max(0, SILENCE_TICKS - stateTicks);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (forceCommandKill) return false;
        // Rift assemble — no damage until he fully emerges.
        if (isAssembling()) return true;
        if (DeadAirDamageTypes.isAcoustic(source) || isSignalBreakerMelee(source)) return false;
        // Block void /kill / everything else — including BYPASSES_INVULNERABILITY types.
        return true;
    }

    private static boolean isSignalBreakerMelee(DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity attacker)) return false;
        return attacker.getMainHandItem().is(DeadAirItems.SIGNAL_BREAKER.get());
    }

    @Nullable
    public UUID getEncounterId() {
        return encounterId;
    }

    /**
     * Admin remove via {@code /deadair listener kill} / {@code purge}.
     * Does <b>not</b> go through {@link #die} — FINAL admin cleanup must not award victory loot
     * or {@code finalDefeated}. Clears boss bar, aborts any linked encounter, then discards.
     */
    public void killFromCommand() {
        if (level().isClientSide || isRemoved()) return;
        bossEvent.removeAllPlayers();
        // Abort encounter first (anchors, fog/jam, participant packets) — ABORTED, not DEFEATED.
        ListenerEncounter.abortForListener(this);
        if (!isRemoved()) {
            bossEvent.removeAllPlayers();
            discard();
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        // Any removal path (admin, dissolve, death tick) must drop sticky boss bars on dedicated.
        bossEvent.removeAllPlayers();
        super.remove(reason);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return !isAssembling() && super.isPickable();
    }

    @Override
    public boolean isAttackable() {
        return !isAssembling() && super.isAttackable();
    }

    @Override
    public boolean canBeCollidedWith() {
        return !isAssembling() && super.canBeCollidedWith();
    }

    @Override
    public boolean canBeLeashed(Player player) {
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
        tickSpawnIntro();
        // Underfoot signal dust — skip until he has emerged from the rift.
        if (!isAssembling() || getSpawnEmergeScale(0f) > 0.35f) {
            spawnUnderfootSignal();
        }
        if (!level().isClientSide) {
            maybeBeginSpawnAssemble();
            maybeAnnounceAgro();
        }
        // Delay orphan cleanup a few ticks so spawn-tick race with encounter registry cannot self-discard.
        // Use killFromCommand so boss bars / linked encounter state cannot stick on dedicated.
        if (!level().isClientSide && mode != Mode.WILD && tickCount > 40
            && !ListenerEncounter.isActive(encounterId)) {
            killFromCommand();
        }
    }

    /** Fresh egg / summon / encounter spawn — sound + synced rift intro. Loaded entities skip. */
    private void maybeBeginSpawnAssemble() {
        if (spawnSoundPlayed) return;
        spawnSoundPlayed = true;
        entityData.set(DATA_SPAWN_INTRO, SPAWN_INTRO_TICKS);
        setDeltaMovement(Vec3.ZERO);
        getNavigation().stop();
        setTarget(null);
        // Broadcast from the block so egg /summon hears it with the rift VFX.
        level().playSound(null, getX(), getY(), getZ(),
            DeadAirSounds.LISTENER_SPAWN.get(), SoundSource.HOSTILE, 3.0f, 1.0f);
    }

    private void tickSpawnIntro() {
        int left = getSpawnIntroTicksRemaining();
        if (left <= 0) return;
        setDeltaMovement(Vec3.ZERO);
        getNavigation().stop();
        if (!level().isClientSide) {
            entityData.set(DATA_SPAWN_INTRO, left - 1);
            spawnAssembleParticles(SPAWN_INTRO_TICKS - left);
        } else {
            // Client mirrors particles for responsiveness (server also sends for MP).
            spawnAssembleParticlesClient(SPAWN_INTRO_TICKS - left);
        }
    }

    /** Server: crack → expanding swirl → emerge burst → seal. {@code elapsed} 0‥SPAWN_INTRO_TICKS. */
    private void spawnAssembleParticles(int elapsed) {
        if (!(level() instanceof ServerLevel server)) return;
        double cx = getX();
        double cy = getY() + 2.4;
        double cz = getZ();
        float p = elapsed / (float) SPAWN_INTRO_TICKS;

        if (elapsed < 12) {
            // Atmospheric crack a few blocks up — vertical spark / static seam.
            for (int i = 0; i < 3; i++) {
                double y = cy - 0.8 + random.nextDouble() * 1.6;
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, y, cz, 1, 0.02, 0.15, 0.02, 0.01);
                server.sendParticles(STATIC_DUST, cx + (random.nextDouble() - 0.5) * 0.15, y, cz + (random.nextDouble() - 0.5) * 0.15,
                    1, 0.02, 0.08, 0.02, 0);
            }
        }

        if (p >= 0.12f && p < 0.72f) {
            // Swirling void expands toward Listener silhouette size (~1.1 block radius).
            float swell = Mth.clamp((p - 0.12f) / 0.45f, 0f, 1f);
            double radius = 0.15 + swell * 1.05;
            int rings = 10 + (int) (swell * 14);
            double spin = elapsed * 0.35;
            for (int i = 0; i < rings; i++) {
                double a = spin + i * (Math.PI * 2.0 / rings);
                double y = getY() + 0.4 + (i / (double) rings) * getBbHeight() * swell;
                double x = cx + Math.cos(a) * radius;
                double z = cz + Math.sin(a) * radius;
                server.sendParticles(STATIC_DUST, x, y, z, 1, 0.02, 0.02, 0.02, 0);
                if (i % 3 == 0) {
                    server.sendParticles(ParticleTypes.SMOKE, x, y, z, 1, 0.01, 0.01, 0.01, 0.0);
                }
            }
            // Darker core so the rift reads as a hole, not a cloud.
            if (elapsed % 2 == 0) {
                server.sendParticles(STATIC_UNDER_DARK, cx, getY() + getBbHeight() * 0.5 * swell, cz,
                    2, radius * 0.25, getBbHeight() * 0.2 * swell, radius * 0.25, 0);
            }
        }

        if (elapsed == 28 || elapsed == 29) {
            // Emerge “boom” — bright static burst as the body appears.
            server.sendParticles(STATIC_UNDER_BRIGHT, cx, getY() + 1.5, cz, 40, 0.7, 1.4, 0.7, 0.02);
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, getY() + 1.8, cz, 28, 0.6, 1.2, 0.6, 0.15);
            server.sendParticles(ParticleTypes.SONIC_BOOM, cx, getY() + 1.2, cz, 1, 0, 0, 0, 0);
        }

        if (p >= 0.68f) {
            // Rift seals — particles fall inward toward the torso.
            float seal = Mth.clamp((p - 0.68f) / 0.32f, 0f, 1f);
            double radius = 1.1 * (1.0 - seal);
            if (radius > 0.08) {
                for (int i = 0; i < 8; i++) {
                    double a = random.nextDouble() * Math.PI * 2;
                    double x = cx + Math.cos(a) * radius;
                    double z = cz + Math.sin(a) * radius;
                    double y = getY() + 0.5 + random.nextDouble() * getBbHeight();
                    server.sendParticles(STATIC_DUST, x, y, z, 1, -Math.cos(a) * 0.08, 0.02, -Math.sin(a) * 0.08, 0.01);
                }
            }
        }
    }

    /** Lightweight client-only dust so the egg spawner sees the rift without waiting on packets. */
    private void spawnAssembleParticlesClient(int elapsed) {
        float p = elapsed / (float) SPAWN_INTRO_TICKS;
        double cx = getX();
        double cy = getY() + 2.4;
        double cz = getZ();
        if (elapsed < 12) {
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, cx, cy + (random.nextDouble() - 0.5), cz, 0, 0.02, 0);
        }
        if (p >= 0.12f && p < 0.72f) {
            float swell = Mth.clamp((p - 0.12f) / 0.45f, 0f, 1f);
            double radius = 0.15 + swell * 1.05;
            double a = elapsed * 0.35 + random.nextDouble() * Math.PI * 2;
            level().addParticle(STATIC_DUST,
                cx + Math.cos(a) * radius,
                getY() + random.nextDouble() * getBbHeight() * Math.max(0.2, swell),
                cz + Math.sin(a) * radius,
                0, 0.01, 0);
        }
    }

    /** First player lock-on: the special “I hear you” growl. */
    private void maybeAnnounceAgro() {
        if (agroAnnounced) return;
        LivingEntity t = getTarget();
        if (!(t instanceof Player)) return;
        agroAnnounced = true;
        playSound(DeadAirSounds.LISTENER_AGRO.get(), 2.8f, 1.0f);
    }

    /**
     * Floating-spectre cue: grey/white TV static under the cloak (ash + white_ash).
     * No custom yaw/look logic here â€” pure vanilla Mob head/body.
     */
    private void spawnUnderfootSignal() {
        if (tickCount % 2 != 0) return;
        double underY = getY() + 0.85 + random.nextDouble() * 0.7;
        if (level().isClientSide) {
            for (int i = 0; i < 8; i++) {
                double x = getX() + (random.nextDouble() - 0.5) * 0.95;
                double y = underY + random.nextDouble() * 0.3;
                double z = getZ() + (random.nextDouble() - 0.5) * 0.95;
                if (random.nextBoolean()) {
                    level().addParticle(ParticleTypes.WHITE_ASH, x, y, z,
                        (random.nextDouble() - 0.5) * 0.04, 0.01 + random.nextDouble() * 0.05, (random.nextDouble() - 0.5) * 0.04);
                } else {
                    level().addParticle(ParticleTypes.ASH, x, y, z,
                        (random.nextDouble() - 0.5) * 0.04, 0.01 + random.nextDouble() * 0.05, (random.nextDouble() - 0.5) * 0.04);
                }
            }
            if (random.nextInt(4) == 0) {
                level().addParticle(STATIC_UNDER_BRIGHT,
                    getX() + (random.nextDouble() - 0.5) * 0.35,
                    underY, getZ() + (random.nextDouble() - 0.5) * 0.35,
                    0.0, 0.015, 0.0);
            }
            return;
        }
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.WHITE_ASH, getX(), underY, getZ(), 10, 0.45, 0.2, 0.45, 0.0);
            server.sendParticles(ParticleTypes.ASH, getX(), underY, getZ(), 8, 0.4, 0.18, 0.4, 0.0);
        }
    }

    /**
     * Never despawn in Peaceful â€” spawn-egg /summon WILD bosses were vanishing instantly
     * because Monster.shouldDespawnInPeaceful defaults true and checkDespawn discards them.
     */
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public void checkDespawn() {
        // Boss is persistent; never distance/peaceful despawn (egg + encounter summons).
        if (!isPersistenceRequired()) {
            setPersistenceRequired();
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (isAssembling()) {
            setTarget(null);
            getNavigation().stop();
            return;
        }
        if (mode != Mode.FIRST) bossEvent.setProgress(getHealth() / getMaxHealth());
        if (clawCooldown > 0) clawCooldown--;
        if (screamCooldown > 0) screamCooldown--;
        if (jumpCooldown > 0) jumpCooldown--;
        if (silenceCooldown > 0) silenceCooldown--;
        if (!isStealthWindow()) {
            stealthBrokenBy.clear();
        }
        if (locateBlindTicks > 0) {
            locateBlindTicks--;
            LivingEntity t = getTarget();
            // Stay lost on quiet crouchers during the blind window (not Acoustic revealers).
            if (t instanceof Player p && isStealthHidden(p)) {
                setTarget(null);
            }
        }
        // Egg /summon: if target goals missed (creative → survival swap, etc.), re-lock nearby players.
        if (mode == Mode.WILD && (getTarget() == null || !getTarget().isAlive()) && tickCount % 20 == 0) {
            Player nearest = level().getNearestPlayer(this, getAttributeValue(Attributes.FOLLOW_RANGE));
            if (nearest != null && canAttack(nearest) && !isStealthHidden(nearest)) {
                setTarget(nearest);
            }
        }
        if (level() instanceof ServerLevel server) {
            if (random.nextInt(3) == 0) {
                server.sendParticles(STATIC_DUST, getX(), getY() + 3.0 + random.nextDouble() * 2.0, getZ(), 1, 0.5, 0.6, 0.5, 0);
            }
        }
        tickAttacks();
    }

    private void tickAttacks() {
        LivingEntity target = getTarget();
        if (state != State.IDLE) {
            stateTicks++;
            // if/else â€” not switch â€” avoids synthetic SwitchMap class (NoClassDefFoundError ListenerEntity$1).
            if (state == State.CLAW) tickClaw(target);
            else if (state == State.SCREAM) tickScream();
            else if (state == State.JUMP_OUT) tickJumpOut(target);
            else if (state == State.JUMP_IN) tickJumpIn(target);
            else if (state == State.SILENCE) tickSilence();
            else if (state == State.PULSE) tickPulse();
            return;
        }
        if (target == null || !target.isAlive()) return;
        double dist = distanceTo(target);
        outOfReachTicks = dist > 6 ? outOfReachTicks + 1 : 0;

        // Listening Silence interrupts mid-fight so crouch stealth can actually occur (claw/scream
        // used to starve this forever while you stayed in melee).
        if (silenceCooldown <= 0 && dist < 28) {
            begin(State.SILENCE, "silence");
            playSound(DeadAirSounds.LISTENER_SILENCE.get(), 2.5f, 1.0f);
            silenceLastPos.clear();
            // Already crouching when the cue starts — drop lock immediately.
            if (target instanceof Player startP && isQuietCrouch(startP)
                && !stealthBrokenBy.contains(startP.getUUID())) {
                setTarget(null);
            }
            for (Player p : nearbyPlayers(28)) {
                silenceLastPos.put(p.getUUID(), p.position());
                if (p instanceof ServerPlayer sp) {
                    // WILD: brief fog for the cue. Encounter: fog only for participants (bystanders cue-only).
                    boolean fog = isEncounter() ? ListenerEncounter.isParticipant(sp) : true;
                    DeadAirNet.sendTo(sp, new EncounterStatePacket(fog, SILENCE_TICKS, 0));
                }
            }
        } else if (dist < 4.5 && clawCooldown <= 0) {
            begin(State.CLAW, "claw");
            playSound(DeadAirSounds.LISTENER_CLAW.get(), 1.2f, 1.0f);
        } else if (dist < 10 && screamCooldown <= 0) {
            begin(State.SCREAM, "scream");
            playSound(DeadAirSounds.LISTENER_SCREAM.get(), 3.0f, 1.0f);
        } else if (locateBlindTicks <= 0
            && !(target instanceof Player jp && isQuietCrouch(jp) && isStealthWindow())
            && (dist > 14 || outOfReachTicks > 80) && jumpCooldown <= 0) {
            // No Signal Jump while struggling to re-lock after a quiet Listening Silence,
            // and never jump onto a quiet croucher mid-stealth window.
            begin(State.JUMP_OUT, "jump_out");
            playSound(DeadAirSounds.LISTENER_JUMP_OUT.get(), 2.0f, 1.0f);
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
        if (target != null) getLookControl().setLookAt(target, 8f, 8f);
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
                    boolean fog = isEncounter() && ListenerEncounter.isParticipant(sp);
                    DeadAirNet.sendTo(sp, new EncounterStatePacket(fog, 0, 12));
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
            // Abort onto a quiet croucher — Listening Silence payoff is "he lost you", not a free TP.
            if (target instanceof Player jp && isQuietCrouch(jp)
                && (isStealthWindow() || locateBlindTicks > 0)
                && !stealthBrokenBy.contains(jp.getUUID())) {
                setTarget(null);
                jumpCooldown = cd(100);
                playSound(DeadAirSounds.LISTENER_DEFLECT.get(), 1.2f, 0.6f);
                finish();
                return;
            }
            Vec3 dest = target != null ? findSpotBehind(target) : null;
            if (dest != null) {
                teleportTo(dest.x, dest.y, dest.z);
                if (target != null) lookAt(target, 10f, 10f);
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
            for (Player p : nearbyPlayers(28)) {
                Vec3 prev = silenceLastPos.get(p.getUUID());
                Vec3 last = silenceLastPos.put(p.getUUID(), p.position());
                // Already-participant who walks into the 28m bubble mid-cue — sync the HUD.
                if (last == null && p instanceof ServerPlayer sp) {
                    boolean fog = isEncounter() ? ListenerEncounter.isParticipant(sp) : true;
                    DeadAirNet.sendTo(sp, new EncounterStatePacket(fog, listeningSilenceCueTicksRemaining(), 0));
                }
                if (p.isCreative() || p.isSpectator()) continue;
                // Quiet crouch never feeds the pulse — even if a shove moved them a bit.
                if (isQuietCrouch(p) && !stealthBrokenBy.contains(p.getUUID())) continue;
                if (isLoudDuringSilence(p, prev != null ? prev : last)) {
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
        // Drop lock immediately on quiet crouch — don't keep chasing while "listening"
        // (Acoustic revealers keep the lock).
        LivingEntity t = getTarget();
        if (t instanceof Player p && isStealthHidden(p)) {
            setTarget(null);
            getLookControl().setLookAt(
                getX() + (random.nextDouble() - 0.5) * 8.0,
                getY() + getBbHeight() * 0.6,
                getZ() + (random.nextDouble() - 0.5) * 8.0,
                6f, 6f);
        } else if (t != null) {
            getLookControl().setLookAt(t, 8f, 8f);
        }
        if (stateTicks >= SILENCE_TICKS) {
            silenceCooldown = cd(300);
            clearSilenceCue();
            // Quiet crouch = he loses the lock for a moment (the "struggle to locate" payoff).
            boolean quietCrouch = false;
            for (Player p : nearbyPlayers(28)) {
                if (p.isCreative() || p.isSpectator()) continue;
                if (isQuietCrouch(p) && !stealthBrokenBy.contains(p.getUUID())) {
                    quietCrouch = true;
                    break;
                }
            }
            if (quietCrouch) {
                // Keep Acoustic revealers locked; drop everyone else (incl. quiet crouchers).
                LivingEntity cur = getTarget();
                if (!(cur instanceof Player cp) || !stealthBrokenBy.contains(cp.getUUID())) {
                    setTarget(null);
                }
                locateBlindTicks = 100;
                outOfReachTicks = 0;
                playSound(DeadAirSounds.LISTENER_DEFLECT.get(), 1.4f, 0.55f + random.nextFloat() * 0.1f);
                if (level() instanceof ServerLevel server) {
                    server.sendParticles(STATIC_DUST, getX(), getY() + 2.5, getZ(), 18, 0.8, 1.2, 0.8, 0);
                }
            }
            finish();
        }
    }

    /** Crouched and not sprinting — the stealth stance the HUD asks for. */
    public static boolean isQuietCrouch(Player p) {
        if (p == null || p.isSprinting()) return false;
        // Pose + shift cover toggle-crouch and packs that only set one of the two.
        return p.isCrouching() || p.isShiftKeyDown()
            || p.getPose() == net.minecraft.world.entity.Pose.CROUCHING;
    }

    /**
     * Listening Silence hearing rules. Crouching is the stealth stance: crouch-walk and light swings
     * do not give you away. Sprint / standing walk / using items still do.
     */
    private static boolean isLoudDuringSilence(Player p, Vec3 lastPos) {
        if (isQuietCrouch(p)) {
            double moved = lastPos == null ? 0 : p.position().subtract(lastPos).horizontalDistance();
            // Crouch-walk is quiet; only a sudden shove/knockback-scale move counts.
            return moved > 0.35;
        }
        if (p.isSprinting()) return true;
        if (p.isUsingItem()) return true;
        double moved = lastPos == null ? 0 : p.position().subtract(lastPos).horizontalDistance();
        // Standing: any real walk or a swing gives the pulse.
        return p.swinging || moved > 0.06;
    }

    private void clearSilenceCue() {
        for (Player p : nearbyPlayers(32)) {
            if (p instanceof ServerPlayer sp) {
                // WILD drops fog; encounter participants keep fog; bystanders stay fog-off.
                boolean fog = isEncounter() && ListenerEncounter.isParticipant(sp);
                DeadAirNet.sendTo(sp, new EncounterStatePacket(fog, 0, 0));
            }
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
            if (isAssembling()) return false;
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
            // During Listening Silence, quiet crouchers are hard to "hear" — don't stare-lock them
            // (Acoustic revealers stay locked).
            if (state == State.SILENCE && t instanceof Player p && isStealthHidden(p)) {
                return;
            }
            // Slow vanilla LookControl only â€” no custom yaw accumulation.
            getLookControl().setLookAt(t, 8f, 8f);
            if (state != State.IDLE) return;
            // Blind freezes chase on hidden crouchers only — Acoustic revealers stay chaseable.
            if (locateBlindTicks > 0 && t instanceof Player p && isStealthHidden(p)) {
                getNavigation().stop();
                return;
            }
            if (distanceTo(t) < 3.0) {
                getNavigation().stop();
                return;
            }
            if (--repath <= 0) {
                repath = 8;
                getNavigation().moveTo(t, phaseTwo() ? 1.0 : 0.82);
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
        playSound(DeadAirSounds.LISTENER_DISSOLVE.get(), 2.4f, 1.0f);
        bossEvent.removeAllPlayers();
        discard();
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        // Encounter boss/timer bar is for participants only (onJoin adds them).
        // WILD egg tests still show the bar to anyone who sees him.
        if (mode == Mode.WILD || ListenerEncounter.isParticipant(player)) {
            bossEvent.addPlayer(player);
        }
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
        // Chunk reload — already assembled; do not replay rift + spawn sting.
        spawnSoundPlayed = true;
        entityData.set(DATA_SPAWN_INTRO, 0);
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
