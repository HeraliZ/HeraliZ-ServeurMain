package fr.heraliz.tactique.couteaux;

import javax.annotation.Nullable;

/**
 * Coup puissant (clic droit) de chaque modèle de couteau TaCZ, skins compris : ÉCRIT PAR
 * {@code mods/heraliz-tactique/outils/couteaux_coups.py}, ne pas modifier à la main (relancer le script).
 * <ul>
 *    <li>ct_knife : geste « poignard » écrit ici, 0,85 s, impact à 0,24 s</li>
 *    <li>t_knife : geste « poignard » écrit ici, 0,85 s, impact à 0,24 s</li>
 *    <li>m9 : coup lourd repris de « Miséricorde » de LR (mercy), 1,79 s, impact à 0,31 s, cadence de LR 22 ticks (écart +4)</li>
 *    <li>butterfly_knife : coup lourd repris de « Lame de Desmoulins » de LR (desmoulins_edge), 2,75 s, impact à 0,28 s, cadence de LR 24 ticks (écart +6)</li>
 *    <li>karambit : coup lourd repris de « Credo » de LR (creed), 2,18 s, impact à 0,23 s, cadence de LR 16 ticks (écart -2)</li>
 * </ul>
 * Délai : ticks entre la demande et le coup donné par le serveur, à l'impact de l'animation du joueur. Écart de cadence :
 * ajouté au réglage {@code cadence_lourd} (celui de la Dague tactique), comme l'arme de LR dont le coup est repris.
 */
public final class CoupsLourds {
   /** Modèles d'animation des couteaux (fichiers {@code assets/heraliz_tactique/couteaux/<modèle>.animation.json}). */
   private static final String[] MODELES = {"ct_knife", "t_knife", "m9", "butterfly_knife", "karambit"};
   private static final int[] DELAIS = {5, 5, 6, 6, 5};
   private static final int[] ECARTS_CADENCE = {0, 0, 4, 6, -2};
   /** Délai d'un couteau inconnu (coup puissant du gunpack), en ticks. */
   public static final int DELAI_DEFAUT = 5;
   /** Plus grand délai : une cadence doit le dépasser (un coup à la fois). */
   public static final int DELAI_MAX = 6;

   private CoupsLourds() {
   }

   /** Modèle d'animation d'un couteau d'après le chemin de son identifiant d'arme TaCZ (karambit__..., m9__...). */
   @Nullable
   public static String modele(@Nullable String chemin) {
      if (chemin == null) return null;
      if (chemin.contains("karambit")) return "karambit";
      if (chemin.contains("butterfly")) return "butterfly_knife";
      if (chemin.startsWith("m9")) return "m9";
      if (chemin.contains("ct_knife")) return "ct_knife";
      if (chemin.contains("t_knife")) return "t_knife";
      return null;
   }

   /** Ticks entre la demande et le coup puissant de ce modèle. */
   public static int delai(@Nullable String modele) {
      int i = indice(modele);
      return i < 0 ? DELAI_DEFAUT : DELAIS[i];
   }

   /** Ticks ajoutés à la cadence du coup puissant pour ce modèle (négatif : plus rapide). */
   public static int ecartCadence(@Nullable String modele) {
      int i = indice(modele);
      return i < 0 ? 0 : ECARTS_CADENCE[i];
   }

   private static int indice(@Nullable String modele) {
      if (modele != null) {
         for (int i = 0; i < MODELES.length; i++) {
            if (MODELES[i].equals(modele)) return i;
         }
      }
      return -1;
   }
}
