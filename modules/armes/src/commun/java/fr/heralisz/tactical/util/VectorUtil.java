package fr.heralisz.tactical.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class VectorUtil {
   public static Vec3 distanceVector(Vec3 point, AABB box) {
      double dx = 0.0;
      if (box.minX > point.x) {
         dx = box.minX - point.x;
      } else if (box.maxX < point.x) {
         dx = box.maxX - point.x;
      }

      double dy = 0.0;
      if (box.minY > point.y) {
         dy = box.minY - point.y;
      } else if (box.maxY < point.y) {
         dy = box.maxY - point.y;
      }

      double dz = 0.0;
      if (box.minZ > point.z) {
         dz = box.minZ - point.z;
      } else if (box.maxZ < point.z) {
         dz = box.maxZ - point.z;
      }

      return new Vec3(dx, dy, dz);
   }

   public static double angleBetween(Vec3 v1, Vec3 v2) {
      return Math.toDegrees(Math.acos(v1.normalize().dot(v2.normalize())));
   }

   public static boolean isInAngle(Vec3 origin, Vec3 view, Entity target, double maxAngle, double maxDistance) {
      Vec3 positionVector = target.position().add(0.0, target.getBbHeight() / 2.0F, 0.0).subtract(origin);
      Vec3 distanceVector = distanceVector(origin, target.getBoundingBox());
      double distance = distanceVector.length();
      if (distance == 0.0) {
         return true;
      } else {
         return distance > maxDistance ? false : angleBetween(view, distanceVector) <= maxAngle || angleBetween(view, positionVector) <= maxAngle;
      }
   }
}
