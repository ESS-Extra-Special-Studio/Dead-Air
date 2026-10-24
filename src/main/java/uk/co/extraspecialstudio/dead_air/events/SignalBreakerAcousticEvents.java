package uk.co.extraspecialstudio.dead_air.events;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.damage.DeadAirDamageTypes;

/**
 * Converts Signal Breaker melee hits into {@code dead_air:acoustic} damage (NeoForge).
 */
@EventBusSubscriber(modid = Dead_air.MODID)
public final class SignalBreakerAcousticEvents {

    private static final ResourceLocation SIGNAL_BREAKER_ID =
        ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "signal_breaker");

    private SignalBreakerAcousticEvents() {}

    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (DeadAirDamageTypes.isAcoustic(source)) return;
        if (!(source.getEntity() instanceof LivingEntity attacker)) return;
        ItemStack held = attacker.getMainHandItem();
        if (!SIGNAL_BREAKER_ID.equals(BuiltInRegistries.ITEM.getKey(held.getItem()))) return;

        event.setCanceled(true);
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        target.hurt(DeadAirDamageTypes.acoustic(target.level(), attacker), event.getAmount());
    }
}
