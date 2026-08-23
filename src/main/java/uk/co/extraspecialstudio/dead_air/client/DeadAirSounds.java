package uk.co.extraspecialstudio.dead_air.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import uk.co.extraspecialstudio.dead_air.Dead_air;

/**
 * Registers custom Dead Air sound events.
 * Custom music is loaded from the configured folder (customMusicPath) via CustomMusicLoader.
 */
public final class DeadAirSounds {
    public static final DeferredRegister<SoundEvent> REGISTER =
        DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Dead_air.MODID);

    public static final RegistryObject<SoundEvent> STATIC = register("static");

    // Bundled Creatopia tracks: same folder as existing Creatopia Radio songs (music.*), no "custom" in name
    public static final RegistryObject<SoundEvent> MUSIC_ROUND_1 = register("music.round_1");
    public static final RegistryObject<SoundEvent> MUSIC_SLIME_ISLAND = register("music.slime_island");
    public static final RegistryObject<SoundEvent> MUSIC_MINESHAFT = register("music.mineshaft");
    public static final RegistryObject<SoundEvent> MUSIC_FIRE_IN_THE_STATIC = register("music.fire_in_the_static");
    public static final RegistryObject<SoundEvent> MUSIC_COPPER_AGE = register("music.copper_age");

    private static RegistryObject<SoundEvent> register(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, path);
        return REGISTER.register(path.replace(".", "_"), () -> SoundEvent.createVariableRangeEvent(id));
    }

    private DeadAirSounds() {}
}
