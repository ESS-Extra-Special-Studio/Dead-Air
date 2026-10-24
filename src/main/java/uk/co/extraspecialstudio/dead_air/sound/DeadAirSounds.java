package uk.co.extraspecialstudio.dead_air.sound;

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

    // Dead Air 3.0: The Listener, broadcasts, relays
    public static final RegistryObject<SoundEvent> LISTENER_AMBIENT = register("listener.ambient");
    public static final RegistryObject<SoundEvent> LISTENER_HURT = register("listener.hurt");
    public static final RegistryObject<SoundEvent> LISTENER_DEFLECT = register("listener.deflect");
    public static final RegistryObject<SoundEvent> LISTENER_DEATH = register("listener.death");
    public static final RegistryObject<SoundEvent> LISTENER_SCREAM = register("listener.scream");
    public static final RegistryObject<SoundEvent> LISTENER_STEP = register("listener.step");
    public static final RegistryObject<SoundEvent> LISTENER_JUMP_OUT = register("listener.jump_out");
    public static final RegistryObject<SoundEvent> LISTENER_JUMP_IN = register("listener.jump_in");
    public static final RegistryObject<SoundEvent> LISTENER_SILENCE = register("listener.silence");
    public static final RegistryObject<SoundEvent> LISTENER_PULSE = register("listener.pulse");
    public static final RegistryObject<SoundEvent> LISTENER_CLAW = register("listener.claw");
    public static final RegistryObject<SoundEvent> LISTENER_DRONE = register("listener.drone");
    public static final RegistryObject<SoundEvent> LISTENER_FADE_PULSE = register("listener.fade_pulse");
    public static final RegistryObject<SoundEvent> LISTENER_AGRO = register("listener.agro");
    public static final RegistryObject<SoundEvent> LISTENER_SPAWN = register("listener.spawn");
    public static final RegistryObject<SoundEvent> LISTENER_DISSOLVE = register("listener.dissolve");
    public static final RegistryObject<SoundEvent> ANCHOR_HUM = register("anchor.hum");
    public static final RegistryObject<SoundEvent> ANCHOR_BREAK = register("anchor.break");
    public static final RegistryObject<SoundEvent> BROADCAST_ALERT = register("broadcast.alert");
    public static final RegistryObject<SoundEvent> BROADCAST_VOICE = register("broadcast.voice");
    /** Survivor distress VO — camp / convoy / relay Emergency Broadcasts. */
    public static final RegistryObject<SoundEvent> BROADCAST_DISTRESS_CALL = register("broadcast.distress_call");
    public static final RegistryObject<SoundEvent> BROADCAST_DECODED = register("broadcast.decoded");
    public static final RegistryObject<SoundEvent> RELAY_PING = register("relay.ping");
    public static final RegistryObject<SoundEvent> BASE_UNDER_ATTACK = register("base.under_attack");
    public static final RegistryObject<SoundEvent> BREAKER_SWING = register("breaker.swing");
    public static final RegistryObject<SoundEvent> BREAKER_HIT = register("breaker.hit");
    public static final RegistryObject<SoundEvent> ACOUSTIC_HIT = register("acoustic.hit");
    public static final RegistryObject<SoundEvent> CHORD_MERGE = register("chord.merge");

    private static RegistryObject<SoundEvent> register(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, path);
        return REGISTER.register(path.replace(".", "_"), () -> SoundEvent.createVariableRangeEvent(id));
    }

    private DeadAirSounds() {}
}
