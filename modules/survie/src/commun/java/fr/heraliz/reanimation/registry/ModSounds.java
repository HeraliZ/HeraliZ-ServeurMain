package fr.heraliz.reanimation.registry;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
   public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "reanimation");
   public static final RegistryObject<SoundEvent> WHOOSH = register("fx.whoosh");
   public static final RegistryObject<SoundEvent> WHOOSH_HEAVY = register("fx.whoosh_heavy");
   public static final RegistryObject<SoundEvent> DASH = register("fx.dash");
   public static final RegistryObject<SoundEvent> IMPACT = register("fx.impact");
   public static final RegistryObject<SoundEvent> IMPACT_HEAVY = register("fx.impact_heavy");
   public static final RegistryObject<SoundEvent> SLAM = register("fx.slam");
   public static final RegistryObject<SoundEvent> SHOCKWAVE = register("fx.shockwave");
   public static final RegistryObject<SoundEvent> CRIT = register("fx.crit");
   public static final RegistryObject<SoundEvent> BONE_CRACK = register("fx.bone_crack");
   public static final RegistryObject<SoundEvent> BODY_FALL = register("fx.body_fall");
   public static final RegistryObject<SoundEvent> FLESH = register("fx.flesh");
   public static final RegistryObject<SoundEvent> HEARTBEAT = register("fx.heartbeat");
   public static final RegistryObject<SoundEvent> RISER = register("fx.riser");
   public static final RegistryObject<SoundEvent> DROP_BOOM = register("fx.drop_boom");
   public static final RegistryObject<SoundEvent> REVERSE_SWELL = register("fx.reverse_swell");
   public static final RegistryObject<SoundEvent> GLITCH = register("fx.glitch");
   public static final RegistryObject<SoundEvent> SPARKLE = register("fx.sparkle");
   public static final RegistryObject<SoundEvent> CHIME = register("fx.chime");
   public static final RegistryObject<SoundEvent> HUM = register("fx.hum");
   public static final RegistryObject<SoundEvent> CHARGE = register("fx.charge");
   public static final RegistryObject<SoundEvent> BLAST = register("fx.blast");
   public static final RegistryObject<SoundEvent> BEAM = register("fx.beam");
   public static final RegistryObject<SoundEvent> ZAP = register("fx.zap");
   public static final RegistryObject<SoundEvent> THUNDER = register("fx.thunder");
   public static final RegistryObject<SoundEvent> CRACKLE = register("fx.crackle");
   public static final RegistryObject<SoundEvent> FIRE_WHOOSH = register("fx.fire_whoosh");
   public static final RegistryObject<SoundEvent> FIRE_ROAR = register("fx.fire_roar");
   public static final RegistryObject<SoundEvent> ICE_CRACK = register("fx.ice_crack");
   public static final RegistryObject<SoundEvent> ICE_SHATTER = register("fx.ice_shatter");
   public static final RegistryObject<SoundEvent> FREEZE = register("fx.freeze");
   public static final RegistryObject<SoundEvent> BLADE_DRAW = register("fx.blade_draw");
   public static final RegistryObject<SoundEvent> BLADE_SLASH = register("fx.blade_slash");
   public static final RegistryObject<SoundEvent> SHEATH_CLICK = register("fx.sheath_click");
   public static final RegistryObject<SoundEvent> CARD_SHUFFLE = register("fx.card_shuffle");
   public static final RegistryObject<SoundEvent> CARD_FLICK = register("fx.card_flick");
   public static final RegistryObject<SoundEvent> CARD_THUD = register("fx.card_thud");
   public static final RegistryObject<SoundEvent> STRING_PLUCK = register("fx.string_pluck");
   public static final RegistryObject<SoundEvent> STRING_SNAP = register("fx.string_snap");
   public static final RegistryObject<SoundEvent> PORTAL_OPEN = register("fx.portal_open");
   public static final RegistryObject<SoundEvent> PORTAL_CLOSE = register("fx.portal_close");
   public static final RegistryObject<SoundEvent> WARP = register("fx.warp");
   public static final RegistryObject<SoundEvent> BLACKHOLE = register("fx.blackhole");
   public static final RegistryObject<SoundEvent> IMPLOSION = register("fx.implosion");
   public static final RegistryObject<SoundEvent> RUNE = register("fx.rune");
   public static final RegistryObject<SoundEvent> CHAIN = register("fx.chain");
   public static final RegistryObject<SoundEvent> CHOIR = register("fx.choir");
   public static final RegistryObject<SoundEvent> SWORD_FALL = register("fx.sword_fall");
   public static final RegistryObject<SoundEvent> HOLY_IMPACT = register("fx.holy_impact");
   public static final RegistryObject<SoundEvent> ROCK_RUMBLE = register("fx.rock_rumble");
   public static final RegistryObject<SoundEvent> ROCK_HIT = register("fx.rock_hit");
   public static final RegistryObject<SoundEvent> CLONE_SPLIT = register("fx.clone_split");
   public static final RegistryObject<SoundEvent> PETAL_GUST = register("fx.petal_gust");
   public static final RegistryObject<SoundEvent> SPRAY = register("fx.spray");
   public static final RegistryObject<SoundEvent> CAN_RATTLE = register("fx.can_rattle");
   public static final RegistryObject<SoundEvent> NEON_IGNITE = register("fx.neon_ignite");
   public static final RegistryObject<SoundEvent> SCRATCH = register("fx.scratch");
   public static final RegistryObject<SoundEvent> SLEIGH_BELLS = register("fx.sleigh_bells");
   public static final RegistryObject<SoundEvent> CONFETTI_POP = register("fx.confetti_pop");
   public static final RegistryObject<SoundEvent> GIFT_SHAKE = register("fx.gift_shake");
   public static final RegistryObject<SoundEvent> RIBBON = register("fx.ribbon");
   public static final RegistryObject<SoundEvent> CANDY_CRACK = register("fx.candy_crack");
   public static final RegistryObject<SoundEvent> SNOW_CRUNCH = register("fx.snow_crunch");
   public static final RegistryObject<SoundEvent> SNOW_BURST = register("fx.snow_burst");
   public static final RegistryObject<SoundEvent> JINGLE_CHORD = register("fx.jingle_chord");
   public static final RegistryObject<SoundEvent> MUSIC_MILLE_COUPES = register("music.mille_coupes");
   public static final RegistryObject<SoundEvent> MUSIC_SINGULARITE = register("music.singularite");
   public static final RegistryObject<SoundEvent> MUSIC_COURROUX_CIEL = register("music.courroux_ciel");
   public static final RegistryObject<SoundEvent> MUSIC_SOUFFLE_DRAGON = register("music.souffle_dragon");
   public static final RegistryObject<SoundEvent> MUSIC_ZERO_ABSOLU = register("music.zero_absolu");
   public static final RegistryObject<SoundEvent> MUSIC_DANSE_OMBRES = register("music.danse_ombres");
   public static final RegistryObject<SoundEvent> MUSIC_MAIN_JOKER = register("music.main_joker");
   public static final RegistryObject<SoundEvent> MUSIC_FAILLE = register("music.faille");
   public static final RegistryObject<SoundEvent> MUSIC_ONDE_CELESTE = register("music.onde_celeste");
   public static final RegistryObject<SoundEvent> MUSIC_APESANTEUR = register("music.apesanteur");
   public static final RegistryObject<SoundEvent> MUSIC_MARIONNETTISTE = register("music.marionnettiste");
   public static final RegistryObject<SoundEvent> MUSIC_FREESTYLE_COMBO = register("music.freestyle_combo");
   public static final RegistryObject<SoundEvent> MUSIC_TEMPETE_SAKURA = register("music.tempete_sakura");
   public static final RegistryObject<SoundEvent> MUSIC_TAG_FINAL = register("music.tag_final");
   public static final RegistryObject<SoundEvent> MUSIC_JUGEMENT_RUNIQUE = register("music.jugement_runique");
   public static final RegistryObject<SoundEvent> MUSIC_CADEAU_SURPRISE = register("music.cadeau_surprise");
   public static final RegistryObject<SoundEvent> MUSIC_SUCRE_D_ORGE = register("music.sucre_d_orge");
   public static final RegistryObject<SoundEvent> MUSIC_BONHOMME_DE_NEIGE = register("music.bonhomme_de_neige");
   public static final RegistryObject<SoundEvent> MUSIC_LAMES_FANTOMES = register("music.lames_fantomes");
   public static final RegistryObject<SoundEvent> MUSIC_PANTIN = register("music.pantin");

   private ModSounds() {
   }

   private static RegistryObject<SoundEvent> register(String name) {
      return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("reanimation", name)));
   }

   @Nullable
   public static RegistryObject<SoundEvent> music(String id) {
      return switch (id) {
         case "mille_coupes" -> MUSIC_MILLE_COUPES;
         case "singularite" -> MUSIC_SINGULARITE;
         case "courroux_ciel" -> MUSIC_COURROUX_CIEL;
         case "souffle_dragon" -> MUSIC_SOUFFLE_DRAGON;
         case "zero_absolu" -> MUSIC_ZERO_ABSOLU;
         case "danse_ombres" -> MUSIC_DANSE_OMBRES;
         case "main_joker" -> MUSIC_MAIN_JOKER;
         case "faille" -> MUSIC_FAILLE;
         case "onde_celeste" -> MUSIC_ONDE_CELESTE;
         case "apesanteur" -> MUSIC_APESANTEUR;
         case "marionnettiste" -> MUSIC_MARIONNETTISTE;
         case "freestyle_combo" -> MUSIC_FREESTYLE_COMBO;
         case "tempete_sakura" -> MUSIC_TEMPETE_SAKURA;
         case "tag_final" -> MUSIC_TAG_FINAL;
         case "jugement_runique" -> MUSIC_JUGEMENT_RUNIQUE;
         case "cadeau_surprise" -> MUSIC_CADEAU_SURPRISE;
         case "sucre_d_orge" -> MUSIC_SUCRE_D_ORGE;
         case "bonhomme_de_neige" -> MUSIC_BONHOMME_DE_NEIGE;
         case "lames_fantomes" -> MUSIC_LAMES_FANTOMES;
         case "pantin" -> MUSIC_PANTIN;
         default -> null;
      };
   }
}
