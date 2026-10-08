package fr.heralisz.tactical.api.collision;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class OBBFilter implements ITargetFilter {
   @SerializedName("max_range")
   private double maxRange = 4.0;
   @SerializedName("half_width")
   private double halfWidth = 0.5;
   @SerializedName("half_height")
   private double halfHeight = 0.5;
   @SerializedName("exclude_self")
   private boolean excludeSelf = true;
   @SerializedName("roll")
   private float roll = 0.0F;

   @NotNull
   @Override
   public List<Entity> filterTargets(LivingEntity attacker, Vec3 origin, Vec3 direction) {
      Quaternionf rotation = new Quaternionf()
         .rotationYXZ(-attacker.getYRot() * (float) (Math.PI / 180.0), attacker.getXRot() * (float) (Math.PI / 180.0), this.roll * (float) (Math.PI / 180.0));
      Vector3f extents = new Vec3(this.halfWidth, this.halfHeight, this.maxRange / 2.0).toVector3f();
      Vector3f center = origin.add(direction.scale(this.maxRange / 2.0)).toVector3f();
      OBB obb = new OBB(center, extents, rotation);
      List<Entity> targets = new ArrayList<>();
      AABB area = attacker.getBoundingBox().inflate(this.maxRange * 2.0, this.maxRange, this.maxRange * 2.0);

      for (Entity livingentity : attacker.level().getEntitiesOfClass(Entity.class, area)) {
         boolean flag = !(livingentity instanceof ArmorStand armorStand && armorStand.isMarker());
         boolean see = ITargetFilter.hasLineOfSight(attacker, livingentity);
         boolean self = this.excludeSelf && livingentity == attacker;
         if (!self && flag && obb.interactsWithAABB(livingentity.getBoundingBox()) && see) {
            targets.add(livingentity);
         }
      }

      return targets;
   }

   @Override
   public double getMaxRange() {
      return this.maxRange;
   }
}
