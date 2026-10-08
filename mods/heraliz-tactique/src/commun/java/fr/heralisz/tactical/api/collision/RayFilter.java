package fr.heralisz.tactical.api.collision;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class RayFilter implements ITargetFilter {
   @SerializedName("max_range")
   private double maxRange = 2.5;
   @SerializedName("penetration")
   private int penetration = 0;

   public RayFilter(double maxDistance, int penetration) {
      this.maxRange = maxDistance;
      this.penetration = penetration;
   }

   public RayFilter(double maxDistance, int penetration, boolean excludeSelf, boolean excludeAllies) {
      this.maxRange = maxDistance;
      this.penetration = penetration;
   }

   @NotNull
   @Override
   public List<Entity> filterTargets(LivingEntity attacker, Vec3 origin, Vec3 direction) {
      Vec3 to = origin.add(direction.normalize().scale(this.maxRange));
      List<RayFilter.RayEntityHitResult> targets = this.findEntitiesOnPath(attacker, origin, to);
      return targets.stream().limit(this.penetration + 1).<Entity>map(EntityHitResult::getEntity).toList();
   }

   @NotNull
   public List<RayFilter.RayEntityHitResult> findEntitiesOnPath(LivingEntity attacker, Vec3 startVec, Vec3 endVec) {
      List<RayFilter.RayEntityHitResult> hitEntities = new ArrayList<>();
      AABB area = attacker.getBoundingBox().expandTowards(attacker.getViewVector(1.0F).scale(this.maxRange)).inflate(1.0);

      for (Entity entity : attacker.level().getEntities(attacker, area, EntitySelector.NO_SPECTATORS)) {
         if (!entity.equals(attacker) && !entity.equals(attacker.getVehicle()) && entity.isAlive() && ITargetFilter.hasLineOfSight(attacker, entity)) {
            Optional<Vec3> optional = entity.getBoundingBox().clip(startVec, endVec);
            if (optional.isPresent()) {
               RayFilter.RayEntityHitResult result = new RayFilter.RayEntityHitResult(entity, optional.get(), startVec);
               hitEntities.add(result);
            } else if (entity.getBoundingBox().contains(startVec)) {
               RayFilter.RayEntityHitResult result = new RayFilter.RayEntityHitResult(entity, startVec, startVec);
               hitEntities.add(result);
            }
         }
      }

      hitEntities.sort((a, b) -> (int)(a.getDistanceSqr() - b.getDistanceSqr()));
      return hitEntities;
   }

   @Override
   public double getMaxRange() {
      return this.maxRange;
   }

   public static class RayEntityHitResult extends EntityHitResult {
      private final Vec3 source;
      private final double distanceSqr;

      public RayEntityHitResult(Entity entity, Vec3 location, Vec3 source) {
         super(entity, location);
         this.source = source;
         this.distanceSqr = source.distanceToSqr(location);
      }

      public Vec3 getSource() {
         return this.source;
      }

      public double getDistanceSqr() {
         return this.distanceSqr;
      }
   }
}
