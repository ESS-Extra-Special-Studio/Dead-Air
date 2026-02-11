package uk.creatopia.unbound.dead_air.zombie;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import uk.creatopia.unbound.dead_air.Config;
import uk.creatopia.unbound.dead_air.radio.RadioTower;
import uk.creatopia.unbound.dead_air.radio.TowerManager;

import java.util.List;

/**
 * Manages zombie attraction to active radio tower broadcasts.
 */
@SuppressWarnings("null")
public class ZombieAttractionManager {
    private static final int ATTRACTION_RANGE = 100; // Blocks
    private static final int CHECK_INTERVAL = 40; // Check every 2 seconds
    private static final double ATTRACTION_SPEED_MULTIPLIER = 1.2; // 20% faster movement toward towers
    
    /**
     * Update zombie attraction to active towers.
     */
    public static void update(ServerLevel level) {
        if (!Config.attractZombies) {
            return;
        }
        
        if (level.getGameTime() % CHECK_INTERVAL != 0) {
            return;
        }
        
        // Get all active powered towers
        var allTowers = TowerManager.getTowersInRange(level, Vec3.ZERO); // Get all towers
        var activeTowers = allTowers.stream()
            .filter(RadioTower::isPowered)
            .toList();
        
        if (activeTowers.isEmpty()) {
            return;
        }
        
        // Find zombies near active towers
        for (var tower : activeTowers) {
            BlockPos towerPos = tower.getPosition();
            AABB searchArea = new AABB(towerPos).inflate(ATTRACTION_RANGE);
            
            List<Mob> nearbyZombies = level.getEntitiesOfClass(Mob.class, searchArea, 
                entity -> isZombieType(entity));
            
            for (Mob zombie : nearbyZombies) {
                attractZombieToTower(zombie, tower);
            }
        }
    }
    
    /**
     * Check if an entity is a zombie type.
     */
    private static boolean isZombieType(Mob entity) {
        EntityType<?> type = entity.getType();
        return type == EntityType.ZOMBIE ||
               type == EntityType.ZOMBIE_VILLAGER ||
               type == EntityType.HUSK ||
               type == EntityType.DROWNED ||
               type == EntityType.ZOMBIFIED_PIGLIN ||
               type.getDescriptionId().toLowerCase().contains("zombie");
    }
    
    /**
     * Attract a zombie to move toward an active tower.
     */
    private static void attractZombieToTower(Mob zombie, RadioTower tower) {
        if (zombie.getTarget() != null) {
            // Zombie already has a target, don't override
            return;
        }
        
        BlockPos towerPos = tower.getPosition();
        Vec3 zombiePos = zombie.position();
        Vec3 towerVec = Vec3.atCenterOf(towerPos);
        
        double distance = zombiePos.distanceTo(towerVec);
        
        if (distance > ATTRACTION_RANGE) {
            return;
        }
        
        // Calculate direction to tower
        Vec3 direction = towerVec.subtract(zombiePos).normalize();
        
        // Add movement goal to approach tower
        // In a full implementation, this would use a custom AI goal
        // For now, we'll use a simple approach by modifying movement
        
        // Set zombie to look at tower
        zombie.getLookControl().setLookAt(towerVec.x, towerVec.y, towerVec.z, 30.0f, 30.0f);
        
        // Add movement toward tower (simplified - full implementation would use custom goal)
        if (distance > 5.0) {
            // Move toward tower
            Vec3 moveVec = direction.scale(0.1 * ATTRACTION_SPEED_MULTIPLIER);
            zombie.setDeltaMovement(zombie.getDeltaMovement().add(moveVec));
            
            // Mark zombie as attracted (for visual/audio effects)
            // Could add NBT tag or custom data to track attraction
        }
    }
}
