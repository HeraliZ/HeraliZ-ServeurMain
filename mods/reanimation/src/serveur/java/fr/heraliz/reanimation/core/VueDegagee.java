package fr.heraliz.reanimation.core;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ce que le serveur exige avant de laisser agir sur un joueur à terre (achever, fouiller, réanimer) : le voir sans mur
 * entre les deux, et ne jamais être placé à genoux de l'autre côté d'un mur. Sans ça, un jeu modifié agissait à travers
 * un mur (la distance seule était vérifiée) et la mise à genoux le faisait passer de l'autre côté.
 */
public final class VueDegagee {
   private VueDegagee() {
   }

   /** Un point au moins du corps (milieu, haut, coins un peu rentrés) se voit depuis les yeux de l'acteur. */
   public static boolean voit(LivingEntity acteur, Entity cible) {
      if (acteur.level() != cible.level()) {
         return false;
      }
      Vec3 yeux = acteur.getEyePosition();
      AABB b = cible.getBoundingBox().deflate(0.1);
      double haut = Math.min(b.maxY, b.minY + 0.2);
      Vec3[] points = {
         b.getCenter(),
         new Vec3(b.getCenter().x, b.maxY, b.getCenter().z),
         new Vec3(b.minX, haut, b.minZ),
         new Vec3(b.maxX, haut, b.minZ),
         new Vec3(b.minX, haut, b.maxZ),
         new Vec3(b.maxX, haut, b.maxZ)
      };
      for (Vec3 p : points) {
         if (libre(acteur, yeux, p)) {
            return true;
         }
      }
      return false;
   }

   /** On peut aller de là où est l'acteur jusqu'à cette place sans traverser de bloc (aux genoux et à la poitrine). */
   public static boolean chemin(LivingEntity acteur, Vec3 place) {
      Vec3 pieds = acteur.position();
      for (double h : new double[]{0.3, 1.0}) {
         if (!libre(acteur, pieds.add(0.0, h, 0.0), new Vec3(place.x, pieds.y + h, place.z))) {
            return false;
         }
      }
      return true;
   }

   private static boolean libre(LivingEntity acteur, Vec3 de, Vec3 vers) {
      return acteur.level().clip(new ClipContext(de, vers, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, acteur)).getType() == HitResult.Type.MISS;
   }
}
