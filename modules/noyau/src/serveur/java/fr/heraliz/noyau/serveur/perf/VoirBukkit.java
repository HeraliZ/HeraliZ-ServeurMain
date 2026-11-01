package fr.heraliz.noyau.serveur.perf;

import com.mojang.logging.LogUtils;
import fr.heraliz.noyau.NoyauConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Point 1 a) de l'étape 2 du chantier « Minecraft allégé » (01/11) : le test de Bukkit que Mohist ajoute au suivi des
 * entités ({@code ChunkMap.TrackedEntity.updatePlayer} : « ce joueur peut-il voir cette entité ? », pour le vanish du staff
 * et tout plugin qui cache une entité). Mohist ne le pose que si l'entité est déjà à portée et que
 * {@code broadcastToPlayer} a dit oui (ordre vérifié dans son patch de ChunkMap) ; c'est le nombre de ces paires à portée,
 * rejugées à chaque paquet de mouvement (balles de TaCZ autour d'un combat), qui le rendait cher : banc à 150 joueurs,
 * {@code CraftEntity.isVisibleByDefault} 18,5 ms par tick et {@code getUniqueId} 2,1 ms.
 * <p>Ce que fait Bukkit ({@code CraftPlayer.canSee(Entity)}) : {@code equals(entité) || entité.isVisibleByDefault() ^
 * invertedVisibilityEntities.containsKey(entité.getUniqueId())}. Ici, même réponse sans passer par l'objet Bukkit de
 * l'entité quand le joueur n'a aucune visibilité inversée (aucun plugin ne lui cache ni ne lui montre d'entité, le cas de
 * tous les joueurs sauf ceux à qui un staff est caché) et que l'entité n'a pas le numéro du joueur : la réponse est alors
 * le champ {@code visibleByDefault} de l'entité, lu directement. Sinon : le vrai {@code canSee} de Bukkit. Une fois sur 4 096,
 * les deux réponses sont comparées ; une seule différence coupe ce raccourci jusqu'au redémarrage (journal).
 * <p>Sans Mohist (Forge seul), Minecraft ne pose pas cette question : {@link #voit} répond toujours oui. Mohist présent mais
 * méthodes introuvables : {@link #casse()} (le suivi rapide n'est alors jamais utilisé). Fil serveur seulement.
 */
public final class VoirBukkit {
   private static final Logger LOG = LogUtils.getLogger();

   /** Mohist (ou un autre serveur Bukkit) : Entity.getBukkitEntity existe. */
   static final boolean PRESENT;
   /** Mohist présent mais une méthode manque : la décision de Minecraft + Bukkit ne peut pas être refaite ici. */
   private static final boolean CASSE;
   /** Entity.getBukkitEntity() : (Entity) Object. */
   private static final MethodHandle BUKKIT;
   /** CraftPlayer.canSee(org.bukkit.entity.Entity) : (Object, Object) boolean. */
   private static final MethodHandle VOIT;
   /** CraftPlayer.invertedVisibilityEntities : (Object) Map, ou null (raccourci impossible). */
   private static final MethodHandle INVERSES;
   /** Entity.visibleByDefault (champ ajouté par Mohist) : (Entity) boolean, ou null. */
   private static final MethodHandle VISIBLE;

   private static boolean raccourciCasse;
   private static int compteur;

   static {
      boolean present = false;
      boolean casse = false;
      MethodHandle bukkit = null, voit = null, inverses = null, visible = null;
      Method getBukkit = null;
      try {
         getBukkit = Entity.class.getMethod("getBukkitEntity");
         present = true;
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         present = false;
      }
      if (present) {
         try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            bukkit = l.unreflect(getBukkit).asType(MethodType.methodType(Object.class, Entity.class));
            Class<?> craftPlayer = ServerPlayer.class.getMethod("getBukkitEntity").getReturnType();
            Class<?> entiteBukkit = Class.forName("org.bukkit.entity.Entity", false, craftPlayer.getClassLoader());
            voit = l.unreflect(craftPlayer.getMethod("canSee", entiteBukkit))
                  .asType(MethodType.methodType(boolean.class, Object.class, Object.class));
            try {
               Field f = craftPlayer.getDeclaredField("invertedVisibilityEntities");
               f.setAccessible(true);
               Field v = Entity.class.getField("visibleByDefault");
               if (Map.class.isAssignableFrom(f.getType()) && v.getType() == boolean.class) {
                  inverses = l.unreflectGetter(f).asType(MethodType.methodType(Map.class, Object.class));
                  visible = l.unreflectGetter(v).asType(MethodType.methodType(boolean.class, Entity.class));
               }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
               inverses = null;
               visible = null;
               LOG.info("[noyau] suivi des entités : raccourci de Bukkit (canSee) indisponible sur ce serveur ({}), le vrai test est gardé", e.toString());
            }
         } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            casse = true;
            LOG.warn("[noyau] suivi des entités : test de Bukkit (canSee) introuvable ({}), suivi rapide jamais utilisé", e.toString());
         }
      }
      PRESENT = present;
      CASSE = casse;
      BUKKIT = bukkit;
      VOIT = voit;
      INVERSES = inverses;
      VISIBLE = visible;
   }

   private VoirBukkit() {
   }

   /** Vrai si la décision de Minecraft (et de Mohist) peut être refaite à l'identique ici. */
   static boolean ok() {
      return !CASSE;
   }

   static boolean casse() {
      return CASSE;
   }

   /**
    * Le test de Bukkit posé par Mohist pour la paire (joueur, entité), quand l'entité est à portée et que
    * {@code broadcastToPlayer} a dit oui : 1 visible, 0 cachée, -1 si le test n'a pas pu être fait (l'appelant refait
    * alors la paire par Minecraft).
    */
   static int voit(ServerPlayer joueur, Entity entite) {
      if (!PRESENT) {
         return 1;
      }
      try {
         Object bj = (Object) BUKKIT.invokeExact((Entity) joueur);
         if (NoyauConfig.suiviVoirRapide && !raccourciCasse && INVERSES != null && entite.getId() != joueur.getId()) {
            Map<?, ?> inverses = (Map<?, ?>) INVERSES.invokeExact(bj);
            if (inverses.isEmpty()) {
               boolean r = (boolean) VISIBLE.invokeExact(entite);
               Allege.voirRaccourcis++;
               if ((++compteur & 4095) == 0) {
                  boolean vrai = (boolean) VOIT.invokeExact(bj, (Object) BUKKIT.invokeExact(entite));
                  if (vrai != r) {
                     raccourciCasse = true;
                     LOG.warn("[noyau] suivi des entités : raccourci de Bukkit différent du vrai test pour {} (coupé jusqu'au redémarrage)",
                           entite.getType());
                     return vrai ? 1 : 0;
                  }
               }
               return r ? 1 : 0;
            }
         }
         return (boolean) VOIT.invokeExact(bj, (Object) BUKKIT.invokeExact(entite)) ? 1 : 0;
      } catch (Throwable t) {
         if (t instanceof Error err && !(t instanceof LinkageError)) {
            throw err;
         }
         LOG.warn("[noyau] suivi des entités : test de Bukkit impossible ({}), la paire est jugée par Minecraft", t.toString());
         return -1;
      }
   }
}
