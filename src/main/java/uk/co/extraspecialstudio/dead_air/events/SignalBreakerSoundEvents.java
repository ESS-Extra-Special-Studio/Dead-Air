package uk.co.extraspecialstudio.dead_air.events;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.client.DeadAirSounds;
import uk.co.extraspecialstudio.dead_air.item.DeadAirItems;

/** Signal Breaker swing whoosh (entity attack + empty left-click). */
public final class SignalBreakerSoundEvents {

    private SignalBreakerSoundEvents() {}

    @EventBusSubscriber(modid = Dead_air.MODID)
    public static final class Common {
        private Common() {}

        @SubscribeEvent
        public static void onAttackEntity(AttackEntityEvent event) {
            Player player = event.getEntity();
            if (player.level().isClientSide) return;
            if (!player.getMainHandItem().is(DeadAirItems.SIGNAL_BREAKER.get())) return;
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                DeadAirSounds.BREAKER_SWING.get(), SoundSource.PLAYERS,
                0.85f, 0.92f + player.getRandom().nextFloat() * 0.12f);
        }
    }

    /** LeftClickEmpty is client-only ? play locally so misses still whoosh. */
    @EventBusSubscriber(modid = Dead_air.MODID, value = Dist.CLIENT)
    public static final class Client {
        private Client() {}

        @SubscribeEvent
        public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
            if (!event.getItemStack().is(DeadAirItems.SIGNAL_BREAKER.get())) return;
            Player player = event.getEntity();
            player.playSound(DeadAirSounds.BREAKER_SWING.get(),
                0.75f, 0.92f + player.getRandom().nextFloat() * 0.12f);
        }
    }
}
