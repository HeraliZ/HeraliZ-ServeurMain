package fr.heralisz.camo.serveur;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Cadence des actions des camos et des stickers (21/11, audit, choix du propriétaire) : une action au plus toutes les
 * {@link #ECART_MS} ms par joueur (poser ou retirer un camo, poser, déplacer ou gratter un sticker, le StatTrack). Un joueur
 * normal clique, puis attend que l'écran se refasse : il n'arrive jamais si vite. Un jeu modifié qui les envoie en rafale
 * (chaque action réécrit l'arme et joue un son aux joueurs proches) voit les suivantes ignorées, sans message. Fil du
 * serveur seulement ; oublié à la déconnexion et à l'arrêt (PartieServeur).
 */
final class Cadence {
   /** Écart minimal entre deux actions d'un même joueur. */
   static final long ECART_MS = 150L;

   private static final Map<UUID, Long> DERNIERE = new HashMap<>();

   private Cadence() {
   }

   /** Vrai si l'action est permise maintenant (elle est alors notée), faux si elle arrive trop tôt après la précédente. */
   static boolean permis(ServerPlayer joueur) {
      long maintenant = System.nanoTime() / 1_000_000L;
      Long avant = DERNIERE.get(joueur.getUUID());
      if (avant != null && maintenant - avant < ECART_MS) {
         return false;
      }
      DERNIERE.put(joueur.getUUID(), maintenant);
      return true;
   }

   static void oublier(UUID joueur) {
      DERNIERE.remove(joueur);
   }

   static void vider() {
      DERNIERE.clear();
   }
}
