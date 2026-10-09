package fr.heralisz.camo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collection de camos à vie du joueur, vue du client : reçue du serveur (CollectionSyncPacket) à la connexion et à chaque
 * changement. Elle ne sert qu'à l'affichage (onglet Skin) : le serveur revérifie tout (fr.heralisz.camo.serveur.CamosAVie).
 */
public final class CollectionVue {
   private static volatile Set<Camo> possedes = Collections.emptySet();
   /** Fin du délai après une mort, en heure du client (ms). */
   private static volatile Map<Camo, Long> finRecharge = Collections.emptyMap();
   private static volatile int version;

   /** Nouvelle collection reçue : ids possédés et délais restants (ms). */
   public static void recevoir(List<String> ids, Map<String, Long> restantMs) {
      Set<Camo> s = EnumSet.noneOf(Camo.class);
      for (String id : ids) {
         Camo c = Camo.byId(id);
         if (c != null) s.add(c);
      }
      long now = System.currentTimeMillis();
      Map<Camo, Long> r = new EnumMap<>(Camo.class);
      restantMs.forEach((id, ms) -> {
         Camo c = Camo.byId(id);
         if (c != null && ms > 0) r.put(c, now + ms);
      });
      possedes = s;
      finRecharge = r;
      version++;
   }

   public static boolean possede(Camo camo) {
      return possedes.contains(camo);
   }

   /** Camos possédés, dans l'ordre du catalogue. */
   public static List<Camo> possedes() {
      return new ArrayList<>(possedes);
   }

   /** Temps restant avant de pouvoir remettre ce camo (ms), 0 s'il est disponible. */
   public static long rechargeRestanteMs(Camo camo) {
      Long fin = finRecharge.get(camo);
      return fin == null ? 0L : Math.max(0L, fin - System.currentTimeMillis());
   }

   /** Nombre de camos en attente en ce moment (l'écran se redessine quand un délai se termine). */
   public static int enRecharge() {
      long now = System.currentTimeMillis();
      int n = 0;
      for (long fin : finRecharge.values()) {
         if (fin > now) n++;
      }
      return n;
   }

   /** Change à chaque réception (l'écran se reconstruit). */
   public static int version() {
      return version;
   }

   /** Déconnexion : rien ne reste d'un serveur à l'autre. */
   public static void vider() {
      possedes = Collections.emptySet();
      finRecharge = Collections.emptyMap();
      version++;
   }

   private CollectionVue() {}
}
