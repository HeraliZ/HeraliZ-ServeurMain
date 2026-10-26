package fr.heraliz.noyau.api;

/**
 * État du joueur côté client, rempli par les paquets du serveur (aucune décision ici : affichage seulement). Lu par les
 * interfaces des autres mods, par réflexion ou dépendance : ne jamais renommer cette classe ni ses méthodes.
 */
public final class EtatClient {
   private EtatClient() {}

   private static volatile long combatFinMs;
   private static volatile int combatTotalS;
   private static volatile boolean enMatch;

   /** Réservé au réseau du noyau : secondes de tag restantes (0 = fini). */
   public static void combat(int secondes) {
      long now = System.currentTimeMillis();
      if (secondes <= 0) {
         combatFinMs = 0;
         combatTotalS = 0;
         return;
      }
      combatTotalS = combatRestantMs() > 0 ? Math.max(combatTotalS, secondes) : secondes;
      combatFinMs = now + secondes * 1000L;
   }

   /** Réservé au mod des matchs (côté client). */
   public static void enMatch(boolean en) {
      enMatch = en;
      if (en) combat(0);
   }

   public static long combatRestantMs() {
      long fin = combatFinMs;
      return fin == 0 ? 0 : Math.max(0, fin - System.currentTimeMillis());
   }

   /** Part restante du tag (1 = tout juste tagué, 0 = fini), pour une jauge. */
   public static float combatRatio() {
      int total = combatTotalS;
      return total <= 0 ? 0 : Math.min(1f, combatRestantMs() / (total * 1000f));
   }

   public static boolean enMatch() {
      return enMatch;
   }
}
