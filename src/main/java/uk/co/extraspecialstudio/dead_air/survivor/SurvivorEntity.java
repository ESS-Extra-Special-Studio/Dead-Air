package uk.co.extraspecialstudio.dead_air.survivor;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.base.PlayerBaseManager;
import uk.co.extraspecialstudio.dead_air.base.PlayerBaseStorage;
import uk.co.extraspecialstudio.dead_air.broadcast.BroadcastProgress;
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

/** Recruitable survivor: identity, jobs, base settle, combat + soft TaCZ guns. */
public class SurvivorEntity extends PathfinderMob {
    public static final int STRANDED = 0;
    public static final int FOLLOWING = 1;
    public static final int SETTLED = 2;

    public enum Profession {
        SCAVENGER("scavenger"), MEDIC("medic"), RADIO_OPERATOR("radio_operator"),
        PILOT("pilot"), MILITARY("military"), CIVILIAN("civilian");

        public final String id;

        Profession(String id) {
            this.id = id;
        }

        static Profession byIndex(int i) {
            Profession[] v = values();
            return v[Math.floorMod(i, v.length)];
        }
    }

    private static final EntityDataAccessor<Integer> DATA_PROFESSION =
        SynchedEntityData.defineId(SurvivorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STATE =
        SynchedEntityData.defineId(SurvivorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_JOB =
        SynchedEntityData.defineId(SurvivorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DUTY =
        SynchedEntityData.defineId(SurvivorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_DOWNED =
        SynchedEntityData.defineId(SurvivorEntity.class, EntityDataSerializers.BOOLEAN);

    private static final int BLEED_OUT_TICKS = 20 * 90;
    private static final int REVIVE_TICKS = 20 * 3;

    @Nullable private UUID owner;
    @Nullable private UUID caller;
    @Nullable private BlockPos home;
    private int homeRange = 16;
    private long lastSupplyDay = -1;
    private final SurvivorIdentity identity = new SurvivorIdentity();
    /** True after {@link #initRandom} or loading a persisted Identity â€” spawn eggs need this. */
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
    private boolean forceRealDeath;
    private boolean skipDeathChat;
    private int partyOrder = SurvivorParty.ORDER_FOLLOW;
    private int retreatTicks;
    @Nullable private BlockPos partyDefendPos;

    public SurvivorEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        // Always drop equipped gear on death â€” default Mob chances (~8.5%) would silently delete most kits.
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
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PROFESSION, 0);
        builder.define(DATA_STATE, STRANDED);
        builder.define(DATA_JOB, SurvivorJob.IDLE.ordinal());
        builder.define(DATA_DUTY, SurvivorDuty.NONE.ordinal());
        builder.define(DATA_DOWNED, false);
    }

    public boolean isDowned() {
        return entityData.get(DATA_DOWNED);
    }

    private void setDowned(boolean downed) {
        entityData.set(DATA_DOWNED, downed);
    }

    private boolean isInParty() {
        return owner != null;
    }

    public void initRandom(RandomSource random, @Nullable UUID caller) {
        this.caller = caller;
        entityData.set(DATA_PROFESSION, random.nextInt(Profession.values().length));
        identity.roll(random);
        identity.biasSkillsForProfession(getProfession(), random);
        identityRolled = true;
        setCustomName(Component.literal(identity.name));
        setCustomNameVisible(true);
        // TaCZ / Point Blank / Cold Sweat when present
        uk.co.extraspecialstudio.dead_air.compat.SurvivorGunCompat.applyModdedStarterLoadout(this, random);
    }

    /**
     * Spawn eggs / {@code /summon} go through this; EB {@link SurvivorSpawner} and debug commands
     * call {@link #initRandom} before {@code addFreshEntity} and skip finalizeSpawn.
     */
    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData);
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

    public SurvivorJob getJob() {
        return SurvivorJob.byIndex(entityData.get(DATA_JOB));
    }

    public void setJob(SurvivorJob job) {
        entityData.set(DATA_JOB, job.ordinal());
    }

    public SurvivorDuty getDuty() {
        return SurvivorDuty.byIndex(entityData.get(DATA_DUTY));
    }

    public void setDuty(SurvivorDuty duty) {
        entityData.set(DATA_DUTY, duty.ordinal());
    }

    private void setSurvivorState(int state) {
        entityData.set(DATA_STATE, state);
        if (state == SETTLED && home != null) {
            restrictTo(home, homeRange);
        } else if (state != SETTLED) {
            clearRestriction();
        }
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
        retreatTicks = Math.max(0, ticks);
        partyOrder = SurvivorParty.ORDER_RETREAT;
        setTarget(null);
        getNavigation().stop();
    }

    public void clearRetreat() {
        retreatTicks = 0;
        if (partyOrder == SurvivorParty.ORDER_RETREAT) {
            partyOrder = SurvivorParty.ORDER_FOLLOW;
        }
    }

    public void applyPartyDefend(BlockPos pos) {
        partyDefendPos = pos == null ? null : pos.immutable();
        partyOrder = SurvivorParty.ORDER_DEFEND;
        if (pos != null) {
            getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
        }
    }

    public void clearDefendHold() {
        partyDefendPos = null;
        if (partyOrder == SurvivorParty.ORDER_DEFEND) {
            partyOrder = SurvivorParty.ORDER_FOLLOW;
        }
    }

    public boolean tryRegroup(long gameTime) {
        ServerPlayer ownerPlayer = getOwnerPlayer();
        if (ownerPlayer == null || ownerPlayer.level() != level()) return false;
        softTeleportNear(ownerPlayer.position());
        partyOrder = SurvivorParty.ORDER_FOLLOW;
        clearRetreat();
        clearDefendHold();
        return true;
    }

    public void softTeleportNear(Vec3 target) {
        teleportTo(target.x, target.y, target.z);
    }

    @Nullable
    public ServerPlayer getOwnerPlayer() {
        if (owner == null || !(level() instanceof ServerLevel server)) return null;
        return server.getServer().getPlayerList().getPlayer(owner);
    }

    public boolean isOwnedBy(Player player) {
        return owner != null && player.getUUID().equals(owner);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.3) {
            @Override
            public boolean canUse() {
                return shouldFlee() && super.canUse();
            }
        });
        goalSelector.addGoal(2, new SurvivorGunGoal());
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15, true) {
            @Override
            public boolean canUse() {
                return wantsMelee() && super.canUse();
            }
        });
        goalSelector.addGoal(4, new AvoidEntityGoal<>(this, Monster.class, 10f, 1.0, 1.35) {
            @Override
            public boolean canUse() {
                return shouldFlee() && getSurvivorState() != FOLLOWING && super.canUse();
            }
        });
        goalSelector.addGoal(5, new FollowOwnerGoal());
        goalSelector.addGoal(6, new DefendGoal());
        goalSelector.addGoal(7, new PatrolGoal());
        goalSelector.addGoal(8, new RestAtNightGoal());
        goalSelector.addGoal(9, new MoveTowardsRestrictionGoal(this, 1.0));
        goalSelector.addGoal(10, new WaterAvoidingRandomStrollGoal(this, 0.6) {
            @Override
            public boolean canUse() {
                return getSurvivorState() != FOLLOWING
                    && getDuty() == SurvivorDuty.NONE
                    && super.canUse();
            }
        });
        goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(12, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, 12, true, false,
            e -> shouldFight()));
    }

    private boolean shouldFlee() {
        if (SurvivorGunCompat.holdingGun(this) || hasMeleeWeapon()) return false;
        return switch (identity.orientation) {
            case CAUTIOUS, SUPPORT -> true;
            case DEFENSIVE -> getHealth() < getMaxHealth() * 0.4f;
            case AGGRESSIVE -> false;
        } || getProfession() == Profession.MEDIC && !hasMeleeWeapon();
    }

    private boolean shouldFight() {
        if (getSurvivorState() == STRANDED) return false;
        if (getJob() == SurvivorJob.GUARD || getDuty() == SurvivorDuty.DEFEND) return true;
        if (SurvivorGunCompat.holdingGun(this) || hasMeleeWeapon()) return true;
        return switch (identity.orientation) {
            case AGGRESSIVE -> true;
            case DEFENSIVE -> getSurvivorState() == FOLLOWING || getJob() == SurvivorJob.GUARD;
            case SUPPORT, CAUTIOUS -> false;
        } || getProfession() == Profession.SCAVENGER;
    }

    private boolean wantsMelee() {
        return shouldFight() && !SurvivorGunCompat.holdingGun(this) && getTarget() != null;
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

        if (!level().isClientSide && player instanceof ServerPlayer sp && isOwnedBy(sp) && !held.isEmpty()) {
            if (tryEquipFromPlayer(sp, held)) {
                return InteractionResult.SUCCESS;
            }
        }

        if (!level().isClientSide && player instanceof ServerPlayer sp) {
            boolean mine = isOwnedBy(sp);
            boolean hasBase = PlayerBaseManager.hasBase(sp.server, sp.getUUID());
            DeadAirNet.sendTo(sp, new SurvivorScreenPacket(
                getId(), identity.name, getProfession().id, getSurvivorState(), mine,
                mine && supplyReady(), dialogueLine(mine),
                hasBase, getJob().id, getDuty().ordinal(),
                identity.personality.name().toLowerCase(),
                identity.orientation.name().toLowerCase(),
                identity.hopeLineKey(), missing));
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    public boolean isMissing() {
        return missing;
    }

    public void forceDefendAt(BlockPos panel) {
        defendPos = panel.immutable();
        home = defendPos;
        homeRange = 16;
        setDuty(SurvivorDuty.DEFEND);
        setJob(SurvivorJob.GUARD);
        setSurvivorState(SETTLED);
        missing = false;
    }

    public void markMissingAt(BlockPos at) {
        missing = true;
        setDuty(SurvivorDuty.NONE);
        defendPos = null;
        patrol.clear();
        home = null;
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
        }
    }

    /** Equip gear from player hand into armor / main / off. Returns true if consumed an equip action. */
    private boolean tryEquipFromPlayer(ServerPlayer player, ItemStack held) {
        if (held.getItem() instanceof ArmorItem armor) {
            EquipmentSlot armorSlot = armor.getEquipmentSlot();
            ItemStack previous = getItemBySlot(armorSlot);
            setItemSlot(armorSlot, held.split(1));
            if (!previous.isEmpty() && !player.getInventory().add(previous)) player.drop(previous, false);
            player.sendSystemMessage(Component.translatable("message.dead_air.survivor.equipped", getName())
                .withStyle(ChatFormatting.GREEN));
            return true;
        }
        // Weapons / tools / TaCZ guns / anything else -> main hand (shift = offhand)
        if (player.isShiftKeyDown()) {
            ItemStack previous = getItemBySlot(EquipmentSlot.OFFHAND);
            setItemSlot(EquipmentSlot.OFFHAND, held.split(1));
            if (!previous.isEmpty() && !player.getInventory().add(previous)) player.drop(previous, false);
        } else if (isWeaponLike(held) || SurvivorGunCompat.isGun(held)) {
            ItemStack previous = getMainHandItem();
            ItemStack give = held.split(1);
            if (SurvivorGunCompat.isGun(give)) SurvivorGunCompat.prepareGunForNpc(give);
            setItemSlot(EquipmentSlot.MAINHAND, give);
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
        boolean mine = player.getUUID().equals(owner);
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
                owner = player.getUUID();
                if (roster != null) roster.add(owner, getUUID());
                clearMissing();
                setSurvivorState(FOLLOWING);
                player.sendSystemMessage(Component.translatable("message.dead_air.survivor.recruited", getName())
                    .withStyle(ChatFormatting.GREEN));
            }
            case SurvivorActionPacket.FOLLOW -> {
                if (!mine) return;
                boolean wasMissing = missing;
                setDuty(SurvivorDuty.NONE);
                defendPos = null;
                clearMissing();
                setSurvivorState(FOLLOWING);
                player.sendSystemMessage(Component.translatable(
                    wasMissing ? "message.dead_air.survivor.found" : "message.dead_air.survivor.following", getName())
                    .withStyle(ChatFormatting.GREEN));
            }
            case SurvivorActionPacket.SETTLE -> settleNearPlayer(player);
            case SurvivorActionPacket.SEND_TO_BASE -> sendToBase(player);
            case SurvivorActionPacket.COLLECT -> {
                if (!mine || !supplyReady()) return;
                giveSupplies(player);
                lastSupplyDay = currentDay();
            }
            case SurvivorActionPacket.DISMISS -> {
                if (!mine) return;
                SurvivorRoster roster = SurvivorRoster.get(player.server);
                if (roster != null) roster.remove(owner, getUUID());
                owner = null;
                home = null;
                defendPos = null;
                patrol.clear();
                setDuty(SurvivorDuty.NONE);
                setJob(SurvivorJob.IDLE);
                setSurvivorState(STRANDED);
                player.sendSystemMessage(Component.translatable("message.dead_air.survivor.dismissed", getName())
                    .withStyle(ChatFormatting.GRAY));
            }
            case SurvivorActionPacket.OPEN_EQUIPMENT -> {
                if (!mine) return;
                player.openMenu(new SurvivorEquipmentMenu.Provider(this), buf -> buf.writeVarInt(getId()));
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
            case SurvivorActionPacket.CYCLE_JOB -> {
                if (!mine || getSurvivorState() != SETTLED) return;
                SurvivorJob next = getJob().next();
                setJob(next);
                player.sendSystemMessage(Component.translatable("message.dead_air.survivor.job_set", getName(),
                    Component.translatable("survivor.dead_air.job." + next.id)).withStyle(ChatFormatting.GREEN));
            }
            case SurvivorActionPacket.DEFEND_HERE -> {
                if (!mine) return;
                defendPos = player.blockPosition().immutable();
                home = defendPos;
                homeRange = 12;
                setDuty(SurvivorDuty.DEFEND);
                setSurvivorState(SETTLED);
                setJob(SurvivorJob.GUARD);
                player.sendSystemMessage(Component.translatable("message.dead_air.survivor.defend", getName(),
                    defendPos.toShortString()).withStyle(ChatFormatting.GREEN));
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
        setDuty(SurvivorDuty.NONE);
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
        setDuty(SurvivorDuty.NONE);
        setSurvivorState(SETTLED);
        // Soft teleport near panel if far â€” stand beside the panel, not inside it
        if (!blockPosition().closerThan(home, homeRange)) {
            BlockPos stand = findStandBeside(level, home);
            teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        }
        player.sendSystemMessage(Component.translatable("message.dead_air.survivor.sent_to_base", getName(),
            homeRange).withStyle(ChatFormatting.GREEN));
    }

    /** Prefer an adjacent empty standable block so we don't teleport into the panel. */
    private static BlockPos findStandBeside(ServerLevel level, BlockPos panel) {
        BlockPos[] candidates = {
            panel.north(), panel.south(), panel.east(), panel.west(),
            panel.north().above(), panel.south().above(), panel.east().above(), panel.west().above(),
            panel.above()
        };
        for (BlockPos c : candidates) {
            if (level.getBlockState(c).isAir() && level.getBlockState(c.above()).isAir()
                && !level.getBlockState(c.below()).isAir()) {
                return c;
            }
        }
        return panel.above();
    }

    private void giveSupplies(ServerPlayer player) {
        if (!(level() instanceof ServerLevel server)) return;
        ResourceLocation tableId = ResourceLocation.fromNamespaceAndPath(Dead_air.MODID,
            "gameplay/survivor_supplies/" + getProfession().id);
        LootTable table = server.getServer().reloadableRegistries()
            .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, tableId));
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
            } else if (getProfession() == Profession.SCAVENGER) {
                uk.co.extraspecialstudio.dead_air.compat.GunLootCompat.injectScavengerSupplies(player);
                uk.co.extraspecialstudio.dead_air.compat.ToughAsNailsCompat.injectMedicOrSurvivorSupplies(player);
            }
        } catch (Throwable ignored) {
        }
        player.sendSystemMessage(Component.translatable(given > 0 ? "message.dead_air.survivor.supplies"
            : "message.dead_air.survivor.supplies_empty", getName()).withStyle(ChatFormatting.GREEN));
    }

    // ---------- lifecycle / downed ----------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isDowned()) {
            if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || forceRealDeath) {
                forceRealDeath = true;
                setDowned(false);
                setNoAi(false);
                return super.hurt(source, Math.max(amount, getHealth() + 1f));
            }
            return false;
        }
        if (isInParty() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
            && getHealth() - amount <= 0f) {
            enterDowned();
            return true;
        }
        return super.hurt(source, amount);
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
        setPose(Pose.SWIMMING);
        setNoAi(true);
        playSound(SoundEvents.PLAYER_HURT, 1f, 0.85f);
        ServerPlayer p = getOwnerPlayer();
        if (p != null) {
            p.sendSystemMessage(Component.translatable("message.dead_air.survivor.downed", getName())
                .withStyle(ChatFormatting.GOLD));
        }
    }

    private void reviveFrom(ServerPlayer helper) {
        setDowned(false);
        downedTicks = 0;
        reviveProgress = 0;
        reviveHelper = null;
        setNoAi(false);
        setPose(Pose.STANDING);
        setHealth(Math.max(8f, getMaxHealth() * 0.45f));
        playSound(SoundEvents.PLAYER_LEVELUP, 0.55f, 1.35f);
        helper.sendSystemMessage(Component.translatable("message.dead_air.survivor.revived", getName())
            .withStyle(ChatFormatting.GREEN));
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
        if (level() instanceof ServerLevel server) {
            hurt(damageSources().genericKill(), Float.MAX_VALUE);
        }
        forceRealDeath = false;
        skipDeathChat = false;
    }

    private void tickDownedLogic() {
        setPose(Pose.SWIMMING);
        setDeltaMovement(getDeltaMovement().multiply(0.0, 1.0, 0.0));
        getNavigation().stop();
        setTarget(null);
        downedTicks++;
        if (downedTicks >= BLEED_OUT_TICKS) {
            bleedOut();
            return;
        }
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
            reviveFrom(sp);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && isDowned()) {
            tickDownedLogic();
        }
    }

    @Override
    public void die(DamageSource source) {
        if (isDowned() && !forceRealDeath && isInParty()
            && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            setHealth(1f);
            return;
        }
        setNoAi(false);
        setDowned(false);
        super.die(source);
        try {
            if (level() instanceof ServerLevel server && owner != null) {
                SurvivorRoster roster = SurvivorRoster.get(server.getServer());
                if (roster != null) roster.remove(owner, getUUID());
                ServerPlayer p = getOwnerPlayer();
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
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Profession", entityData.get(DATA_PROFESSION));
        tag.putInt("SurvivorState", getSurvivorState());
        tag.putInt("Job", getJob().ordinal());
        tag.putInt("Duty", getDuty().ordinal());
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
        setCustomName(Component.literal(identity.name));
        setCustomNameVisible(true);
        setJob(SurvivorJob.byIndex(tag.getInt("Job")));
        setDuty(SurvivorDuty.byIndex(tag.getInt("Duty")));
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
        if (isDowned()) {
            setPose(Pose.SWIMMING);
            setNoAi(true);
            setHealth(Math.max(1f, getHealth()));
        }
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

    private class SurvivorGunGoal extends Goal {
        SurvivorGunGoal() {
            setFlags(EnumSet.of(Flag.LOOK, Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return SurvivorGunCompat.holdingGun(SurvivorEntity.this)
                && shouldFight()
                && getTarget() != null
                && getTarget().isAlive();
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
            ServerPlayer p = getSurvivorState() == FOLLOWING ? getOwnerPlayer() : null;
            return p != null && p.level() == level() && distanceTo(p) > 4;
        }

        @Override
        public boolean canContinueToUse() {
            ServerPlayer p = getSurvivorState() == FOLLOWING ? getOwnerPlayer() : null;
            return p != null && p.level() == level() && distanceTo(p) > 2.5;
        }

        @Override
        public void tick() {
            ServerPlayer p = getOwnerPlayer();
            if (p == null) return;
            getLookControl().setLookAt(p, 10f, getMaxHeadXRot());
            if (distanceTo(p) > 24 && p.onGround()) {
                Vec3 behind = p.position().subtract(p.getLookAngle().multiply(2, 0, 2));
                teleportTo(behind.x, p.getY(), behind.z);
                getNavigation().stop();
                return;
            }
            if (--repath <= 0) {
                repath = 10;
                getNavigation().moveTo(p, p.isSprinting() ? 1.35 : 1.1);
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
            return getDuty() == SurvivorDuty.DEFEND && defendPos != null && getSurvivorState() == SETTLED
                && getTarget() == null;
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

    private class RestAtNightGoal extends Goal {
        RestAtNightGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return getSurvivorState() == SETTLED && getDuty() == SurvivorDuty.NONE
                && level().isNight() && home != null
                && blockPosition().closerThan(home, 6);
        }

        @Override
        public void start() {
            getNavigation().stop();
        }
    }
}
