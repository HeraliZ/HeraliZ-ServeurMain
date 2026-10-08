package fr.heralisz.bunker.barricade;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Où et si une barricade peut être posée. Même calcul pour la pose (serveur, qui décide) et pour l'aperçu vert / rouge
 * affiché quand on la tient en main (client).
 * Refusée si un rondin ou un pied entre dans un bloc, un prop, une entité ou une autre barricade, ou si elle ne repose
 * pas sur le sol (2 rondins au plus peuvent dépasser dans le vide).
 */
public final class BarricadePlacement {
   /** Position au sol (centre de la barricade), orientation et validité. */
   public record Result(Vec3 pos, float yaw, boolean valid) {
   }

   private BarricadePlacement() {
   }

   public static Result compute(Level level, Player player, BlockHitResult hit) {
      float yaw = player.getYRot() + 180.0F;
      BlockPos pos = hit.getBlockPos();
      double y;
      if (level.getBlockState(pos).canBeReplaced()) {
         // herbe, fleur... : on pose au sol, sur le bloc en dessous
         y = pos.getY();
         if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
            return new Result(new Vec3(hit.getLocation().x, y, hit.getLocation().z), yaw, false);
         }
      } else if (hit.getDirection() == Direction.UP && level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)) {
         y = pos.getY() + 1.0;
      } else {
         return new Result(hit.getLocation(), yaw, false);
      }
      Vec3 at = new Vec3(hit.getLocation().x, y, hit.getLocation().z);
      return new Result(at, yaw, fits(level, at, yaw));
   }

   /** Axe de la barricade (le long des rondins) pour une orientation donnée ; même repère que le rendu. */
   public static Vec3 along(float yawDeg) {
      float yaw = yawDeg * Mth.DEG_TO_RAD;
      return new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
   }

   /** Vers l'arrière (côté des jambes de force). */
   public static Vec3 back(float yawDeg) {
      float yaw = yawDeg * Mth.DEG_TO_RAD;
      return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
   }

   static boolean fits(Level level, Vec3 at, float yaw) {
      Vec3 along = along(yaw), back = back(yaw);
      double r = BarricadeEntity.LOG_D / 2.0 / 16.0 - 0.02;
      int unsupported = 0;
      for (int i = 0; i < BarricadeEntity.LOGS; i++) {
         Vec3 c = at.add(along.scale(BarricadeEntity.logOffset(i)));
         AABB box = new AABB(c.x - r, at.y + 0.05, c.z - r, c.x + r, at.y + BarricadeEntity.LOG_H[i] / 16.0, c.z + r);
         if (blocked(level, box)) {
            return false;
         }
         BlockPos below = BlockPos.containing(c.x, at.y - 0.3, c.z);
         if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
            unsupported++;
         }
      }
      // pieds de force à l'arrière, près des deux bouts
      for (int k = -1; k <= 1; k += 2) {
         Vec3 c = at.add(along.scale(k * BarricadeEntity.LEG_X / 16.0)).add(back.scale(0.45));
         if (blocked(level, new AABB(c.x - 0.12, at.y + 0.08, c.z - 0.12, c.x + 0.12, at.y + 1.1, c.z + 0.12))) {
            return false;
         }
      }
      return unsupported <= 2;
   }

   private static boolean blocked(Level level, AABB box) {
      if (!level.noCollision(box)) {
         return true;
      }
      return !level.getEntities((Entity) null, box, e -> e.isPickable() && !(e instanceof ItemEntity) && !e.isSpectator()).isEmpty();
   }
}
