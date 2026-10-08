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
   AUTOTRONIC("autotronic", 66);

   public final String id;
   public final int shaderId;

   KnifeFinish(String id, int shaderId) {
      this.id = id;
      this.shaderId = shaderId;
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
