package fr.heralisz.sylvorn.sound;

import fr.heralisz.sylvorn.HeraliszSylvorn;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Sons de Sylvorn, de l'Arbre-Monde et du Sanctuaire des Racines (assets/heralisz_sylvorn/sounds.json). */
public final class Sounds {
   public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HeraliszSylvorn.MODID);

   private static RegistryObject<SoundEvent> reg(String name) {
      return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(HeraliszSylvorn.MODID, name)));
   }

   public static final RegistryObject<SoundEvent> SY_BREATH = reg("sylvorn.breath");
   public static final RegistryObject<SoundEvent> SY_CHIMES = reg("sylvorn.chimes");
   public static final RegistryObject<SoundEvent> SY_HOWL = reg("sylvorn.howl");
   public static final RegistryObject<SoundEvent> SY_HURT = reg("sylvorn.hurt");
   public static final RegistryObject<SoundEvent> SY_PETRIFY = reg("sylvorn.petrify");
   public static final RegistryObject<SoundEvent> SY_ROAR = reg("sylvorn.roar");
   public static final RegistryObject<SoundEvent> SY_ROOTS = reg("sylvorn.roots");
   public static final RegistryObject<SoundEvent> SY_STEP = reg("sylvorn.step");
   public static final RegistryObject<SoundEvent> SY_THORNS = reg("sylvorn.thorns");
   public static final RegistryObject<SoundEvent> SY_WHISPER = reg("sylvorn.whisper");
   public static final RegistryObject<SoundEvent> MUSIC_SYLVORN = reg("music.sylvorn");
   public static final RegistryObject<SoundEvent> AMB_BED = reg("ambience.bed");
   public static final RegistryObject<SoundEvent> FX_HEARTBEAT = reg("foley.heartbeat");
   public static final RegistryObject<SoundEvent> FX_WOOD = reg("foley.wood_break");
   public static final RegistryObject<SoundEvent> FX_BONE = reg("foley.bone_crack");
   public static final RegistryObject<SoundEvent> FX_EARTH = reg("foley.earth_break");
   public static final RegistryObject<SoundEvent> FX_GOO = reg("foley.goo");
   public static final RegistryObject<SoundEvent> FX_STEP = reg("foley.heavy_step");
   public static final RegistryObject<SoundEvent> FX_SWIPE = reg("foley.swipe");
   public static final RegistryObject<SoundEvent> DG_CREAK = reg("dungeon.creak");
   public static final RegistryObject<SoundEvent> DG_ROOTS = reg("dungeon.roots");
   public static final RegistryObject<SoundEvent> DG_HORN = reg("dungeon.horn");
   public static final RegistryObject<SoundEvent> DG_WHISPER = reg("dungeon.whisper");
   public static final RegistryObject<SoundEvent> DG_GROAN = reg("dungeon.groan");
   public static final RegistryObject<SoundEvent> DG_DRIP = reg("dungeon.drip");
   public static final RegistryObject<SoundEvent> DG_PLANT = reg("dungeon.plant");
   /** (sons partagés : pas de fichier propre) */
   public static final RegistryObject<SoundEvent> FX_EXPLODE = FX_EARTH, FX_METAL = FX_BONE, FX_POWER_UP = SY_CHIMES, KN_IMPACT = FX_EARTH;

   private Sounds() {}
}
