package uk.co.extraspecialstudio.dead_air.events;

import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.entity.SitOnFoldingChairGoal;

/** Hook villagers / traders so they can idle-sit on Dead Air folding chairs. */
@EventBusSubscriber(modid = Dead_air.MODID)
public final class ChairSitEvents {
    private ChairSitEvents() {}

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractVillager villager)) return;
        // Survivors have their own CampIdlePoiGoal; only vanilla NPC merchants.
        if (!(villager instanceof Villager) && !(villager instanceof WanderingTrader)) return;
        for (WrappedGoal wrapped : villager.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof SitOnFoldingChairGoal) return;
        }
        villager.goalSelector.addGoal(6, new SitOnFoldingChairGoal(villager));
    }
}
