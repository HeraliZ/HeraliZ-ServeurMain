package fr.heraliz.tactique.armes;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Dégâts des armes TaCZ contre les joueurs (config {@code heraliz_tactique/armes.json}, équilibrage du 14/10), connus
 * des deux côtés : le serveur les applique (DegatsArmes), le joueur les reçoit à la connexion ({@link PaquetDegatsArmes})
 * pour afficher les vrais chiffres (infobulle de l'arme, Atelier d'arme). Vide : chiffres de TaCZ.
 */
public final class DegatsJoueurs {
   /** Dégâts d'une balle (d'un plomb) à courte distance et multiplicateur de la tête. */
   public record Reglage(float degats, float tete) {
   }

   private static volatile Map<ResourceLocation, Reglage> reglages = Map.of();

   private DegatsJoueurs() {
   }

   public static void remplacer(Map<ResourceLocation, Reglage> r) {
      reglages = Map.copyOf(r);
   }

   public static Map<ResourceLocation, Reglage> tous() {
      return reglages;
   }

   public static Reglage de(ResourceLocation arme) {
      return arme == null ? null : reglages.get(arme);
   }

   /**
    * Dégâts de TaCZ ramenés aux dégâts contre un joueur : même proportion que sur le serveur ({@code origine} = dégâts
    * d'une balle de l'arme chargée par TaCZ). Arme sans réglage : inchangés.
    */
   public static double ajuster(ResourceLocation arme, double degatsTacz, float origine) {
      Reglage r = de(arme);
      if (r == null) return degatsTacz;
      if (origine > 0.01F) return degatsTacz * r.degats() / origine;
      return r.degats();
   }
}
