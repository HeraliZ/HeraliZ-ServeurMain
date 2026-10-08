package fr.heralisz.camo;

import net.minecraft.ChatFormatting;

import javax.annotation.Nullable;

/**
 * Les camouflages. {@code shaderId} = numéro de recette dans camo.fsh ; {@code parts} = parties de l'arme couvertes
 * (bits : 1 corps, 2 chargeur, 4 canon).
 */
public enum Camo {
   // Tous les camos couvrent toute l'arme (corps, chargeur, canon) : sur certaines armes les pièces principales
   // s'appellent « barrel » / « mag » et restaient sans camo.
   // Commun : couleurs simples
   CRIMSON("crimson", 20, Tier.COMMON, Camo.ALL),
   NAVY("navy", 21, Tier.COMMON, Camo.ALL),
   ARCTIC("arctic", 22, Tier.COMMON, Camo.ALL),
   OLIVE("olive", 23, Tier.COMMON, Camo.ALL),
   SAND("sand", 24, Tier.COMMON, Camo.ALL),
   SAKURA("sakura", 25, Tier.COMMON, Camo.ALL),
   // Peu commun : motifs
   CARBON("carbon", 13, Tier.UNCOMMON, Camo.ALL),
   WOODLAND("woodland", 14, Tier.UNCOMMON, Camo.ALL),
   DIGITAL("digital", 15, Tier.UNCOMMON, Camo.ALL),
   TIGER("tiger", 16, Tier.UNCOMMON, Camo.ALL),
   RUST("rust", 48, Tier.UNCOMMON, Camo.ALL),
   // Rare et au-delà : toute l'arme
   ICE("ice", 11, Tier.RARE, Camo.ALL),
   TOXIC("toxic", 10, Tier.RARE, Camo.ALL),
   PLASMA("plasma", 9, Tier.RARE, Camo.ALL),
   CIRCUIT("circuit", 47, Tier.RARE, Camo.ALL),
   GOLD("gold", 1, Tier.EPIC, Camo.ALL),
   HONEYCOMB("honeycomb", 12, Tier.EPIC, Camo.ALL),
   LAVA("lava", 7, Tier.EPIC, Camo.ALL),
   GALAXY("galaxy", 8, Tier.EPIC, Camo.ALL),
   // collection « légendes COD » (inspirations)
   SOLAR("solar", 30, Tier.EPIC, Camo.ALL),
   BOREALIS("borealis", 31, Tier.EPIC, Camo.ALL),
   AFTERLIFE("afterlife", 33, Tier.EPIC, Camo.ALL),
   BIOLUMINESCENCE("bioluminescence", 34, Tier.EPIC, Camo.ALL),
   GENESIS("genesis", 35, Tier.EPIC, Camo.ALL),
   DARK_AETHER("dark_aether", 38, Tier.EPIC, Camo.ALL),
   INFESTATION("infestation", 40, Tier.EPIC, Camo.ALL),
   DAMASCUS("damascus", 41, Tier.UNCOMMON, Camo.ALL),
   ATOMIC("atomic", 42, Tier.EPIC, Camo.ALL),
   OBSIDIAN("obsidian", 43, Tier.EPIC, Camo.ALL),
   GLITCH("glitch", 45, Tier.EPIC, Camo.ALL),
   AMETHYST("amethyst", 49, Tier.EPIC, Camo.ALL),
   ORION("orion", 32, Tier.LEGENDARY, Camo.ALL),
   SINGULARITY("singularity", 36, Tier.LEGENDARY, Camo.ALL),
   NEXUS_HORIZON("nexus_horizon", 37, Tier.LEGENDARY, Camo.ALL),
   DARK_MATTER_NUCLEAR("dark_matter_nuclear", 39, Tier.LEGENDARY, Camo.ALL),
   DIAMOND("diamond", 5, Tier.EPIC, Camo.ALL),
   BLACK_ICE("black_ice", 71, Tier.EPIC, Camo.ALL),
   LIQUID_CHROME("liquid_chrome", 44, Tier.LEGENDARY, Camo.ALL),
   DRAGON("dragon", 46, Tier.LEGENDARY, Camo.ALL),
   STORM("storm", 70, Tier.LEGENDARY, Camo.ALL),
   DARK_MATTER("dark_matter", 6, Tier.LEGENDARY, Camo.ALL),
   // (72 Dark Matter Origine, le Dark Matter à piquots visqueux, retiré en 0.9.12 : son id est lu comme DARK_MATTER, voir byId)
   // collection Pack-a-Punch, façon COD Zombies : les 3 niveaux de l'arme améliorée du mode zombies (HeraliZ Matchs pose
   // pap_1, pap_2 puis pap_3 sur l'arme). Ces ids sont le contrat avec heralisz_matchs : ne jamais les changer.
   PAP_1("pap_1", 73, Tier.LEGENDARY, Camo.ALL),
   PAP_2("pap_2", 74, Tier.LEGENDARY, Camo.ALL),
   PAP_3("pap_3", 75, Tier.LEGENDARY, Camo.ALL),
   // skins X : camo vivant + effet 3D, onglet « HeralisZ - skinX » (un skin peut être réservé à une arme : Camo.gun)
   SERAPHIN("seraphin", 208, Tier.MYTHIC, Camo.ALL),
   CHAINES_ENFER("chaines_enfer", 209, Tier.MYTHIC, Camo.ALL),
   // skins « buzz » (réactifs, mèmes) : 230+
   TEXTURE_MANQUANTE("texture_manquante", 230, Tier.MYTHIC, Camo.ALL),
   MONEY_GUN("money_gun", 231, Tier.MYTHIC, Camo.ALL),
   SURCHAUFFE("surchauffe", 232, Tier.MYTHIC, Camo.ALL),
   TROPHEE("trophee", 233, Tier.MYTHIC, Camo.ALL);

   public static final int BODY = 1, MAG = 2, BARREL = 4, ALL = 7;

   public final String id;
   public final int shaderId;
   public final Tier tier;
   public final int parts;
   /** Arme à laquelle le skin est réservé (skins de boss), null : toutes. */
   @Nullable
   public final String gun;

   Camo(String id, int shaderId, Tier tier, int parts) {
      this(id, shaderId, tier, parts, null);
   }

   Camo(String id, int shaderId, Tier tier, int parts, @Nullable String gun) {
      this.id = id;
      this.shaderId = shaderId;
      this.tier = tier;
      this.parts = parts;
      this.gun = gun;
   }

   /** Ce skin peut-il être posé sur cette arme ? */
   public boolean fits(@Nullable net.minecraft.resources.ResourceLocation gunId) {
      return this.gun == null || gunId != null && this.gun.equals(gunId.toString());
   }

   /** Skin X : camo vivant avec effet 3D (onglet dédié). */
   public boolean isSkinX() {
      return this.tier == Tier.MYTHIC;
   }

   public String nameKey() {
      return "camo.heralisz_camo." + this.id;
   }

   @Nullable
   public static Camo byId(String id) {
      for (Camo c : values()) {
         if (c.id.equals(id)) {
            return c;
         }
      }
      // camos retirés : les armes et les sacs qui portent encore l'ancien id gardent le camo qui le remplace
      if (DARK_MATTER_ORIGIN_ID.equals(id)) {
         return DARK_MATTER;
      }
      return null;
   }

   /** Id du Dark Matter Origine (retiré en 0.9.12, remplacé par le Dark Matter) : NBT des armes et objet camo_ (CamoMigration). */
   public static final String DARK_MATTER_ORIGIN_ID = "dark_matter_origin";

   /** Raretés des caisses. */
   public enum Tier {
      COMMON(ChatFormatting.GRAY),
      UNCOMMON(ChatFormatting.GREEN),
      RARE(ChatFormatting.BLUE),
      EPIC(ChatFormatting.LIGHT_PURPLE),
      LEGENDARY(ChatFormatting.GOLD),
      MYTHIC(ChatFormatting.RED);

      public final ChatFormatting color;

      Tier(ChatFormatting color) {
         this.color = color;
      }

      public String key() {
         return "tier.heralisz_camo." + this.name().toLowerCase();
      }
   }
}
