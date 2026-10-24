package uk.co.extraspecialstudio.dead_air.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import uk.co.extraspecialstudio.dead_air.Dead_air;

import java.lang.reflect.Method;

/**
 * Soft RadioTowers integration for grounded airdrop-plane wrecks.
 * Uses registry lookup / reflection only — no hard compile dependency on RT classes.
 */
public final class RadioTowersPlaneCompat {
    private static final ResourceLocation PLANE_ID = ResourceLocation.fromNamespaceAndPath("radiotowers", "planeentity");

    private RadioTowersPlaneCompat() {}

    public static boolean isRadioTowersLoaded() {
        return ModList.get().isLoaded("radiotowers");
    }

    /**
     * Spawn a grounded, crashed RT plane wreck at the crash site.
     * Avoids EntityType.spawn / finalizeSpawn so the airdrop fly-in procedure does not run.
     * Calls {@code markAsCrashedWreck()} when present (RadioTowers 1.2.x+).
     * @return true if a plane entity was placed
     */
    public static boolean trySpawnGroundedWreck(ServerLevel level, BlockPos at) {
        if (!isRadioTowersLoaded()) return false;
        try {
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(PLANE_ID);
            if (type == null) {
                Dead_air.LOGGER.debug("[Dead Air] radiotowers:planeentity not registered");
                return false;
            }
            Entity entity = type.create(level);
            if (entity == null) return false;
            float yaw = level.getRandom().nextFloat() * 360f;
            // Entity pitch stays 0 — crash bank/nose is applied in PlaneentityRenderer when crashed.
            entity.moveTo(at.getX() + 0.5, at.getY() + 0.05, at.getZ() + 0.5, yaw, 0f);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setNoGravity(false);
            if (entity instanceof Mob mob) {
                mob.setNoAi(true);
                mob.setPersistenceRequired();
            }
            entity.getPersistentData().putBoolean("DeadAirCrashWreck", true);
            entity.getPersistentData().putLong("DeadAirCrashDay", level.getDayTime() / 24000L);
            markCrashed(entity);
            if (!level.addFreshEntity(entity)) return false;
            Dead_air.LOGGER.info("[Dead Air] Spawned crashed RT plane wreck at {}", at);
            return true;
        } catch (Throwable t) {
            Dead_air.LOGGER.warn("[Dead Air] Failed to spawn RT plane wreck: {}", t.toString());
            return false;
        }
    }

    private static void markCrashed(Entity entity) {
        try {
            Method m = entity.getClass().getMethod("markAsCrashedWreck");
            m.invoke(entity);
            return;
        } catch (ReflectiveOperationException ignored) {
            // Older RT jars — persistent tag only; visual stays upright.
        }
        try {
            // Fallback: synched DATA_CRASHED field if method missing but field present
            var field = entity.getClass().getField("DATA_CRASHED");
            Object accessor = field.get(null);
            Method set = entity.getEntityData().getClass().getMethod("set",
                Class.forName("net.minecraft.network.syncher.EntityDataAccessor"), Object.class);
            set.invoke(entity.getEntityData(), accessor, Boolean.TRUE);
        } catch (Throwable ignored) {
        }
    }
}
