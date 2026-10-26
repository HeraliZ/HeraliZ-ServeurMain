package fr.heralisz.camo.serveur;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Liens facultatifs avec nos autres mods, par réflexion (rien ne casse s'ils sont absents). */
final class Compat {
   private static final Logger LOG = LogUtils.getLogger();
   private static boolean cherche;
   private static MethodHandle aTerre;

   private Compat() {
   }

   /**
    * Joueur à terre (Réanimation, {@code fr.heraliz.reanimation.core.Downed.isDowned(Entity)}) : il ne touche pas à ses camos
    * à vie (sinon il retirerait le sien juste avant d'être achevé, pour échapper au délai).
    */
   static boolean aTerre(ServerPlayer joueur) {
      if (!cherche) {
         cherche = true;
         if (ModList.get().isLoaded("reanimation")) {
            try {
               Class<?> api = Class.forName("fr.heraliz.reanimation.core.Downed");
               aTerre = MethodHandles.publicLookup().findStatic(api, "isDowned", MethodType.methodType(boolean.class, Entity.class));
            } catch (ReflectiveOperationException | LinkageError e) {
               LOG.warn("[Camos] Réanimation présente mais sans son API Downed : camos à vie modifiables à terre ({})", e.toString());
            }
         }
      }
      if (aTerre == null) {
         return false;
      }
      try {
         return (boolean) aTerre.invokeExact((Entity) joueur);
      } catch (Throwable e) {
         return false;
      }
   }
}
