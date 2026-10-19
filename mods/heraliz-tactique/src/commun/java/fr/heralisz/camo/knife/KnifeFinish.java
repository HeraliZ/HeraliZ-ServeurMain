package fr.heralisz.camo.knife;

import javax.annotation.Nullable;

/**
 * Finitions de couteaux façon CS:GO, rendues par le shader (camo_&lt;shaderId&gt;). Un skin de couteau est un objet TaCZ
 * du pack heralisz_skins dont l'id est « &lt;couteau&gt;__&lt;finition&gt; », ex. heralisz_skins:karambit__doppler_ruby.
 */
public enum KnifeFinish {
   // finitions « ultimes » (gemmes Doppler) ; les ids de shader restent ceux de la collection précédente
   EMERALD("gamma_emerald", 58),
   RUBY("doppler_ruby", 55),
   SAPPHIRE("doppler_sapphire", 56),
   BLACK_PEARL("doppler_black_pearl", 57),
   // finitions CS:GO classiques
   FADE("fade", 59),
   MARBLE_FADE("marble_fade", 60),
   TIGER_TOOTH("tiger_tooth", 61),
   CRIMSON_WEB("crimson_web", 62),
   SLAUGHTER("slaughter", 63),
   CASE_HARDENED("case_hardened", 64),
   LORE("lore", 65),
   AUTOTRONIC("autotronic", 66),
   // skins par paliers (Classique, Raffiné, Prestige, Ultime)
   // <skins_couteaux> : écrit par outils/skins_couteaux.py (catalogue des skins), ne pas modifier à la main
   NIGHT_OPS("night_ops", 120, true),
   FOREST_DDPAT("forest_ddpat", 121, true),
   SATIN("satin", 122, true),
   DAMASCUS_STEEL("damascus_steel", 123, true),
   BLUE_STEEL("blue_steel", 124, true),
   OCEAN_FADE("ocean_fade", 125, true),
   URBAN_MASKED("urban_masked", 126, true),
   STONEWASH("stonewash", 127, true),
   COYOTE("coyote", 128, true),
   NIGHT_STRIPE("night_stripe", 129, true),
   COPPER_PATINA("copper_patina", 130, true),
   AMBER_FADE("amber_fade", 131, true),
   SAFARI_MESH("safari_mesh", 132, true),
   SCORCHED("scorched", 133, true),
   GUNMETAL("gunmetal", 134, true),
   CONTOUR("contour", 135, true),
   BLUE_CARBON("blue_carbon", 136, true),
   KINTSUGI("kintsugi", 137, true),
   OLIVE_DRAB("olive_drab", 138, true),
   TIGER_STRIPE("tiger_stripe", 139, true),
   SATIN_BRONZE("satin_bronze", 140, true),
   HEX_ARMOR("hex_armor", 141, true),
   SUNSET("sunset", 142, true),
   EMERALD_WEB("emerald_web", 143, true),
   LAVA_CORE("lava_core", 104, false),
   ARCTIC_DIGITAL("arctic_digital", 144, true),
   CHAMPAGNE("champagne", 145, true),
   BLACKOUT("blackout", 146, true),
   FREEHAND("freehand", 147, true),
   TWIN_TONE("twin_tone", 148, true),
   PRISM("prism", 149, true),
   FROSTBITE("frostbite", 105, false),
   DESERT_STORM("desert_storm", 150, true),
   IVORY("ivory", 151, true),
   ASH("ash", 152, true),
   ROSE_GOLD("rose_gold", 153, true),
   CRIMSON_CARBON("crimson_carbon", 154, true),
   SILVER_LORE("silver_lore", 155, true),
   GARNET("garnet", 178, true),
   JUNGLE("jungle", 156, true),
   COBALT("cobalt", 157, true),
   CHARCOAL("charcoal", 158, true),
   FORGED_DAMASCUS("forged_damascus", 159, true),
   EMBER_TEMPER("ember_temper", 160, true),
   DRAGON_GOLD("dragon_gold", 107, false),
   PHOENIX("phoenix", 109, false),
   RUST_COAT("rust_coat", 161, true),
   ARCTIC_WHITE("arctic_white", 162, true),
   URBAN_DIGITAL("urban_digital", 163, true),
   BLOOD_STRIPE("blood_stripe", 164, true),
   HAZARD("hazard", 165, true),
   TOXIC_FADE("toxic_fade", 166, true),
   AMETHYST("amethyst", 179, true),
   PEARL("pearl", 167, true),
   SAGE("sage", 168, true),
   SNOW_CAMO("snow_camo", 169, true),
   AZURE_TEMPER("azure_temper", 170, true),
   CELESTE("celeste", 171, true),
   JADE_MARBLE("jade_marble", 172, true),
   OPAL("opal", 180, true),
   NAVY("navy", 173, true),
   MARINE_CAMO("marine_camo", 174, true),
   TIDEWASH("tidewash", 175, true),
   BRIGHT_WATER("bright_water", 102, false),
   TIDE_STRIPE("tide_stripe", 176, true),
   GOLD_HARDENED("gold_hardened", 177, true),
   ABYSSAL_PEARL("abyssal_pearl", 111, false),
   BATTLE_WORN("battle_worn", 300, true),
   HAMMERED("hammered", 301, true),
   DESERT_SPRAY("desert_spray", 302, true),
   LEOPARD("leopard", 303, true),
   SHARD("shard", 304, true),
   SAKURA("sakura", 305, true),
   ARID_DIGITAL("arid_digital", 306, true),
   BLACK_SATIN("black_satin", 307, true),
   EMBER_WORN("ember_worn", 308, true),
   SPLATTER("splatter", 309, true),
   GOLD_HEX("gold_hex", 310, true),
   TITANIUM("titanium", 311, true),
   FOREST_CAMO("forest_camo", 312, true),
   HAMMERED_BRONZE("hammered_bronze", 313, true),
   POLAR_SPRAY("polar_spray", 314, true),
   MIDNIGHT_DAMASCUS("midnight_damascus", 315, true),
   NEON_STRIPE("neon_stripe", 316, true),
   CIRCUIT("circuit", 317, true),
   URBAN_WORN("urban_worn", 318, true),
   SANDSTONE("sandstone", 319, true),
   MIRROR("mirror", 320, true),
   EMBER_SHARD("ember_shard", 321, true),
   SNOW_LEOPARD("snow_leopard", 322, true),
   SILVER_KINTSUGI("silver_kintsugi", 323, true),
   AURORA("aurora", 324, true),
   HAMMERED_STEEL("hammered_steel", 325, true),
   MIDNIGHT_CAMO("midnight_camo", 326, true),
   BRASS("brass", 327, true),
   INK_SPLATTER("ink_splatter", 328, true),
   COPPER_HEX("copper_hex", 329, true),
   IMPERIAL_MARBLE("imperial_marble", 330, true),
   TANZANITE("tanzanite", 331, true),
   NIGHT_DIGITAL("night_digital", 332, true),
   BONE_WORN("bone_worn", 333, true),
   GILDED("gilded", 334, true),
   ROYAL_DAMASCUS("royal_damascus", 335, true),
   CRIMSON_SHARD("crimson_shard", 336, true),
   OBSIDIAN_SAKURA("obsidian_sakura", 337, true),
   MOLTEN_GOLD("molten_gold", 338, true),
   LAVA_SPRAY("lava_spray", 339, true),
   VOLCANIC("volcanic", 340, true),
   OLIVE_SATIN("olive_satin", 341, true),
   BENGAL("bengal", 342, true),
   JADE_HEX("jade_hex", 343, true),
   INFERNAL_FADE("infernal_fade", 344, true),
   CITRINE("citrine", 345, true),
   CHARCOAL_WORN("charcoal_worn", 346, true),
   GLACIER_DIGITAL("glacier_digital", 347, true),
   DARK_HAMMERED("dark_hammered", 348, true),
   TOXIC_SPLATTER("toxic_splatter", 349, true),
   VIOLET_SHARD("violet_shard", 350, true),
   NEON_CIRCUIT("neon_circuit", 351, true),
   ONYX("onyx", 352, true),
   IVORY_HAMMERED("ivory_hammered", 353, true),
   SEAFOAM_SPRAY("seafoam_spray", 354, true),
   STEEL_WORN("steel_worn", 355, true),
   CELESTIAL_SHARD("celestial_shard", 356, true),
   PEARL_DAMASCUS("pearl_damascus", 357, true),
   CHERRY_BLOSSOM("cherry_blossom", 358, true),
   ROSE_QUARTZ("rose_quartz", 359, true),
   CORAL_WORN("coral_worn", 360, true),
   DRIFTWOOD("driftwood", 361, true),
   DEEP_HAMMERED("deep_hammered", 362, true),
   REEF_SPLATTER("reef_splatter", 363, true),
   KRAKEN_HEX("kraken_hex", 364, true),
   TIDAL_MARBLE("tidal_marble", 365, true),
   AQUAMARINE("aquamarine", 366, true),
   ASH_WOOD("ash_wood", 367, true),
   WALNUT("walnut", 368, true),
   BLACKWOOD("blackwood", 369, true),
   ALUMINIUM("aluminium", 370, true),
   GRAPHITE("graphite", 371, true),
   STREET_CAMO("street_camo", 372, true),
   SLUGGER_STRIPES("slugger_stripes", 373, true),
   GRAFFITI("graffiti", 374, true),
   BRUISER_HEX("bruiser_hex", 375, true),
   TEMPERED_ALLOY("tempered_alloy", 376, true),
   MIDNIGHT_FADE("midnight_fade", 377, true),
   RED_CRACKLE("red_crackle", 378, true),
   MOLTEN_STEEL("molten_steel", 379, true),
   PLASMA("plasma", 380, true),
   FORGED_IRON("forged_iron", 381, true),
   BLACKSMITH("blacksmith", 382, true),
   GRANITE("granite", 383, true),
   ANTIQUE_BRONZE("antique_bronze", 384, true),
   HIGHLAND_CAMO("highland_camo", 385, true),
   SLATE("slate", 386, true),
   THUNDER_STRIPE("thunder_stripe", 387, true),
   VERDIGRIS("verdigris", 388, true),
   RUNE_HEX("rune_hex", 389, true),
   AURUM_TEMPER("aurum_temper", 390, true),
   STORM_MARBLE("storm_marble", 391, true),
   RUNIC_GOLD("runic_gold", 392, true),
   THUNDERSTRIKE("thunderstrike", 393, true),
   NEBULA("nebula", 103, false),
   RANGER_DIGITAL("ranger_digital", 394, true),
   BLUE_SATIN("blue_satin", 395, true),
   STEEL_HAMMERED("steel_hammered", 396, true),
   NAVY_WORN("navy_worn", 397, true),
   CONCRETE("concrete", 398, true),
   STEEL_BLUE("steel_blue", 399, true),
   COBALT_DAMASCUS("cobalt_damascus", 400, true),
   ICE_SHARD("ice_shard", 401, true),
   ULTRAVIOLET("ultraviolet", 101, false),
   BLUE_STRIPE("blue_stripe", 402, true),
   BLUE_CIRCUIT("blue_circuit", 403, true),
   GLACIER_MARBLE("glacier_marble", 404, true),
   SAPPHIRE_AURORA("sapphire_aurora", 405, true),
   MOLTEN_SILVER("molten_silver", 406, true),
   DESERT_DIGITAL("desert_digital", 407, true),
   COPPER_SATIN("copper_satin", 408, true),
   RED_WORN("red_worn", 409, true),
   SAND_HAMMERED("sand_hammered", 410, true),
   SUNBAKED("sunbaked", 411, true),
   BRICK("brick", 412, true),
   SUNBURST("sunburst", 413, true),
   RUST_DAMASCUS("rust_damascus", 414, true),
   RED_SPLATTER("red_splatter", 415, true),
   ORANGE_STRIPE("orange_stripe", 416, true),
   INFERNO_CIRCUIT("inferno_circuit", 417, true),
   DESERT_SAKURA("desert_sakura", 418, true),
   SOLAR_FLARE("solar_flare", 419, true),
   RUBY_STORM("ruby_storm", 420, true),
   // </skins_couteaux>
   ;

   /**
    * Programme de shader commun à tous les skins par paliers (camo_120) : il lit les couleurs du skin dans CamoId au
    * moment du dessin. Un programme par skin rallongerait le chargement du jeu (chaque programme est compilé au démarrage).
    */
   public static final int PROGRAMME_PALIERS = 120;

   public final String id;
   public final int shaderId;
   /** Skin par paliers : dessiné par le programme commun PROGRAMME_PALIERS. */
   public final boolean palier;

   KnifeFinish(String id, int shaderId) {
      this(id, shaderId, false);
   }

   KnifeFinish(String id, int shaderId, boolean palier) {
      this.id = id;
      this.shaderId = shaderId;
      this.palier = palier;
   }

   /** Programme de shader à charger : camo_&lt;programme&gt;. */
   public int programme() {
      return this.palier ? PROGRAMME_PALIERS : this.shaderId;
   }

   @Nullable
   public static KnifeFinish byId(String id) {
      for (KnifeFinish f : values()) {
         if (f.id.equals(id)) {
            return f;
         }
      }
      return null;
   }
}
