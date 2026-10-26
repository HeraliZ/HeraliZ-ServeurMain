package fr.heraliz.noyau.api;

import fr.heraliz.noyau.pont.Crochet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Tag de combat du serveur : un joueur est tagué {@link #dureeMs()} (15 s par défaut, réglage {@code combat.tag_secondes})
 * après avoir infligé ou reçu des dégâts d'un AUTRE joueur (projectiles, explosifs, armes TaCZ, mêlée compris : auteur
 * réel de la source). Jamais par un mob, une chute, etc. ; jamais pendant un match.
 * <p><b>Contrat pour les autres mods</b> (sans dépendre de ce code) : tant que le joueur est tagué, son
 * {@code persistentData} porte la clé long {@link #CLE_JUSQUA} = {@value #CLE_JUSQUA} (epoch ms de fin du tag) ; clé
 * absente ou 0 = libre. Lecture : {@code p.getPersistentData().getLong("heralisz:combat_jusqua") >
 * System.currentTimeMillis()}. En Java (dépendance ou réflexion) : {@link #estTague(UUID)}, {@link #restantMs(UUID)}.
 * <p>API lue par réflexion par d'autres mods : ne jamais renommer cette classe ni ses méthodes publiques.
 * Fil serveur uniquement ; lectures sans allocation.
 */
public final class CombatTag {
   private CombatTag() {}

   /** Durée par défaut du tag (ms). */
   public static final long DUREE_DEFAUT_MS = 15_000L;
   /** Clé long du {@code persistentData} du joueur : fin du tag (epoch ms) ; absente ou 0 = libre. */
   public static final String CLE_JUSQUA = "heralisz:combat_jusqua";

   /** Après un marquage (indicateur « EN COMBAT ») : branché par la partie serveur du noyau. */
   public static final Crochet<ServerPlayer> APRES_MARQUAGE = new Crochet<>();
   /** Après un effacement (indicateur masqué) : branché par la partie serveur du noyau. */
   public static final Crochet<ServerPlayer> APRES_EFFACEMENT = new Crochet<>();

   private static long dureeMs = DUREE_DEFAUT_MS;
   /** Fin du tag (epoch ms) par joueur. */
   private static final Map<UUID, Long> FIN = new HashMap<>();

   public static boolean estTague(ServerPlayer joueur) {
      return joueur != null && restantMs(joueur.getUUID()) > 0;
   }

   public static boolean estTague(UUID joueur) {
      return restantMs(joueur) > 0;
   }

   /** Temps restant du tag en ms (0 si non tagué). */
   public static long restantMs(ServerPlayer joueur) {
      return joueur == null ? 0 : restantMs(joueur.getUUID());
   }

   public static long restantMs(UUID joueur) {
      Long fin = FIN.get(joueur);
      if (fin == null) return 0;
      return Math.max(0, fin - System.currentTimeMillis());
   }

   /** Fin du tag (epoch ms), 0 si non tagué. */
   public static long finMs(UUID joueur) {
      Long fin = FIN.get(joueur);
      return fin == null || fin <= System.currentTimeMillis() ? 0 : fin;
   }

   /** Tague (ou re-tague pour une durée pleine) un joueur. Sans effet pour un joueur en match. */
   public static void marquer(ServerPlayer joueur) {
      if (joueur == null || Matchs.enMatch(joueur)) return;
      long fin = System.currentTimeMillis() + dureeMs;
      FIN.put(joueur.getUUID(), fin);
      joueur.getPersistentData().putLong(CLE_JUSQUA, fin);
      APRES_MARQUAGE.accept(joueur);
   }

   /** Tague l'attaquant et la victime (dégâts entre deux joueurs distincts). */
   public static void marquer(ServerPlayer attaquant, ServerPlayer victime) {
      if (attaquant == null || victime == null || attaquant == victime) return;
      marquer(attaquant);
      marquer(victime);
   }

   /** Retire le tag (mort, déconnexion traitée, entrée en match) : clé retirée et indicateur masqué si connecté. */
   public static void effacer(UUID joueur) {
      boolean etait = FIN.remove(joueur) != null;
      ServerPlayer p = enLigne(joueur);
      if (p != null) {
         p.getPersistentData().remove(CLE_JUSQUA);
         if (etait) APRES_EFFACEMENT.accept(p);
      }
   }

   public static long dureeMs() {
      return dureeMs;
   }

   /** Réservé aux réglages : durée bornée à 1..300 s. */
   public static void dureeMs(long ms) {
      dureeMs = Math.max(1000L, Math.min(300_000L, ms));
   }

   /** Réservé au noyau : purge les tags expirés et retire leur clé (appel périodique, pas à chaque tick). */
   public static void purger() {
      long now = System.currentTimeMillis();
      for (Iterator<Map.Entry<UUID, Long>> it = FIN.entrySet().iterator(); it.hasNext(); ) {
         Map.Entry<UUID, Long> e = it.next();
         if (e.getValue() > now) continue;
         it.remove();
         ServerPlayer p = enLigne(e.getKey());
         if (p != null) p.getPersistentData().remove(CLE_JUSQUA);
      }
   }

   /** Vrai si aucun joueur n'est tagué. */
   public static boolean aucun() {
      return FIN.isEmpty();
   }

   /** Nombre de joueurs tagués (moniteur). */
   public static int nombre() {
      return FIN.size();
   }

   /** Réservé au noyau : oublie tout (arrêt du serveur). */
   public static void vider() {
      FIN.clear();
   }

   private static ServerPlayer enLigne(UUID u) {
      MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
      return s == null ? null : s.getPlayerList().getPlayer(u);
   }
}
