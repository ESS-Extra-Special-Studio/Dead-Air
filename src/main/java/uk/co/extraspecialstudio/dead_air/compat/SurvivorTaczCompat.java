package uk.co.extraspecialstudio.dead_air.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import uk.co.extraspecialstudio.dead_air.Dead_air;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Soft-optional TaCZ integration for survivors.
 * LivingEntity already implements IGunOperator via TaCZ mixin — we call it through reflection
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
    @Nullable private static Method bolt;
    @Nullable private static Method draw;
    @Nullable private static Method aim;
    @Nullable private static Method initialData;
    @Nullable private static Method setDummyAmmoAmount;
    @Nullable private static Method setMaxDummyAmmoAmount;
    @Nullable private static Method getCurrentAmmoCount;
    @Nullable private static Method setCurrentAmmoCount;
    @Nullable private static Method setBulletInBarrel;
    @Nullable private static Method setGunId;
    @Nullable private static Method gunBuilderCreate;
    @Nullable private static Method gunBuilderSetId;
    @Nullable private static Method gunBuilderSetAmmo;
    @Nullable private static Method gunBuilderBuild;
    @Nullable private static Method getCommonGunIndex;

    /** NPC mag + dummy pool — high enough to count as infinite for combat. */
    private static final int NPC_MAG_AMMO = 64;
    private static final int NPC_DUMMY_AMMO = 9999;

    /** Sidearms / pistols — default for medics & cautious survivors. */
    private static final String[] SIDEARMS = {
        "tacz:m1911", "tacz:glock_17", "tacz:cz75", "tacz:p320", "tacz:b93r",
        "tacz:m9a4", "tacz:hk_mk23", "tacz:deagle", "tacz:taurus943", "tacz:rhino357"
    };

    /** SMGs — radio ops / mid scavengers. */
    private static final String[] SMGS = {
        "tacz:ump45", "tacz:vector45", "tacz:hk_mp5a5", "tacz:uzi", "tacz:p90"
    };

    /** Assault / battle rifles — scavengers & aggressive. */
    private static final String[] RIFLES = {
        "tacz:ak47", "tacz:m4a1", "tacz:m16a1", "tacz:m16a4", "tacz:type_81",
        "tacz:sks_tactical", "tacz:g36k", "tacz:scar_l", "tacz:hk416d", "tacz:qbz_95"
    };

    /** Close-range shotguns — scavengers. */
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
                bolt = iOp.getMethod("bolt");
            } catch (Throwable ignored) {
            }
            try {
                initialData = iOp.getMethod("initialData");
            } catch (Throwable ignored) {
            }
            try {
                setBulletInBarrel = iGun.getMethod("setBulletInBarrel", ItemStack.class, boolean.class);
            } catch (Throwable ignored) {
            }
            try {
                setMaxDummyAmmoAmount = iGun.getMethod("setMaxDummyAmmoAmount", ItemStack.class, int.class);
            } catch (Throwable ignored) {
            }

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

    /**
     * Infinite NPC ammo: always top up magazine + dummy reserve (enables useDummyAmmo)
     * and chamber a round so bolt-action / closed-bolt guns can fire.
     */
    public static void prepareGunForNpc(ItemStack gun) {
        if (!isGun(gun)) return;
        try {
            Object igun = getIGunOrNull.invoke(null, gun);
            if (igun == null) return;
            if (setCurrentAmmoCount != null) {
                setCurrentAmmoCount.invoke(igun, gun, NPC_MAG_AMMO);
            }
            if (setMaxDummyAmmoAmount != null) {
                setMaxDummyAmmoAmount.invoke(igun, gun, NPC_DUMMY_AMMO);
            }
            setDummyAmmoAmount.invoke(igun, gun, NPC_DUMMY_AMMO);
            if (setBulletInBarrel != null) {
                setBulletInBarrel.invoke(igun, gun, true);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Draw once when combat starts — do not call every shot (causes IS_DRAWING / NOT_DRAW). */
    public static void draw(LivingEntity entity, ItemStack gun) {
        if (!isLoaded() || !isGun(gun)) return;
        try {
            Object op = fromLivingEntity.invoke(null, entity);
            if (initialData != null) {
                initialData.invoke(op);
            }
            prepareGunForNpc(gun);
            draw.invoke(op, (Supplier<ItemStack>) entity::getMainHandItem);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Aim at target and shoot. Returns true if a shot fired (SUCCESS).
     * Does not re-draw every attempt — callers should {@link #draw} when combat begins.
     */
    public static boolean tryShoot(Mob shooter, LivingEntity target) {
        if (!isLoaded() || target == null || !target.isAlive()) return false;
        ItemStack gun = shooter.getMainHandItem();
        if (!isGun(gun)) return false;
        try {
            Object op = fromLivingEntity.invoke(null, shooter);
            prepareGunForNpc(gun);
            shooter.setSprinting(false);
            aim.invoke(op, true);

            float[] aim = aimAt(shooter, target);
            float pitch = aim[0];
            float yaw = aim[1];
            shooter.setXRot(pitch);
            shooter.setYRot(yaw);
            shooter.yHeadRot = yaw;
            shooter.yBodyRot = yaw;
            shooter.getLookControl().setLookAt(target, 30f, 30f);

            String name = shootResultName(shoot.invoke(op,
                (Supplier<Float>) () -> pitch, (Supplier<Float>) () -> yaw));

            if ("SUCCESS".equals(name)) {
                return true;
            }
            if ("NOT_DRAW".equals(name) || "IS_DRAWING".equals(name)) {
                draw.invoke(op, (Supplier<ItemStack>) shooter::getMainHandItem);
                return false;
            }
            if ("NO_AMMO".equals(name) || "NEED_BOLT".equals(name)) {
                if ("NEED_BOLT".equals(name) && bolt != null) {
                    bolt.invoke(op);
                } else {
                    reload.invoke(op);
                }
                prepareGunForNpc(gun);
                name = shootResultName(shoot.invoke(op,
                    (Supplier<Float>) () -> pitch, (Supplier<Float>) () -> yaw));
                return "SUCCESS".equals(name);
            }
            // COOL_DOWN / IS_RELOADING / IS_BOLTING / IS_SPRINTING — wait next tick
            return false;
        } catch (Throwable t) {
            Dead_air.LOGGER.debug("[Dead Air] TaCZ shoot failed: {}", t.toString());
            return false;
        }
    }

    private static String shootResultName(@Nullable Object result) {
        if (result == null) return "";
        if (result instanceof Enum<?> e) return e.name();
        String s = result.toString();
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(dot + 1) : s;
    }

    /** Pitch / yaw toward the target's eyes. */
    private static float[] aimAt(Mob shooter, LivingEntity target) {
        double dx = target.getX() - shooter.getX();
        double dy = target.getEyeY() - shooter.getEyeY();
        double dz = target.getZ() - shooter.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        float pitch = (float) (-(Math.atan2(dy, horiz) * (180.0 / Math.PI)));
        return new float[]{pitch, yaw};
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
            Item gunItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("tacz:modern_kinetic_gun"));
            if (gunItem == null || gunItem == Items.AIR) {
                gunItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("tacz:gun"));
            }
            if (gunItem == null || gunItem == Items.AIR) return ItemStack.EMPTY;
            ItemStack stack = new ItemStack(gunItem, 1);
            Object igun = getIGunOrNull.invoke(null, stack);
            if (igun == null) return ItemStack.EMPTY;
            setGunId.invoke(igun, stack, id);
            stack.getOrCreateTag().putString("GunId", id.toString());
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
