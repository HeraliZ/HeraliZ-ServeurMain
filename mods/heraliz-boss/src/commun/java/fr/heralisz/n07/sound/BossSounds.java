package fr.heralisz.n07.sound;

import fr.heralisz.n07.HeraliszN07;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sons propres aux boss (assets/heralisz_n07/sounds.json, fichiers .ogg générés par tools/audio). Pour remplacer un son
 * par un enregistrement, il suffit d'écraser le .ogg du même nom (mono pour les sons, stéréo pour les musiques).
 */
public final class BossSounds {
   public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HeraliszN07.MODID);

   private static RegistryObject<SoundEvent> reg(String name) {
      return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(HeraliszN07.MODID, name)));
   }

   // ------------------------------------------------------------------ Porteur de Peste
   public static final RegistryObject<SoundEvent> PB_ROAR = reg("plague_bearer.roar");
   public static final RegistryObject<SoundEvent> PB_ROAR_OPEN = reg("plague_bearer.roar_open");
   public static final RegistryObject<SoundEvent> PB_BREATH = reg("plague_bearer.breath");
   public static final RegistryObject<SoundEvent> PB_HISS = reg("plague_bearer.hiss");
   public static final RegistryObject<SoundEvent> PB_GAS_BURST = reg("plague_bearer.gas_burst");
   public static final RegistryObject<SoundEvent> PB_CLANG = reg("plague_bearer.clang");
   public static final RegistryObject<SoundEvent> PB_CHAIN = reg("plague_bearer.chain");
   public static final RegistryObject<SoundEvent> PB_HOOK_HIT = reg("plague_bearer.hook_hit");
   public static final RegistryObject<SoundEvent> PB_FLIES = reg("plague_bearer.flies");
   public static final RegistryObject<SoundEvent> PB_BILE = reg("plague_bearer.bile");
   public static final RegistryObject<SoundEvent> PB_SPORES = reg("plague_bearer.spores");
   public static final RegistryObject<SoundEvent> PB_TANK_BURST = reg("plague_bearer.tank_burst");
   public static final RegistryObject<SoundEvent> PB_MASK_RIP = reg("plague_bearer.mask_rip");
   public static final RegistryObject<SoundEvent> PB_DEATH = reg("plague_bearer.death");
   public static final RegistryObject<SoundEvent> PB_HURT = reg("plague_bearer.hurt");
   public static final RegistryObject<SoundEvent> PB_STEP = reg("plague_bearer.step");
   public static final RegistryObject<SoundEvent> PB_WAVE = reg("plague_bearer.wave");
   public static final RegistryObject<SoundEvent> MUSIC_PLAGUE = reg("music.plague_bearer");

   // ------------------------------------------------------------------ Sylvorn
   public static final RegistryObject<SoundEvent> SY_ROAR = reg("sylvorn.roar");
   public static final RegistryObject<SoundEvent> SY_HOWL = reg("sylvorn.howl");
   public static final RegistryObject<SoundEvent> SY_HURT = reg("sylvorn.hurt");
   public static final RegistryObject<SoundEvent> SY_STEP = reg("sylvorn.step");
   public static final RegistryObject<SoundEvent> SY_ROOTS = reg("sylvorn.roots");
   public static final RegistryObject<SoundEvent> SY_CHIMES = reg("sylvorn.chimes");
   public static final RegistryObject<SoundEvent> SY_WHISPER = reg("sylvorn.whisper");
   public static final RegistryObject<SoundEvent> SY_THORNS = reg("sylvorn.thorns");
   public static final RegistryObject<SoundEvent> SY_PETRIFY = reg("sylvorn.petrify");
   public static final RegistryObject<SoundEvent> SY_BREATH = reg("sylvorn.breath");
   public static final RegistryObject<SoundEvent> MUSIC_SYLVORN = reg("music.sylvorn");

   // ------------------------------------------------------------------ Glacivore
   public static final RegistryObject<SoundEvent> GL_ROAR = reg("glacivore.roar");
   public static final RegistryObject<SoundEvent> GL_SCREECH = reg("glacivore.screech");
   public static final RegistryObject<SoundEvent> GL_SNAP = reg("glacivore.snap");
   public static final RegistryObject<SoundEvent> GL_HURT = reg("glacivore.hurt");
   public static final RegistryObject<SoundEvent> GL_CHITTER = reg("glacivore.chitter");
   public static final RegistryObject<SoundEvent> GL_BURROW = reg("glacivore.burrow");
   public static final RegistryObject<SoundEvent> GL_ERUPT = reg("glacivore.erupt");
   public static final RegistryObject<SoundEvent> GL_BREATH = reg("glacivore.breath");
   public static final RegistryObject<SoundEvent> GL_CHARGE = reg("glacivore.charge");
   public static final RegistryObject<SoundEvent> GL_SHARDS = reg("glacivore.shards");
   public static final RegistryObject<SoundEvent> GL_SPIKES = reg("glacivore.spikes");
   public static final RegistryObject<SoundEvent> GL_SHATTER = reg("glacivore.shatter");
   public static final RegistryObject<SoundEvent> GL_FREEZE = reg("glacivore.freeze");
   public static final RegistryObject<SoundEvent> GL_WHISTLE = reg("glacivore.whistle");
   public static final RegistryObject<SoundEvent> GL_DEATH = reg("glacivore.death");
   public static final RegistryObject<SoundEvent> MUSIC_GLACIVORE = reg("music.glacivore");

   // ------------------------------------------------------------------ Mère des Ruines
   public static final RegistryObject<SoundEvent> RM_SCREECH = reg("ruin_mother.screech");
   public static final RegistryObject<SoundEvent> RM_HISS = reg("ruin_mother.hiss");
   public static final RegistryObject<SoundEvent> RM_CHITTER = reg("ruin_mother.chitter");
   public static final RegistryObject<SoundEvent> RM_HURT = reg("ruin_mother.hurt");
   public static final RegistryObject<SoundEvent> RM_STEP = reg("ruin_mother.step");
   public static final RegistryObject<SoundEvent> RM_BITE = reg("ruin_mother.bite");
   public static final RegistryObject<SoundEvent> RM_SPIT = reg("ruin_mother.spit");
   public static final RegistryObject<SoundEvent> RM_SILK = reg("ruin_mother.silk");
   public static final RegistryObject<SoundEvent> RM_SQUELCH = reg("ruin_mother.squelch");
   public static final RegistryObject<SoundEvent> RM_BURST = reg("ruin_mother.burst");
   public static final RegistryObject<SoundEvent> RM_LAND = reg("ruin_mother.land");
   public static final RegistryObject<SoundEvent> RM_SWOOP = reg("ruin_mother.swoop");
   public static final RegistryObject<SoundEvent> RM_COCOON = reg("ruin_mother.cocoon");
   public static final RegistryObject<SoundEvent> RM_DEATH = reg("ruin_mother.death");
   public static final RegistryObject<SoundEvent> MUSIC_RUIN_MOTHER = reg("music.ruin_mother");

   // ------------------------------------------------------------------ Vulkhar
   public static final RegistryObject<SoundEvent> VK_ROAR = reg("vulkhar.roar");
   public static final RegistryObject<SoundEvent> VK_GROWL = reg("vulkhar.growl");
   public static final RegistryObject<SoundEvent> VK_HURT = reg("vulkhar.hurt");
   public static final RegistryObject<SoundEvent> VK_FLAP = reg("vulkhar.flap");
   public static final RegistryObject<SoundEvent> VK_STEP = reg("vulkhar.step");
   public static final RegistryObject<SoundEvent> VK_INHALE = reg("vulkhar.inhale");
   public static final RegistryObject<SoundEvent> VK_FLAME = reg("vulkhar.flame");
   public static final RegistryObject<SoundEvent> VK_FIREBALL = reg("vulkhar.fireball");
   public static final RegistryObject<SoundEvent> VK_MAGMA_HIT = reg("vulkhar.magma_hit");
   public static final RegistryObject<SoundEvent> VK_METEOR = reg("vulkhar.meteor");
   public static final RegistryObject<SoundEvent> VK_RUMBLE = reg("vulkhar.rumble");
   public static final RegistryObject<SoundEvent> VK_NOVA_CHARGE = reg("vulkhar.nova_charge");
   public static final RegistryObject<SoundEvent> VK_NOVA = reg("vulkhar.nova");
   public static final RegistryObject<SoundEvent> VK_BURN = reg("vulkhar.burn");
   public static final RegistryObject<SoundEvent> VK_DEATH = reg("vulkhar.death");
   public static final RegistryObject<SoundEvent> VK_BREATH = reg("vulkhar.breath");
   public static final RegistryObject<SoundEvent> MUSIC_VULKHAR = reg("music.vulkhar");

   // ------------------------------------------------------------------ Sujet N-07
   public static final RegistryObject<SoundEvent> N7_ALARM = reg("sujet_n07.alarm");
   public static final RegistryObject<SoundEvent> N7_ARC = reg("sujet_n07.arc");
   public static final RegistryObject<SoundEvent> N7_BEAM_CHARGE = reg("sujet_n07.beam_charge");
   public static final RegistryObject<SoundEvent> N7_BEAM_FIRE = reg("sujet_n07.beam_fire");
   public static final RegistryObject<SoundEvent> N7_BONES = reg("sujet_n07.bones");
   public static final RegistryObject<SoundEvent> N7_BREATH = reg("sujet_n07.breath");
   public static final RegistryObject<SoundEvent> N7_BUBBLE = reg("sujet_n07.bubble");
   public static final RegistryObject<SoundEvent> N7_CHARGE_UP = reg("sujet_n07.charge_up");
   public static final RegistryObject<SoundEvent> N7_CRACK = reg("sujet_n07.crack");
   public static final RegistryObject<SoundEvent> N7_DEATH = reg("sujet_n07.death");
   public static final RegistryObject<SoundEvent> N7_GEIGER = reg("sujet_n07.geiger");
   public static final RegistryObject<SoundEvent> N7_GEYSER = reg("sujet_n07.geyser");
   public static final RegistryObject<SoundEvent> N7_GROWL = reg("sujet_n07.growl");
   public static final RegistryObject<SoundEvent> N7_HURT = reg("sujet_n07.hurt");
   public static final RegistryObject<SoundEvent> N7_ISOTOPE = reg("sujet_n07.isotope");
   public static final RegistryObject<SoundEvent> N7_ISOTOPE_BOOM = reg("sujet_n07.isotope_boom");
   public static final RegistryObject<SoundEvent> N7_MASK_RIP = reg("sujet_n07.mask_rip");
   public static final RegistryObject<SoundEvent> N7_MELTDOWN = reg("sujet_n07.meltdown");
   public static final RegistryObject<SoundEvent> N7_MORTAR = reg("sujet_n07.mortar");
   public static final RegistryObject<SoundEvent> N7_PULSE = reg("sujet_n07.pulse");
   public static final RegistryObject<SoundEvent> N7_RIP = reg("sujet_n07.rip");
   public static final RegistryObject<SoundEvent> N7_RIP_FLESH = reg("sujet_n07.rip_flesh");
   public static final RegistryObject<SoundEvent> N7_ROAR = reg("sujet_n07.roar");
   public static final RegistryObject<SoundEvent> N7_ROAR_RAW = reg("sujet_n07.roar_raw");
   public static final RegistryObject<SoundEvent> N7_SLAM = reg("sujet_n07.slam");
   public static final RegistryObject<SoundEvent> N7_SPLASH = reg("sujet_n07.splash");
   public static final RegistryObject<SoundEvent> N7_SPRAY = reg("sujet_n07.spray");
   public static final RegistryObject<SoundEvent> N7_STAB = reg("sujet_n07.stab");
   public static final RegistryObject<SoundEvent> N7_STEP = reg("sujet_n07.step");
   public static final RegistryObject<SoundEvent> N7_SWIPE = reg("sujet_n07.swipe");
   public static final RegistryObject<SoundEvent> N7_THROW = reg("sujet_n07.throw");
   public static final RegistryObject<SoundEvent> N7_VAT_BURST = reg("sujet_n07.vat_burst");
   public static final RegistryObject<SoundEvent> N7_VITRIFY = reg("sujet_n07.vitrify");
   public static final RegistryObject<SoundEvent> MUSIC_N07 = reg("music.sujet_n07");

   // ------------------------------------------------------------------ Chevalier Sans Visage
   public static final RegistryObject<SoundEvent> KN_TOLL = reg("faceless_knight.toll");
   public static final RegistryObject<SoundEvent> KN_SWING = reg("faceless_knight.swing");
   public static final RegistryObject<SoundEvent> KN_CLANG = reg("faceless_knight.clang");
   public static final RegistryObject<SoundEvent> KN_IMPACT = reg("faceless_knight.impact");
   public static final RegistryObject<SoundEvent> KN_PLANT = reg("faceless_knight.plant");
   public static final RegistryObject<SoundEvent> KN_STEP = reg("faceless_knight.step");
   public static final RegistryObject<SoundEvent> KN_ROAR = reg("faceless_knight.roar");
   public static final RegistryObject<SoundEvent> KN_HURT = reg("faceless_knight.hurt");
   public static final RegistryObject<SoundEvent> KN_IGNITE = reg("faceless_knight.ignite");
   public static final RegistryObject<SoundEvent> KN_EMBERS = reg("faceless_knight.embers");
   public static final RegistryObject<SoundEvent> KN_ROOTS = reg("faceless_knight.roots");
   public static final RegistryObject<SoundEvent> KN_CRACK = reg("faceless_knight.crack");
   public static final RegistryObject<SoundEvent> KN_HELM_BREAK = reg("faceless_knight.helm_break");
   public static final RegistryObject<SoundEvent> KN_WAVE = reg("faceless_knight.wave");
   public static final RegistryObject<SoundEvent> KN_PILLAR = reg("faceless_knight.pillar");
   public static final RegistryObject<SoundEvent> KN_BLADES = reg("faceless_knight.blades");
   public static final RegistryObject<SoundEvent> KN_DEATH = reg("faceless_knight.death");
   public static final RegistryObject<SoundEvent> KN_SOUL = reg("faceless_knight.soul");
   public static final RegistryObject<SoundEvent> KN_CLATTER = reg("faceless_knight.clatter");
   public static final RegistryObject<SoundEvent> KN_BREATH = reg("faceless_knight.breath");
   public static final RegistryObject<SoundEvent> KN_FIRE_RING = reg("faceless_knight.fire_ring");
   public static final RegistryObject<SoundEvent> MUSIC_KNIGHT = reg("music.faceless_knight");

   // ------------------------------------------------------------------ donjons : rituels d'invocation
   public static final RegistryObject<SoundEvent> DG_KEY_BEEP = reg("dungeon.key_beep");
   public static final RegistryObject<SoundEvent> DG_KEY_SWIPE = reg("dungeon.key_swipe");
   public static final RegistryObject<SoundEvent> DG_KEY_GRANTED = reg("dungeon.key_granted");
   public static final RegistryObject<SoundEvent> DG_KEY_DENIED = reg("dungeon.key_denied");
   public static final RegistryObject<SoundEvent> DG_ALARM = reg("dungeon.alarm");
   public static final RegistryObject<SoundEvent> DG_DENY = reg("dungeon.deny");
   public static final RegistryObject<SoundEvent> DG_VALVE_MOUNT = reg("dungeon.valve_mount");
   public static final RegistryObject<SoundEvent> DG_VALVE_TURN = reg("dungeon.valve_turn");
   public static final RegistryObject<SoundEvent> DG_STEAM = reg("dungeon.steam");
   public static final RegistryObject<SoundEvent> DG_VENT = reg("dungeon.vent");
   public static final RegistryObject<SoundEvent> DG_EMBER_DROP = reg("dungeon.ember_drop");
   public static final RegistryObject<SoundEvent> DG_IGNITE = reg("dungeon.ignite");
   public static final RegistryObject<SoundEvent> DG_FIRE_RUN = reg("dungeon.fire_run");
   public static final RegistryObject<SoundEvent> DG_PLANT = reg("dungeon.plant");
   public static final RegistryObject<SoundEvent> DG_RUNES = reg("dungeon.runes");
   public static final RegistryObject<SoundEvent> DG_ROOTS = reg("dungeon.roots");
   public static final RegistryObject<SoundEvent> DG_EGG_PLACE = reg("dungeon.egg_place");
   public static final RegistryObject<SoundEvent> DG_EGG_PULSE = reg("dungeon.egg_pulse");

   // décors du spawn (boucles d'ambiance, jouées par DecorFx d'après la fiche du décor)
   public static final RegistryObject<SoundEvent> DECOR_GENERATOR = reg("decor.generator");
   public static final RegistryObject<SoundEvent> DECOR_NEON = reg("decor.neon_buzz");
   public static final RegistryObject<SoundEvent> DECOR_RADIO = reg("decor.radio");
   public static final RegistryObject<SoundEvent> DECOR_STEAM = reg("decor.steam_idle");
   public static final RegistryObject<SoundEvent> DECOR_LAMP = reg("decor.lamp_hum");
   public static final RegistryObject<SoundEvent> DG_SILK_STRAIN = reg("dungeon.silk_strain");
   public static final RegistryObject<SoundEvent> DG_COCOON_TEAR = reg("dungeon.cocoon_tear");
   // décors : boucles d'ambiance
   public static final RegistryObject<SoundEvent> DG_POUR = reg("dungeon.pour");
   public static final RegistryObject<SoundEvent> DG_VAT = reg("dungeon.vat");
   public static final RegistryObject<SoundEvent> DG_LAMP_BUZZ = reg("dungeon.lamp_buzz");
   public static final RegistryObject<SoundEvent> DG_SPARK = reg("dungeon.spark");
   public static final RegistryObject<SoundEvent> DG_COIL_CHARGE = reg("dungeon.coil_charge");
   public static final RegistryObject<SoundEvent> DG_ARC_ZAP = reg("dungeon.arc_zap");
   public static final RegistryObject<SoundEvent> DG_GLASS_BURST = reg("dungeon.glass_burst");
   public static final RegistryObject<SoundEvent> DG_TANK_REFILL = reg("dungeon.tank_refill");
   public static final RegistryObject<SoundEvent> DG_AMB_DRONE = reg("dungeon.amb_drone");
   public static final RegistryObject<SoundEvent> DG_AMB_SCREAM = reg("dungeon.amb_scream");
   public static final RegistryObject<SoundEvent> DG_AMB_GROAN = reg("dungeon.amb_groan");
   public static final RegistryObject<SoundEvent> DG_AMB_MOAN = reg("dungeon.amb_moan");
   public static final RegistryObject<SoundEvent> DG_AMB_THUMP = reg("dungeon.amb_thump");
   public static final RegistryObject<SoundEvent> DG_AMB_WHISPER = reg("dungeon.amb_whisper");
   public static final RegistryObject<SoundEvent> DG_BELL_TOLL = reg("dungeon.bell_toll");
   public static final RegistryObject<SoundEvent> DG_AMB_BELL = reg("dungeon.amb_bell");
   public static final RegistryObject<SoundEvent> DG_AMB_CHAINS = reg("dungeon.amb_chains");
   public static final RegistryObject<SoundEvent> DG_FOREST_HORN = reg("dungeon.forest_horn");
   public static final RegistryObject<SoundEvent> DG_AMB_CREAK = reg("dungeon.amb_creak");
   public static final RegistryObject<SoundEvent> DG_SWARM = reg("dungeon.swarm");
   public static final RegistryObject<SoundEvent> DG_AMB_SKITTER = reg("dungeon.amb_skitter");
   public static final RegistryObject<SoundEvent> DG_AMB_COUGH = reg("dungeon.amb_cough");
   public static final RegistryObject<SoundEvent> DG_AMB_DRIP = reg("dungeon.amb_drip");
   public static final RegistryObject<SoundEvent> DG_CHARGE_ARM = reg("dungeon.charge_arm");
   public static final RegistryObject<SoundEvent> DG_DRILL_SPIN = reg("dungeon.drill_spin");
   public static final RegistryObject<SoundEvent> DG_ICE_CRACK = reg("dungeon.ice_crack");
   public static final RegistryObject<SoundEvent> DG_ICE_SHATTER = reg("dungeon.ice_shatter");
   public static final RegistryObject<SoundEvent> DG_AMB_WIND = reg("dungeon.amb_wind");
   public static final RegistryObject<SoundEvent> DG_AMB_ICE = reg("dungeon.amb_ice");
   public static final RegistryObject<SoundEvent> DG_CORE_PLACE = reg("dungeon.core_place");
   public static final RegistryObject<SoundEvent> DG_ANVIL_STRIKE = reg("dungeon.anvil_strike");
   public static final RegistryObject<SoundEvent> DG_LAVA_SURGE = reg("dungeon.lava_surge");
   public static final RegistryObject<SoundEvent> DG_FORGE_ROAR = reg("dungeon.forge_roar");
   public static final RegistryObject<SoundEvent> DG_AMB_LAVA = reg("dungeon.amb_lava");

   private BossSounds() {}
}
