package fr.heraliz.tactique.couteaux;

import fr.heralisz.tactical.config.ServerConfig;
import fr.heraliz.tactique.melee.PorteeMelee;
import fr.heraliz.tactique.onglets.Couteaux;
import javax.annotation.Nullable;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Couteaux TaCZ (armes des gunpacks dont la fiche technique est arkaniaz:knife_data, {@link Couteaux#estCouteau} :
 * « Knife » CT et T, karambits, papillons et M9 de heralisz_skins...) : ils frappent comme les armes de mêlée de HeraliZ
 * Tactique (moteur repris de LR Tactical), réglés comme la Dague tactique ({@code heralisz_melee:tac_dagger}).
 * <p>
 * <b>Avant</b> : pour TaCZ, un couteau est une arme à feu. Sa touche Tirer (clic gauche) tirait une « balle » de 0,01 s de
 * vie à 0,1 m/s (0,005 bloc parcouru, rien touché) et vidait son chargeur d'une seule munition (arkaniaz:melee_weapon) :
 * le clic gauche ne faisait rien. Le seul coup était la touche « Mêlée » de TaCZ (V), que personne ne connaît : 13
 * dégâts toutes les 1,02 s, à environ 1,4 bloc. Le clic droit visait (zoom) avec un couteau.
 * <p>
 * <b>Maintenant</b>, comme une arme de mêlée de LR :
 * <ul>
 *    <li>touche Tirer de TaCZ (clic gauche) : coup rapide ; touche tenue = un coup dès que la cadence le permet. La touche
 *    « Mêlée » de TaCZ (V) donne le même coup rapide (le coup de TaCZ n'est plus donné aux couteaux, ni chez le joueur
 *    ni par le serveur) ;</li>
 *    <li>touche Viser de TaCZ (clic droit) : coup puissant, un par appui ; on ne vise plus avec un couteau. Clic droit sur
 *    un joueur à terre : rien (Réanimation : réanimer ou fouiller, comme avant) ;</li>
 *    <li>dégâts : (attaque du joueur, 1 à mains nues, + {@code degats}) pour le coup rapide, ×{@code multiplicateur_lourd}
 *    pour le coup puissant, comme les coups de LR (coup critique en tombant, recul, temps d'invincibilité de la cible) ;
 *    cibles : tout être vivant dans un cône devant les yeux (portée : {@code portee_max} de la section {@code [melee]},
 *    commune à toutes les armes de corps à corps, {@link PorteeMelee} ; {@code angle_rapide} / {@code angle_lourd}, vue
 *    dégagée), plusieurs à la fois comme avec LR ;</li>
 *    <li>deux coups bien différents à voir et à entendre, comme les couteaux de LR (15/10) : coup rapide = tranche
 *    latérale vive (de gauche à droite, puis de droite à gauche), coup puissant = estoc préparé (bras armé 0,22 s) puis
 *    poussé, qui ne touche qu'après la préparation ({@link #DELAI_COUP_LOURD}) ; les autres joueurs voient les gestes
 *    des couteaux de LR en 3e personne et entendent le coup et l'impact ;</li>
 *    <li>pas de coup pendant que le couteau est sorti (temps de sortie de TaCZ), ni à terre ou en train de fouiller ou de
 *    réanimer (Réanimation).</li>
 * </ul>
 * Le joueur ne fait que demander un coup (paquet {@link PaquetCoupCouteau}) : cadence, couteau en main, cibles et dégâts
 * sont décidés par le serveur ({@code fr.heraliz.tactique.serveur.CouteauxServeur}), qui touche
 * {@link #delai} ticks après la demande (moment où la lame passe dans l'animation). Le joueur joue l'animation et le
 * bruit tout de suite ({@code fr.heraliz.tactique.client.couteaux.CouteauxClient}).
 * <p>
 * Réglages : config serveur de heralisz_tactical ({@code heralisz_tactical-server.toml}, section {@code [couteaux]}),
 * envoyée par Forge aux joueurs à la connexion (le joueur et le serveur lisent la même cadence).
 */
public final class CoupsCouteau {
   /** Réglage par défaut : couteaux frappés par HeraliZ (false : comme avant, clic gauche sans effet, touche V de TaCZ). */
   public static final boolean DEFAUT_ACTIF = true;
   /** Réglage par défaut : dégâts ajoutés à l'attaque du joueur ({@code generic.attack_damage} de la Dague tactique). */
   public static final double DEFAUT_DEGATS = 5.5D;
   /** Réglage par défaut : multiplicateur du coup puissant ({@code factor} de l'attaque droite de la Dague tactique). */
   public static final double DEFAUT_MULTIPLICATEUR_LOURD = 1.5D;
   /** Réglage par défaut : ticks entre deux coups rapides ({@code cooldown} de l'attaque gauche de la Dague tactique). */
   public static final int DEFAUT_CADENCE_RAPIDE = 9;
   /** Réglage par défaut : ticks après un coup puissant ({@code cooldown} de l'attaque droite de la Dague tactique). */
   public static final int DEFAUT_CADENCE_LOURD = 18;
   /** Réglage par défaut : ouverture du cône du coup rapide, en degrés (Dague tactique : 65). */
   public static final double DEFAUT_ANGLE_RAPIDE = 65.0D;
   /** Réglage par défaut : ouverture du cône du coup puissant, en degrés (Dague tactique : 75). */
   public static final double DEFAUT_ANGLE_LOURD = 75.0D;
   /** Recul donné aux cibles (Dague tactique : 0,05 et 0,08). */
   public static final float RECUL_RAPIDE = 0.05F;
   public static final float RECUL_LOURD = 0.08F;
   /**
    * Ticks entre la demande et le coup rapide ({@code delay} de la Dague tactique : la lame de la tranche passe au milieu
    * de l'écran vers 0,11 s, animations de {@code outils/couteaux_coups.py}).
    */
   public static final int DELAI_COUP = 2;
   /**
    * Ticks entre la demande et le coup puissant (15/10) : l'estoc est préparé (bras armé pendant 0,22 s) puis poussé, la
    * lame arrive au fond vers 0,27 s ; le serveur ne touche qu'après la préparation.
    */
   public static final int DELAI_COUP_LOURD = 5;
   /** Cadences minimales permises par la config : plus que le délai du coup (un coup à la fois). */
   public static final int CADENCE_MIN = DELAI_COUP + 2;
   public static final int CADENCE_MIN_LOURD = DELAI_COUP_LOURD + 2;

   private CoupsCouteau() {
   }

   public static boolean actif() {
      return valeur(ServerConfig.COUTEAUX, DEFAUT_ACTIF);
   }

   public static double degats() {
      return valeur(ServerConfig.COUTEAUX_DEGATS, DEFAUT_DEGATS);
   }

   public static double multiplicateurLourd() {
      return valeur(ServerConfig.COUTEAUX_MULTIPLICATEUR_LOURD, DEFAUT_MULTIPLICATEUR_LOURD);
   }

   /** Ticks avant le coup suivant, après un coup rapide ou puissant. */
   public static int cadence(boolean lourd) {
      return lourd ? valeur(ServerConfig.COUTEAUX_CADENCE_LOURD, DEFAUT_CADENCE_LOURD)
         : valeur(ServerConfig.COUTEAUX_CADENCE_RAPIDE, DEFAUT_CADENCE_RAPIDE);
   }

   /**
    * Portée en blocs, des yeux au point le plus proche de la cible : celle de toutes les armes de corps à corps
    * ({@code [melee] portee_max}, 3 : la portée d'un coup de Minecraft, que l'anticheat Spartan tolère).
    */
   public static double portee() {
      return PorteeMelee.max();
   }

   /** Ouverture du cône, en degrés (la moitié de chaque côté du regard). */
   public static double angle(boolean lourd) {
      return lourd ? valeur(ServerConfig.COUTEAUX_ANGLE_LOURD, DEFAUT_ANGLE_LOURD)
         : valeur(ServerConfig.COUTEAUX_ANGLE_RAPIDE, DEFAUT_ANGLE_RAPIDE);
   }

   /** Ticks entre la demande et le coup, rapide ou puissant. */
   public static int delai(boolean lourd) {
      return lourd ? DELAI_COUP_LOURD : DELAI_COUP;
   }

   public static float recul(boolean lourd) {
      return lourd ? RECUL_LOURD : RECUL_RAPIDE;
   }

   /** Vrai si ce joueur tient un couteau TaCZ et que les couteaux sont frappés par HeraliZ. */
   public static boolean couteauEnMain(@Nullable Player joueur) {
      return joueur != null && actif() && Couteaux.estCouteau(joueur.getMainHandItem());
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
}
