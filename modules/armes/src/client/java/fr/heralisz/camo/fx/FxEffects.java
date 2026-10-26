package fr.heralisz.camo.fx;

import java.util.Map;
import java.util.TreeMap;

/** Effets 3D par numéro de shader du camo / skin (le shader de l'effet porte le même numéro : fx_&lt;id&gt;). */
public final class FxEffects {
   private static final Map<Integer, FxEffect> EFFECTS = new TreeMap<>();

   static {
      EFFECTS.put(208, new FxSeraph());
      EFFECTS.put(209, new FxInferno());
      EFFECTS.put(230, new FxMissing());
      EFFECTS.put(231, new FxMoney());
      EFFECTS.put(232, new FxOverheat());
      EFFECTS.put(233, new FxTrophy());
      // (72 Dark Matter Origine, piquots visqueux : retiré en 0.9.12, voir _retires/dark_matter_origin_0.9.11)
      EFFECTS.put(70, new FxLegacy.Storm());
   }

   private FxEffects() {
   }

   public static FxEffect get(int id) {
      return EFFECTS.get(id);
   }

   public static boolean has(int id) {
      return EFFECTS.containsKey(id);
   }

   public static Iterable<Integer> ids() {
      return EFFECTS.keySet();
   }
}
