package uk.co.extraspecialstudio.dead_air.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/**
 * Soft RadioTowers integration for grounded airdrop-plane wrecks.
 * Uses registry lookup only â€” no hard compile dependency on RT classes.
 */
public final class RadioTowersPlaneCompat {
    private static final ResourceLocation PLANE_ID = ResourceLocation.fromNamespaceAndPath("radiotowers", "planeentity");

    private RadioTowersPlaneCompat() {}

    public static boolean isRadioTowersLoaded() {
        return ModList.get().isLoaded("radiotowers");
    }

    /**
     * Spawn a grounded, non-flying RT plane at the crash site.
     * Avoids EntityType.spawn / finalizeSpawn so the airdrop fly-in procedure does not run.
     * @return true if a plane entity was placed
     */
    public static boolean trySpawnGroundedWreck(ServerLevel level, BlockPos at) {
        if (!isRadioTowersLoaded()) return false;
        try {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(PLANE_ID).orElse(null);
            if (type == null) {
                Dead_air.LOGGER.debug("[Dead Air] radiotowers:planeentity not registered");
                return false;
            }
            Entity entity = type.create(level);
            if (entity == null) return false;
            entity.moveTo(at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5,
                level.getRandom().nextFloat() * 360f, 25f);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setNoGravity(false);
            if (entity instanceof Mob mob) {
                mob.setNoAi(true);
                mob.setPersistenceRequired();
            }
            entity.getPersistentData().putBoolean("DeadAirCrashWreck", true);
            entity.getPersistentData().putLong("DeadAirCrashDay", level.getDayTime() / 24000L);
            if (!level.addFreshEntity(entity)) return false;
            Dead_air.LOGGER.info("[Dead Air] Spawned grounded RT plane wreck at {}", at);
            return true;
        } catch (Throwable t) {
            Dead_air.LOGGER.warn("[Dead Air] Failed to spawn RT plane wreck: {}", t.toString());
            return false;
        }
    }
}