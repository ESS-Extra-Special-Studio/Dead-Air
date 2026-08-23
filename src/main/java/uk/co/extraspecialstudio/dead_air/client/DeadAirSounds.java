package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/**
 * Registers custom Dead Air sound events.
 * Custom music is loaded from the configured folder (customMusicPath) via CustomMusicLoader.
 */
public final class DeadAirSounds {
    public static final DeferredRegister<SoundEvent> REGISTER =
        DeferredRegister.create(net.minecraft.core.registries.Registries.SOUND_EVENT, Dead_air.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> STATIC = register("static");

    // Bundled Creatopia tracks: same folder as existing Creatopia Radio songs (music.*), no "custom" in name
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_ROUND_1 = register("music.round_1");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_SLIME_ISLAND = register("music.slime_island");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_MINESHAFT = register("music.mineshaft");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_FIRE_IN_THE_STATIC = register("music.fire_in_the_static");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_COPPER_AGE = register("music.copper_age");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, path);
        return REGISTER.register(path.replace(".", "_"), () -> SoundEvent.createVariableRangeEvent(id));
    }

    private DeadAirSounds() {}
}
