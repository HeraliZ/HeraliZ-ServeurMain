package fr.heraliz.tactique.allonge;

import com.tacz.guns.entity.shooter.ShooterDataHolder;
import fr.heralisz.tactical.config.ServerConfig;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * S'allonger : règles HeraliZ ajoutées au « ramper » de TaCZ (touche « Ramper » ; TaCZ couche le joueur, qui peut tirer
 * et se déplacer allongé).
 * <ul>
 *    <li>avec ou sans arme en main (main vide ou n'importe quel objet) : sans arme TaCZ, HeraliZ fait lui-même ce que
 *    TaCZ fait avec une arme (même touche, même pose à plat, même lenteur : celle du « ramper » de Minecraft, x0,3), sur
 *    le même état ({@code isCrawling} de TaCZ). Passer d'une arme à un autre objet en restant allongé ne relève pas le
 *    joueur : TaCZ reprend la main dès qu'une arme est tenue, HeraliZ dès qu'elle ne l'est plus. Une arme TaCZ qui ne
 *    permet pas de ramper relève toujours le joueur (règle de TaCZ) ;</li>
 *    <li>un appui pour s'allonger, un autre pour se relever : réglage « HoldToCrawl » de TaCZ forcé à false chez le joueur ;</li>
 *    <li>relevé tout seul en sautant, hors du sol, dans l'eau, en véhicule, en spectateur, et quand il tombe à terre ou
 *    se met à fouiller ou réanimer quelqu'un (Réanimation, {@link CompatReanimation}) ;</li>
 *    <li>pas de tir pendant que le joueur se couche (passage debout -> allongé) : {@code duree_sans_tir_couche} ;</li>
 *    <li>après s'être relevé (touche, saut, autre objet en main, eau...), pas de nouvel allongé avant
 *    {@code delai_rallonger}, compté depuis le moment où il s'est relevé ;</li>
 *    <li>plongeon façon Call of Duty : en avançant, la touche pendant un saut (jusqu'à {@link #FENETRE_SAUT_MS} après) ou
 *    avec la touche de saut enfoncée lance le joueur en avant, corps à plat, et il retombe allongé. L'élan est décidé et
 *    donné par le serveur seul (vitesse de Minecraft envoyée au joueur : l'anticheat Spartan la voit) ; le joueur ne fait
 *    que le demander. Le plongeon compte comme « se coucher » : pas de tir pendant {@code duree_sans_tir_couche} depuis son
 *    début, il respecte le délai pour se rallonger, et deux plongeons sont séparés de {@code delai_plongeon}.</li>
 * </ul>
 * Les réglages sont dans la config serveur de heralisz_tactical ({@code heralisz_tactical-server.toml}, section
 * {@code [allonge]}), que Forge envoie aux joueurs à la connexion : le joueur et le serveur lisent les mêmes valeurs. Le
 * joueur applique la valeur réglée ; le serveur, seul juge, compte un peu moins ({@link #MARGE_RALLONGER_MS},
 * {@link #MARGE_TIR_MS}, {@link #MARGE_PLONGEON_MS}) pour ne jamais refuser un joueur honnête à cause du décalage du réseau.
 * <p>
 * Ici, ce que lisent les deux côtés (horloge, réglages, état noté sur les données du tireur de TaCZ). La décision du
 * serveur (refus de s'allonger, refus du tir, allongé sans arme, plongeon) est branchée par la partie serveur
 * ({@code fr.heraliz.tactique.serveur.AllongeServeur}, {@code PlongeonServeur}) ; la touche, le refus local et les messages
 * au joueur par la partie client ({@code fr.heraliz.tactique.client.allonge.AllongeClient}).
 */
public final class Allonge {
   /** Réglage par défaut : délai avant de pouvoir se rallonger (secondes). */
   public static final double DEFAUT_DELAI_RALLONGER = 4.5D;
   /**
    * Réglage par défaut : pas de tir pendant qu'on se couche (secondes). Minecraft passe le corps à plat en 12 ticks
    * (swimAmount + 0,09 par tick, soit 0,6 s) ; un tick de plus pour le changement de pose, un autre de marge : 0,7 s.
    */
   public static final double DEFAUT_SANS_TIR = 0.7D;
   /** Réglage par défaut : plongeon permis. */
   public static final boolean DEFAUT_PLONGEON = true;
   /**
    * Réglage par défaut : vitesse vers l'avant donnée par le plongeon, en blocs par tick (un sprint va à 0,28) : environ
    * 2,5 blocs depuis le sol, 3,5 à 4,5 blocs depuis un saut (pas de frottement du sol au départ).
    */
   public static final double DEFAUT_PLONGEON_VITESSE = 0.7D;
   /**
    * Réglage par défaut : petit élan vers le haut d'un plongeon depuis le sol, en blocs par tick (un saut vaut 0,42) :
    * 0,15 fait monter le corps d'environ 0,2 bloc. En l'air (juste après un saut), le plongeon ne donne aucun élan vers le
    * haut : le saut l'a déjà donné. Même si le saut et le plongeon s'additionnent (réseau), on reste sous 1,5 bloc
    * (1,25 pour le saut + 0,22) : pas de clôture ni de muret franchis grâce au plongeon.
    */
   public static final double DEFAUT_PLONGEON_HAUTEUR = 0.15D;
   /** Réglage par défaut : délai entre deux plongeons (secondes, compté depuis le début du précédent). */
   public static final double DEFAUT_DELAI_PLONGEON = 6.0D;
   /** Le serveur compte 0,5 s de moins sur le délai pour se rallonger (le relevé et le rallongé arrivent par le réseau). */
   public static final long MARGE_RALLONGER_MS = 500L;
   /** Le serveur compte 0,2 s de moins sur la durée sans tir (l'allongé et le tir arrivent par le réseau). */
   public static final long MARGE_TIR_MS = 200L;
   /** Le serveur compte 0,5 s de moins sur le délai entre deux plongeons. */
   public static final long MARGE_PLONGEON_MS = 500L;
   /** Chez le joueur : la touche compte comme un plongeon jusqu'à 0,45 s après le saut (un saut dure environ 0,6 s). */
   public static final long FENETRE_SAUT_MS = 450L;
   /** Le serveur accepte 0,3 s de plus après le saut (le saut et la demande arrivent par le réseau). */
   public static final long MARGE_SAUT_MS = 300L;
   /**
    * Vitesse vers l'avant minimale pour plonger, chez le joueur (blocs par tick, dans le sens du regard) : marche 0,22,
    * sprint 0,28, accroupi ou allongé 0,07.
    */
   public static final double AVANT_MIN = 0.1D;
   /** La même vue par le serveur, plus large : positions reçues par le réseau (par paquets), moyennées. */
   public static final double AVANT_MIN_SERVEUR = 0.06D;
   /** Plongeon accepté mais le joueur ne quitte pas le sol (élan perdu) : abandonné au bout de 1,5 s (+ ping au serveur). */
   public static final long DECOLLAGE_MAX_MS = 1500L;
   /** Plongeon en l'air depuis plus de 2,5 s (chute) : abandonné, le joueur retombera debout. */
   public static final long VOL_MAX_MS = 2500L;

   private static final long ORIGINE_NS = System.nanoTime();

   /** Décision du serveur : ce joueur peut-il s'allonger maintenant ? Branchée par la partie serveur ; sans elle : oui. */
   @FunctionalInterface
   public interface Regle {
      boolean peutSAllonger(LivingEntity tireur, ShooterDataHolder donnees);
   }

   /**
    * Tick du serveur d'un joueur, juste avant le « ramper » de TaCZ (LivingEntityCrawl.tickCrawling) : allongé sans arme,
    * plongeon. Branché par la partie serveur ; sans elle, rien (TaCZ décide seul, comme avant).
    */
   @FunctionalInterface
   public interface TickServeur {
      /** Vrai si le tick est entièrement traité : le « ramper » de TaCZ ne passe pas (il relèverait le joueur sans arme). */
      boolean avantTick(Player joueur, ShooterDataHolder donnees);
   }

   private static volatile Regle regle = (tireur, donnees) -> true;
   private static volatile TickServeur tickServeur = (joueur, donnees) -> false;

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

   /** Plongeon permis (réglage {@code plongeon}). */
   public static boolean plongeonActif() {
      return valeur(ServerConfig.PLONGEON, DEFAUT_PLONGEON);
   }

   /** Vitesse vers l'avant du plongeon (blocs par tick). */
   public static double plongeonVitesse() {
      return valeur(ServerConfig.PLONGEON_VITESSE, DEFAUT_PLONGEON_VITESSE);
   }

   /** Élan vers le haut d'un plongeon depuis le sol (blocs par tick). */
   public static double plongeonHauteur() {
      return valeur(ServerConfig.PLONGEON_HAUTEUR, DEFAUT_PLONGEON_HAUTEUR);
   }

   /** Délai entre deux plongeons appliqué chez le joueur (ms). */
   public static long delaiPlongeonMs() {
      return millis(ServerConfig.DELAI_PLONGEON, DEFAUT_DELAI_PLONGEON);
   }

   /** Délai entre deux plongeons appliqué par le serveur (ms) : un peu plus court, pour le décalage du réseau. */
   public static long delaiPlongeonServeurMs() {
      return Math.max(0L, delaiPlongeonMs() - MARGE_PLONGEON_MS);
   }

   /** Fenêtre après un saut pendant laquelle le serveur accepte un plongeon (ms). */
   public static long fenetreSautServeurMs() {
      return FENETRE_SAUT_MS + MARGE_SAUT_MS;
   }

   private static long millis(ForgeConfigSpec.DoubleValue valeur, double defaut) {
      return Math.round(valeur(valeur, defaut) * 1000.0D);
   }

   private static <T> T valeur(@Nullable ForgeConfigSpec.ConfigValue<T> valeur, T defaut) {
      try {
         if (valeur != null) {
            T v = valeur.get();
            if (v != null) {
               return v;
            }
         }
      } catch (IllegalStateException pasEncoreRecue) {
         // chez le joueur, la config serveur n'existe qu'une fois connecté : valeur par défaut en attendant
      }
      return defaut;
   }

   /** Branche la décision du serveur (appelé par la partie serveur, pendant le chargement du mod). */
   public static void brancherRegle(Regle nouvelle) {
      regle = nouvelle;
   }

   /** Branche le tick du serveur (appelé par la partie serveur, pendant le chargement du mod). */
   public static void brancherTick(TickServeur nouveau) {
      tickServeur = nouveau;
   }

   /** Vrai si le serveur laisse ce tireur s'allonger maintenant (appelé par le mixin de LivingEntityCrawl). */
   public static boolean peutSAllonger(LivingEntity tireur, ShooterDataHolder donnees) {
      return regle.peutSAllonger(tireur, donnees);
   }

   /** Tick du serveur d'un joueur avant celui de TaCZ (appelé par le mixin de LivingEntityCrawl) : vrai = traité. */
   public static boolean avantTickServeur(Player joueur, ShooterDataHolder donnees) {
      return tickServeur.avantTick(joueur, donnees);
   }

   /** L'état noté sur ces données de TaCZ ; null si le mixin n'a pas pu s'appliquer (TaCZ différent). */
   @Nullable
   public static EtatAllonge etat(@Nullable ShooterDataHolder donnees) {
      return donnees instanceof EtatAllonge etat ? etat : null;
   }

   /**
    * Note un passage debout <-> allongé (heure du coucher, heure du relevé). Appelé côté serveur après chaque endroit où
    * l'état change : la touche (crawl), chaque tick (tickCrawling de TaCZ, ou le tick HeraliZ sans arme, qui relèvent le
    * joueur tout seuls : autre objet en main, saut, eau, véhicule...). Un joueur qui s'allonge en retombant d'un plongeon
    * est noté couché depuis le début du plongeon (la durée sans tir compte depuis là).
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
         Plongeon plongeon = etat.heraliz$getPlongeon();
         etat.heraliz$setCoucheA(plongeon != null && plongeon.enCours() ? plongeon.debutA : maintenant());
      } else {
         etat.heraliz$setReleveA(maintenant());
      }
   }
}
