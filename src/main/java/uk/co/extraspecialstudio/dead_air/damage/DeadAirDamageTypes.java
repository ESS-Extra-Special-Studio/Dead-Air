package uk.co.extraspecialstudio.dead_air.damage;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import uk.co.extraspecialstudio.dead_air.Dead_air;

import javax.annotation.Nullable;

/** Datapack damage types shipped by Dead Air. */
public final class DeadAirDamageTypes {
    public static final ResourceKey<DamageType> ACOUSTIC = ResourceKey.create(
        Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, "acoustic"));

    private DeadAirDamageTypes() {}

    public static boolean isAcoustic(DamageSource source) {
        return source.is(ACOUSTIC);
    }

    /** Acoustic strike; {@code attacker} is both direct and causing entity when non-null. */
    public static DamageSource acoustic(Level level, @Nullable Entity attacker) {
        Holder<DamageType> type = level.registryAccess()
            .registryOrThrow(Registries.DAMAGE_TYPE)
            .getHolderOrThrow(ACOUSTIC);
        if (attacker != null) {
            return new DamageSource(type, attacker, attacker);
        }
        return new DamageSource(type);
    }
}
