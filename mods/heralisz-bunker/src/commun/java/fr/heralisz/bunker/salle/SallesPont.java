package fr.heralisz.bunker.salle;

import fr.heralisz.bunker.keycard.AccessLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Salles à carte d'accès : façade commune branchée par la partie serveur (src/serveur, fr.heralisz.bunker.serveur.salle).
 * Le code commun (porte, lecteur, baguette) l'appelle ; sans partie serveur (jar -client), elle ne fait rien et la porte
 * se comporte comme avant (ouverte le temps du signal du lecteur).
 */
public final class SallesPont {
   /** Règles des salles, tenues par le serveur. */
   public interface Regles {
      /** La porte (contrôleur en porte) doit-elle rester ouverte pour une salle (10 s pour entrer, salle occupée) ? */
      boolean tenueOuverte(Level level, BlockPos porte);

      /** Un lecteur de cette couleur vient d'accorder l'accès : la fenêtre d'entrée de ses salles commence. */
      void accesAccorde(Level level, BlockPos lecteur, AccessLevel niveau);

      /** Baguette du staff : coin (1 ou 2) de la sélection posé sur ce bloc. */
      void coin(ServerPlayer joueur, BlockPos pos, int coin);
   }

   public static volatile Regles REGLES;

   public static boolean tenueOuverte(Level level, BlockPos porte) {
      Regles r = REGLES;
      return r != null && level != null && !level.isClientSide && r.tenueOuverte(level, porte);
   }

   public static void accesAccorde(Level level, BlockPos lecteur, AccessLevel niveau) {
      Regles r = REGLES;
      if (r != null && !level.isClientSide) r.accesAccorde(level, lecteur, niveau);
   }

   public static void coin(ServerPlayer joueur, BlockPos pos, int coin) {
      Regles r = REGLES;
      if (r != null) r.coin(joueur, pos, coin);
   }

   private SallesPont() {}
}
