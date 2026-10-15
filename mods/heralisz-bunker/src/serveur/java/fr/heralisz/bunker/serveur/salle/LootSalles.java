package fr.heralisz.bunker.serveur.salle;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Pont vers HeraliZ Loot (dépendance facultative, appel par réflexion : CLAUDE.md §5) : API
 * {@code fr.heraliz.loot.SallesLoot} de la partie serveur de Loot. Sans Loot (ou sans sa partie serveur), les salles
 * gardent la porte tenue ouverte, mais leur loot n'est pas géré (résultat -1 / null).
 */
final class LootSalles {
   private static final Logger LOG = LogUtils.getLogger();
   private static final String API = "fr.heraliz.loot.SallesLoot";
   private static boolean charge;
   @Nullable
   private static Method declarer, retirer, recharger, etat;

   private LootSalles() {}

   private static synchronized void charger() {
      if (charge) return;
      charge = true;
      try {
         Class<?> c = Class.forName(API);
         declarer = c.getMethod("declarer", ServerLevel.class, String.class, String.class, BlockPos.class, BlockPos.class);
         retirer = c.getMethod("retirer", ServerLevel.class, String.class);
         recharger = c.getMethod("recharger", ServerLevel.class, String.class);
         etat = c.getMethod("etat", ServerLevel.class, String.class);
         LOG.info("[salles] HeraliZ Loot trouvé : loot des salles à carte géré");
      } catch (ClassNotFoundException ex) {
         LOG.info("[salles] HeraliZ Loot absent : le loot des salles à carte n'est pas géré");
      } catch (ReflectiveOperationException | LinkageError ex) {
         LOG.warn("[salles] API des salles de HeraliZ Loot illisible ({}) : loot des salles non géré", API, ex);
         declarer = retirer = recharger = etat = null;
      }
   }

   static boolean present() {
      charger();
      return declarer != null;
   }

   @Nullable
   private static Object appeler(@Nullable Method m, Object... args) {
      if (!present() || m == null) return null;
      try {
         return m.invoke(null, args);
      } catch (InvocationTargetException ex) {
         LOG.error("[salles] HeraliZ Loot : {} a échoué", m.getName(), ex.getCause());
      } catch (ReflectiveOperationException | RuntimeException ex) {
         LOG.error("[salles] HeraliZ Loot : {} impossible", m.getName(), ex);
      }
      return null;
   }

   /** Nom de la zone de loot de la salle, vu par le staff dans /heralizloot zones. */
   private static String libelle(SallesMonde.Salle s) {
      String c = switch (s.niveau) {
         case GREEN -> "verte";
         case BLUE -> "bleue";
         case RED -> "rouge";
      };
      return "Salle à carte « " + s.nom + " » (" + c + ")";
   }

   /** Déclare la salle à Loot (ses conteneurs quittent la rotation habituelle). -> conteneurs, -1 sans Loot. */
   static int declarer(ServerLevel l, SallesMonde.Salle s) {
      return appeler(declarer, l, s.nom, libelle(s), s.min, s.max) instanceof Integer n ? n : -1;
   }

   static boolean retirer(ServerLevel l, String nom) {
      return appeler(retirer, l, nom) instanceof Boolean b && b;
   }

   /** Remplit à neuf les conteneurs de la salle. -> conteneurs remplis, -1 sans Loot (ou salle inconnue de Loot). */
   static int recharger(ServerLevel l, String nom) {
      return appeler(recharger, l, nom) instanceof Integer n ? n : -1;
   }

   /** {conteneurs, remplis, remplis déjà ouverts}, null sans Loot ou salle inconnue de Loot. */
   @Nullable
   static int[] etat(ServerLevel l, String nom) {
      return appeler(etat, l, nom) instanceof int[] e ? e : null;
   }

   /** Premier passage après le démarrage : Loot connaît-il la salle (et sa boîte) ? Sinon elle lui est déclarée et
    *  remplie (Loot ajouté après coup, données de Loot remises à zéro). */
   static void verifier(ServerLevel l, SallesMonde.Salle s) {
      if (!present()) return;
      boolean connue = etat(l, s.nom) != null;
      declarer(l, s);                                              // même boîte : rien ne change
      if (!connue) recharger(l, s.nom);
   }
}
