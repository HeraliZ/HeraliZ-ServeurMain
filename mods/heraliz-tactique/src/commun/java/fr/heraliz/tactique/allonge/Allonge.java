package fr.heraliz.tactique.allonge;

import com.tacz.guns.entity.shooter.ShooterDataHolder;
import fr.heralisz.tactical.config.ServerConfig;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * S'allonger : règles HeraliZ ajoutées au « ramper » de TaCZ (touche « Ramper », une arme TaCZ en main ; TaCZ couche le
 * joueur, qui peut tirer et se déplacer allongé).
 * <ul>
 *    <li>un appui pour s'allonger, un autre pour se relever : réglage « HoldToCrawl » de TaCZ forcé à false chez le joueur ;</li>
 *    <li>pas de tir pendant que le joueur se couche (passage debout -> allongé) : {@code duree_sans_tir_couche} ;</li>
 *    <li>après s'être relevé (touche, saut, autre objet en main, eau...), pas de nouvel allongé avant
 *    {@code delai_rallonger}, compté depuis le moment où il s'est relevé.</li>
 * </ul>
 * Les deux réglages sont dans la config serveur de heralisz_tactical ({@code heralisz_tactical-server.toml}, section
 * {@code [allonge]}), que Forge envoie aux joueurs à la connexion : le joueur et le serveur lisent les mêmes valeurs. Le
 * joueur applique la valeur réglée ; le serveur, seul juge, compte un peu moins ({@link #MARGE_RALLONGER_MS},
 * {@link #MARGE_TIR_MS}) pour ne jamais refuser un joueur honnête à cause du décalage du réseau.
 * <p>
 * Ici, ce que lisent les deux côtés (horloge, réglages, état noté sur les données du tireur de TaCZ). La décision du
 * serveur (refus de s'allonger, refus du tir) est branchée par la partie serveur
 * ({@code fr.heraliz.tactique.serveur.AllongeServeur}) ; la touche, le refus local et le message au joueur par la partie
 * client ({@code fr.heraliz.tactique.client.allonge.AllongeClient}).
 */
public final class Allonge {
   /** Réglage par défaut : délai avant de pouvoir se rallonger (secondes). */
   public static final double DEFAUT_DELAI_RALLONGER = 4.5D;
   /**
    * Réglage par défaut : pas de tir pendant qu'on se couche (secondes). Minecraft passe le corps à plat en 12 ticks
    * (swimAmount + 0,09 par tick, soit 0,6 s) ; un tick de plus pour le changement de pose, un autre de marge : 0,7 s.
    */
   public static final double DEFAUT_SANS_TIR = 0.7D;
   /** Le serveur compte 0,5 s de moins sur le délai pour se rallonger (le relevé et le rallongé arrivent par le réseau). */
   public static final long MARGE_RALLONGER_MS = 500L;
   /** Le serveur compte 0,2 s de moins sur la durée sans tir (l'allongé et le tir arrivent par le réseau). */
   public static final long MARGE_TIR_MS = 200L;

   private static final long ORIGINE_NS = System.nanoTime();

   /** Décision du serveur : ce joueur peut-il s'allonger maintenant ? Branchée par la partie serveur ; sans elle : oui. */
   @FunctionalInterface
   public interface Regle {
      boolean peutSAllonger(LivingEntity tireur, ShooterDataHolder donnees);
   }

   private static volatile Regle regle = (tireur, donnees) -> true;

   private Allonge() {
   }

   /**
    * Horloge en millisecondes, monotone (ne dépend ni des ticks ni de l'heure du PC). Elle commence à une heure : une heure
    * à 0 (« jamais ») est toujours plus ancienne que n'importe quel délai réglable.
    */
   public static long maintenant() {
      return (System.nanoTime() - ORIGINE_NS) / 1_000_000L + 3_600_000L;
   }

   /** Délai pour se rallonger appliqué chez le joueur (ms). */
   public static long delaiRallongerMs() {
      return millis(ServerConfig.DELAI_RALLONGER, DEFAUT_DELAI_RALLONGER);
   }

   /** Durée sans tir en se couchant appliquée chez le joueur (ms). */
   public static long sansTirMs() {
      return millis(ServerConfig.SANS_TIR_EN_SE_COUCHANT, DEFAUT_SANS_TIR);
   }

   /** Délai pour se rallonger appliqué par le serveur (ms) : un peu plus court, pour le décalage du réseau. */
   public static long delaiRallongerServeurMs() {
      return Math.max(0L, delaiRallongerMs() - MARGE_RALLONGER_MS);
   }

   /** Durée sans tir en se couchant appliquée par le serveur (ms) : un peu plus courte, pour le décalage du réseau. */
   public static long sansTirServeurMs() {
      return Math.max(0L, sansTirMs() - MARGE_TIR_MS);
   }

   private static long millis(ForgeConfigSpec.DoubleValue valeur, double defaut) {
      double secondes = defaut;
      try {
         if (valeur != null) {
            secondes = valeur.get();
         }
      } catch (IllegalStateException pasEncoreRecue) {
         // chez le joueur, la config serveur n'existe qu'une fois connecté : valeur par défaut en attendant
      }
      return Math.round(secondes * 1000.0D);
   }

   /** Branche la décision du serveur (appelé par la partie serveur, pendant le chargement du mod). */
   public static void brancherRegle(Regle nouvelle) {
      regle = nouvelle;
   }

   /** Vrai si le serveur laisse ce tireur s'allonger maintenant (appelé par le mixin de LivingEntityCrawl). */
   public static boolean peutSAllonger(LivingEntity tireur, ShooterDataHolder donnees) {
      return regle.peutSAllonger(tireur, donnees);
   }

   /** L'état noté sur ces données de TaCZ ; null si le mixin n'a pas pu s'appliquer (TaCZ différent). */
   @Nullable
   public static EtatAllonge etat(@Nullable ShooterDataHolder donnees) {
      return donnees instanceof EtatAllonge etat ? etat : null;
   }

   /**
    * Note un passage debout <-> allongé (heure du coucher, heure du relevé). Appelé côté serveur après chaque endroit où
    * TaCZ change l'état : la touche (crawl) et chaque tick (tickCrawling, qui relève le joueur tout seul : autre objet en
    * main, saut, eau, véhicule...).
    */
   public static void noter(ShooterDataHolder donnees) {
      EtatAllonge etat = etat(donnees);
      if (etat == null) {
         return;
      }
      boolean allonge = donnees.isCrawling;
      if (allonge == etat.heraliz$getEtaitAllonge()) {
         return;
      }
      etat.heraliz$setEtaitAllonge(allonge);
      if (allonge) {
         etat.heraliz$setCoucheA(maintenant());
      } else {
         etat.heraliz$setReleveA(maintenant());
      }
   }
}
