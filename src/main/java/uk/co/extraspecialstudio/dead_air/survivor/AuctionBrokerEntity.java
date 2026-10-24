package uk.co.extraspecialstudio.dead_air.survivor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.InteractGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Illusioner;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;

/**
 * Dead-channel auction broker — WanderingTrader without the drink-invisibility hide.
 * Uses a survivor skin key for client rendering.
 */
public class AuctionBrokerEntity extends WanderingTrader {
    private static final EntityDataAccessor<String> DATA_SKIN =
        SynchedEntityData.defineId(AuctionBrokerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_FEMALE =
        SynchedEntityData.defineId(AuctionBrokerEntity.class, EntityDataSerializers.BOOLEAN);

    private static final String[] SKINS = {
        "scavenger_male_0", "scavenger_male_1",
        "civilian_male_0", "civilian_male_1",
        "scavenger_female_0", "scavenger_female_1",
        "civilian_female_0", "civilian_female_1"
    };

    public AuctionBrokerEntity(EntityType<? extends WanderingTrader> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MOVEMENT_SPEED, 0.5D)
            .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKIN, "scavenger_male_0");
        builder.define(DATA_FEMALE, false);
    }

    public String getSkinTextureKey() {
        String k = entityData.get(DATA_SKIN);
        return k == null || k.isBlank() ? "scavenger_male_0" : k;
    }

    public boolean isFemaleSkin() {
        return entityData.get(DATA_FEMALE);
    }

    private void rollSkin(RandomSource random) {
        String skin = SKINS[random.nextInt(SKINS.length)];
        entityData.set(DATA_SKIN, skin);
        entityData.set(DATA_FEMALE, skin.contains("female"));
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData);
        rollSkin(level.getRandom());
        if (!hasCustomName()) {
            setCustomName(Component.translatable("entity.dead_air.auction_broker"));
            setCustomNameVisible(true);
        }
        setPersistenceRequired();
        return data;
    }

    /**
     * Same as vanilla trader AI minus {@code UseItemGoal} invisibility potion.
     * Still flees hostiles — just stays visible while doing it.
     */
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Zombie.class, 8.0F, 0.5D, 0.5D));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Evoker.class, 12.0F, 0.5D, 0.5D));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Vindicator.class, 8.0F, 0.5D, 0.5D));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Illusioner.class, 8.0F, 0.5D, 0.5D));
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Zoglin.class, 8.0F, 0.5D, 0.5D));
        this.goalSelector.addGoal(1, new PanicGoal(this, 0.5D));
        this.goalSelector.addGoal(1, new LookAtTradingPlayerGoal(this));
        this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.35D));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.35D));
        this.goalSelector.addGoal(9, new InteractGoal(this, Player.class, 3.0F, 1.0F));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide) {
            // Belt-and-braces: strip any invis that potions / other mods apply.
            if (hasEffect(MobEffects.INVISIBILITY)) {
                removeEffect(MobEffects.INVISIBILITY);
            }
            if (isInvisible()) {
                setInvisible(false);
            }
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SkinKey", getSkinTextureKey());
        tag.putBoolean("FemaleSkin", isFemaleSkin());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SkinKey")) {
            entityData.set(DATA_SKIN, tag.getString("SkinKey"));
        }
        if (tag.contains("FemaleSkin")) {
            entityData.set(DATA_FEMALE, tag.getBoolean("FemaleSkin"));
        }
    }
}
