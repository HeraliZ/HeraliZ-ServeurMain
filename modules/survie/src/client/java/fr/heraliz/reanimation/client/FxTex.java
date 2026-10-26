package fr.heraliz.reanimation.client;

import net.minecraft.resources.ResourceLocation;

final class FxTex {
   static final ResourceLocation PARTICLES = fx("particles");
   static final ResourceLocation BEAM = fx("beam");
   static final ResourceLocation SWIRL = fx("swirl");
   static final ResourceLocation HOLOGRAM = fx("hologram");
   static final ResourceLocation SHADOW = fx("shadow");
   static final ResourceLocation PORTAL = fx("portal");
   static final ResourceLocation[] SIGIL = new ResourceLocation[]{fx("sigil_0"), fx("sigil_1"), fx("sigil_2"), fx("sigil_3")};
   static final ResourceLocation SHOCK = fx("shock");
   static final ResourceLocation FROST = fx("frost");
   static final ResourceLocation SCORCH = fx("scorch");
   static final ResourceLocation TAG = fx("tag");
   static final ResourceLocation SOUL = new ResourceLocation("reanimation", "textures/entity/soul.png");
   static final ResourceLocation KATANA = prop("katana");
   static final ResourceLocation CARDS = prop("cards");
   static final ResourceLocation JOKER = prop("joker");
   static final ResourceLocation ICE = prop("ice");
   static final ResourceLocation CRYSTAL = prop("crystal");
   static final ResourceLocation GIFT = prop("gift");
   static final ResourceLocation CANDY = prop("candy");
   static final ResourceLocation SNOW = prop("snow");
   static final ResourceLocation HAT = prop("hat");
   static final ResourceLocation SCARF = prop("scarf");
   static final ResourceLocation CHAIN = prop("chain");
   static final ResourceLocation THREAD = prop("thread");
   static final ResourceLocation DISK = prop("disk");
   static final ResourceLocation SWORD_LIGHT = prop("sword_light");
   static final ResourceLocation CAN = prop("can");
   static final ResourceLocation CARROT = prop("carrot");
   static final ResourceLocation COAL = prop("coal");
   static final ResourceLocation ROCK = prop("rock");
   static final ResourceLocation VOID = prop("void");
   static final ResourceLocation BARK = prop("bark");
   static final ResourceLocation WRAP = prop("wrap");
   static final ResourceLocation RIBBON = prop("ribbon");
   static final ResourceLocation SCALES = prop("scales");
   static final ResourceLocation HORN = prop("horn");
   static final ResourceLocation FOLIAGE = prop("foliage");
   static final ResourceLocation UMBRA = prop("umbra");
   static final ResourceLocation STARFIELD = fx("starfield");
   static final ResourceLocation NEBULA = fx("nebula");

   private FxTex() {
   }

   private static ResourceLocation fx(String name) {
      return new ResourceLocation("reanimation", "textures/fx/" + name + ".png");
   }

   private static ResourceLocation prop(String name) {
      return new ResourceLocation("reanimation", "textures/prop/" + name + ".png");
   }
}
