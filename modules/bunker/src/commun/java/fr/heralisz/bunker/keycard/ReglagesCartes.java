package fr.heralisz.bunker.keycard;

/**
 * Réglages des cartes d'accès lus par le code commun (audit du 02/11, point 11). Le fichier (KeycardConfig,
 * config/heralisz_bunker-common.toml) est un réglage du serveur seulement, déclaré par la partie serveur : jamais écrit
 * sur le PC des joueurs.
 * <ul>
 *    <li>passages avant que la carte casse : recopiés ici par le serveur à chaque chargement du fichier, et envoyés aux
 *    joueurs (KeycardNetwork.ReglagesPacket : à la connexion et quand le fichier change) pour l'infobulle et la barre
 *    d'usure de la carte (avant, le jeu du joueur lisait sa propre copie du fichier) ;</li>
 *    <li>durée du signal du lecteur : serveur seulement (tick du lecteur), jamais envoyée.</li>
 * </ul>
 * Valeurs d'origine tant que rien n'est reçu.
 */
public final class ReglagesCartes {
   private ReglagesCartes() {}

   /** Borne des passages (comme le réglage). */
   public static final int MAX_PASSAGES = 10000;

   private static volatile int vert = 10, bleu = 8, rouge = 6;
   private static volatile int impulsionTicks = 100;

   /** Passages avant que la carte de ce niveau casse (0 = illimitée). */
   public static int passages(AccessLevel niveau) {
      return switch (niveau) {
         case GREEN -> vert;
         case BLUE -> bleu;
         case RED -> rouge;
      };
   }

   public static void definirPassages(int v, int b, int r) {
      vert = borne(v);
      bleu = borne(b);
      rouge = borne(r);
   }

   /** Durée du signal redstone après un accès accordé (ticks) : serveur seulement. */
   public static int impulsionTicks() {
      return impulsionTicks;
   }

   public static void definirImpulsion(int ticks) {
      impulsionTicks = Math.max(10, Math.min(12000, ticks));
   }

   private static int borne(int n) {
      return Math.max(0, Math.min(MAX_PASSAGES, n));
   }
}
