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

    // Dead Air 3.0: The Listener, broadcasts, relays
    public static final DeferredHolder<SoundEvent, SoundEvent> LISTENER_AMBIENT = register("listener.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> LISTENER_HURT = register("listener.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> LISTENER_DEFLECT = register("listener.deflect");
    public static final DeferredHolder<SoundEvent, SoundEvent> LISTENER_DEATH = register("listener.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> LISTENER_SCREAM = register("listener.scream");
    public static final DeferredHolder<SoundEvent, SoundEvent> LISTENER_STEP = register("listener.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> LISTENER_JUMP_OUT = register("listener.jump_out");
    public static final DeferredHolder<SoundEvent, SoundEvent> LISTENER_JUMP_IN = register("listener.jump_in");
    public static final DeferredHolder<SoundEvent, SoundEvent> LISTENER_SILENCE = register("listener.silence");
    public static final DeferredHolder<SoundEvent, SoundEvent> LISTENER_PULSE = register("listener.pulse");
    public static final DeferredHolder<SoundEvent, SoundEvent> LISTENER_CLAW = register("listener.claw");
    public static final DeferredHolder<SoundEvent, SoundEvent> LISTENER_DRONE = register("listener.drone");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANCHOR_HUM = register("anchor.hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANCHOR_BREAK = register("anchor.break");
    public static final DeferredHolder<SoundEvent, SoundEvent> BROADCAST_ALERT = register("broadcast.alert");
    public static final DeferredHolder<SoundEvent, SoundEvent> BROADCAST_VOICE = register("broadcast.voice");
    public static final DeferredHolder<SoundEvent, SoundEvent> BROADCAST_DISTRESS_CALL = register("broadcast.distress_call");
    public static final DeferredHolder<SoundEvent, SoundEvent> BROADCAST_DECODED = register("broadcast.decoded");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELAY_PING = register("relay.ping");
    public static final DeferredHolder<SoundEvent, SoundEvent> BASE_UNDER_ATTACK = register("base.under_attack");
    public static final DeferredHolder<SoundEvent, SoundEvent> BREAKER_SWING = register("breaker.swing");
    public static final DeferredHolder<SoundEvent, SoundEvent> BREAKER_HIT = register("breaker.hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> ACOUSTIC_HIT = register("acoustic.hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHORD_MERGE = register("chord.merge");
    public static final DeferredHolder<SoundEvent, SoundEvent> LISTENER_FADE_PULSE = register("listener.fade_pulse");
    public static final DeferredHolder<SoundEvent, SoundEvent> LISTENER_AGRO = register("listener.agro");
    public static final DeferredHolder<SoundEvent, SoundEvent> LISTENER_SPAWN = register("listener.spawn");
    public static final DeferredHolder<SoundEvent, SoundEvent> LISTENER_DISSOLVE = register("listener.dissolve");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Dead_air.MODID, path);
        return REGISTER.register(path.replace(".", "_"), () -> SoundEvent.createVariableRangeEvent(id));
    }

    private DeadAirSounds() {}
}
