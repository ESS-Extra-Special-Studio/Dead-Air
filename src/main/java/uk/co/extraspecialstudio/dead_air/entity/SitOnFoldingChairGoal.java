package uk.co.extraspecialstudio.dead_air.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import uk.co.extraspecialstudio.dead_air.block.DeadAirBlocks;

import java.util.EnumSet;

/**
 * Idle sit for villagers / wandering traders — walk to a nearby folding chair and mount briefly.
 */
public class SitOnFoldingChairGoal extends Goal {
    private static final int SEARCH = 12;
    private static final int STAY_MIN = 100;
    private static final int STAY_MAX = 240;
    private static final int COOLDOWN = 500;
    /** Abort if pathing never arrives (stuck nav / blocked chair). */
    private static final int APPROACH_TIMEOUT = 160;

    private final PathfinderMob mob;
    private BlockPos chair;
    private BlockPos approach;
    private int stayTicks;
    private int approachTicks;
    private int cooldown;

    public SitOnFoldingChairGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (mob.isPassenger() || mob.getTarget() != null || mob.isSleeping()) return false;
        if (mob.getRandom().nextInt(100) != 0) return false;
        BlockPos found = findChair(mob.blockPosition());
        if (found == null) return false;
        chair = found;
        approach = standBeside(found);
        return approach != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (mob.getTarget() != null) return false;
        if (mob.isPassenger()) return stayTicks > 0;
        return stayTicks > 0 && chair != null && approach != null;
    }

    @Override
    public void start() {
        stayTicks = STAY_MIN + mob.getRandom().nextInt(STAY_MAX - STAY_MIN + 1);
        approachTicks = APPROACH_TIMEOUT;
        mob.getNavigation().moveTo(approach.getX() + 0.5, approach.getY(), approach.getZ() + 0.5, 0.6);
    }

    @Override
    public void tick() {
        if (chair == null || approach == null) return;
        if (mob.isPassenger()) {
            stayTicks--;
            return;
        }
        double dist = mob.distanceToSqr(approach.getX() + 0.5, approach.getY(), approach.getZ() + 0.5);
        if (dist > 2.25) {
            if (--approachTicks <= 0) {
                stayTicks = 0;
                return;
            }
            mob.getNavigation().moveTo(approach.getX() + 0.5, approach.getY(), approach.getZ() + 0.5, 0.6);
            return;
        }
        mob.getNavigation().stop();
        BlockState state = mob.level().getBlockState(chair);
        if (!SeatEntity.trySit(mob.level(), chair, state, mob)) {
            // Chair taken — abort
            stayTicks = 0;
            return;
        }
        stayTicks--;
    }

    @Override
    public void stop() {
        if (mob.isPassenger() && mob.getVehicle() instanceof SeatEntity) {
            mob.stopRiding();
        }
        chair = null;
        approach = null;
        stayTicks = 0;
        approachTicks = 0;
        cooldown = COOLDOWN + mob.getRandom().nextInt(200);
        mob.getNavigation().stop();
    }

    private BlockPos findChair(BlockPos origin) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -SEARCH; dx <= SEARCH; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -SEARCH; dz <= SEARCH; dz++) {
                    m.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!mob.level().getBlockState(m).is(DeadAirBlocks.FOLDING_CHAIR.get())) continue;
                    if (SeatEntity.isOccupied(mob.level(), m)) continue;
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

    private BlockPos standBeside(BlockPos target) {
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
        for (int[] o : offsets) {
            BlockPos p = target.offset(o[0], 0, o[1]);
            if (mob.level().getBlockState(p).isAir()
                && mob.level().getBlockState(p.above()).isAir()
                && !mob.level().getBlockState(p.below()).isAir()) {
                return p;
            }
        }
        return null;
    }
}
