package fr.heralisz.plague.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Tirs en cloche sous un plafond : les salles de boss sont souvent basses, et un obus qui monte trop haut tape la voûte et
 * n'arrive jamais. On simule la trajectoire (même intégration que ThrowableProjectile : position += vitesse, vitesse × 0,99,
 * puis gravité) et on raccourcit la durée de vol, donc la hauteur de la cloche, jusqu'à ce qu'elle passe sous le plafond.
 */
public final class Lob {
   private static final double DRAG = 0.99;
   /** Marge sous le plafond (blocs) : l'obus et ses particules ne doivent pas le frôler. */
   private static final double MARGIN = 1.2;
   private static final int MIN_FLIGHT = 5;

   private Lob() {}

   /** Vitesse de départ pour tomber sur `aim` en `flight` ticks. */
   public static Vec3 velocity(Vec3 from, Vec3 aim, int flight, double g) {
      double f = (1 - Math.pow(DRAG, flight)) / (1 - DRAG);
      return new Vec3((aim.x - from.x) / f, (aim.y - from.y + 0.5 * g * flight * flight) / f, (aim.z - from.z) / f);
   }

   /** La plus longue durée de vol (au plus `flight`) dont la cloche ne touche aucun bloc avant d'arriver. */
   public static int fit(Level level, Vec3 from, Vec3 aim, int flight, double g) {
      for (int t = flight; t > MIN_FLIGHT; t -= t > 20 ? 2 : 1) {
         if (clear(level, from, aim, t, g)) return t;
      }
      return MIN_FLIGHT;
   }

   private static boolean clear(Level level, Vec3 from, Vec3 aim, int flight, double g) {
      Vec3 v = velocity(from, aim, flight, g);
      double x = from.x, y = from.y, z = from.z, vx = v.x, vy = v.y, vz = v.z;
      BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
      for (int t = 0; t < flight - 2; t++) {
         x += vx;
         y += vy;
         z += vz;
         vx *= DRAG;
         vy = vy * DRAG - g;
         vz *= DRAG;
         // seule la partie haute du vol compte : près de la cible, l'obus descend et doit toucher le sol
         if (y < aim.y + 1.5) continue;
         p.set(Math.floor(x), Math.floor(y + MARGIN), Math.floor(z));
         if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return false;
         p.set(Math.floor(x), Math.floor(y), Math.floor(z));
         if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return false;
      }
      return true;
   }
}
