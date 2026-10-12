package fr.heraliz.tactique.son;

import fr.heraliz.tactique.pont.Crochet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Tir silencieux entendu de loin (choix du propriétaire, 12/10). Jusqu'à sa portée nette (10 à 20 blocs, gunpacks :
 * outils/silencieux.py), un tir silencieux s'entend net et à sa vraie place (son de TaCZ). Au-delà, jusqu'à la portée
 * d'un tir normal (256 blocs, tacz-common.toml), les autres joueurs entendent le même son, plus grave et plus faible,
 * venu d'un endroit flou autour du tireur : on sait qu'on a tiré, à peu près de quel côté, pas d'où exactement.
 * <p>Le serveur décide de tout (qui l'entend, à quel volume, d'où) et n'envoie qu'une position déjà floue : le jeu d'un
 * joueur ne reçoit jamais la vraie position d'un tireur lointain. Le point flou d'un tireur reste le même pour un joueur
 * pendant 30 s, sinon une rafale ferait la moyenne et trahirait la vraie place.
 * <p>Branchements : le mixin TaczSonSilencieuxMixin appelle {@link #TIR_SERVEUR} à chaque tir silencieux envoyé par TaCZ
 * (partie serveur : SonLointainServeur) ; le paquet {@link PaquetTirLointain} est joué chez le joueur (partie client :
 * SonLointainClient). Réglages : section [silencieux] de heralisz_tactical-server.toml.
 */
public final class SonLointain {
   public static final boolean DEFAUT_ACTIF = true;
   /** Volume du son lointain juste après la portée nette (avant le carré du jeu, comme TaCZ : 0,35 -> 0,12). */
   public static final double DEFAUT_VOLUME = 0.35;
   /** Flou de l'endroit d'où vient le son lointain, en part de la distance (0,5 : à 100 blocs, jusqu'à 50 blocs à côté). */
   public static final double DEFAUT_FLOU = 0.5;
   /** Hauteur (pitch) du son lointain par rapport au son du tir : plus grave, plus sourd. */
   public static final float GRAVE = 0.6F;

   /** Un tir silencieux que TaCZ vient d'envoyer aux joueurs proches. {@code distance} : celle que TaCZ a lue. */
   public record Tir(LivingEntity tireur, int distance, ResourceLocation arme, ResourceLocation affichage, float hauteur) {
   }

   /** Tir silencieux sur le serveur (appelé par le mixin) : branché par la partie serveur. */
   public static final Crochet<Tir> TIR_SERVEUR = new Crochet<>();

   private SonLointain() {
   }
}
