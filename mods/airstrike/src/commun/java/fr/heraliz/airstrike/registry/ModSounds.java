package fr.heraliz.airstrike.registry;

import fr.heraliz.airstrike.AirstrikeMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, AirstrikeMod.MODID);

    public static final RegistryObject<SoundEvent> JET_ENGINE = far("jet_engine", 360f);
    public static final RegistryObject<SoundEvent> PROP_ENGINE = far("prop_engine", 300f);
    public static final RegistryObject<SoundEvent> BOMB_WHISTLE = far("bomb_whistle", 120f);
    public static final RegistryObject<SoundEvent> EXPLOSION_FAR = far("explosion_far", 480f);
    public static final RegistryObject<SoundEvent> EXPLOSION_NEAR = far("explosion_near", 96f);
    public static final RegistryObject<SoundEvent> GUN_BRRRT = far("gun_brrrt", 480f);
    public static final RegistryObject<SoundEvent> BULLET_IMPACT = variable("bullet_impact");
    public static final RegistryObject<SoundEvent> RADIO_CONFIRM = variable("radio_confirm");
    public static final RegistryObject<SoundEvent> RADIO_DENIED = variable("radio_denied");
    public static final RegistryObject<SoundEvent> DESIGNATOR_RAISE = variable("designator_raise");
    public static final RegistryObject<SoundEvent> DESIGNATOR_ZOOM = variable("designator_zoom");
    public static final RegistryObject<SoundEvent> DESIGNATOR_BEEP = variable("designator_beep");
    public static final RegistryObject<SoundEvent> DESIGNATOR_LOCK = variable("designator_lock");
    public static final RegistryObject<SoundEvent> SMOKE_HISS = variable("smoke_hiss");
    public static final RegistryObject<SoundEvent> PARACHUTE_OPEN = variable("parachute_open");
    public static final RegistryObject<SoundEvent> CANISTER_POP = far("canister_pop", 96f);
    public static final RegistryObject<SoundEvent> CRATE_LAND = variable("crate_land");
    public static final RegistryObject<SoundEvent> NAPALM_WHOOSH = far("napalm_whoosh", 96f);
    public static final RegistryObject<SoundEvent> DESIGNATOR_RELOAD = variable("designator_reload");
    public static final RegistryObject<SoundEvent> CRATE_REVEAL = variable("crate_reveal");
    public static final RegistryObject<SoundEvent> TINNITUS = variable("tinnitus");
    public static final RegistryObject<SoundEvent> FLARES = far("flares", 200f);
    /** Hélicoptère de patrouille (sons écrits par outils/sons_helico.py). */
    public static final RegistryObject<SoundEvent> HELICO_ROTOR = far("helico_rotor", 400f);
    public static final RegistryObject<SoundEvent> HELICO_TIR = far("helico_tir", 220f);
    public static final RegistryObject<SoundEvent> HELICO_CHUTE = far("helico_chute", 300f);

    private static RegistryObject<SoundEvent> variable(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(AirstrikeMod.MODID, name)));
    }

    private static RegistryObject<SoundEvent> far(String name, float range) {
        return SOUNDS.register(name, () -> SoundEvent.createFixedRangeEvent(new ResourceLocation(AirstrikeMod.MODID, name), range));
    }

    private ModSounds() {
    }
}
