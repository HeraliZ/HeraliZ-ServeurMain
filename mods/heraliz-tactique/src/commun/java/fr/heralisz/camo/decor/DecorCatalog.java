package fr.heralisz.camo.decor;

import net.minecraft.ChatFormatting;

import javax.annotation.Nullable;

/** Catalogues : dessins de stickers (planche textures/decor/stickers.png), finitions, pendentifs. */
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

   /**
    * Pendentifs, au bout d'une chaînette :
    * <ul>
    * <li>répliques miniatures des vraies armes du serveur avec leur skin (couteaux de caisse, armes camo), vrai modèle TaCZ ;</li>
    * <li>mobs de Minecraft (vrai modèle, texture, variante et petites animations : ailes, mouton « jeb_ », tentacules...).</li>
    * </ul>
    */
   public enum Charm {
      BUTTERFLY_FADE("heralisz_skins:butterfly__fade", null, Kind.KNIFE),
      BUTTERFLY_MARBLE_FADE("heralisz_skins:butterfly__marble_fade", null, Kind.KNIFE),
      BUTTERFLY_GAMMA_EMERALD("heralisz_skins:butterfly__gamma_emerald", null, Kind.KNIFE),
      BUTTERFLY_RUBY("heralisz_skins:butterfly__doppler_ruby", null, Kind.KNIFE),
      BUTTERFLY_SAPPHIRE("heralisz_skins:butterfly__doppler_sapphire", null, Kind.KNIFE),
      BUTTERFLY_BLACK_PEARL("heralisz_skins:butterfly__doppler_black_pearl", null, Kind.KNIFE),
      KARAMBIT_CRIMSON_WEB("heralisz_skins:karambit__crimson_web", null, Kind.KNIFE),
      KARAMBIT_TIGER_TOOTH("heralisz_skins:karambit__tiger_tooth", null, Kind.KNIFE),
      KARAMBIT_CASE_HARDENED("heralisz_skins:karambit__case_hardened", null, Kind.KNIFE),
      M9_LORE("heralisz_skins:m9__lore", null, Kind.KNIFE),
      M9_AUTOTRONIC("heralisz_skins:m9__autotronic", null, Kind.KNIFE),
      M9_SLAUGHTER("heralisz_skins:m9__slaughter", null, Kind.KNIFE),
      AK47_DARK_MATTER("tacz:ak47", "dark_matter", Kind.GUN),
      AWP_DRAGON("arkaniaz:cs_awp", "dragon", Kind.GUN),
      DEAGLE_GOLD("tacz:deagle", "gold", Kind.GUN),
      VECTOR_TOXIC("tacz:vector45", "toxic", Kind.GUN),
      SCAR_PLASMA("tacz:scar_h", "plasma", Kind.GUN),
      M200_BLACK_ICE("arkaniaz:m200", "black_ice", Kind.GUN),
      CAT_TABBY("minecraft:cat", "{variant:\"minecraft:tabby\"}", Kind.MOB),
      CAT_BLACK("minecraft:cat", "{variant:\"minecraft:all_black\"}", Kind.MOB),
      CAT_SIAMESE("minecraft:cat", "{variant:\"minecraft:siamese\"}", Kind.MOB),
      CAT_CALICO("minecraft:cat", "{variant:\"minecraft:calico\"}", Kind.MOB),
      SHEEP("minecraft:sheep", null, Kind.MOB),
      SHEEP_PINK("minecraft:sheep", "{Color:6b}", Kind.MOB),
      SHEEP_RAINBOW("minecraft:sheep", "{CustomName:'{\"text\":\"jeb_\"}'}", Kind.MOB),
      COW("minecraft:cow", null, Kind.MOB),
      MOOSHROOM("minecraft:mooshroom", "{Type:\"red\"}", Kind.MOB),
      PIG("minecraft:pig", null, Kind.MOB),
      PIG_BABY("minecraft:pig", "{Age:-24000}", Kind.MOB),
      CHICKEN("minecraft:chicken", null, Kind.MOB),
      FOX("minecraft:fox", "{Type:\"red\"}", Kind.MOB),
      FOX_SNOW("minecraft:fox", "{Type:\"snow\"}", Kind.MOB),
      WOLF("minecraft:wolf", "{Owner:[I;1,2,3,4],CollarColor:14b}", Kind.MOB),
      AXOLOTL_PINK("minecraft:axolotl", "{Variant:0}", Kind.MOB),
      AXOLOTL_BLUE("minecraft:axolotl", "{Variant:4}", Kind.MOB),
      FROG("minecraft:frog", "{variant:\"minecraft:temperate\"}", Kind.MOB),
      BEE("minecraft:bee", null, Kind.MOB),
      PANDA("minecraft:panda", null, Kind.MOB),
      PARROT("minecraft:parrot", "{Variant:0}", Kind.MOB),
      RABBIT("minecraft:rabbit", "{RabbitType:0}", Kind.MOB),
      ALLAY("minecraft:allay", null, Kind.MOB),
      CREEPER("minecraft:creeper", null, Kind.MOB),
      CREEPER_CHARGED("minecraft:creeper", "{powered:1b}", Kind.MOB),
      ZOMBIE("minecraft:zombie", null, Kind.MOB),
      SKELETON("minecraft:skeleton", null, Kind.MOB),
      SLIME("minecraft:slime", "{Size:0}", Kind.MOB),
      SPIDER("minecraft:spider", null, Kind.MOB),
      BLAZE("minecraft:blaze", null, Kind.MOB),
      ENDERMAN("minecraft:enderman", null, Kind.MOB),
      WARDEN("minecraft:warden", null, Kind.MOB),
      GHAST("minecraft:ghast", null, Kind.MOB);

      /** Couteau : pend par le manche, lame en bas ; arme : à plat, anneau au milieu du dessus ; mob : debout, anneau sur le dos. */
      public enum Kind {
         KNIFE, GUN, MOB
      }

      /** Arme TaCZ reproduite (GunId) ou type de mob. */
      public final String ref;
      /** Arme : camo du mod posé sur la réplique (null : skin porté par l'arme, couteaux de caisse). Mob : NBT de sa variante. */
      @Nullable public final String variant;
      public final Kind kind;

      Charm(String ref, @Nullable String variant, Kind kind) {
         this.ref = ref;
         this.variant = variant;
         this.kind = kind;
      }

      public String id() {
         return this.name().toLowerCase();
      }

      @Nullable
      public static Charm byId(String id) {
         for (Charm c : values()) {
            if (c.id().equals(id)) return c;
         }
         return null;
      }
   }
}
