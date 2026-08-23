package uk.co.extraspecialstudio.dead_air.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.item.DeadAirItems;
import uk.co.extraspecialstudio.dead_air.walkie.WalkieTalkieManager;

/**
 * Drops Static Notes when a player kills a mob while their single active walkie is on and tuned.
 * Chance comes only from that active radio (T1 15% / T2 30%) — extra radios in inventory do not help.
 * Does not require holding the walkie.
 */
@EventBusSubscriber(modid = Dead_air.MODID)
public class StaticNoteDropEvents {

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (event.getEntity() instanceof Player) return;

        // Enforce one active radio on the server inventory, then roll that walkie's chance only.
        WalkieTalkieManager.ensureSingleActiveListening(player);
        if (!WalkieTalkieManager.isListeningToRadio(player)) return;

        float chance = WalkieTalkieManager.getStaticNoteDropChance(player);
        if (chance <= 0f) return;
        if (player.getRandom().nextFloat() >= chance) return;

        ItemStack note = new ItemStack(DeadAirItems.STATIC_NOTE.get());
        ItemEntity entity = new ItemEntity(level, event.getEntity().getX(), event.getEntity().getY() + 0.5,
            event.getEntity().getZ(), note);
        entity.setDefaultPickUpDelay();
        entity.setDeltaMovement(
            (player.getRandom().nextDouble() - 0.5) * 0.1,
            0.2,
            (player.getRandom().nextDouble() - 0.5) * 0.1
        );
        entity.setGlowingTag(true);
        level.addFreshEntity(entity);
    }
}
