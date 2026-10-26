package fr.heralisz.mere.sound;

import fr.heralisz.mere.HeraliszMere;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Sons de la Mère des Ruines, de sa couvée et du nid (assets/heralisz_mere/sounds.json). */
public final class Sounds {
   public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HeraliszMere.MODID);

   private static RegistryObject<SoundEvent> reg(String name) {
      return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(HeraliszMere.MODID, name)));
   }

   public static final RegistryObject<SoundEvent> BITE = reg("ruin_mother.bite");
   public static final RegistryObject<SoundEvent> BURST = reg("ruin_mother.burst");
   public static final RegistryObject<SoundEvent> CHITTER = reg("ruin_mother.chitter");
   public static final RegistryObject<SoundEvent> COCOON = reg("ruin_mother.cocoon");
   public static final RegistryObject<SoundEvent> DEATH = reg("ruin_mother.death");
   public static final RegistryObject<SoundEvent> HISS = reg("ruin_mother.hiss");
   public static final RegistryObject<SoundEvent> HURT = reg("ruin_mother.hurt");
   public static final RegistryObject<SoundEvent> LAND = reg("ruin_mother.land");
   public static final RegistryObject<SoundEvent> SCREECH = reg("ruin_mother.screech");
   public static final RegistryObject<SoundEvent> SILK = reg("ruin_mother.silk");
   public static final RegistryObject<SoundEvent> SPIT = reg("ruin_mother.spit");
   public static final RegistryObject<SoundEvent> SQUELCH = reg("ruin_mother.squelch");
   public static final RegistryObject<SoundEvent> STEP = reg("ruin_mother.step");
   public static final RegistryObject<SoundEvent> SWOOP = reg("ruin_mother.swoop");
   public static final RegistryObject<SoundEvent> COCOON_TEAR = reg("dungeon.cocoon_tear");
   public static final RegistryObject<SoundEvent> EGG_PULSE = reg("dungeon.egg_pulse");
   public static final RegistryObject<SoundEvent> SILK_STRAIN = reg("dungeon.silk_strain");
   public static final RegistryObject<SoundEvent> EGG_PLACE = reg("dungeon.egg_place");
   public static final RegistryObject<SoundEvent> GOO = reg("foley.goo");
   public static final RegistryObject<SoundEvent> SWIPE = reg("foley.swipe");
   public static final RegistryObject<SoundEvent> AMB_SCREAM = reg("ambience.spider_scream");
   public static final RegistryObject<SoundEvent> AMB_NEST = reg("ambience.bed_ruin");
   public static final RegistryObject<SoundEvent> MUSIC = reg("music.ruin_mother");

   private Sounds() {}
}
