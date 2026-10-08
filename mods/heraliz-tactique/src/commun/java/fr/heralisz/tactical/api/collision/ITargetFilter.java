package fr.heralisz.tactical.api.collision;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.List;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface ITargetFilter {
   @NotNull
   List<Entity> filterTargets(LivingEntity var1, Vec3 var2, Vec3 var3);

   default double getMaxRange() {
      return 2.5;
   }

   static boolean hasLineOfSight(LivingEntity attacker, Entity pEntity) {
      if (pEntity.level() == attacker.level()) {
         Vec3 vec3 = new Vec3(attacker.getX(), attacker.getEyeY(), attacker.getZ());
         if (clip(attacker, new Vec3(pEntity.getX(), pEntity.getEyeY(), pEntity.getZ()), vec3)) {
            return true;
         }

         AABB boundingBox = pEntity.getBoundingBox();

         for (double x : new double[]{boundingBox.minX, boundingBox.maxX}) {
            for (double y : new double[]{boundingBox.minY, boundingBox.maxY}) {
               for (double z : new double[]{boundingBox.minZ, boundingBox.maxZ}) {
                  if (clip(attacker, new Vec3(x, y, z), vec3)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   static boolean clip(LivingEntity attacker, Vec3 vec31, Vec3 vec3) {
      return vec31.distanceTo(vec3) > 128.0
         ? false
         : attacker.level().clip(new ClipContext(vec3, vec31, Block.COLLIDER, Fluid.NONE, attacker)).getType() == Type.MISS;
   }

   class Deserializer implements JsonDeserializer<ITargetFilter> {
      public ITargetFilter deserialize(JsonElement element, java.lang.reflect.Type type, JsonDeserializationContext ctx) throws JsonParseException {
         if (!element.isJsonObject()) {
            throw new JsonParseException("Expected a JsonObject, get " + element);
         }

         JsonObject jsonObject = (JsonObject)element;
         String typeName = GsonHelper.getAsString(jsonObject, "type");

         return switch (typeName) {
            case "cone" -> (ITargetFilter)ctx.deserialize(jsonObject, ConeFilter.class);
            case "ray" -> (ITargetFilter)ctx.deserialize(jsonObject, RayFilter.class);
            case "obb" -> (ITargetFilter)ctx.deserialize(jsonObject, OBBFilter.class);
            default -> throw new JsonParseException("Unknown filter type: " + typeName);
         };
      }
   }
}
