package fr.heraliz.tactique.allonge;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/**
 * Lien facultatif avec Réanimation (mod « reanimation »), par réflexion : rien ne casse s'il est absent ou différent.
 * <p>
 * Un joueur à terre est déjà à plat (pose « nage » forcée par Réanimation, qui ignore toute autre pose forcée tant qu'il
 * est à terre) : il ne peut ni s'allonger ni plonger, et un joueur allongé qui tombe à terre n'est plus allongé (il se
 * relève normalement quand il est réanimé, au lieu de rester couché). Un joueur qui fouille ou réanime quelqu'un est
 * accroupi par Réanimation : de même, ni allongé ni plongeon pendant ce temps (sinon les deux mods imposeraient chacun
 * leur pose). Lu des deux côtés : {@code fr.heraliz.reanimation.core.Downed.isDowned(Entity)} et {@code isActing(Entity)}
 * lisent l'état du serveur, ou chez le joueur les drapeaux envoyés par le serveur.
 */
public final class CompatReanimation {
   private static final Logger LOG = LogUtils.getLogger();
   private static volatile boolean cherche;
   private static volatile MethodHandle aTerre;
   private static volatile MethodHandle occupe;

   private CompatReanimation() {
   }

   /** Vrai si ce joueur est à terre (Réanimation) ; faux si le mod est absent. */
   public static boolean aTerre(Entity entite) {
      if (!cherche) {
         chercher();
      }
      return appeler(aTerre, entite);
   }

   /** Vrai si ce joueur est à terre, ou fouille ou réanime quelqu'un (Réanimation) : ni allongé ni plongeon. */
   public static boolean empeche(Entity entite) {
      if (!cherche) {
         chercher();
      }
      return appeler(aTerre, entite) || appeler(occupe, entite);
   }

   private static boolean appeler(MethodHandle m, Entity entite) {
      if (m == null) {
         return false;
      }
      try {
         return (boolean) m.invokeExact(entite);
      } catch (Throwable e) {
         return false;
      }
   }

   private static synchronized void chercher() {
      if (cherche) {
         return;
      }
      try {
         ModList mods = ModList.get();
         if (mods != null && mods.isLoaded("reanimation")) {
            Class<?> api = Class.forName("fr.heraliz.reanimation.core.Downed");
            MethodType type = MethodType.methodType(boolean.class, Entity.class);
            aTerre = MethodHandles.publicLookup().findStatic(api, "isDowned", type);
            occupe = MethodHandles.publicLookup().findStatic(api, "isActing", type);
         }
      } catch (ReflectiveOperationException | LinkageError e) {
         LOG.warn("[heraliz_tactique] Réanimation présente mais sans son API Downed : s'allonger ne voit pas les joueurs à terre ({})",
            e.toString());
      } finally {
         cherche = true;
      }
   }
}
