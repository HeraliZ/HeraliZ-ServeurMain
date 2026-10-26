package fr.heraliz.reanimation.client;

import fr.heraliz.reanimation.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;

enum Sfx {
   WHOOSH,
   WHOOSH_HEAVY,
   DASH,
   IMPACT,
   IMPACT_HEAVY,
   SLAM,
   SHOCKWAVE,
   CRIT,
   BONE_CRACK,
   BODY_FALL,
   FLESH,
   HEARTBEAT,
   RISER,
   DROP_BOOM,
   REVERSE_SWELL,
   GLITCH,
   SPARKLE,
   CHIME,
   HUM,
   CHARGE,
   BLAST,
   BEAM,
   ZAP,
   THUNDER,
   CRACKLE,
   FIRE_WHOOSH,
   FIRE_ROAR,
   ICE_CRACK,
   ICE_SHATTER,
   FREEZE,
   BLADE_DRAW,
   BLADE_SLASH,
   SHEATH_CLICK,
   CARD_SHUFFLE,
   CARD_FLICK,
   CARD_THUD,
   STRING_PLUCK,
   STRING_SNAP,
   PORTAL_OPEN,
   PORTAL_CLOSE,
   WARP,
   BLACKHOLE,
   IMPLOSION,
   RUNE,
   CHAIN,
   CHOIR,
   SWORD_FALL,
   HOLY_IMPACT,
   ROCK_RUMBLE,
   ROCK_HIT,
   CLONE_SPLIT,
   PETAL_GUST,
   SPRAY,
   CAN_RATTLE,
   NEON_IGNITE,
   SCRATCH,
   SLEIGH_BELLS,
   CONFETTI_POP,
   GIFT_SHAKE,
   RIBBON,
   CANDY_CRACK,
   SNOW_CRUNCH,
   SNOW_BURST,
   JINGLE_CHORD;

   RegistryObject<SoundEvent> get() {
      return switch (this) {
         case WHOOSH -> ModSounds.WHOOSH;
         case WHOOSH_HEAVY -> ModSounds.WHOOSH_HEAVY;
         case DASH -> ModSounds.DASH;
         case IMPACT -> ModSounds.IMPACT;
         case IMPACT_HEAVY -> ModSounds.IMPACT_HEAVY;
         case SLAM -> ModSounds.SLAM;
         case SHOCKWAVE -> ModSounds.SHOCKWAVE;
         case CRIT -> ModSounds.CRIT;
         case BONE_CRACK -> ModSounds.BONE_CRACK;
         case BODY_FALL -> ModSounds.BODY_FALL;
         case FLESH -> ModSounds.FLESH;
         case HEARTBEAT -> ModSounds.HEARTBEAT;
         case RISER -> ModSounds.RISER;
         case DROP_BOOM -> ModSounds.DROP_BOOM;
         case REVERSE_SWELL -> ModSounds.REVERSE_SWELL;
         case GLITCH -> ModSounds.GLITCH;
         case SPARKLE -> ModSounds.SPARKLE;
         case CHIME -> ModSounds.CHIME;
         case HUM -> ModSounds.HUM;
         case CHARGE -> ModSounds.CHARGE;
         case BLAST -> ModSounds.BLAST;
         case BEAM -> ModSounds.BEAM;
         case ZAP -> ModSounds.ZAP;
         case THUNDER -> ModSounds.THUNDER;
         case CRACKLE -> ModSounds.CRACKLE;
         case FIRE_WHOOSH -> ModSounds.FIRE_WHOOSH;
         case FIRE_ROAR -> ModSounds.FIRE_ROAR;
         case ICE_CRACK -> ModSounds.ICE_CRACK;
         case ICE_SHATTER -> ModSounds.ICE_SHATTER;
         case FREEZE -> ModSounds.FREEZE;
         case BLADE_DRAW -> ModSounds.BLADE_DRAW;
         case BLADE_SLASH -> ModSounds.BLADE_SLASH;
         case SHEATH_CLICK -> ModSounds.SHEATH_CLICK;
         case CARD_SHUFFLE -> ModSounds.CARD_SHUFFLE;
         case CARD_FLICK -> ModSounds.CARD_FLICK;
         case CARD_THUD -> ModSounds.CARD_THUD;
         case STRING_PLUCK -> ModSounds.STRING_PLUCK;
         case STRING_SNAP -> ModSounds.STRING_SNAP;
         case PORTAL_OPEN -> ModSounds.PORTAL_OPEN;
         case PORTAL_CLOSE -> ModSounds.PORTAL_CLOSE;
         case WARP -> ModSounds.WARP;
         case BLACKHOLE -> ModSounds.BLACKHOLE;
         case IMPLOSION -> ModSounds.IMPLOSION;
         case RUNE -> ModSounds.RUNE;
         case CHAIN -> ModSounds.CHAIN;
         case CHOIR -> ModSounds.CHOIR;
         case SWORD_FALL -> ModSounds.SWORD_FALL;
         case HOLY_IMPACT -> ModSounds.HOLY_IMPACT;
         case ROCK_RUMBLE -> ModSounds.ROCK_RUMBLE;
         case ROCK_HIT -> ModSounds.ROCK_HIT;
         case CLONE_SPLIT -> ModSounds.CLONE_SPLIT;
         case PETAL_GUST -> ModSounds.PETAL_GUST;
         case SPRAY -> ModSounds.SPRAY;
         case CAN_RATTLE -> ModSounds.CAN_RATTLE;
         case NEON_IGNITE -> ModSounds.NEON_IGNITE;
         case SCRATCH -> ModSounds.SCRATCH;
         case SLEIGH_BELLS -> ModSounds.SLEIGH_BELLS;
         case CONFETTI_POP -> ModSounds.CONFETTI_POP;
         case GIFT_SHAKE -> ModSounds.GIFT_SHAKE;
         case RIBBON -> ModSounds.RIBBON;
         case CANDY_CRACK -> ModSounds.CANDY_CRACK;
         case SNOW_CRUNCH -> ModSounds.SNOW_CRUNCH;
         case SNOW_BURST -> ModSounds.SNOW_BURST;
         case JINGLE_CHORD -> ModSounds.JINGLE_CHORD;
      };
   }
}
