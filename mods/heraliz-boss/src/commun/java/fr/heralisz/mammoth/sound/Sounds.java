package fr.heralisz.mammoth.sound;

import fr.heralisz.mammoth.HeraliszMammoth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Sons d'Ymirak (assets/heralisz_mammoth/sounds.json, .ogg générés par tools/audio/mammoth.py). */
public final class Sounds {
   public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HeraliszMammoth.MODID);

   private static RegistryObject<SoundEvent> reg(String name) {
      return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(HeraliszMammoth.MODID, name)));
   }

   public static final RegistryObject<SoundEvent> TRUMPET = reg("mammoth.trumpet");
   public static final RegistryObject<SoundEvent> ROAR = reg("mammoth.roar");
   public static final RegistryObject<SoundEvent> STEP = reg("mammoth.step");
   public static final RegistryObject<SoundEvent> STOMP = reg("mammoth.stomp");
   public static final RegistryObject<SoundEvent> CHARGE = reg("mammoth.charge_loop");
   public static final RegistryObject<SoundEvent> TUSK_SWING = reg("mammoth.tusk_swing");
   public static final RegistryObject<SoundEvent> BLIZZARD = reg("mammoth.blizzard");
   public static final RegistryObject<SoundEvent> ICE_CRACK = reg("mammoth.ice_crack");
   public static final RegistryObject<SoundEvent> ICE_SHATTER = reg("mammoth.ice_shatter");
   public static final RegistryObject<SoundEvent> SPIKES = reg("mammoth.spikes");
   public static final RegistryObject<SoundEvent> ICICLE_HIT = reg("mammoth.icicle_hit");
   public static final RegistryObject<SoundEvent> HURT = reg("mammoth.hurt");
   public static final RegistryObject<SoundEvent> DEATH = reg("mammoth.death");
   public static final RegistryObject<SoundEvent> AMB_WIND = reg("mammoth.amb_wind");
   public static final RegistryObject<SoundEvent> AMB_MOAN = reg("mammoth.amb_moan");
   public static final RegistryObject<SoundEvent> HEARTBEAT = reg("mammoth.heartbeat");
   public static final RegistryObject<SoundEvent> VORTEX = reg("mammoth.vortex");
   public static final RegistryObject<SoundEvent> NOVA = reg("mammoth.nova");
   public static final RegistryObject<SoundEvent> MAW_OPEN = reg("mammoth.maw_open");
   public static final RegistryObject<SoundEvent> SKULL_CRACK = reg("mammoth.skull_crack");
   public static final RegistryObject<SoundEvent> RIP = reg("mammoth.rip");
   public static final RegistryObject<SoundEvent> WHOOSH = reg("mammoth.whoosh");
   public static final RegistryObject<SoundEvent> LASH = reg("mammoth.lash");
   public static final RegistryObject<SoundEvent> BONE_HIT = reg("mammoth.bone_hit");
   public static final RegistryObject<SoundEvent> MUSIC = reg("music.mammoth");

   private Sounds() {}
}
