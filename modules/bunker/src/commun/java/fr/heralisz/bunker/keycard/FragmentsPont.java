package fr.heralisz.bunker.keycard;

import net.minecraft.server.level.ServerPlayer;

/**
 * Assemblage des fragments de carte d'accès : façade commune branchée par la partie serveur
 * (fr.heralisz.bunker.serveur.fragments.AssemblageFragments). Le fragment l'appelle au clic droit ; sans partie
 * serveur (jar -client), elle ne fait rien : seul le serveur décide.
 */
public final class FragmentsPont {
   public interface Assemblage {
      /** Le joueur a fait clic droit avec un fragment de cette carte : assembler si les 4 numéros sont là. */
      void assembler(ServerPlayer joueur, AccessLevel carte);
   }

   public static volatile Assemblage ASSEMBLAGE;

   public static void assembler(ServerPlayer joueur, AccessLevel carte) {
      Assemblage a = ASSEMBLAGE;
      if (a != null) a.assembler(joueur, carte);
   }

   private FragmentsPont() {}
}
