package fr.heralisz.camo.fx;

import java.util.Map;
import java.util.TreeMap;

/**
 * Effets 3D des skins X posés sur un sac porté (même numéro que le shader du skin : fx_&lt;id&gt;, mêmes « sortes » d'éléments
 * que sur l'arme). Surchauffe (232) et Trophée (233) n'en ont pas : leurs effets suivent les tirs et les kills d'une arme.
 */
public final class BagEffects {
   /** Construit l'effet d'un skin sur un sac (géométrie dans c.mesh). */
   @FunctionalInterface
   public interface BagEffect {
      void build(FxContext c, BagFrame b);
   }

   private static final Map<Integer, BagEffect> EFFECTS = new TreeMap<>();

   static {
      EFFECTS.put(208, new BagSeraph());
      EFFECTS.put(209, new BagInferno());
      EFFECTS.put(230, new BagMissing());
      EFFECTS.put(231, new BagMoney());
   }

   private BagEffects() {
   }

   public static BagEffect get(int id) {
      return EFFECTS.get(id);
   }
}
