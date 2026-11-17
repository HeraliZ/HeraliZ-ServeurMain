package fr.heralisz.bunker.keycard;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Portes de bunker (17/11, demande du propriétaire) : la porte posée devant un bunker (l'écluse à air ronde), désignée par
 * le staff ({@code /salle bunker ajouter [nom]}), ne s'ouvre qu'au lecteur de carte posé à côté ; ouverte, elle le reste
 * {@code ouvertureMinutes}, annoncée à tout le serveur, puis se ferme : qui est resté dedans ressort avec une carte
 * (lecteur intérieur), sinon il y reste.
 * Façade commune branchée par la partie serveur (src/serveur, BunkerServeur) ; sans elle (jar -client), la porte ne reste
 * ouverte que le temps du signal du lecteur, comme une porte à carte.
 */
public final class BunkerPont {
   /** Règles des portes de bunker, tenues par le serveur. */
   public interface Regles {
      /** Durée d'ouverture après un passage de carte (millisecondes, temps réel). */
      long ouvertureMs();

      /** Combien de temps avant la fermeture les joueurs proches sont prévenus (millisecondes ; 0 : jamais). */
      long avertirMs();

      /** La porte (contrôleur en porte), fermée, vient d'être ouverte par un lecteur : annonce (nom "" : sans nom). */
      void ouverte(ServerLevel level, BlockPos porte, String nom);

      /** La porte se ferme dans ce nombre de secondes : les joueurs proches sont prévenus. */
      void bientotFermee(ServerLevel level, BlockPos porte, int secondes);
   }

   public static volatile Regles REGLES;

   public static long ouvertureMs() {
      Regles r = REGLES;
      return r == null ? 0L : Math.max(0L, r.ouvertureMs());
   }

   public static long avertirMs() {
      Regles r = REGLES;
      return r == null ? 0L : Math.max(0L, r.avertirMs());
   }

   public static void ouverte(Level level, BlockPos porte, String nom) {
      Regles r = REGLES;
      if (r != null && level instanceof ServerLevel sl) r.ouverte(sl, porte, nom);
   }

   public static void bientotFermee(Level level, BlockPos porte, int secondes) {
      Regles r = REGLES;
      if (r != null && level instanceof ServerLevel sl) r.bientotFermee(sl, porte, secondes);
   }

   private BunkerPont() {}
}
