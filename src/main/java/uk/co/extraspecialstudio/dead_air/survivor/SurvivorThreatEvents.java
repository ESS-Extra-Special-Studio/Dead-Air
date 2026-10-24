package uk.co.extraspecialstudio.dead_air.survivor;

import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/**
 * Vanilla hostiles only aggro players / villagers by default — survivors are PathfinderMobs
 * in {@code CREATURE}, so zombies etc. walk past them. Hook Monster join to also hunt survivors.
 */
@EventBusSubscriber(modid = Dead_air.MODID)
public final class SurvivorThreatEvents {
    private SurvivorThreatEvents() {}

    /** Marker subclass so we can detect an already-injected goal without reflecting generics. */
    private static final class HuntSurvivorGoal extends NearestAttackableTargetGoal<SurvivorEntity> {
        HuntSurvivorGoal(Monster monster) {
            super(monster, SurvivorEntity.class, true);
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Monster monster)) return;
        for (WrappedGoal wrapped : monster.targetSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof HuntSurvivorGoal) return;
        }
        // Priority 3 — after hurt-by / player (typical zombie layout uses 1–2).
        monster.targetSelector.addGoal(3, new HuntSurvivorGoal(monster));
    }
}
