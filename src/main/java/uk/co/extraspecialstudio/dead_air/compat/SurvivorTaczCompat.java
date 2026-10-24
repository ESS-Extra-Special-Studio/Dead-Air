package uk.co.extraspecialstudio.dead_air.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.minecraft.core.registries.BuiltInRegistries;
import uk.co.extraspecialstudio.dead_air.Dead_air;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Soft-optional TaCZ integration for survivors.
 * LivingEntity already implements IGunOperator via TaCZ mixin â€” we call it through reflection
 * so Dead Air has no hard compile dependency on TaCZ.
 *
 * Landed: create GunId stacks, equip on spawn, draw + shoot + reload + dummy ammo for NPCs.
 */
public final class SurvivorTaczCompat {
    private static final boolean LOADED = ModList.get().isLoaded("tacz");
    private static boolean probed;
    private static boolean available;

    @Nullable private static Method getIGunOrNull;
    @Nullable private static Method fromLivingEntity;
    @Nullable private static Method shoot;
    @Nullable private static Method reload;
    @Nullable private static Method draw;
    @Nullable private static Method aim;
    @Nullable private static Method setDummyAmmoAmount;
    @Nullable private static Method getCurrentAmmoCount;
    @Nullable private static Method setGunId;
    @Nullable private static Method setCurrentAmmoCount;
    @Nullable private static Method gunBuilderCreate;
    @Nullable private static Method gunBuilderSetId;
    @Nullable private static Method gunBuilderSetAmmo;
    @Nullable private static Method gunBuilderBuild;
    @Nullable private static Method getCommonGunIndex;

    /** Sidearms / pistols â€” default for medics & cautious survivors. */
    private static final String[] SIDEARMS = {
        "tacz:m1911", "tacz:glock_17", "tacz:cz75", "tacz:p320", "tacz:b93r",
        "tacz:m9a4", "tacz:hk_mk23", "tacz:deagle", "tacz:taurus943", "tacz:rhino357"
    };

    /** SMGs â€” radio ops / mid scavengers. */
    private static final String[] SMGS = {
        "tacz:ump45", "tacz:vector45", "tacz:hk_mp5a5", "tacz:uzi", "tacz:p90"
    };

    /** Assault / battle rifles â€” scavengers & aggressive. */
    private static final String[] RIFLES = {
        "tacz:ak47", "tacz:m4a1", "tacz:m16a1", "tacz:m16a4", "tacz:type_81",
        "tacz:sks_tactical", "tacz:g36k", "tacz:scar_l", "tacz:hk416d", "tacz:qbz_95"
    };

    /** Close-range shotguns â€” scavengers. */
    private static final String[] SHOTGUNS = {
        "tacz:m870", "tacz:db_short", "tacz:m1014", "tacz:spas_12", "tacz:db_long"
    };

    public enum StarterTier {
        SIDEARM, PRIMARY, MIXED
    }

    private SurvivorTaczCompat() {}

    public static boolean isLoaded() {
        return LOADED && ensure();
    }

    private static boolean ensure() {
        if (probed) return available;
        probed = true;
        if (!LOADED) {
            available = false;
            return false;
        }
        try {
            Class<?> iGun = Class.forName("com.tacz.guns.api.item.IGun");
            Class<?> iOp = Class.forName("com.tacz.guns.api.entity.IGunOperator");
            getIGunOrNull = iGun.getMethod("getIGunOrNull", ItemStack.class);
            fromLivingEntity = iOp.getMethod("fromLivingEntity", LivingEntity.class);
            shoot = iOp.getMethod("shoot", Supplier.class, Supplier.class);
            reload = iOp.getMethod("reload");
            draw = iOp.getMethod("draw", Supplier.class);
            aim = iOp.getMethod("aim", boolean.class);
            setDummyAmmoAmount = iGun.getMethod("setDummyAmmoAmount", ItemStack.class, int.class);
            getCurrentAmmoCount = iGun.getMethod("getCurrentAmmoCount", ItemStack.class);
            setGunId = iGun.getMethod("setGunId", ItemStack.class, ResourceLocation.class);
            setCurrentAmmoCount = iGun.getMethod("setCurrentAmmoCount", ItemStack.class, int.class);

            try {
                Class<?> builder = Class.forName("com.tacz.guns.api.item.builder.GunItemBuilder");
                gunBuilderCreate = builder.getMethod("create");
                gunBuilderSetId = builder.getMethod("setId", ResourceLocation.class);
                gunBuilderSetAmmo = builder.getMethod("setAmmoCount", int.class);
                gunBuilderBuild = builder.getMethod("build");
            } catch (Throwable ignored) {
            }

            try {
                Class<?> timeless = Class.forName("com.tacz.guns.api.TimelessAPI");
                getCommonGunIndex = timeless.getMethod("getCommonGunIndex", ResourceLocation.class);
            } catch (Throwable ignored) {
            }

            available = true;
            Dead_air.LOGGER.info("[Dead Air] TaCZ gun operator hooks ready for survivors");
        } catch (Throwable t) {
            available = false;
            Dead_air.LOGGER.warn("[Dead Air] TaCZ present but gun API unavailable: {}", t.toString());
        }
        return available;
    }

    public static boolean isGun(ItemStack stack) {
        if (!isLoaded() || stack == null || stack.isEmpty()) return false;
        try {
            return getIGunOrNull.invoke(null, stack) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Give NPC guns infinite-ish dummy ammo so they can fire without player inventory ammo. */
    public static void prepareGunForNpc(ItemStack gun) {
        if (!isGun(gun)) return;
        try {
            Object igun = getIGunOrNull.invoke(null, gun);
            if (igun == null) return;
            int ammo = (Integer) getCurrentAmmoCount.invoke(igun, gun);
            if (ammo <= 0 && setCurrentAmmoCount != null) {
                setCurrentAmmoCount.invoke(igun, gun, 24);
            }
            // Writing DummyAmmo enables useDummyAmmo() â€” NPC pool so they keep firing
            setDummyAmmoAmount.invoke(igun, gun, 96);
        } catch (Throwable ignored) {
        }
    }

    public static void draw(LivingEntity entity, ItemStack gun) {
        if (!isLoaded() || !isGun(gun)) return;
        try {
            Object op = fromLivingEntity.invoke(null, entity);
            draw.invoke(op, (Supplier<ItemStack>) () -> gun);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Aim at target and shoot. Returns true if a shot was attempted successfully (SUCCESS).
     */
    public static boolean tryShoot(Mob shooter, LivingEntity target) {
        if (!isLoaded()) return false;
        ItemStack gun = shooter.getMainHandItem();
        if (!isGun(gun)) return false;
        try {
            Object op = fromLivingEntity.invoke(null, shooter);
            prepareGunForNpc(gun);
            draw.invoke(op, (Supplier<ItemStack>) shooter::getMainHandItem);
            aim.invoke(op, true);

            shooter.getLookControl().setLookAt(target, 30f, 30f);
            float pitch = shooter.getXRot();
            float yaw = shooter.getYRot();
            Object result = shoot.invoke(op, (Supplier<Float>) () -> pitch, (Supplier<Float>) () -> yaw);
            String name = result != null ? result.toString() : "";
            if ("NO_AMMO".equals(name) || "NEED_BOLT".equals(name)) {
                reload.invoke(op);
                prepareGunForNpc(gun);
                return false;
            }
            return "SUCCESS".equals(name);
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] TaCZ shoot failed: {}", t.toString());
            return false;
        }
    }

    public static boolean holdingGun(LivingEntity entity) {
        return isGun(entity.getMainHandItem());
    }

    /** Build a real TaCZ gun stack for {@code tacz:&lt;name&gt;} (or {@code tacz:gun/&lt;name&gt;}). */
    public static ItemStack createGun(String gunId) {
        if (!isLoaded() || gunId == null || gunId.isEmpty()) return ItemStack.EMPTY;
        ResourceLocation rl = ResourceLocation.tryParse(gunId);
        if (rl == null) return ItemStack.EMPTY;
        return createGun(rl);
    }

    public static ItemStack createGun(ResourceLocation gunId) {
        if (!isLoaded() || gunId == null) return ItemStack.EMPTY;
        ResourceLocation id = normalizeGunId(gunId);
        if (!gunExists(id)) {
            // try alternate path forms
            ResourceLocation alt = alternateGunId(id);
            if (alt == null || !gunExists(alt)) return ItemStack.EMPTY;
            id = alt;
        }

        ItemStack built = buildViaBuilder(id);
        if (built.isEmpty()) built = buildViaSetGunId(id);
        if (built.isEmpty()) return ItemStack.EMPTY;
        prepareGunForNpc(built);
        return built;
    }

    private static ResourceLocation normalizeGunId(ResourceLocation id) {
        String path = id.getPath();
        if (path.startsWith("gun/")) {
            return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path.substring(4));
        }
        return id;
    }

    @Nullable
    private static ResourceLocation alternateGunId(ResourceLocation id) {
        String path = id.getPath();
        if (path.startsWith("gun/")) {
            return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path.substring(4));
        }
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "gun/" + path);
    }

    private static boolean gunExists(ResourceLocation id) {
        if (getCommonGunIndex == null) return true; // optimistic if index API missing
        try {
            Object opt = getCommonGunIndex.invoke(null, id);
            if (opt instanceof Optional<?> o) return o.isPresent();
        } catch (Throwable ignored) {
        }
        return true;
    }

    private static ItemStack buildViaBuilder(ResourceLocation id) {
        if (gunBuilderCreate == null) return ItemStack.EMPTY;
        try {
            Object builder = gunBuilderCreate.invoke(null);
            builder = gunBuilderSetId.invoke(builder, id);
            builder = gunBuilderSetAmmo.invoke(builder, 24);
            Object stack = gunBuilderBuild.invoke(builder);
            if (stack instanceof ItemStack s && !s.isEmpty() && isGun(s)) return s;
        } catch (Throwable ignored) {
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack buildViaSetGunId(ResourceLocation id) {
        try {
            Item gunItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse("tacz:modern_kinetic_gun"));
            if (gunItem == null || gunItem == Items.AIR) {
                gunItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse("tacz:gun"));
            }
            if (gunItem == null || gunItem == Items.AIR) return ItemStack.EMPTY;
            ItemStack stack = new ItemStack(gunItem, 1);
            Object igun = getIGunOrNull.invoke(null, stack);
            if (igun == null) return ItemStack.EMPTY;
            setGunId.invoke(igun, stack, id);
            if (setCurrentAmmoCount != null) setCurrentAmmoCount.invoke(igun, stack, 24);
            return stack;
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    /** Pick a curated starter gun that exists in the loaded TaCZ pack. */
    public static ItemStack pickStarterGun(RandomSource random, StarterTier tier) {
        if (!isLoaded()) return ItemStack.EMPTY;
        List<String> pool = new ArrayList<>();
        switch (tier) {
            case SIDEARM -> {
                addAll(pool, SIDEARMS);
                if (random.nextFloat() < 0.25f) addAll(pool, SMGS);
            }
            case PRIMARY -> {
                addAll(pool, RIFLES);
                addAll(pool, SMGS);
                if (random.nextFloat() < 0.35f) addAll(pool, SHOTGUNS);
                if (random.nextFloat() < 0.2f) addAll(pool, SIDEARMS);
            }
            case MIXED -> {
                addAll(pool, SIDEARMS);
                addAll(pool, SMGS);
                addAll(pool, RIFLES);
                addAll(pool, SHOTGUNS);
            }
        }
        if (pool.isEmpty()) return ItemStack.EMPTY;

        // Shuffle-ish: try several picks so missing addon guns don't brick the roll
        for (int attempt = 0; attempt < 12; attempt++) {
            String id = pool.get(random.nextInt(pool.size()));
            ItemStack gun = createGun(id);
            if (!gun.isEmpty()) return gun;
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack pickLootGun(RandomSource random) {
        return pickStarterGun(random, StarterTier.MIXED);
    }

    private static void addAll(List<String> pool, String[] ids) {
        for (String id : ids) pool.add(id);
    }
}
