package fr.heralisz.plague.sound;

import fr.heralisz.plague.HeraliszPlague;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Sons du Porteur de Peste et de la Station de Quarantaine (assets/heralisz_plague/sounds.json). */
public final class Sounds {
   public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HeraliszPlague.MODID);

   private static RegistryObject<SoundEvent> reg(String name) {
      return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(HeraliszPlague.MODID, name)));
   }

   public static final RegistryObject<SoundEvent> PB_BILE = reg("plague_bearer.bile");
   public static final RegistryObject<SoundEvent> PB_BREATH = reg("plague_bearer.breath");
   public static final RegistryObject<SoundEvent> PB_CHAIN = reg("plague_bearer.chain");
   public static final RegistryObject<SoundEvent> PB_CLANG = reg("plague_bearer.clang");
   public static final RegistryObject<SoundEvent> PB_DEATH = reg("plague_bearer.death");
   public static final RegistryObject<SoundEvent> PB_FLIES = reg("plague_bearer.flies");
   public static final RegistryObject<SoundEvent> PB_GAS_BURST = reg("plague_bearer.gas_burst");
   public static final RegistryObject<SoundEvent> PB_HISS = reg("plague_bearer.hiss");
   public static final RegistryObject<SoundEvent> PB_HOOK_HIT = reg("plague_bearer.hook_hit");
   public static final RegistryObject<SoundEvent> PB_HURT = reg("plague_bearer.hurt");
   public static final RegistryObject<SoundEvent> PB_MASK_RIP = reg("plague_bearer.mask_rip");
   public static final RegistryObject<SoundEvent> PB_ROAR = reg("plague_bearer.roar");
   public static final RegistryObject<SoundEvent> PB_ROAR_OPEN = reg("plague_bearer.roar_open");
   public static final RegistryObject<SoundEvent> PB_SPORES = reg("plague_bearer.spores");
   public static final RegistryObject<SoundEvent> PB_STEP = reg("plague_bearer.step");
   public static final RegistryObject<SoundEvent> PB_TANK_BURST = reg("plague_bearer.tank_burst");
   public static final RegistryObject<SoundEvent> PB_WAVE = reg("plague_bearer.wave");
   public static final RegistryObject<SoundEvent> MUSIC_PLAGUE_BEARER = reg("music.plague_bearer");
   public static final RegistryObject<SoundEvent> DG_ALARM = reg("dungeon.alarm");
   public static final RegistryObject<SoundEvent> DG_AMB_DRONE = reg("dungeon.amb_drone");
   public static final RegistryObject<SoundEvent> DG_AMB_DRIP = reg("dungeon.amb_drip");
   public static final RegistryObject<SoundEvent> DG_AMB_GROAN = reg("dungeon.amb_groan");
   public static final RegistryObject<SoundEvent> DG_AMB_THUMP = reg("dungeon.amb_thump");
   public static final RegistryObject<SoundEvent> DG_AMB_COUGH = reg("dungeon.amb_cough");
   public static final RegistryObject<SoundEvent> DG_AMB_MOAN = reg("dungeon.amb_moan");
   public static final RegistryObject<SoundEvent> DG_AMB_SCREAM = reg("dungeon.amb_scream");
   public static final RegistryObject<SoundEvent> DG_DENY = reg("dungeon.deny");
   public static final RegistryObject<SoundEvent> DG_STEAM = reg("dungeon.steam");
   public static final RegistryObject<SoundEvent> DG_VALVE_MOUNT = reg("dungeon.valve_mount");
   public static final RegistryObject<SoundEvent> DG_VALVE_TURN = reg("dungeon.valve_turn");
   public static final RegistryObject<SoundEvent> DG_VAT = reg("dungeon.vat");
   public static final RegistryObject<SoundEvent> DG_VENT = reg("dungeon.vent");
   public static final RegistryObject<SoundEvent> DG_SWARM = reg("dungeon.swarm");
   public static final RegistryObject<SoundEvent> DG_POUR = reg("dungeon.pour");
   public static final RegistryObject<SoundEvent> DG_IGNITE = reg("dungeon.ignite");
   public static final RegistryObject<SoundEvent> DG_SPARK = reg("dungeon.spark");
   public static final RegistryObject<SoundEvent> FX_EXPLODE = reg("foley.explode");
   public static final RegistryObject<SoundEvent> FX_METAL_LAND = reg("foley.metal_land");
   public static final RegistryObject<SoundEvent> FX_SWIPE = reg("foley.swipe");
   public static final RegistryObject<SoundEvent> FX_HISS = reg("foley.hiss");
   public static final RegistryObject<SoundEvent> FX_BONE_CRACK = reg("foley.bone_crack");
   public static final RegistryObject<SoundEvent> FX_EARTH_BREAK = reg("foley.earth_break");
   public static final RegistryObject<SoundEvent> FX_HEAVY_STEP = reg("foley.heavy_step");

   private Sounds() {}
}
