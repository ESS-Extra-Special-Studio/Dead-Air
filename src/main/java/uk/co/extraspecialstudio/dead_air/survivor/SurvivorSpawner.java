package uk.co.extraspecialstudio.dead_air.survivor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import uk.co.extraspecialstudio.dead_air.entity.DeadAirEntities;

import java.util.UUID;

public final class SurvivorSpawner {

    /** Spawn a stranded survivor at a camp; 'caller' is the player who heard the distress call. */
    public static SurvivorEntity spawnStranded(ServerLevel level, BlockPos pos, UUID caller) {
        SurvivorEntity s = DeadAirEntities.SURVIVOR.get().create(level);
        if (s == null) return null;
        s.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360f, 0);
        s.initRandom(level.random, caller, false);
        s.bindCampHome(pos);
        level.addFreshEntity(s);
        return s;
    }

    private SurvivorSpawner() {}
}
