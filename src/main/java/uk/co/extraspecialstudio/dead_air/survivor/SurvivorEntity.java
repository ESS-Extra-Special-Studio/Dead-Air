package uk.co.extraspecialstudio.dead_air.survivor;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.base.PlayerBaseManager;
import uk.co.extraspecialstudio.dead_air.base.PlayerBaseStorage;
import uk.co.extraspecialstudio.dead_air.broadcast.BroadcastProgress;
import uk.co.extraspecialstudio.dead_air.broadcast.PendingSites;
import uk.co.extraspecialstudio.dead_air.compat.SurvivorGunCompat;
import uk.co.extraspecialstudio.dead_air.menu.SurvivorEquipmentMenu;
import uk.co.extraspecialstudio.dead_air.net.DeadAirNet;
import uk.co.extraspecialstudio.dead_air.net.SurvivorActionPacket;
import uk.co.extraspecialstudio.dead_air.net.SurvivorScreenPacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/** Recruitable survivor: identity, profession, base settle, combat + soft TaCZ guns. */
public class SurvivorEntity extends PathfinderMob {
    public static final int STRANDED = 0;
    public static final int FOLLOWING = 1;
    public static final int SETTLED = 2;
    /** Scavenger satchel slots (player opens via Equipment). */
    public static final int LOOT_PACK_SIZE = 9;
    private static final int TRAIN_COST_IRON = 16;
    private static final int TRAIN_COOLDOWN_TICKS = 20 * 60 * 10;

    public enum Profession {
        SCAVENGER("scavenger"),
        MEDIC("medic"),
        RADIO_OPERATOR("radio_operator"),
        PILOT("pilot"),
        MILITARY("military"),
        CIVILIAN("civilian");

        public final String id;

        Profession(String id) {
            this.id = id;
        }

        static Profession byIndex(int i) {
            Profession[] v = values();
            return v[Math.floorMod(i, v.length)];
        }

        /** Professions that can appear as stranded EB recruits (no Pilot). */
        static Profession[] fieldPool() {
            return new Profession[] { SCAVENGER, MEDIC, RADIO_OPERATOR, MILITARY, CIVILIAN };
        }
    }

    private static final EntityDataAccessor<Integer> DATA_PROFESSION =
        SynchedEntityData.defineId(SurvivorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STATE =
        SynchedEntityData.defineId(SurvivorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DUTY =
        SynchedEntityData.defineId(SurvivorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_GENDER =
        SynchedEntityData.defineId(SurvivorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SKIN =
        SynchedEntityData.defineId(SurvivorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_DOWNED =
        SynchedEntityData.defineId(SurvivorEntity.class, EntityDataSerializers.BOOLEAN);

    /** Seconds before a downed party survivor bleeds out. */
    private static final int BLEED_OUT_TICKS = 20 * 90;
    /** Right-click + stay close channel length. */
    private static final int REVIVE_TICKS = 20 * 3;

    @Nullable private UUID owner;
    @Nullable private UUID caller;
    @Nullable private BlockPos home;
    private int homeRange = 16;
    private long lastSupplyDay = -1;
    private final SurvivorIdentity identity = new SurvivorIdentity();
    /** True after {@link #initRandom} or loading a persisted Identity — spawn eggs need this. */
    private boolean identityRolled;
    @Nullable private BlockPos defendPos;
    private final List<BlockPos> patrol = new ArrayList<>();
    private int patrolIndex;
    private int gunCooldown;
    /** Temporarily separated from base (missing-recruit EB); still owned. */
    private boolean missing;
    private int downedTicks;
    private int reviveProgress;
    @Nullable private UUID reviveHelper;
    /** Medic heal action cooldown (ticks). */
    private int healCooldownTicks;
    /** Scavenger loot satchel — path-pickups land here; owner empties via Equipment. */
    private final SimpleContainer lootPack = new SimpleContainer(LOOT_PACK_SIZE);
    /** When true, {@link #die} proceeds even if currently flagged downed. */
    private boolean forceRealDeath;
    /** Suppress the generic death chat when bleed-out already messaged. */
    private boolean skipDeathChat;
    private int partyOrder = SurvivorParty.ORDER_FOLLOW;
    private int retreatTicks;
    private int lostFarTicks;
    private boolean pendingReunion;
    long regroupCooldownUntil;
    /** Player currently in dialogue / equipment with this survivor — stand still and face them. */
    @Nullable private UUID talkingTo;
    private int talkingTicks;
    /**
     * Soft claim on a camp idle POI (firepit / chair / tent / bed) while pathing or using it.
     * Firepits allow up to {@link #FIREPIT_IDLE_CAP} claimers; seats and sleep spots allow 1.
     */
    @Nullable private BlockPos campIdleClaim;
    private static final int FIREPIT_IDLE_CAP = 4;
    /** Restored when dialogue ends (downed survivors keep no-AI). */
    private boolean conversationPausedAi;

    public SurvivorEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        // Always drop equipped gear on death — default Mob chances (~8.5%) would silently delete most kits.
        setDropChance(EquipmentSlot.HEAD, 1.0f);
        setDropChance(EquipmentSlot.CHEST, 1.0f);
        setDropChance(EquipmentSlot.LEGS, 1.0f);
        setDropChance(EquipmentSlot.FEET, 1.0f);
        setDropChance(EquipmentSlot.MAINHAND, 1.0f);
        setDropChance(EquipmentSlot.OFFHAND, 1.0f);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 24.0)
            .add(Attributes.MOVEMENT_SPEED, 0.3)
            .add(Attributes.FOLLOW_RANGE, 32.0)
            .add(Attributes.ATTACK_DAMAGE, 3.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_PROFESSION, 0);
        entityData.define(DATA_STATE, STRANDED);
        entityData.define(DATA_DUTY, SurvivorDuty.NONE.ordinal());
        entityData.define(DATA_GENDER, 0);
        entityData.define(DATA_SKIN, 0);
        entityData.define(DATA_DOWNED, false);
    }

    public boolean isDowned() {
        return entityData.get(DATA_DOWNED);
    }

    private void setDowned(boolean downed) {
        entityData.set(DATA_DOWNED, downed);
    }

    /** Debug / automated tests — force the party downed state. */
    public void debugForceDowned() {
        if (level().isClientSide) return;
        enterDowned();
    }

    /** Recruited / owned survivors go down instead of dying outright. */
    private boolean isInParty() {
        return owner != null;
    }

    public void initRandom(RandomSource random, @Nullable UUID caller) {
        initRandom(random, caller, true);
    }

    /**
     * @param includePilot false for EB camp/convoy stranded — Pilots belong at base, not in the wild.
     */
    public void initRandom(RandomSource random, @Nullable UUID caller, boolean includePilot) {
        this.caller = caller;
        Profession[] pool = includePilot ? Profession.values() : Profession.fieldPool();
        entityData.set(DATA_PROFESSION, pool[random.nextInt(pool.length)].ordinal());
        identity.roll(random);
        identity.biasSkillsForProfession(getProfession(), random);
        syncAppearanceData();
        identityRolled = true;
        setCustomName(Component.literal(identity.name));
        setCustomNameVisible(true);
        SurvivorProgress.applyMaxHealth(this);
        equipStarterKit(random);
    }

    /** EB camp/convoy: pin idle POI search and restriction around the camp. */
    public void bindCampHome(BlockPos pos) {
        home = pos.immutable();
        homeRange = 16;
        restrictTo(home, homeRange);
    }

    private void syncAppearanceData() {
        entityData.set(DATA_GENDER, identity.gender.ordinal());
        entityData.set(DATA_SKIN, identity.skinVariant);
    }

    public boolean isFemale() {
        return entityData.get(DATA_GENDER) == SurvivorIdentity.Gender.FEMALE.ordinal();
    }

    public String getSkinTextureKey() {
        int skin = Math.floorMod(entityData.get(DATA_SKIN), SurvivorIdentity.SKIN_VARIANT_COUNT);
        String g = isFemale() ? "female" : "male";
        return getProfession().id + "_" + g + "_" + skin;
    }

    public void setProfession(Profession profession) {
        entityData.set(DATA_PROFESSION, profession.ordinal());
    }

    /** Starter weapon/armor so spawn-egg and EB stranded survivors are not empty-handed. */
    private void equipStarterKit(RandomSource random) {
        Profession profession = getProfession();
        if (profession == Profession.MEDIC) {
            setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
            setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        } else if (profession == Profession.RADIO_OPERATOR) {
            setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        } else if (profession == Profession.PILOT) {
            setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        } else if (profession == Profession.MILITARY) {
            setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
            setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
            setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        } else if (profession == Profession.CIVILIAN) {
            setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
            if (random.nextBoolean()) {
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
            }
        } else {
            // SCAVENGER
            setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
            setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
            setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
            int roll = random.nextInt(4);
            ItemStack weapon;
            if (roll == 0) weapon = new ItemStack(Items.IRON_SWORD);
            else if (roll == 1) weapon = new ItemStack(Items.STONE_AXE);
            else if (roll == 2) weapon = new ItemStack(Items.BOW);
            else weapon = new ItemStack(Items.IRON_AXE);
            setItemSlot(EquipmentSlot.MAINHAND, weapon);
            if (weapon.is(Items.BOW)) {
                setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 16 + random.nextInt(17)));
            }
        }
        // Aggressive survivors sometimes get a better scrap weapon
        if (identity.orientation == SurvivorIdentity.CombatOrientation.AGGRESSIVE
            && getMainHandItem().isEmpty()) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        }

        // TaCZ / Point Blank / Cold Sweat overlay — real GunId stacks when those mods are present
        SurvivorGunCompat.applyModdedStarterLoadout(this, random);
    }

    /**
     * Spawn eggs / {@code /summon} go through this; EB {@link SurvivorSpawner} and debug commands
     * call {@link #initRandom} before {@code addFreshEntity} and skip finalizeSpawn.
     */
    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData,
                                        @Nullable CompoundTag dataTag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
        if (!identityRolled) {
            initRandom(level.getRandom(), null);
        }
        return data;
    }

    public SurvivorIdentity getIdentity() {
        return identity;
    }

    public Profession getProfession() {
        return Profession.byIndex(entityData.get(DATA_PROFESSION));
    }

    public int getSurvivorState() {
        return entityData.get(DATA_STATE);
    }

    public SurvivorDuty getDuty() {
        return SurvivorDuty.byIndex(entityData.get(DATA_DUTY));
    }

    public void setDuty(SurvivorDuty duty) {
        entityData.set(DATA_DUTY, duty.ordinal());
    }

    /** Hold the Line — player-ordered defend only (not raid forceDefend / NBT load). */
    private void awardDefendIfOwned() {
        if (level().isClientSide || owner == null || !(level() instanceof ServerLevel sl)) return;
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(owner);
        if (p != null) {
            uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.award(p,
                uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.SURVIVOR_DEFEND);
        }
    }

    private void setSurvivorState(int state) {
        // Pilots never join the FOLLOWING party — base / helipad only (4.0).
        if (state == FOLLOWING && getProfession() == Profession.PILOT) {
            state = SETTLED;
        }
        int prev = getSurvivorState();
        entityData.set(DATA_STATE, state);
        // Party orders only apply while FOLLOWING — clear sticky combat flags on leave.
        if (prev == FOLLOWING && state != FOLLOWING) {
            partyOrder = SurvivorParty.ORDER_FOLLOW;
            retreatTicks = 0;
            lostFarTicks = 0;
        }
        if (state == SETTLED && home != null) {
            restrictTo(home, homeRange);
        } else if (state != SETTLED) {
            clearRestriction();
        }
        // Persist FOLLOWING membership so partyFull still counts unloaded party members.
        // Pilots always clear FOLLOWING in the roster (covers load redirect STRANDED→SETTLED).
        if (!level().isClientSide && owner != null && level() instanceof ServerLevel sl) {
            boolean nowFollowing = state == FOLLOWING;
            boolean pilot = getProfession() == Profession.PILOT;
            if (pilot || (prev != state && (prev == FOLLOWING || nowFollowing))) {
                SurvivorRoster roster = SurvivorRoster.get(sl.getServer());
                if (roster != null) {
                    roster.setFollowing(owner, getUUID(), nowFollowing && !pilot);
                }
            }
        }
    }

    @Nullable
    public ServerPlayer getOwnerPlayer() {
        if (owner == null || !(level() instanceof ServerLevel server)) return null;
        return server.getServer().getPlayerList().getPlayer(owner);
    }

    public boolean isOwnedBy(Player player) {
        return owner != null && player.getUUID().equals(owner);
    }

    @Nullable
    public UUID getOwnerUuid() {
        return owner;
    }

    public void setPartyOrder(int order) {
        this.partyOrder = order;
    }

    public int getPartyOrder() {
        return partyOrder;
    }

    public void beginRetreat(int ticks) {
        this.retreatTicks = Math.max(this.retreatTicks, ticks);
    }

    public void clearRetreat() {
        this.retreatTicks = 0;
    }

    public boolean tryRegroup(long gameTime) {
        if (gameTime < regroupCooldownUntil) return false;
        regroupCooldownUntil = gameTime + 100;
        return true;
    }

    /** Party DEFEND order: hold a point while remaining FOLLOWING. */
    public void applyPartyDefend(BlockPos at) {
        defendPos = at.immutable();
        // Do not overwrite home — lost/base teleport must keep the real home/base.
        setDuty(SurvivorDuty.DEFEND);
        awardDefendIfOwned();
    }

    /** Clear a temporary party DEFEND hold without dismissing ownership. */
    public void clearDefendHold() {
        if (getDuty() == SurvivorDuty.DEFEND || defendPos != null) {
            defendPos = null;
            if (getDuty() == SurvivorDuty.DEFEND) setDuty(SurvivorDuty.NONE);
        }
        // DEFEND order is meaningless without a hold point.
        if (partyOrder == SurvivorParty.ORDER_DEFEND) {
            partyOrder = SurvivorParty.ORDER_FOLLOW;
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.3) {
            @Override
            public boolean canUse() {
                // Unarmed / cautious survivors still panic — including while FOLLOWING.
                if (!shouldFlee()) return false;
                dismountIdleForCombat();
                return super.canUse();
            }
        });
        goalSelector.addGoal(2, new MedicSupportGoal());
        goalSelector.addGoal(3, new SurvivorGunGoal());
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.15, true) {
            @Override
            public boolean canUse() {
                if (!wantsMelee()) return false;
                dismountIdleForCombat();
                return super.canUse();
            }
        });
        goalSelector.addGoal(5, new ScavengeLootGoal());
        // Flee any Enemy (pack zombies often aren't Monster subclasses).
        goalSelector.addGoal(6, new FleeHostilesGoal());
        goalSelector.addGoal(7, new FollowOwnerGoal());
        goalSelector.addGoal(8, new DefendGoal());
        goalSelector.addGoal(9, new PatrolGoal());
        goalSelector.addGoal(10, new CampIdlePoiGoal());
        goalSelector.addGoal(11, new MoveTowardsRestrictionGoal(this, 1.0));
        goalSelector.addGoal(12, new WaterAvoidingRandomStrollGoal(this, 0.6) {
            @Override
            public boolean canUse() {
                return getSurvivorState() != FOLLOWING
                    && getDuty() == SurvivorDuty.NONE
                    && getTarget() == null
                    && super.canUse();
            }
        });
        goalSelector.addGoal(13, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(14, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                if (getLastHurtByMob() instanceof Player p && isOwnedBy(p)) return false;
                if (getLastHurtByMob() instanceof SurvivorEntity ally
                    && owner != null && owner.equals(ally.getOwnerUuid())) return false;
                return super.canUse();
            }
        }.setAlertOthers());
        // Acquire any hostile (mod zombies often skip Monster). mustSee=false so fog/corners still aggro.
        // shouldFight is checked by attack goals — still lock a target so flee/dismount can react.
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 5, false, false,
            e -> e instanceof Enemy && !(e instanceof SurvivorEntity) && !(e instanceof Player)));
    }

    private boolean shouldFlee() {
        if (isDowned()) return false;
        if (SurvivorGunCompat.holdingUsableGun(this) || hasMeleeWeapon()) return false;
        SurvivorIdentity.CombatOrientation o = identity.orientation;
        boolean flee = o == SurvivorIdentity.CombatOrientation.CAUTIOUS
            || o == SurvivorIdentity.CombatOrientation.SUPPORT
            || (o == SurvivorIdentity.CombatOrientation.DEFENSIVE && getHealth() < getMaxHealth() * 0.4f);
        Profession p = getProfession();
        return flee
            || (p == Profession.MEDIC && !hasMeleeWeapon())
            || (p == Profession.CIVILIAN && !hasMeleeWeapon() && !SurvivorGunCompat.holdingUsableGun(this));
    }

    private boolean shouldFight() {
        if (isDowned()) return false;
        if (retreatTicks > 0 || partyOrder == SurvivorParty.ORDER_RETREAT) return false;
        if (medicShouldSupport()) return false;
        // Fight back when recently hurt (fists OK).
        LivingEntity last = getLastHurtByMob();
        if (last instanceof Enemy && last.isAlive() && tickCount - getLastHurtByMobTimestamp() < 200) {
            return true;
        }
        if (getDuty() == SurvivorDuty.DEFEND) return true;
        if (partyOrder == SurvivorParty.ORDER_ATTACK) return true;
        // Armed → always engage. Unarmed use flee instead.
        return SurvivorGunCompat.holdingUsableGun(this) || hasMeleeWeapon();
    }

    /** Hostiles in range — used to cancel chair/tent idle before combat AI can run. */
    private boolean hostileNearby(double range) {
        AABB box = getBoundingBox().inflate(range);
        return !level().getEntitiesOfClass(Mob.class, box,
            e -> e instanceof Enemy && e.isAlive() && e != this && !(e instanceof SurvivorEntity)).isEmpty();
    }

    /**
     * Sitting / sleeping blocks pathing and melee. Stand up when a fight or flee is needed.
     */
    private void dismountIdleForCombat() {
        if (isSleeping()) {
            try {
                stopSleeping();
            } catch (Throwable ignored) {
            }
        }
        if (isPassenger() && getVehicle() instanceof uk.co.extraspecialstudio.dead_air.entity.SeatEntity) {
            stopRiding();
        }
    }

    private boolean wantsMelee() {
        return shouldFight() && !SurvivorGunCompat.holdingUsableGun(this) && getTarget() != null;
    }

    private boolean hasMeleeWeapon() {
        ItemStack stack = getMainHandItem();
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof SwordItem
            || stack.getItem() instanceof AxeItem
            || stack.getItem() instanceof TridentItem
            || stack.getItem() instanceof BowItem
            || stack.getItem() instanceof CrossbowItem
            || SurvivorGunCompat.isGun(stack);
    }

    // ---------- interaction ----------

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

        // Downed party members: right-click starts/continues a revive channel (stay close).
        if (isDowned()) {
            if (!level().isClientSide && player instanceof ServerPlayer sp && isOwnedBy(sp)) {
                reviveHelper = sp.getUUID();
                if (reviveProgress <= 0) {
                    sp.sendSystemMessage(Component.translatable("message.dead_air.survivor.revive_hint", getName())
                        .withStyle(ChatFormatting.YELLOW));
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        ItemStack held = player.getItemInHand(hand);

        // Sneak + held gear equips. Plain right-click always opens dialogue (used to swallow
        // the click when holding a sword/gun/armor and never show the window).
        if (!level().isClientSide && player instanceof ServerPlayer sp
            && isOwnedBy(sp) && player.isShiftKeyDown() && !held.isEmpty()) {
            if (tryEquipFromPlayer(sp, held)) {
                return InteractionResult.SUCCESS;
            }
        }

        if (!level().isClientSide && player instanceof ServerPlayer sp) {
            try {
                beginConversation(sp);
                boolean mine = isOwnedBy(sp);
                boolean hasBase = PlayerBaseManager.hasBase(sp.server, sp.getUUID());
                DeadAirNet.sendTo(sp, new SurvivorScreenPacket(
                    getId(), identity.name, getProfession().id, getSurvivorState(), mine,
                    mine && supplyReady(), dialogueLine(mine),
                    hasBase, getDuty().name().toLowerCase(), getDuty().ordinal(),
                    identity.personality.name().toLowerCase(),
                    identity.orientation.name().toLowerCase(),
                    identity.hopeLineKey(), missing));
            } catch (LinkageError | Exception e) {
                Dead_air.LOGGER.error("Dead Air: failed to open survivor dialogue for {}", getName().getString(), e);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Dialogue / equipment opened — stop and face the player. */
    public void beginConversation(ServerPlayer player) {
        if (player == null) return;
        talkingTo = player.getUUID();
        talkingTicks = 600;
        if (!isDowned() && !isNoAi()) {
            setNoAi(true);
            conversationPausedAi = true;
        }
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
        getLookControl().setLookAt(player, 40f, 40f);
    }

    public void endConversation(@Nullable Player player) {
        if (player != null && talkingTo != null && !talkingTo.equals(player.getUUID())) return;
        talkingTo = null;
        talkingTicks = 0;
        if (conversationPausedAi && !isDowned()) {
            setNoAi(false);
        }
        conversationPausedAi = false;
    }

    public boolean isInConversation() {
        return talkingTo != null && talkingTicks > 0;
    }

    private void tickConversation() {
        if (talkingTo == null || talkingTicks <= 0) {
            if (talkingTo != null || conversationPausedAi) endConversation(null);
            return;
        }
        talkingTicks--;
        ServerPlayer speaker = null;
        if (level() instanceof ServerLevel server) {
            speaker = server.getServer().getPlayerList().getPlayer(talkingTo);
        }
        if (speaker == null || speaker.level() != level() || distanceTo(speaker) > 10.0) {
            endConversation(null);
            return;
        }
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
        getLookControl().setLookAt(speaker, 40f, 40f);
    }

    public boolean isMissing() {
        return missing;
    }

    /** Base-under-attack: force defend duty at the panel. */
    public void forceDefendAt(BlockPos panel) {
        defendPos = panel.immutable();
        home = defendPos;
        homeRange = 16;
        setDuty(SurvivorDuty.DEFEND);
        setSurvivorState(SETTLED);
        // Clear EB missing soft-lock too (bare missing=false left missingSurvivorId stuck).
        clearMissing();
    }

    /**
     * After a base-attack wave ends: drop sticky DEFEND and restore wander range around the panel.
     * No-op if this survivor is not holding that panel.
     */
    public void releaseRaidDefend(BlockPos panel, int signalRange) {
        if (panel == null || defendPos == null || !defendPos.equals(panel)) return;
        if (getDuty() != SurvivorDuty.DEFEND) return;
        clearDefendHold();
        home = panel.immutable();
        homeRange = Math.max(16, signalRange);
        if (getSurvivorState() == SETTLED) {
            restrictTo(home, homeRange);
        }
    }

    /** Missing-recruit: teleport to last-known ping as injured/stranded while keeping owner. */
    public void markMissingAt(BlockPos at) {
        missing = true;
        setDuty(SurvivorDuty.NONE);
        defendPos = null;
        patrol.clear();
        home = null;
        pendingReunion = false;
        clearRetreat();
        setTarget(null);
        clearRestriction();
        setSurvivorState(STRANDED);
        teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
    }

    public void clearMissing() {
        missing = false;
        if (level() instanceof ServerLevel server && owner != null) {
            BroadcastProgress data = BroadcastProgress.get(server.getServer());
            if (data != null) {
                BroadcastProgress.PlayerProgress p = data.get(owner);
                if (getUUID().equals(p.missingSurvivorId)) {
                    p.missingSurvivorId = null;
                    data.setDirty();
                }
            }
            PendingSites.clearMissingFor(server.getServer(), owner);
        }
    }

    /** Player recovered a missing recruit (Follow / Settle / Send to base) — not dismiss / raid / death. */
    private void awardMissingRecovered(ServerPlayer player) {
        if (player == null) return;
        uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.award(player,
            uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.SURVIVOR_MISSING);
    }

    private void awardRecruitAdvancements(ServerPlayer player, @Nullable SurvivorRoster roster) {
        uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.award(player,
            uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.SURVIVOR_RECRUIT);
        if (getSurvivorState() == FOLLOWING) {
            uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.award(player,
                uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.SURVIVOR_FOLLOW);
        }
        int count = roster != null ? roster.count(player.getUUID()) : 1;
        if (count >= 3) {
            uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.award(player,
                uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.SURVIVOR_THREE);
        }
        if (count >= Config.maxSurvivorsPerPlayer) {
            uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.award(player,
                uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.SURVIVOR_FULL);
        }
        if (nearPlacedConvoy(player)) {
            uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.award(player,
                uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.EB_CONVOY);
        }
    }

    /** True when recruiting beside an owned placed convoy site (not merely “ever placed”). */
    private boolean nearPlacedConvoy(ServerPlayer player) {
        final double maxDistSq = 48.0 * 48.0;
        for (PendingSites.Site site : PendingSites.forOwner(player.server, player.getUUID())) {
            if (!PendingSites.KIND_CONVOY.equals(site.kind) || !site.placed) continue;
            BlockPos at = site.keyPos != null ? site.keyPos : site.center;
            if (at == null) continue;
            if (distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) <= maxDistSq) {
                return true;
            }
        }
        return false;
    }

    /** Equip gear from player hand into armor / main / off. Returns true if consumed an equip action. */
    private boolean tryEquipFromPlayer(ServerPlayer player, ItemStack held) {
        if (held.getItem() instanceof ArmorItem armor) {
            EquipmentSlot slot = armor.getEquipmentSlot();
            ItemStack previous = getItemBySlot(slot);
            setItemSlot(slot, held.split(1));
            if (!previous.isEmpty() && !player.getInventory().add(previous)) player.drop(previous, false);
            player.sendSystemMessage(Component.translatable("message.dead_air.survivor.equipped", getName())
                .withStyle(ChatFormatting.GREEN));
            return true;
        }
        // Caller already requires sneak. Armor handled above; weapons/tools/guns -> main, else offhand.
        if (isWeaponLike(held) || SurvivorGunCompat.isGun(held)
            || held.getAttributeModifiers(EquipmentSlot.MAINHAND).containsKey(Attributes.ATTACK_DAMAGE)) {
            ItemStack previous = getMainHandItem();
            ItemStack give = held.split(1);
            if (SurvivorGunCompat.isGun(give)) SurvivorGunCompat.prepareGunForNpc(give);
            setItemSlot(EquipmentSlot.MAINHAND, give);
            if (!previous.isEmpty() && !player.getInventory().add(previous)) player.drop(previous, false);
            uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.award(player,
                uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.SURVIVOR_ARM);
        } else if (!(held.getItem() instanceof net.minecraft.world.item.SpawnEggItem)) {
            ItemStack previous = getItemBySlot(EquipmentSlot.OFFHAND);
            setItemSlot(EquipmentSlot.OFFHAND, held.split(1));
            if (!previous.isEmpty() && !player.getInventory().add(previous)) player.drop(previous, false);
        } else {
            return false;
        }
        player.sendSystemMessage(Component.translatable("message.dead_air.survivor.equipped", getName())
            .withStyle(ChatFormatting.GREEN));
        return true;
    }

    private static boolean isWeaponLike(ItemStack stack) {
        return stack.getItem() instanceof SwordItem
            || stack.getItem() instanceof AxeItem
            || stack.getItem() instanceof TridentItem
            || stack.getItem() instanceof BowItem
            || stack.getItem() instanceof CrossbowItem;
    }

    private String dialogueLine(boolean mine) {
        if (missing && mine) return "survivor.dead_air.line.missing";
        String prof = getProfession().id;
        return switch (getSurvivorState()) {
            case STRANDED -> "survivor.dead_air.line.stranded." + prof;
            case FOLLOWING -> mine ? "survivor.dead_air.line.following" : "survivor.dead_air.line.taken";
            default -> mine
                ? (supplyReady() ? "survivor.dead_air.line.supplies_ready" : "survivor.dead_air.line.settled")
                : "survivor.dead_air.line.taken";
        };
    }

    private long currentDay() {
        return level().getDayTime() / 24000L;
    }

    private boolean supplyReady() {
        return getSurvivorState() == SETTLED && lastSupplyDay >= 0
            && currentDay() - lastSupplyDay >= Config.survivorSupplyDays;
    }

    public void handleAction(ServerPlayer player, int action) {
        if (action == SurvivorActionPacket.END_DIALOGUE) {
            endConversation(player);
            return;
        }
        // Any dialogue button keeps them facing you (incl. opening equipment).
        beginConversation(player);
        boolean mine = player.getUUID().equals(owner);
        // Downed survivors can only be talked to / looted / dismissed (dismiss has its own guard).
        if (isDowned() && action != SurvivorActionPacket.WHO_ARE_YOU
            && action != SurvivorActionPacket.IDLE_CHAT
            && action != SurvivorActionPacket.OPEN_EQUIPMENT
            && action != SurvivorActionPacket.DISMISS) {
            player.sendSystemMessage(Component.translatable("message.dead_air.survivor.downed_busy", getName())
                .withStyle(ChatFormatting.GOLD));
            return;
        }
        switch (action) {
            case SurvivorActionPacket.RECRUIT -> {
                if (getSurvivorState() != STRANDED || owner != null) return;
                if (!Config.survivorsEnabled) {
                    player.sendSystemMessage(Component.translatable("message.dead_air.survivor.disabled")
                        .withStyle(ChatFormatting.RED));
                    return;
                }
                SurvivorRoster roster = SurvivorRoster.get(player.server);
                if (roster != null && roster.count(player.getUUID()) >= Config.maxSurvivorsPerPlayer) {
                    player.sendSystemMessage(Component.translatable("message.dead_air.survivor.full",
                        Config.maxSurvivorsPerPlayer).withStyle(ChatFormatting.RED));
                    return;
                }
                boolean pilot = getProfession() == Profession.PILOT;
                if (!pilot && SurvivorParty.partyFull(player.server, player.getUUID())) {
                    player.sendSystemMessage(Component.translatable("message.dead_air.survivor.party_full",
                        Config.maxPartySize).withStyle(ChatFormatting.RED));
                    return;
                }
                owner = player.getUUID();
                if (roster != null) roster.add(owner, getUUID());
                clearMissing();
                if (pilot) {
                    settleNearPlayer(player);
                    player.sendSystemMessage(Component.translatable("message.dead_air.survivor.recruited_pilot", getName())
                        .withStyle(ChatFormatting.GREEN));
                    uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.award(player,
                        uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.SURVIVOR_SETTLE);
                } else {
                    setSurvivorState(FOLLOWING);
                    partyOrder = SurvivorParty.ORDER_FOLLOW;
                    player.sendSystemMessage(Component.translatable("message.dead_air.survivor.recruited", getName())
                        .withStyle(ChatFormatting.GREEN));
                }
                awardRecruitAdvancements(player, roster);
            }
            case SurvivorActionPacket.FOLLOW -> {
                if (!mine) return;
                boolean wasMissing = missing;
                // Missing escort always clears the EB soft-lock first (pilots / full party settle instead).
                if (wasMissing) clearMissing();
                if (getProfession() == Profession.PILOT) {
                    player.sendSystemMessage(Component.translatable("message.dead_air.survivor.pilot_no_party", getName())
                        .withStyle(ChatFormatting.GRAY));
                    settleNearPlayer(player);
                    uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.award(player,
                        uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.SURVIVOR_SETTLE);
                    if (wasMissing) {
                        player.sendSystemMessage(Component.translatable("message.dead_air.survivor.found", getName())
                            .withStyle(ChatFormatting.GREEN));
                        awardMissingRecovered(player);
                    }
                    return;
                }
                if (getSurvivorState() != FOLLOWING
                    && SurvivorParty.partyFull(player.server, player.getUUID())) {
                    if (wasMissing) {
                        // Recover the recruit home without needing a party slot.
                        settleNearPlayer(player);
                        player.sendSystemMessage(Component.translatable("message.dead_air.survivor.found", getName())
                            .withStyle(ChatFormatting.GREEN));
                        player.sendSystemMessage(Component.translatable("message.dead_air.survivor.party_full",
                            Config.maxPartySize).withStyle(ChatFormatting.GRAY));
                        uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.award(player,
                            uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.SURVIVOR_SETTLE);
                        awardMissingRecovered(player);
                        return;
                    }
                    player.sendSystemMessage(Component.translatable("message.dead_air.survivor.party_full",
                        Config.maxPartySize).withStyle(ChatFormatting.RED));
                    return;
                }
                setDuty(SurvivorDuty.NONE);
                defendPos = null;
                if (!wasMissing) clearMissing();
                setSurvivorState(FOLLOWING);
                partyOrder = SurvivorParty.ORDER_FOLLOW;
                clearRetreat();
                player.sendSystemMessage(Component.translatable(
                    wasMissing ? "message.dead_air.survivor.found" : "message.dead_air.survivor.following", getName())
                    .withStyle(ChatFormatting.GREEN));
                uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.award(player,
                    uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.SURVIVOR_FOLLOW);
                if (wasMissing) awardMissingRecovered(player);
            }
            case SurvivorActionPacket.SETTLE -> {
                boolean wasMissing = missing;
                if (mine) clearMissing();
                settleNearPlayer(player);
                if (mine) {
                    uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.award(player,
                        uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.SURVIVOR_SETTLE);
                    if (wasMissing) awardMissingRecovered(player);
                }
            }
            case SurvivorActionPacket.SEND_TO_BASE -> {
                boolean wasMissing = missing;
                if (mine) clearMissing();
                sendToBase(player);
                if (mine) {
                    uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.award(player,
                        uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.SURVIVOR_SETTLE);
                    if (wasMissing) awardMissingRecovered(player);
                }
            }
            case SurvivorActionPacket.COLLECT -> {
                if (!mine || !supplyReady()) return;
                giveSupplies(player);
                lastSupplyDay = currentDay();
                uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.award(player,
                    uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.SURVIVOR_SUPPLIES);
            }
            case SurvivorActionPacket.DISMISS -> {
                if (!mine) return;
                if (isDowned()) {
                    player.sendSystemMessage(Component.translatable("message.dead_air.survivor.dismiss_downed", getName())
                        .withStyle(ChatFormatting.RED));
                    return;
                }
                SurvivorRoster roster = SurvivorRoster.get(player.server);
                if (roster != null) roster.remove(owner, getUUID());
                clearMissing();
                giveLootPackTo(player);
                owner = null;
                home = null;
                defendPos = null;
                patrol.clear();
                setDuty(SurvivorDuty.NONE);
                partyOrder = SurvivorParty.ORDER_FOLLOW;
                pendingReunion = false;
                lostFarTicks = 0;
                clearRetreat();
                setTarget(null);
                setSurvivorState(STRANDED);
                player.sendSystemMessage(Component.translatable("message.dead_air.survivor.dismissed", getName())
                    .withStyle(ChatFormatting.GRAY));
            }
            case SurvivorActionPacket.OPEN_EQUIPMENT -> {
                if (!mine) return;
                NetworkHooks.openScreen(player, new SurvivorEquipmentMenu.Provider(this), buf -> buf.writeVarInt(getId()));
            }
            case SurvivorActionPacket.IDLE_CHAT -> {
                if (!mine) return;
                player.sendSystemMessage(Component.translatable(identity.nextIdleLineKey(getProfession().id), getName())
                    .withStyle(ChatFormatting.GRAY));
                player.sendSystemMessage(Component.translatable(identity.hopeLineKey())
                    .withStyle(ChatFormatting.DARK_GRAY));
            }
            case SurvivorActionPacket.WHO_ARE_YOU -> {
                player.sendSystemMessage(Component.translatable(identity.whoLineKey(getProfession().id), identity.name,
                    Component.translatable("survivor.dead_air.profession." + getProfession().id),
                    Component.translatable("survivor.dead_air.orientation." + identity.orientation.name().toLowerCase()))
                    .withStyle(ChatFormatting.AQUA));
            }
            case SurvivorActionPacket.HEAL -> tryMedicHeal(player);
            case SurvivorActionPacket.TRAIN_SCAVENGER -> trainCivilian(player, Profession.SCAVENGER);
            case SurvivorActionPacket.TRAIN_MEDIC -> trainCivilian(player, Profession.MEDIC);
            case SurvivorActionPacket.TRAIN_RADIO -> trainCivilian(player, Profession.RADIO_OPERATOR);
            case SurvivorActionPacket.TRAIN_MILITARY -> trainCivilian(player, Profession.MILITARY);
            case SurvivorActionPacket.TRAIN_PILOT -> trainCivilian(player, Profession.PILOT);
            case SurvivorActionPacket.CYCLE_JOB -> {
                // Legacy client button — jobs removed; professions + actions only.
            }
            case SurvivorActionPacket.DEFEND_HERE -> {
                if (!mine) return;
                BlockPos at = player.blockPosition().immutable();
                if (getSurvivorState() == FOLLOWING) {
                    // Match party HUD Defend: hold point, stay in party, keep real home/base.
                    applyPartyDefend(at);
                    partyOrder = SurvivorParty.ORDER_DEFEND;
                    player.sendSystemMessage(Component.translatable("message.dead_air.survivor.defend", getName(),
                        at.toShortString()).withStyle(ChatFormatting.GREEN));
                    return;
                }
                defendPos = at;
                // Do not clobber an existing base/home with the hold point.
                if (home == null) {
                    home = at;
                    homeRange = 12;
                }
                setDuty(SurvivorDuty.DEFEND);
                awardDefendIfOwned();
                setSurvivorState(SETTLED);
                player.sendSystemMessage(Component.translatable("message.dead_air.survivor.defend", getName(),
                    at.toShortString()).withStyle(ChatFormatting.GREEN));
            }
            case SurvivorActionPacket.PATROL_ADD -> {
                if (!mine) return;
                BlockPos wp = player.blockPosition().immutable();
                patrol.add(wp);
                player.sendSystemMessage(Component.translatable("message.dead_air.survivor.patrol_add",
                    patrol.size(), wp.toShortString()).withStyle(ChatFormatting.GREEN));
            }
            case SurvivorActionPacket.PATROL_CLEAR -> {
                if (!mine) return;
                patrol.clear();
                patrolIndex = 0;
                if (getDuty() == SurvivorDuty.PATROL) setDuty(SurvivorDuty.NONE);
                player.sendSystemMessage(Component.translatable("message.dead_air.survivor.patrol_clear")
                    .withStyle(ChatFormatting.GRAY));
            }
            case SurvivorActionPacket.PATROL_START -> {
                if (!mine) return;
                if (patrol.size() < 2) {
                    player.sendSystemMessage(Component.translatable("message.dead_air.survivor.patrol_need_waypoints")
                        .withStyle(ChatFormatting.RED));
                    return;
                }
                setDuty(SurvivorDuty.PATROL);
                setSurvivorState(SETTLED);
                home = patrol.get(0);
                homeRange = 48;
                restrictTo(home, homeRange);
                player.sendSystemMessage(Component.translatable("message.dead_air.survivor.patrol_start", getName(),
                    patrol.size()).withStyle(ChatFormatting.GREEN));
            }
            default -> {}
        }
    }

    private void settleNearPlayer(ServerPlayer player) {
        if (!player.getUUID().equals(owner)) return;
        // Prefer player base if set
        PlayerBaseStorage.Base base = PlayerBaseManager.getBase(player.server, player.getUUID());
        if (base != null && base.dimension == level().dimension()) {
            sendToBase(player);
            return;
        }
        BlockPos bed = player.getRespawnPosition();
        boolean sameDim = player.getRespawnDimension() == level().dimension();
        home = bed != null && sameDim ? bed : blockPosition();
        homeRange = 16;
        if (lastSupplyDay < 0) lastSupplyDay = currentDay();
        clearDefendHold();
        setDuty(SurvivorDuty.NONE);
        setTarget(null);
        setSurvivorState(SETTLED);
        player.sendSystemMessage(Component.translatable(bed != null && sameDim
            ? "message.dead_air.survivor.settled_bed" : "message.dead_air.survivor.settled_here", getName())
            .withStyle(ChatFormatting.GREEN));
    }

    private void sendToBase(ServerPlayer player) {
        if (!player.getUUID().equals(owner)) return;
        PlayerBaseStorage.Base base = PlayerBaseManager.getBase(player.server, player.getUUID());
        if (base == null) {
            player.sendSystemMessage(Component.translatable("message.dead_air.base.none").withStyle(ChatFormatting.RED));
            return;
        }
        if (base.dimension != level().dimension()) {
            player.sendSystemMessage(Component.translatable("message.dead_air.survivor.base_wrong_dim")
                .withStyle(ChatFormatting.RED));
            return;
        }
        ServerLevel level = (ServerLevel) level();
        home = base.panelPos.immutable();
        homeRange = PlayerBaseManager.signalRange(level, home);
        if (lastSupplyDay < 0) lastSupplyDay = currentDay();
        clearDefendHold();
        setDuty(SurvivorDuty.NONE);
        setTarget(null);
        setSurvivorState(SETTLED);
        // Soft teleport near panel if far — stand beside the panel, not inside it
        if (!blockPosition().closerThan(home, homeRange)) {
            BlockPos stand = findStandBeside(level, home);
            teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        }
        player.sendSystemMessage(Component.translatable("message.dead_air.survivor.sent_to_base", getName(),
            homeRange).withStyle(ChatFormatting.GREEN));
    }

    /** Prefer an adjacent standable block so we don't teleport into the panel / foliage / mid-air. */
    private static BlockPos findStandBeside(ServerLevel level, BlockPos panel) {
        BlockPos[] offsets = {
            panel.north(), panel.south(), panel.east(), panel.west(),
            panel.north().north(), panel.south().south(), panel.east().east(), panel.west().west(),
            panel
        };
        // Prefer near the reference Y (caves / bases) before snapping to world surface.
        for (BlockPos col : offsets) {
            BlockPos feet = localStand(level, col, panel.getY());
            if (feet != null) return feet;
        }
        for (BlockPos col : offsets) {
            BlockPos feet = surfaceStand(level, col);
            if (canStandAt(level, feet)) return feet;
        }
        return panel;
    }

    /** Scan a few blocks around refY for a standable feet position. */
    @Nullable
    private static BlockPos localStand(ServerLevel level, BlockPos col, int refY) {
        for (int dy = 2; dy >= -8; dy--) {
            BlockPos feet = new BlockPos(col.getX(), refY + dy, col.getZ());
            if (canStandAt(level, feet)) return feet;
        }
        return null;
    }

    private static BlockPos surfaceStand(ServerLevel level, BlockPos pos) {
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            pos.getX(), pos.getZ());
        return new BlockPos(pos.getX(), y, pos.getZ());
    }

    private static boolean canStandAt(ServerLevel level, BlockPos feet) {
        BlockState atFeet = level.getBlockState(feet);
        BlockState atHead = level.getBlockState(feet.above());
        BlockState below = level.getBlockState(feet.below());
        return atFeet.getCollisionShape(level, feet).isEmpty()
            && atHead.getCollisionShape(level, feet.above()).isEmpty()
            && below.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP);
    }

    private void giveSupplies(ServerPlayer player) {
        if (!(level() instanceof ServerLevel server)) return;
        ResourceLocation tableId = ResourceLocation.fromNamespaceAndPath(Dead_air.MODID,
            "gameplay/survivor_supplies/" + getProfession().id);
        LootTable table = server.getServer().getLootData().getLootTable(tableId);
        LootParams params = new LootParams.Builder(server)
            .withParameter(LootContextParams.ORIGIN, position())
            .withParameter(LootContextParams.THIS_ENTITY, this)
            .create(LootContextParamSets.GIFT);
        int given = 0;
        for (ItemStack stack : table.getRandomItems(params)) {
            // Light skill bias: scavengers with high skill get an extra roll occasionally
            given++;
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
        if (identity.skillScavenge >= 3 && getProfession() == Profession.SCAVENGER && random.nextFloat() < 0.35f) {
            for (ItemStack stack : table.getRandomItems(params)) {
                given++;
                if (!player.getInventory().add(stack)) player.drop(stack, false);
            }
        }
        try {
            if (getProfession() == Profession.MEDIC) {
                uk.co.extraspecialstudio.dead_air.compat.ColdSweatCompat.injectMedicSupplies(player);
                uk.co.extraspecialstudio.dead_air.compat.ToughAsNailsCompat.injectMedicOrSurvivorSupplies(player);
                uk.co.extraspecialstudio.dead_air.compat.ApocalypseLootCompat.injectMedicSupplies(player);
            } else if (getProfession() == Profession.SCAVENGER
                || getProfession() == Profession.MILITARY) {
                uk.co.extraspecialstudio.dead_air.compat.GunLootCompat.injectScavengerSupplies(player);
                uk.co.extraspecialstudio.dead_air.compat.ToughAsNailsCompat.injectMedicOrSurvivorSupplies(player);
                uk.co.extraspecialstudio.dead_air.compat.ApocalypseLootCompat.injectScavengerSupplies(player);
            } else if (getProfession() == Profession.CIVILIAN
                || getProfession() == Profession.PILOT
                || getProfession() == Profession.RADIO_OPERATOR) {
                uk.co.extraspecialstudio.dead_air.compat.ToughAsNailsCompat.injectMedicOrSurvivorSupplies(player);
                uk.co.extraspecialstudio.dead_air.compat.ApocalypseLootCompat.injectScavengerSupplies(player);
            }
        } catch (Throwable ignored) {
        }
        player.sendSystemMessage(Component.translatable(given > 0 ? "message.dead_air.survivor.supplies"
            : "message.dead_air.survivor.supplies_empty", getName()).withStyle(ChatFormatting.GREEN));
    }

    /** Civilian only — train into another profession (iron cost + cooldown). */
    private void trainCivilian(ServerPlayer player, Profession target) {
        if (!player.getUUID().equals(owner)) return;
        if (getProfession() != Profession.CIVILIAN) {
            player.sendSystemMessage(Component.translatable("message.dead_air.survivor.train_not_civilian", getName())
                .withStyle(ChatFormatting.RED));
            return;
        }
        if (getSurvivorState() != SETTLED) {
            player.sendSystemMessage(Component.translatable("message.dead_air.survivor.train_need_settled", getName())
                .withStyle(ChatFormatting.RED));
            return;
        }
        if (target == Profession.CIVILIAN) return;
        long now = level().getGameTime();
        long readyAt = player.getPersistentData().getLong("dead_air_train_ready");
        if (now < readyAt) {
            int sec = Math.max(1, (int) ((readyAt - now + 19) / 20));
            player.sendSystemMessage(Component.translatable("message.dead_air.survivor.train_cooldown", getName(), sec)
                .withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!consumeTrainCost(player)) {
            player.sendSystemMessage(Component.translatable("message.dead_air.survivor.train_need_iron",
                getName(), TRAIN_COST_IRON).withStyle(ChatFormatting.RED));
            return;
        }
        giveLootPackTo(player);
        setProfession(target);
        identity.skinVariant = random.nextInt(SurvivorIdentity.SKIN_VARIANT_COUNT);
        identity.biasSkillsForProfession(target, random);
        syncAppearanceData();
        // Drop sticky civilian duties before the new profession kit/state.
        clearDefendHold();
        patrol.clear();
        patrolIndex = 0;
        setDuty(SurvivorDuty.NONE);
        // Return old gear to the owner instead of deleting it.
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR
                && slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND) {
                continue;
            }
            ItemStack old = getItemBySlot(slot);
            if (!old.isEmpty()) {
                setItemSlot(slot, ItemStack.EMPTY);
                if (!player.getInventory().add(old)) player.drop(old, false);
            }
        }
        equipStarterKit(random);
        // Always re-assert SETTLED (pilots must leave FOLLOWING; clears stale party roster bits).
        setSurvivorState(SETTLED);
        player.getPersistentData().putLong("dead_air_train_ready", level().getGameTime() + TRAIN_COOLDOWN_TICKS);
        player.sendSystemMessage(Component.translatable("message.dead_air.survivor.trained", getName(),
            Component.translatable("survivor.dead_air.profession." + target.id)).withStyle(ChatFormatting.GREEN));
        uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.award(player,
            uk.co.extraspecialstudio.dead_air.advancement.DeadAirAdvancements.SURVIVOR_TRAIN);
    }

    private static boolean consumeTrainCost(ServerPlayer player) {
        int need = TRAIN_COST_IRON;
        int have = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(Items.IRON_INGOT)) have += s.getCount();
        }
        if (have < need) return false;
        int left = need;
        for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.is(Items.IRON_INGOT)) continue;
            int take = Math.min(left, s.getCount());
            s.shrink(take);
            left -= take;
        }
        return left <= 0;
    }

    public SimpleContainer getLootPack() {
        return lootPack;
    }

    public boolean usesLootPack() {
        return getProfession() == Profession.SCAVENGER;
    }

    public boolean lootPackHasSpace() {
        for (int i = 0; i < lootPack.getContainerSize(); i++) {
            if (lootPack.getItem(i).isEmpty()) return true;
            ItemStack s = lootPack.getItem(i);
            if (s.getCount() < s.getMaxStackSize()) return true;
        }
        return false;
    }

    /** Move satchel contents into the player's inventory (overflow drops). */
    public void giveLootPackTo(Player player) {
        for (int i = 0; i < lootPack.getContainerSize(); i++) {
            ItemStack stack = lootPack.getItem(i);
            if (stack.isEmpty()) continue;
            lootPack.setItem(i, ItemStack.EMPTY);
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
    }

    /** Try to stash a ground stack into the satchel; returns leftover. */
    public ItemStack tryInsertLoot(ItemStack stack) {
        if (stack.isEmpty() || !usesLootPack()) return stack;
        int before = stack.getCount();
        ItemStack remaining = stack.copy();
        for (int i = 0; i < lootPack.getContainerSize() && !remaining.isEmpty(); i++) {
            ItemStack slot = lootPack.getItem(i);
            if (slot.isEmpty()) {
                int put = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                ItemStack placed = remaining.copy();
                placed.setCount(put);
                lootPack.setItem(i, placed);
                remaining.shrink(put);
            } else if (ItemStack.isSameItemSameTags(slot, remaining) && slot.getCount() < slot.getMaxStackSize()) {
                int room = slot.getMaxStackSize() - slot.getCount();
                int put = Math.min(room, remaining.getCount());
                slot.grow(put);
                remaining.shrink(put);
                lootPack.setChanged();
            }
        }
        if (remaining.getCount() < before) {
            SurvivorProgress.awardXp(this, 3, SurvivorProgress.SkillBias.SCAVENGE);
        }
        return remaining;
    }

    /** True when medic should drop combat and treat someone. */
    private boolean medicShouldSupport() {
        if (getProfession() != Profession.MEDIC || isDowned()) return false;
        if (healCooldownTicks > 0) return false;
        if (retreatTicks > 0 || partyOrder == SurvivorParty.ORDER_RETREAT) return false;
        int state = getSurvivorState();
        // Settled medics still auto-revive downed allies at base; owner heal is FOLLOWING-only.
        if (state != FOLLOWING && state != SETTLED) return false;
        if (findDownedAlly(16) != null) return true;
        if (state != FOLLOWING) return false;
        ServerPlayer ownerPlayer = getOwnerPlayer();
        return ownerPlayer != null && ownerPlayer.level() == level()
            && ownerPlayer.getHealth() < ownerPlayer.getMaxHealth() - 2f;
    }

    @Nullable
    private SurvivorEntity findDownedAlly(double range) {
        if (owner == null || !(level() instanceof ServerLevel server)) return null;
        SurvivorEntity best = null;
        double bestDist = range * range;
        for (SurvivorEntity s : server.getEntitiesOfClass(SurvivorEntity.class, getBoundingBox().inflate(range))) {
            if (s == this || !s.isDowned() || !owner.equals(s.owner)) continue;
            double d = distanceToSqr(s);
            if (d <= bestDist) {
                bestDist = d;
                best = s;
            }
        }
        return best;
    }

    /** Medic profession only — heal owner or revive a nearby downed owned survivor. */
    private void tryMedicHeal(ServerPlayer player) {
        if (!player.getUUID().equals(owner)) return;
        if (getProfession() != Profession.MEDIC) {
            player.sendSystemMessage(Component.translatable("message.dead_air.survivor.heal_not_medic", getName())
                .withStyle(ChatFormatting.RED));
            return;
        }
        if (isDowned()) {
            player.sendSystemMessage(Component.translatable("message.dead_air.survivor.heal_self_downed", getName())
                .withStyle(ChatFormatting.RED));
            return;
        }
        if (healCooldownTicks > 0) {
            int sec = Math.max(1, (healCooldownTicks + 19) / 20);
            player.sendSystemMessage(Component.translatable("message.dead_air.survivor.heal_cooldown", getName(), sec)
                .withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!(level() instanceof ServerLevel)) return;

        SurvivorEntity downed = findDownedAlly(16);
        if (downed != null) {
            downed.reviveFrom();
            healCooldownTicks = 20 * 45;
            SurvivorProgress.awardXp(this, 5, SurvivorProgress.SkillBias.MEDICINE);
            player.sendSystemMessage(Component.translatable("message.dead_air.survivor.heal_revived",
                getName(), downed.getName()).withStyle(ChatFormatting.GREEN));
            return;
        }

        float missing = player.getMaxHealth() - player.getHealth();
        if (missing < 0.5f) {
            // Avoid burning cooldown / chat spam when already topped off.
            player.sendSystemMessage(Component.translatable("message.dead_air.survivor.heal_not_needed", getName())
                .withStyle(ChatFormatting.GRAY));
            return;
        }
        float amount = 6f + identity.skillMedicine;
        player.heal(amount);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 5, 0));
        healCooldownTicks = 20 * 60;
        SurvivorProgress.awardXp(this, 5, SurvivorProgress.SkillBias.MEDICINE);
        player.sendSystemMessage(Component.translatable("message.dead_air.survivor.healed", getName())
            .withStyle(ChatFormatting.GREEN));
    }

    // ---------- lifecycle / downed ----------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) {
            return super.hurt(source, amount);
        }
        // Friendly fire: owner (and same-owner allies) cannot damage any recruit still theirs —
        // FOLLOWING, SETTLED/base, patrol, defend, missing, etc. Only wild / fully dismissed
        // (owner cleared) take player damage from you.
        Entity attacker = source.getEntity();
        if (attacker instanceof Player p && isOwnedBy(p)) {
            return false;
        }
        if (attacker instanceof SurvivorEntity other
            && owner != null && owner.equals(other.getOwnerUuid())) {
            return false;
        }
        if (isDowned()) {
            if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || forceRealDeath) {
                forceRealDeath = true;
                setDowned(false);
                return super.hurt(source, Math.max(amount, getHealth() + 1f));
            }
            return false;
        }
        if (isInParty() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            float previewAmount = amount;
            // Vanilla i-frame overkill still applies (amount - lastHurt); keep that path downable.
            if (invulnerableTime > 10.0F && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
                if (amount <= lastHurt) {
                    return false;
                }
                previewAmount = amount - lastHurt;
            }
            if (isDamageSourceBlocked(source)) {
                return super.hurt(source, amount);
            }
            // Non-mutating preview (CombatRules) so we do not double-wear armor via getDamageAfterArmorAbsorb.
            float incoming = estimateIncomingDamage(source, previewAmount);
            if (getHealth() - incoming <= 0f) {
                setAbsorptionAmount(0f);
                enterDowned();
                invulnerableTime = 20;
                lastHurt = amount;
                return true;
            }
        }
        return super.hurt(source, amount);
    }

    /** Approximate post-armor / resistance / enchant / absorption damage without mutating gear. */
    private float estimateIncomingDamage(DamageSource source, float amount) {
        float dmg = amount;
        if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            dmg = CombatRules.getDamageAfterAbsorb(dmg, (float) getArmorValue(),
                (float) getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        }
        if (!source.is(DamageTypeTags.BYPASSES_EFFECTS)
            && hasEffect(MobEffects.DAMAGE_RESISTANCE)
            && !source.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
            int amp = getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() + 1;
            int keep = 25 - amp * 5;
            dmg = Math.max(dmg * keep / 25.0F, 0.0F);
        }
        if (dmg > 0f && !source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
            int prot = EnchantmentHelper.getDamageProtection(getArmorSlots(), source);
            if (prot > 0) {
                dmg = CombatRules.getDamageAfterMagicAbsorb(dmg, (float) prot);
            }
        }
        return Math.max(0f, dmg - getAbsorptionAmount());
    }

    private void enterDowned() {
        setHealth(1f);
        setDowned(true);
        downedTicks = 0;
        reviveProgress = 0;
        reviveHelper = null;
        setTarget(null);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        clearFire();
        // STANDING + client lie-on-back rotation — SWIMMING crawl pose looked twisted upright.
        setPose(Pose.STANDING);
        setXRot(0f);
        refreshDimensions();
        setNoAi(true);
        playSound(SoundEvents.PLAYER_HURT, 1f, 0.85f);
        ServerPlayer p = getOwnerPlayer();
        if (p != null) {
            p.sendSystemMessage(Component.translatable("message.dead_air.survivor.downed", getName())
                .withStyle(ChatFormatting.GOLD));
            SurvivorPartyEvents.syncPlayerNow(p);
        }
    }

    /** Revive without chat — callers send the appropriate message. */
    private void reviveFrom() {
        setDowned(false);
        downedTicks = 0;
        reviveProgress = 0;
        reviveHelper = null;
        setNoAi(false);
        setPose(Pose.STANDING);
        refreshDimensions();
        setHealth(Math.max(8f, getMaxHealth() * 0.45f));
        playSound(SoundEvents.PLAYER_LEVELUP, 0.55f, 1.35f);
        ServerPlayer p = getOwnerPlayer();
        if (p != null) SurvivorPartyEvents.syncPlayerNow(p);
    }

    private void bleedOut() {
        forceRealDeath = true;
        skipDeathChat = true;
        setNoAi(false);
        setDowned(false);
        ServerPlayer p = getOwnerPlayer();
        if (p != null) {
            p.sendSystemMessage(Component.translatable("message.dead_air.survivor.bleed_out", getName())
                .withStyle(ChatFormatting.DARK_RED));
        }
        hurt(damageSources().genericKill(), Float.MAX_VALUE);
        forceRealDeath = false;
        skipDeathChat = false;
    }

    private void tickDownedLogic() {
        setPose(Pose.STANDING);
        setXRot(0f);
        setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
        getNavigation().stop();
        setTarget(null);
        // Hurt is blocked while downed — keep air full so drown ticks don't spam rejected damage.
        setAirSupply(getMaxAirSupply());
        // tickPartyAi is skipped while downed — still evacuate if the owner teleports away / leaves dim / logs out.
        tickDownedAbandonCheck();
        downedTicks++;
        if (downedTicks >= BLEED_OUT_TICKS) {
            bleedOut();
            return;
        }
        // Periodic reminder so the owner knows the timer is ticking.
        if (downedTicks % 200 == 0) {
            ServerPlayer ownerPlayer = getOwnerPlayer();
            if (ownerPlayer != null) {
                int left = Math.max(1, (BLEED_OUT_TICKS - downedTicks) / 20);
                ownerPlayer.sendSystemMessage(Component.translatable(
                    "message.dead_air.survivor.downed_timer", getName(), left)
                    .withStyle(ChatFormatting.GOLD));
            }
        }
        if (reviveHelper == null) {
            reviveProgress = Math.max(0, reviveProgress - 1);
            return;
        }
        Player helper = level().getPlayerByUUID(reviveHelper);
        if (!(helper instanceof ServerPlayer sp) || !helper.isAlive() || distanceToSqr(helper) > 12.25) {
            reviveProgress = Math.max(0, reviveProgress - 2);
            if (reviveProgress <= 0) reviveHelper = null;
            return;
        }
        reviveProgress++;
        int pct = Math.min(100, reviveProgress * 100 / REVIVE_TICKS);
        sp.displayClientMessage(Component.translatable("message.dead_air.survivor.reviving", getName(), pct), true);
        if (reviveProgress >= REVIVE_TICKS) {
            reviveFrom();
            sp.sendSystemMessage(Component.translatable("message.dead_air.survivor.revived", getName())
                .withStyle(ChatFormatting.GREEN));
        }
    }

    /**
     * Downed party members never run {@link #tickPartyAi}, so owner teleport / dim change / logout
     * would otherwise leave them bleeding out alone until death. After a short abandon window,
     * leave the party and soft-teleport to base/home while still downed (bleed timer continues;
     * medic / owner can revive at base).
     */
    private void tickDownedAbandonCheck() {
        if (getSurvivorState() != FOLLOWING || owner == null) {
            lostFarTicks = 0;
            return;
        }
        ServerPlayer ownerPlayer = getOwnerPlayer();
        double lostSq = (double) Config.partyLostDistance * Config.partyLostDistance;
        boolean abandoned = ownerPlayer == null
            || ownerPlayer.level() != level()
            || distanceToSqr(ownerPlayer) > lostSq;
        if (!abandoned) {
            lostFarTicks = 0;
            return;
        }
        lostFarTicks++;
        // Match standing lost gates: ~3s when owner is online-but-far, ~5s when offline / other dim.
        int threshold = (ownerPlayer == null || ownerPlayer.level() != level()) ? 100 : 60;
        if (lostFarTicks <= threshold) return;
        reviveHelper = null;
        reviveProgress = 0;
        markLostFromParty();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        // Harness clock — must run even if ModEvents early-outs on shutdownRequested.
        uk.co.extraspecialstudio.dead_air.commands.DeadAirSurvivorCommand.onSurvivorServerTick(this);
        if (healCooldownTicks > 0) healCooldownTicks--;
        tickConversation();
        if (isDowned()) {
            tickDownedLogic();
            // Evacuated-to-base survivors stay SETTLED+downed; still clear sticky raid DEFEND.
            if (getSurvivorState() == SETTLED && tickCount % 20 == 0) {
                uk.co.extraspecialstudio.dead_air.broadcast.BaseAttackManager.applyPendingReleaseIfAny(this);
            }
            return;
        }
        // Unloaded mid-raid defenders catch sticky DEFEND release when they tick back in.
        if (getSurvivorState() == SETTLED && tickCount % 20 == 0) {
            uk.co.extraspecialstudio.dead_air.broadcast.BaseAttackManager.applyPendingReleaseIfAny(this);
        }
        // Chair / tent idle must not lock survivors while zombies chew on them.
        if ((isPassenger() || isSleeping())
            && (getTarget() != null || shouldFlee() || shouldFight())
            && (getTarget() != null || hostileNearby(12.0)
                || (getLastHurtByMob() instanceof Enemy
                    && tickCount - getLastHurtByMobTimestamp() < 100))) {
            dismountIdleForCombat();
        }
        if (!isInConversation()) {
            tickPartyAi();
            tickRadioOperatorXp();
        }
    }

    /** Slow XP drip while a following radio op supports an owner listening to music. */
    private void tickRadioOperatorXp() {
        if (tickCount % 200 != 0) return;
        if (getProfession() != Profession.RADIO_OPERATOR || getSurvivorState() != FOLLOWING) return;
        if (isDowned() || owner == null) return;
        ServerPlayer ownerPlayer = getOwnerPlayer();
        if (ownerPlayer == null || ownerPlayer.level() != level()) return;
        if (!uk.co.extraspecialstudio.dead_air.radio.RadioManager.isListeningToRadio(ownerPlayer)) return;
        if (!uk.co.extraspecialstudio.dead_air.api.DeadAirAPI.isPlayerPlayingMusic(ownerPlayer)) return;
        SurvivorProgress.awardXp(this, 2, SurvivorProgress.SkillBias.RADIO);
    }

    private void tickPartyAi() {
        if (owner == null) {
            lostFarTicks = 0;
            return;
        }
        ServerPlayer ownerPlayer = getOwnerPlayer();
        if (pendingReunion && ownerPlayer != null && ownerPlayer.level() == level()
            && distanceToSqr(ownerPlayer) < 100) {
            pendingReunion = false;
            int line = random.nextInt(3) + 1;
            ownerPlayer.sendSystemMessage(Component.translatable(
                "message.dead_air.survivor.reunion." + line, getName())
                .withStyle(ChatFormatting.YELLOW));
        }
        if (getSurvivorState() != FOLLOWING) {
            lostFarTicks = 0;
            return;
        }
        if (ownerPlayer == null || ownerPlayer.level() != level()) {
            lostFarTicks++;
            if (lostFarTicks > 100) markLostFromParty();
            return;
        }

        double distSq = distanceToSqr(ownerPlayer);
        double lost = Config.partyLostDistance;

        // DEFEND order is "hold this point" — do not treat distance from owner as lost,
        // and do not cancel combat / yank home just because the owner sprinted away.
        boolean holdingDefend = partyOrder == SurvivorParty.ORDER_DEFEND
            && getDuty() == SurvivorDuty.DEFEND;

        // Retreat stickiness: owner sprinting away or ordered retreat → drop chase.
        // ATTACK keeps focus while you kite — only FOLLOW / RETREAT / active retreatTicks yank home.
        boolean attacking = partyOrder == SurvivorParty.ORDER_ATTACK;
        boolean retreating = retreatTicks > 0 || partyOrder == SurvivorParty.ORDER_RETREAT;
        boolean ownerFleeing = !holdingDefend && !attacking && ownerPlayer.isSprinting() && distSq > 36;
        if (!holdingDefend && !attacking && (retreating || ownerFleeing)) {
            if (retreatTicks > 0) retreatTicks--;
            setTarget(null);
            // Soft-teleport catch-up (FollowOwnerGoal is off during RETREAT).
            if (distSq > 24 * 24) {
                int slot = SurvivorParty.formationIndex(ownerPlayer.server, this);
                softTeleportNear(SurvivorPartyOrders.formationPoint(ownerPlayer, slot));
                getNavigation().stop();
            } else if (distSq > 9) {
                getNavigation().moveTo(ownerPlayer, ownerPlayer.isSprinting() ? 1.4 : 1.2);
            }
            // Retreat order lasts for retreatTicks only (also covers reload with stale ORDER_RETREAT).
            if (partyOrder == SurvivorParty.ORDER_RETREAT && retreatTicks <= 0) {
                partyOrder = SurvivorParty.ORDER_FOLLOW;
            }
        }

        // Chase leash: don't wander away from the owner while FOLLOWING.
        // Skip owner-distance leash on DEFEND hold and ATTACK (kite focus); still drop ally/player targets.
        LivingEntity target = getTarget();
        if (target != null) {
            boolean allyOrOwner = (target instanceof SurvivorEntity ally && owner.equals(ally.getOwnerUuid()))
                || (target instanceof Player p && isOwnedBy(p));
            if (allyOrOwner) {
                setTarget(null);
            } else if (!holdingDefend && !attacking) {
                // Drop chase only if WE drifted too far from the owner — not when the mob
                // is merely farther from the owner than the leash (that cancelled all combat).
                double leash = (double) Config.partyFollowLeash * Config.partyFollowLeash;
                if (distanceToSqr(ownerPlayer) > leash) {
                    setTarget(null);
                }
            }
        }

        // DEFEND hold leash: fight near the post, then return — do not chase across the map.
        if (holdingDefend && defendPos != null) {
            double hold = 12.0;
            double holdSq = hold * hold;
            double selfDist = distanceToSqr(defendPos.getX() + 0.5, defendPos.getY(), defendPos.getZ() + 0.5);
            if (target != null && target.distanceToSqr(defendPos.getX() + 0.5, defendPos.getY(), defendPos.getZ() + 0.5) > holdSq) {
                setTarget(null);
            }
            if (selfDist > holdSq) {
                setTarget(null);
                getNavigation().moveTo(defendPos.getX() + 0.5, defendPos.getY(), defendPos.getZ() + 0.5, 1.1);
            }
        }

        // ATTACK / active RETREAT: don't force-home mid-kite or mid-fallback.
        if (!holdingDefend && !attacking && !retreating && distSq > lost * lost) {
            lostFarTicks++;
            if (lostFarTicks >= 60) {
                markLostFromParty();
            }
        } else {
            lostFarTicks = 0;
        }

    }

    /** Soft-teleport into a standable block near an approximate point (regroup). */
    public void softTeleportNear(Vec3 approx) {
        if (!(level() instanceof ServerLevel sl)) {
            teleportTo(approx.x, approx.y, approx.z);
            return;
        }
        BlockPos stand = findStandBeside(sl, BlockPos.containing(approx));
        teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
    }

    /** Leave FOLLOWING, head to base/home, keep ownership, greet next meeting. Safe while downed. */
    public void markLostFromParty() {
        if (getSurvivorState() != FOLLOWING || owner == null) return;
        lostFarTicks = 0;
        retreatTicks = 0;
        partyOrder = SurvivorParty.ORDER_FOLLOW;
        setTarget(null);
        clearDefendHold();
        pendingReunion = true;
        ServerPlayer p = getOwnerPlayer();
        if (p != null) {
            String key = isDowned()
                ? "message.dead_air.survivor.lost_downed"
                : "message.dead_air.survivor.lost";
            p.sendSystemMessage(Component.translatable(key, getName())
                .withStyle(ChatFormatting.GOLD));
            SurvivorPartyEvents.syncPlayerNow(p);
        }
        // Base resolve only needs server + owner UUID (works when owner is offline).
        if (level() instanceof ServerLevel sl) {
            PlayerBaseStorage.Base base = PlayerBaseManager.getBase(sl.getServer(), owner);
            if (base != null && base.dimension == level().dimension()) {
                home = base.panelPos.immutable();
                homeRange = PlayerBaseManager.signalRange(sl, home);
                settleAfterLost();
                if (!blockPosition().closerThan(home, homeRange)) {
                    BlockPos stand = findStandBeside(sl, home);
                    teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
                }
                return;
            }
        }
        if (home != null) {
            settleAfterLost();
            if (level() instanceof ServerLevel sl && !blockPosition().closerThan(home, Math.max(8, homeRange))) {
                BlockPos stand = findStandBeside(sl, home);
                teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
            }
            return;
        }
        // No base/home: soft-settle where they are.
        home = blockPosition().immutable();
        homeRange = 16;
        settleAfterLost();
    }

    /** SETTLED after lost-from-party — arm supply timer so Collect is not soft-locked forever. */
    private void settleAfterLost() {
        setSurvivorState(SETTLED);
        if (lastSupplyDay < 0) lastSupplyDay = currentDay();
    }

    @Override
    public void die(DamageSource source) {
        // Lethal hits on party members should have become downed already; if something
        // still calls die while flagged downed without force, convert once.
        if (isDowned() && !forceRealDeath && isInParty()
            && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            setHealth(1f);
            return;
        }
        setNoAi(false);
        setDowned(false);
        // Leave FOLLOWING before the corpse lingers — otherwise reconcile re-adds the UUID
        // and the party slot leaks after the entity is removed.
        if (getSurvivorState() == FOLLOWING) {
            setSurvivorState(STRANDED);
        }
        for (int i = 0; i < lootPack.getContainerSize(); i++) {
            ItemStack stack = lootPack.getItem(i);
            if (!stack.isEmpty()) {
                spawnAtLocation(stack);
                lootPack.setItem(i, ItemStack.EMPTY);
            }
        }
        UUID wasOwner = owner;
        // Clear missing-recruit soft-lock before owner is wiped.
        clearMissing();
        super.die(source);
        try {
            if (level() instanceof ServerLevel server && wasOwner != null) {
                SurvivorRoster roster = SurvivorRoster.get(server.getServer());
                if (roster != null) roster.remove(wasOwner, getUUID());
                // clearMissing needs owner — also wipe progress id if still set after owner cleared.
                BroadcastProgress data = BroadcastProgress.get(server.getServer());
                if (data != null) {
                    BroadcastProgress.PlayerProgress prog = data.get(wasOwner);
                    if (getUUID().equals(prog.missingSurvivorId)) {
                        prog.missingSurvivorId = null;
                        data.setDirty();
                    }
                }
                ServerPlayer p = server.getServer().getPlayerList().getPlayer(wasOwner);
                if (p != null && !skipDeathChat) {
                    p.sendSystemMessage(Component.translatable("message.dead_air.survivor.died", getName())
                        .withStyle(ChatFormatting.RED));
                }
            }
        } catch (Throwable t) {
            Dead_air.LOGGER.error("Survivor death cleanup failed for {}", getUUID(), t);
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        // die() cleans KILLED; DISCARDED (/kill leftovers, structure void, etc.) used to leak
        // owned/FOLLOWING roster slots and soft-lock party + recruit caps forever.
        if (!level().isClientSide && owner != null && reason != null && reason.shouldDestroy()) {
            UUID wasOwner = owner;
            try {
                clearMissing();
                if (level() instanceof ServerLevel sl) {
                    SurvivorRoster roster = SurvivorRoster.get(sl.getServer());
                    if (roster != null) roster.remove(wasOwner, getUUID());
                }
            } catch (Throwable t) {
                Dead_air.LOGGER.error("Survivor remove cleanup failed for {}", getUUID(), t);
            }
        }
        super.remove(reason);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Profession", entityData.get(DATA_PROFESSION));
        tag.putInt("SurvivorState", getSurvivorState());
        tag.putInt("Duty", getDuty().ordinal());
        tag.putInt("HealCooldown", healCooldownTicks);
        ListTag lootTag = new ListTag();
        for (int i = 0; i < lootPack.getContainerSize(); i++) {
            ItemStack stack = lootPack.getItem(i);
            if (stack.isEmpty()) continue;
            CompoundTag slot = new CompoundTag();
            slot.putByte("Slot", (byte) i);
            stack.save(slot);
            lootTag.add(slot);
        }
        tag.put("LootPack", lootTag);
        if (owner != null) tag.putUUID("Owner", owner);
        if (caller != null) tag.putUUID("Caller", caller);
        if (home != null) tag.putLong("Home", home.asLong());
        tag.putInt("HomeRange", homeRange);
        tag.putLong("LastSupplyDay", lastSupplyDay);
        CompoundTag idTag = new CompoundTag();
        identity.save(idTag);
        tag.put("Identity", idTag);
        if (defendPos != null) tag.putLong("Defend", defendPos.asLong());
        ListTag patrolTag = new ListTag();
        for (BlockPos p : patrol) {
            CompoundTag pt = new CompoundTag();
            pt.putLong("P", p.asLong());
            patrolTag.add(pt);
        }
        tag.put("Patrol", patrolTag);
        tag.putInt("PatrolIdx", patrolIndex);
        tag.putBoolean("Missing", missing);
        tag.putBoolean("Downed", isDowned());
        tag.putInt("DownedTicks", downedTicks);
        tag.putInt("ReviveProgress", reviveProgress);
        if (reviveHelper != null) tag.putUUID("ReviveHelper", reviveHelper);
        tag.putInt("PartyOrder", partyOrder);
        tag.putBoolean("PendingReunion", pendingReunion);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_PROFESSION, tag.getInt("Profession"));
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        caller = tag.hasUUID("Caller") ? tag.getUUID("Caller") : null;
        home = tag.contains("Home") ? BlockPos.of(tag.getLong("Home")) : null;
        homeRange = tag.contains("HomeRange") ? tag.getInt("HomeRange") : 16;
        lastSupplyDay = tag.contains("LastSupplyDay") ? tag.getLong("LastSupplyDay") : -1;
        if (tag.contains("Identity")) {
            identity.load(tag.getCompound("Identity"));
            identityRolled = true;
        } else if (tag.contains("Name") || getCustomName() != null) {
            // Legacy: keep custom name
            if (getCustomName() != null) identity.name = getCustomName().getString();
            identityRolled = true;
        }
        syncAppearanceData();
        setCustomName(Component.literal(identity.name));
        setCustomNameVisible(true);
        // Legacy "Job" NBT ignored — professions + duties/actions only.
        setDuty(SurvivorDuty.byIndex(tag.getInt("Duty")));
        healCooldownTicks = tag.getInt("HealCooldown");
        lootPack.clearContent();
        ListTag lootTag = tag.getList("LootPack", Tag.TAG_COMPOUND);
        for (int i = 0; i < lootTag.size(); i++) {
            CompoundTag slot = lootTag.getCompound(i);
            int idx = slot.getByte("Slot") & 0xFF;
            if (idx < lootPack.getContainerSize()) {
                lootPack.setItem(idx, ItemStack.of(slot));
            }
        }
        defendPos = tag.contains("Defend") ? BlockPos.of(tag.getLong("Defend")) : null;
        patrol.clear();
        ListTag patrolTag = tag.getList("Patrol", Tag.TAG_COMPOUND);
        for (int i = 0; i < patrolTag.size(); i++) {
            patrol.add(BlockPos.of(patrolTag.getCompound(i).getLong("P")));
        }
        patrolIndex = tag.getInt("PatrolIdx");
        missing = tag.getBoolean("Missing");
        setSurvivorState(tag.getInt("SurvivorState"));
        setDowned(tag.getBoolean("Downed"));
        downedTicks = tag.getInt("DownedTicks");
        reviveProgress = tag.getInt("ReviveProgress");
        reviveHelper = tag.hasUUID("ReviveHelper") ? tag.getUUID("ReviveHelper") : null;
        if (isDowned()) {
            setPose(Pose.STANDING);
            setXRot(0f);
            setNoAi(true);
            setHealth(Math.max(1f, getHealth()));
            refreshDimensions();
        } else {
            reviveProgress = 0;
            reviveHelper = null;
        }
        int loadedOrder = tag.contains("PartyOrder") ? tag.getInt("PartyOrder") : SurvivorParty.ORDER_FOLLOW;
        partyOrder = Math.max(SurvivorParty.ORDER_FOLLOW, Math.min(SurvivorParty.ORDER_REGROUP, loadedOrder));
        // Instant / sticky-only orders should not survive a reload mid-action.
        if (partyOrder == SurvivorParty.ORDER_RETREAT || partyOrder == SurvivorParty.ORDER_REGROUP) {
            partyOrder = SurvivorParty.ORDER_FOLLOW;
        }
        // Party orders are FOLLOWING-only (older saves could leave ATTACK stuck on SETTLED).
        if (getSurvivorState() != FOLLOWING) {
            partyOrder = SurvivorParty.ORDER_FOLLOW;
        }
        pendingReunion = tag.getBoolean("PendingReunion");
        SurvivorProgress.applyMaxHealth(this);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    // ---------- goals ----------

    /** Unarmed / cautious: run from any Enemy (not Monster-only). */
    private class FleeHostilesGoal extends Goal {
        private static final double RANGE = 10.0;
        private LivingEntity fleeFrom;
        private int repath;

        FleeHostilesGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!shouldFlee()) return false;
            dismountIdleForCombat();
            fleeFrom = findHostile(RANGE);
            return fleeFrom != null;
        }

        @Override
        public boolean canContinueToUse() {
            return shouldFlee() && fleeFrom != null && fleeFrom.isAlive()
                && distanceToSqr(fleeFrom) < RANGE * RANGE * 1.5;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void tick() {
            if (fleeFrom == null) return;
            if (--repath > 0) return;
            repath = 8;
            Vec3 away = position().subtract(fleeFrom.position()).normalize().scale(8.0).add(position());
            getNavigation().moveTo(away.x, away.y, away.z, 1.35);
        }

        @Override
        public void stop() {
            fleeFrom = null;
            getNavigation().stop();
        }

        @Nullable
        private LivingEntity findHostile(double range) {
            LivingEntity best = null;
            double bestD = range * range;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range),
                ent -> ent instanceof Enemy && ent.isAlive() && !(ent instanceof SurvivorEntity) && ent != SurvivorEntity.this)) {
                double d = distanceToSqr(e);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
            return best;
        }
    }

    private class MedicSupportGoal extends Goal {
        private int repath;

        MedicSupportGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return medicShouldSupport();
        }

        @Override
        public boolean canContinueToUse() {
            // Keep pathing even if cooldown just started; canUse gates new runs.
            if (getProfession() != Profession.MEDIC || isDowned() || owner == null) return false;
            int state = getSurvivorState();
            if (state != FOLLOWING && state != SETTLED) return false;
            if (findDownedAlly(16) != null) return true;
            if (state != FOLLOWING || healCooldownTicks > 0) return false;
            ServerPlayer ownerPlayer = getOwnerPlayer();
            return ownerPlayer != null && ownerPlayer.level() == level()
                && ownerPlayer.getHealth() < ownerPlayer.getMaxHealth() - 2f;
        }

        @Override
        public void tick() {
            if (owner == null) return;
            setTarget(null);
            SurvivorEntity downed = findDownedAlly(16);
            ServerPlayer ownerPlayer = getOwnerPlayer();
            if (downed != null) {
                getLookControl().setLookAt(downed, 30f, 30f);
                if (distanceToSqr(downed) > 4) {
                    if (--repath <= 0) {
                        repath = 8;
                        getNavigation().moveTo(downed, 1.2);
                    }
                } else if (healCooldownTicks <= 0) {
                    // Revive even if owner is unloaded.
                    downed.reviveFrom();
                    healCooldownTicks = 20 * 45;
                    SurvivorProgress.awardXp(SurvivorEntity.this, 5, SurvivorProgress.SkillBias.MEDICINE);
                    if (ownerPlayer != null) {
                        ownerPlayer.sendSystemMessage(Component.translatable(
                            "message.dead_air.survivor.heal_revived", getName(), downed.getName())
                            .withStyle(ChatFormatting.GREEN));
                    }
                }
                return;
            }
            if (ownerPlayer != null && ownerPlayer.level() == level() && healCooldownTicks <= 0) {
                getLookControl().setLookAt(ownerPlayer, 30f, 30f);
                if (distanceToSqr(ownerPlayer) > 9) {
                    if (--repath <= 0) {
                        repath = 8;
                        getNavigation().moveTo(ownerPlayer, 1.15);
                    }
                } else {
                    tryMedicHeal(ownerPlayer);
                }
            }
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    /** Walk up to ground loot and stash it in the scavenger satchel. */
    private class ScavengeLootGoal extends Goal {
        private ItemEntity targetItem;
        private int repath;
        private int holdTicks;

        ScavengeLootGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!usesLootPack() || isDowned() || owner == null) return false;
            // Party scavengers only — settled base scavengers shouldn't strip farm drops.
            if (getSurvivorState() != FOLLOWING) return false;
            if (retreatTicks > 0 || partyOrder == SurvivorParty.ORDER_RETREAT) return false;
            if (getTarget() != null && getTarget().isAlive()) return false;
            if (!lootPackHasSpace()) return false;
            if (!(level() instanceof ServerLevel server)) return false;
            targetItem = findLoot(server);
            return targetItem != null;
        }

        @Override
        public boolean canContinueToUse() {
            if (retreatTicks > 0 || partyOrder == SurvivorParty.ORDER_RETREAT) return false;
            return getSurvivorState() == FOLLOWING
                && targetItem != null && targetItem.isAlive() && !targetItem.getItem().isEmpty()
                && lootPackHasSpace() && (getTarget() == null || !getTarget().isAlive());
        }

        @Override
        public void stop() {
            targetItem = null;
            repath = 0;
            holdTicks = 0;
            getNavigation().stop();
        }

        @Override
        public void tick() {
            if (targetItem == null || !targetItem.isAlive()) return;
            getLookControl().setLookAt(targetItem, 30f, 30f);
            if (distanceToSqr(targetItem) > 2.25) {
                holdTicks = 0;
                if (--repath <= 0) {
                    repath = 8;
                    getNavigation().moveTo(targetItem, 1.2);
                }
                return;
            }
            getNavigation().stop();
            if (++holdTicks < 4) return; // brief pause to "pick up"
            ItemStack stack = targetItem.getItem();
            ItemStack leftover = tryInsertLoot(stack);
            if (leftover.isEmpty()) {
                targetItem.discard();
                playSound(SoundEvents.ITEM_PICKUP, 0.35f, 1.0f + random.nextFloat() * 0.2f);
            } else if (leftover.getCount() != stack.getCount()) {
                targetItem.setItem(leftover);
                playSound(SoundEvents.ITEM_PICKUP, 0.25f, 1.0f + random.nextFloat() * 0.2f);
            }
            targetItem = null;
            holdTicks = 0;
        }

        @Nullable
        private ItemEntity findLoot(ServerLevel server) {
            ItemEntity best = null;
            double bestDist = 12.0 * 12.0;
            for (ItemEntity item : server.getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(12))) {
                if (!item.isAlive() || item.hasPickUpDelay() || item.getItem().isEmpty()) continue;
                Entity thrower = item.getOwner();
                if (thrower instanceof Player other && (owner == null || !other.getUUID().equals(owner))) {
                    continue;
                }
                double d = distanceToSqr(item);
                if (d < bestDist) {
                    bestDist = d;
                    best = item;
                }
            }
            return best;
        }
    }

    private class SurvivorGunGoal extends Goal {
        SurvivorGunGoal() {
            setFlags(EnumSet.of(Flag.LOOK, Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return SurvivorGunCompat.holdingUsableGun(SurvivorEntity.this)
                && shouldFight()
                && getTarget() != null
                && getTarget().isAlive();
        }

        @Override
        public void start() {
            // Draw once when combat begins — tryShoot must not re-draw every cooldown tick.
            SurvivorGunCompat.draw(SurvivorEntity.this, getMainHandItem());
            gunCooldown = 8; // brief beat so draw cooldown can clear
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) return;
            getLookControl().setLookAt(target, 30f, 30f);
            double dist = distanceToSqr(target);
            float prefer = identity.orientation == SurvivorIdentity.CombatOrientation.AGGRESSIVE ? 64f : 144f;
            if (dist > prefer) {
                getNavigation().moveTo(target, 1.1);
            } else {
                getNavigation().stop();
            }
            if (--gunCooldown <= 0) {
                gunCooldown = identity.orientation == SurvivorIdentity.CombatOrientation.CAUTIOUS ? 25 : 12;
                SurvivorGunCompat.tryShoot(SurvivorEntity.this, target);
            }
        }
    }

    private class FollowOwnerGoal extends Goal {
        private int repath;

        FollowOwnerGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (isDowned()) return false;
            if (getTarget() != null) return false; // combat / flee wins
            if (retreatTicks > 0 || partyOrder == SurvivorParty.ORDER_RETREAT) return false;
            if (getDuty() == SurvivorDuty.DEFEND && partyOrder == SurvivorParty.ORDER_DEFEND) return false;
            ServerPlayer p = getSurvivorState() == FOLLOWING ? getOwnerPlayer() : null;
            return p != null && p.level() == level() && distanceTo(p) > 3.5;
        }

        @Override
        public boolean canContinueToUse() {
            if (getTarget() != null) return false;
            if (retreatTicks > 0 || partyOrder == SurvivorParty.ORDER_RETREAT) return false;
            if (getDuty() == SurvivorDuty.DEFEND && partyOrder == SurvivorParty.ORDER_DEFEND) return false;
            ServerPlayer p = getSurvivorState() == FOLLOWING ? getOwnerPlayer() : null;
            return p != null && p.level() == level() && distanceTo(p) > 2.2;
        }

        @Override
        public void tick() {
            ServerPlayer p = getOwnerPlayer();
            if (p == null || level().isClientSide) return;
            getLookControl().setLookAt(p, 10f, getMaxHeadXRot());
            // Catch up even if owner is swimming / boating / flying — softTeleportNear finds standable ground.
            if (distanceTo(p) > 24) {
                int slot = SurvivorParty.formationIndex(p.server, SurvivorEntity.this);
                softTeleportNear(SurvivorPartyOrders.formationPoint(p, slot));
                getNavigation().stop();
                return;
            }
            if (--repath <= 0) {
                repath = 10;
                int slot = SurvivorParty.formationIndex(p.server, SurvivorEntity.this);
                Vec3 slotPos = SurvivorPartyOrders.formationPoint(p, slot);
                getNavigation().moveTo(slotPos.x, slotPos.y, slotPos.z, p.isSprinting() ? 1.35 : 1.1);
            }
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    private class DefendGoal extends Goal {
        private int repath;

        DefendGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (getDuty() != SurvivorDuty.DEFEND || defendPos == null || getTarget() != null) return false;
            // Settled guards, or party DEFEND order while still FOLLOWING.
            return getSurvivorState() == SETTLED
                || (getSurvivorState() == FOLLOWING && partyOrder == SurvivorParty.ORDER_DEFEND);
        }

        @Override
        public void tick() {
            if (defendPos == null) return;
            if (!blockPosition().closerThan(defendPos, 4)) {
                if (--repath <= 0) {
                    repath = 20;
                    getNavigation().moveTo(defendPos.getX() + 0.5, defendPos.getY(), defendPos.getZ() + 0.5, 0.9);
                }
            }
        }
    }

    private class PatrolGoal extends Goal {
        private int repath;

        PatrolGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return getDuty() == SurvivorDuty.PATROL && patrol.size() >= 2
                && getSurvivorState() == SETTLED && getTarget() == null;
        }

        @Override
        public void tick() {
            if (patrol.isEmpty()) return;
            BlockPos dest = patrol.get(Math.floorMod(patrolIndex, patrol.size()));
            if (blockPosition().closerThan(dest, 2.5)) {
                patrolIndex = Math.floorMod(patrolIndex + 1, patrol.size());
                repath = 0;
                return;
            }
            if (--repath <= 0) {
                repath = 15;
                getNavigation().moveTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, 0.85);
            }
        }
    }

    @Nullable
    public BlockPos getCampIdleClaim() {
        return campIdleClaim;
    }

    private void clearCampIdleClaim() {
        campIdleClaim = null;
    }

    /** Other survivors already claiming this POI (excludes {@code self}). */
    private static int countCampIdleClaims(Level level, BlockPos poi, @Nullable SurvivorEntity self) {
        if (level == null || poi == null) return 0;
        AABB box = new AABB(poi).inflate(24.0);
        int n = 0;
        for (SurvivorEntity s : level.getEntitiesOfClass(SurvivorEntity.class, box,
            e -> e.isAlive() && e != self)) {
            BlockPos claim = s.getCampIdleClaim();
            if (claim != null && claim.equals(poi)) n++;
        }
        return n;
    }

    private boolean isSleepSpotTaken(BlockPos poi) {
        if (countCampIdleClaims(level(), poi, this) > 0) return true;
        AABB box = new AABB(poi).inflate(1.5);
        for (SurvivorEntity s : level().getEntitiesOfClass(SurvivorEntity.class, box,
            e -> e.isAlive() && e != this && e.isSleeping())) {
            var sp = s.getSleepingPos();
            if (sp.isPresent() && sp.get().equals(poi)) return true;
        }
        return false;
    }

    private boolean isChairTaken(BlockPos chair) {
        if (uk.co.extraspecialstudio.dead_air.entity.SeatEntity.isOccupied(level(), chair)) return true;
        return countCampIdleClaims(level(), chair, this) > 0;
    }

    private boolean isFirepitFull(BlockPos fire) {
        return countCampIdleClaims(level(), fire, this) >= FIREPIT_IDLE_CAP;
    }

    /** Claim POI for idle use. Capacity: firepit 4, seat/sleep 1. */
    private boolean tryClaimCampIdle(BlockPos poi, CampIdlePoiGoal.Mode mode) {
        if (poi == null) return false;
        int cap = mode == CampIdlePoiGoal.Mode.FIRE ? FIREPIT_IDLE_CAP : 1;
        if (countCampIdleClaims(level(), poi, this) >= cap) return false;
        if (mode == CampIdlePoiGoal.Mode.SIT && isChairTaken(poi)) return false;
        if (mode == CampIdlePoiGoal.Mode.SLEEP && isSleepSpotTaken(poi)) return false;
        if (mode == CampIdlePoiGoal.Mode.FIRE && isFirepitFull(poi)) return false;
        campIdleClaim = poi.immutable();
        return true;
    }

    /**
     * Occasional idle: path to tent/bed to rest, or stand by a firepit — not permanent.
     * Only when SETTLED (camp/base) or STRANDED with duty NONE.
     */
    private class CampIdlePoiGoal extends Goal {
        private static final int SEARCH = 16;
        private static final int STAY_MIN = 80;
        private static final int STAY_MAX = 200;
        private static final int COOLDOWN = 400;
        private static final int APPROACH_TIMEOUT = 160;

        enum Mode { SLEEP, FIRE, SIT }

        private Mode mode;
        private BlockPos poi;
        private BlockPos standAt;
        private int stayTicks;
        private int approachTicks;
        private int cooldown;

        CampIdlePoiGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            if (isDowned() || getTarget() != null || isPassenger()) return false;
            if (hostileNearby(12.0)) return false;
            if (getDuty() != SurvivorDuty.NONE) return false;
            int st = getSurvivorState();
            if (st != SETTLED && st != STRANDED) return false;
            if (getRandom().nextInt(60) != 0) return false;
            BlockPos origin = home != null ? home : blockPosition();
            BlockPos chair = findChair(origin);
            BlockPos tent = findSleepPoi(origin, true);
            BlockPos bed = findSleepPoi(origin, false);
            BlockPos fire = findFirePoi(origin);
            if (chair == null && tent == null && bed == null && fire == null) return false;

            float roll = getRandom().nextFloat();
            Mode pickMode = null;
            BlockPos pickPoi = null;
            BlockPos pickStand = null;
            // Sit and firepit are the common idle beats; sleep less often during day wander.
            if (chair != null && fire != null && roll < 0.55f) {
                if (roll < 0.28f) {
                    pickMode = Mode.SIT;
                    pickPoi = chair;
                    pickStand = standBeside(chair, false);
                } else {
                    pickMode = Mode.FIRE;
                    pickPoi = fire;
                    pickStand = standBeside(fire, true);
                }
            } else if (chair != null && roll < 0.4f) {
                pickMode = Mode.SIT;
                pickPoi = chair;
                pickStand = standBeside(chair, false);
            } else if ((tent != null || bed != null) && (fire == null || roll < 0.65f)) {
                pickMode = Mode.SLEEP;
                pickPoi = tent != null ? tent : bed;
                boolean atTent = level().getBlockState(pickPoi).is(
                    uk.co.extraspecialstudio.dead_air.block.DeadAirBlocks.SURVIVOR_TENT.get());
                pickStand = standBeside(pickPoi, atTent);
            } else if (fire != null) {
                pickMode = Mode.FIRE;
                pickPoi = fire;
                pickStand = standBeside(pickPoi, true);
            } else if (chair != null) {
                pickMode = Mode.SIT;
                pickPoi = chair;
                pickStand = standBeside(chair, false);
            } else {
                pickMode = Mode.SLEEP;
                pickPoi = tent != null ? tent : bed;
                if (pickPoi == null) return false;
                pickStand = standBeside(pickPoi, true);
            }
            if (pickPoi == null || pickStand == null || pickMode == null) return false;
            if (!tryClaimCampIdle(pickPoi, pickMode)) return false;
            mode = pickMode;
            poi = pickPoi;
            standAt = pickStand;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            if (isDowned() || getTarget() != null) return false;
            if (hostileNearby(12.0)) return false;
            if (getDuty() != SurvivorDuty.NONE) return false;
            int st = getSurvivorState();
            if (st != SETTLED && st != STRANDED) return false;
            if (poi == null || !poiStillAvailable()) return false;
            if (mode == Mode.SIT && isPassenger()) {
                return stayTicks > 0;
            }
            return stayTicks > 0 && standAt != null;
        }

        @Override
        public void start() {
            stayTicks = STAY_MIN + getRandom().nextInt(STAY_MAX - STAY_MIN + 1);
            approachTicks = APPROACH_TIMEOUT;
            getNavigation().moveTo(standAt.getX() + 0.5, standAt.getY(), standAt.getZ() + 0.5, 0.75);
        }

        @Override
        public void tick() {
            if (poi == null || standAt == null) return;
            if (!poiStillAvailable()) {
                stayTicks = 0;
                return;
            }
            if (mode == Mode.SIT && isPassenger()) {
                stayTicks--;
                return;
            }
            double dist = distanceToSqr(standAt.getX() + 0.5, standAt.getY(), standAt.getZ() + 0.5);
            if (dist > 2.25) {
                if (--approachTicks <= 0) {
                    stayTicks = 0;
                    return;
                }
                getNavigation().moveTo(standAt.getX() + 0.5, standAt.getY(), standAt.getZ() + 0.5, 0.75);
                return;
            }
            getNavigation().stop();
            if (mode == Mode.SIT) {
                var state = level().getBlockState(poi);
                if (!state.is(uk.co.extraspecialstudio.dead_air.block.DeadAirBlocks.FOLDING_CHAIR.get())
                    || uk.co.extraspecialstudio.dead_air.entity.SeatEntity.isOccupied(level(), poi)) {
                    stayTicks = 0;
                    return;
                }
                // Close enough beside the chair — mount (force ride inside trySit).
                if (distanceToSqr(poi.getX() + 0.5, poi.getY(), poi.getZ() + 0.5) <= 4.0) {
                    if (!uk.co.extraspecialstudio.dead_air.entity.SeatEntity.trySit(
                        level(), poi, state, SurvivorEntity.this)) {
                        stayTicks = 0;
                        return;
                    }
                }
                stayTicks--;
                return;
            }
            getLookControl().setLookAt(poi.getX() + 0.5, poi.getY() + 0.5, poi.getZ() + 0.5);
            if (mode == Mode.SLEEP && !isSleeping()) {
                try {
                    // Beds: vanilla sleep. Tent: pose-only (not a BedBlock).
                    startSleeping(poi);
                } catch (Throwable ignored) {
                    // Fallback: just idle at standAt if sleep rejects the block.
                }
            }
            stayTicks--;
        }

        @Override
        public void stop() {
            if (isSleeping()) {
                try {
                    stopSleeping();
                } catch (Throwable ignored) {
                }
            }
            if (isPassenger() && getVehicle() instanceof uk.co.extraspecialstudio.dead_air.entity.SeatEntity) {
                stopRiding();
            }
            clearCampIdleClaim();
            poi = null;
            standAt = null;
            stayTicks = 0;
            approachTicks = 0;
            cooldown = COOLDOWN + getRandom().nextInt(200);
            getNavigation().stop();
        }

        /** Still valid for us (our claim holds; seat not stolen; fire not over-cap). */
        private boolean poiStillAvailable() {
            if (poi == null || mode == null) return false;
            BlockPos claim = getCampIdleClaim();
            if (claim == null || !claim.equals(poi)) return false;
            return switch (mode) {
                case SIT -> !uk.co.extraspecialstudio.dead_air.entity.SeatEntity.isOccupied(level(), poi)
                    || isPassenger();
                case SLEEP -> {
                    // Someone else sleeping here while we are not yet asleep → leave.
                    if (isSleeping()) yield true;
                    AABB box = new AABB(poi).inflate(1.5);
                    for (SurvivorEntity s : level().getEntitiesOfClass(SurvivorEntity.class, box,
                        e -> e.isAlive() && e != SurvivorEntity.this && e.isSleeping())) {
                        var sp = s.getSleepingPos();
                        if (sp.isPresent() && sp.get().equals(poi)) yield false;
                    }
                    yield true;
                }
                case FIRE -> countCampIdleClaims(level(), poi, SurvivorEntity.this) < FIREPIT_IDLE_CAP;
            };
        }

        private BlockPos findChair(BlockPos origin) {
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (int dx = -SEARCH; dx <= SEARCH; dx++) {
                for (int dy = -2; dy <= 3; dy++) {
                    for (int dz = -SEARCH; dz <= SEARCH; dz++) {
                        m.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                        var state = level().getBlockState(m);
                        if (!state.is(uk.co.extraspecialstudio.dead_air.block.DeadAirBlocks.FOLDING_CHAIR.get())) {
                            continue;
                        }
                        if (isChairTaken(m)) continue;
                        double d = m.distSqr(origin);
                        if (d < bestD) {
                            bestD = d;
                            best = m.immutable();
                        }
                    }
                }
            }
            return best;
        }

        /** Prefer an adjacent air block so we do not stand in fire or clip into the tent. */
        private BlockPos standBeside(BlockPos target, boolean awayFromFire) {
            int[][] offsets = awayFromFire
                ? new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}, {3, 0}, {-3, 0}}
                : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}};
            for (int[] o : offsets) {
                BlockPos p = target.offset(o[0], 0, o[1]);
                if (level().getBlockState(p).isAir() && level().getBlockState(p.above()).isAir()
                    && !level().getBlockState(p.below()).isAir()) {
                    return p;
                }
            }
            // Never fall back onto the POI itself (firepit / tent mesh).
            return null;
        }

        private BlockPos findFirePoi(BlockPos origin) {
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (int dx = -SEARCH; dx <= SEARCH; dx++) {
                for (int dy = -2; dy <= 3; dy++) {
                    for (int dz = -SEARCH; dz <= SEARCH; dz++) {
                        m.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                        var state = level().getBlockState(m);
                        boolean match = state.is(uk.co.extraspecialstudio.dead_air.block.DeadAirBlocks.SIGNAL_FIREPIT.get())
                            || state.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                            || state.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE);
                        if (!match || isFirepitFull(m)) continue;
                        double d = m.distSqr(origin);
                        if (d < bestD) {
                            bestD = d;
                            best = m.immutable();
                        }
                    }
                }
            }
            return best;
        }

        private BlockPos findSleepPoi(BlockPos origin, boolean tentOnly) {
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (int dx = -SEARCH; dx <= SEARCH; dx++) {
                for (int dy = -2; dy <= 3; dy++) {
                    for (int dz = -SEARCH; dz <= SEARCH; dz++) {
                        m.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                        var state = level().getBlockState(m);
                        boolean match = tentOnly
                            ? state.is(uk.co.extraspecialstudio.dead_air.block.DeadAirBlocks.SURVIVOR_TENT.get())
                            : state.is(net.minecraft.tags.BlockTags.BEDS);
                        if (!match || isSleepSpotTaken(m)) continue;
                        double d = m.distSqr(origin);
                        if (d < bestD) {
                            bestD = d;
                            best = m.immutable();
                        }
                    }
                }
            }
            return best;
        }
    }
}
