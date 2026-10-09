package fr.heralisz.camo.decor;

import net.minecraft.ChatFormatting;

import javax.annotation.Nullable;

/** Catalogues : dessins de stickers (planche textures/decor/stickers.png) et finitions. */
public final class DecorCatalog {
   private DecorCatalog() {
   }

   /** Dessins de stickers : case de la planche (tools/decor_assets.py, même ordre). */
   public enum Sticker {
      ACE, HEADSHOT, CLUTCH, ONE_TAP, NO_SCOPE, GLHF, REAPER, WOLF, VIPER, BOOM, FRAG, SKULL_KING, BIOHAZARD, RADIOACTIVE, SURVIVOR,
      OUTBREAK, DEAD_ZONE, LOCK_N_LOAD, ELITE, PHOENIX, ONI, KITSUNE, KATANA, VOLT, DIAMOND, TOXIC, RAGE_QUIT, LAST_HP, RNG, BLOOD_MONEY,
      WALLBANG, GGWP;

      public String id() {
         return this.name().toLowerCase();
      }

      @Nullable
      public static Sticker byId(String id) {
         for (Sticker s : values()) {
            if (s.id().equals(id)) return s;
         }
         return null;
      }
   }

   /** Finitions (comme CS) : papier, holographique, métallisé, or ; la rareté monte. */
   public enum Finish {
      PAPER(ChatFormatting.GRAY), HOLO(ChatFormatting.LIGHT_PURPLE), FOIL(ChatFormatting.AQUA), GOLD(ChatFormatting.GOLD);

      public final ChatFormatting color;

      Finish(ChatFormatting color) {
         this.color = color;
      }

      public String id() {
         return this.name().toLowerCase();
      }
   }
}
