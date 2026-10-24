package uk.co.extraspecialstudio.dead_air.events;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.damage.DeadAirDamageTypes;
import uk.co.extraspecialstudio.dead_air.entity.ListenerEntity;
import uk.co.extraspecialstudio.dead_air.item.DeadAirItems;
import uk.co.extraspecialstudio.dead_air.sound.DeadAirSounds;

/**
 * Converts Signal Breaker melee hits into {@code dead_air:acoustic} damage.
 * Uses {@link LivingAttackEvent} (pre-armor) so Acoustic is not double-armored.
 * Listener converts inside its own {@code hurt()} — skipped here.
 */
@Mod.EventBusSubscriber(modid = Dead_air.MODID)
public final class SignalBreakerAcousticEvents {

    private SignalBreakerAcousticEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingAttack(LivingAttackEvent event) {
        DamageSource source = event.getSource();
        if (DeadAirDamageTypes.isAcoustic(source)) return;
        if (!(source.getEntity() instanceof LivingEntity attacker)) return;
        if (!attacker.getMainHandItem().is(DeadAirItems.SIGNAL_BREAKER.get())) return;

        LivingEntity target = event.getEntity();
        // Listener converts in hurt(); never let the global path double-hit him.
        if (target instanceof ListenerEntity) return;
        if (target.level().isClientSide) return;

        float amount = event.getAmount();
        if (amount <= 0f) return;
        // Cancel the vanilla sword attack, then land Acoustic once with the raw hit amount.
        // Do not clear invulnerableTime — Acoustic must respect hurt cooldown like Listener.hurt.
        event.setCanceled(true);
        boolean hit = target.hurt(DeadAirDamageTypes.acoustic(target.level(), attacker), amount);
        // Cancelled LivingAttackEvent makes Player.attack skip setLastHurtMob / hurtEnemy —
        // restore those so party ATTACK focus and SB durability still work on non-Listener targets.
        if (hit && attacker instanceof Player player) {
            player.setLastHurtMob(target);
            player.getMainHandItem().hurtEnemy(target, player);
            playHitSounds(target);
        }
    }

    /** Blade ping + sonic crack at the impact point. */
    public static void playHitSounds(LivingEntity target) {
        if (target == null || target.level().isClientSide) return;
        float pitch = 0.95f + target.getRandom().nextFloat() * 0.1f;
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(),
            DeadAirSounds.BREAKER_HIT.get(), SoundSource.PLAYERS, 0.9f, pitch);
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(),
            DeadAirSounds.ACOUSTIC_HIT.get(), SoundSource.PLAYERS, 0.85f, pitch);
    }
}
