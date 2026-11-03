package fr.heraliz.tactique.armes;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Armes à plombs (retour du propriétaire du 02/11 : « certains pompes tirent une seule balle, il faut que TOUS les pompes
 * dispersent leurs plombs ») : réglages par arme de {@code config/heraliz_tactique/armes.json}, écrits par
 * {@code outils/equilibrage_armes.py}, lus par le serveur (DegatsArmes) et appliqués au tir par le mixin
 * TaczPlombsMixin, gunpacks intacts :
 * <ul>
 *   <li>« plombs » : nombre de plombs imposé (0 = celui du gunpack) : le DB-4 Ursus n'en tirait qu'un ;</li>
 *   <li>« dispersion_min » : écart minimal des plombs, en degrés (même dispersion que TaCZ, « inaccuracy »), même en
 *   visant et avec des accessoires (0 = celui du gunpack) : le Stakeout visait à 0,25, ses plombs partaient ensemble.</li>
 * </ul>
 * Rempli côté serveur seulement : chez le joueur la table reste vide et rien ne change (le tir est décidé par le
 * serveur).
 */
public final class PlombsArmes {
   public record Reglage(int plombs, float dispersionMin) {
   }

   private static volatile Map<ResourceLocation, Reglage> reglages = Map.of();

   private PlombsArmes() {
   }

   public static void remplacer(Map<ResourceLocation, Reglage> r) {
      reglages = Map.copyOf(r);
   }

   /** Aucune arme réglée : le mixin sort tout de suite (cas du joueur, et du serveur sans armes.json). */
   public static boolean vide() {
      return reglages.isEmpty();
   }

   public static Reglage de(ResourceLocation arme) {
      return arme == null ? null : reglages.get(arme);
   }
}
