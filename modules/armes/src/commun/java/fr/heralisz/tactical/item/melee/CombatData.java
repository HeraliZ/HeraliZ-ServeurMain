package fr.heralisz.tactical.item.melee;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import fr.heralisz.tactical.api.collision.ConeFilter;
import fr.heralisz.tactical.api.collision.ITargetFilter;
import fr.heralisz.tactical.api.melee.MeleeAction;
import org.jetbrains.annotations.Nullable;

public class CombatData {
   public static ITargetFilter DEFAULT_HITBOX = new ConeFilter(3.5, 60.0);
   public EnumMap<MeleeAction, List<CombatData.MeleeAttackInfo>> attackInfo = new EnumMap<>(MeleeAction.class);

   @Nullable
   public CombatData.MeleeAttackInfo getAttackInfo(MeleeAction action) {
      return this.getAttackInfo(action, 0);
   }

   @Nullable
   public CombatData.MeleeAttackInfo getAttackInfo(MeleeAction action, int index) {
      List<CombatData.MeleeAttackInfo> attackInfos = this.attackInfo.get(action);
      return attackInfos != null && !attackInfos.isEmpty() ? attackInfos.get(index % attackInfos.size()) : null;
   }

   public static class Deserializer implements JsonDeserializer<CombatData> {
      public CombatData deserialize(JsonElement element, Type type, JsonDeserializationContext ctx) throws JsonParseException {
         if (!element.isJsonObject()) {
            throw new JsonParseException("Expected a JsonObject, get " + element);
         }

         JsonObject jsonObject = (JsonObject)element;
         CombatData combatData = new CombatData();

         for (MeleeAction action : MeleeAction.values()) {
            List<CombatData.MeleeAttackInfo> attackInfos = new ArrayList<>();
            if (jsonObject.has(action.getId())) {
               JsonElement actionElement = jsonObject.get(action.getId());
               this.parseAttackInfo(ctx, action, actionElement, attackInfos);
            }

            if (!attackInfos.isEmpty()) {
               combatData.attackInfo.put(action, attackInfos);
            }
         }

         return combatData;
      }

      private void parseAttackInfo(JsonDeserializationContext ctx, MeleeAction action, JsonElement actionElement, List<CombatData.MeleeAttackInfo> attackInfos) throws JsonParseException {
         if (actionElement.isJsonArray()) {
            for (JsonElement ele : actionElement.getAsJsonArray()) {
               CombatData.MeleeAttackInfo attk = (CombatData.MeleeAttackInfo)ctx.deserialize(ele, CombatData.MeleeAttackInfo.class);
               if (attk != null) {
                  attackInfos.add(attk);
               }
            }
         } else {
            if (!actionElement.isJsonObject()) {
               throw new JsonParseException("Expected a JsonArray or JsonObject for action " + action.getId() + ", get " + actionElement);
            }

            CombatData.MeleeAttackInfo attk = (CombatData.MeleeAttackInfo)ctx.deserialize(actionElement, CombatData.MeleeAttackInfo.class);
            if (attk != null) {
               attackInfos.add(attk);
            }
         }
      }
   }

   public static class MeleeAttackInfo {
      @SerializedName("factor")
      private float factor = 1.0F;
      @SerializedName("knockback")
      private float knockback = 0.02F;
      @SerializedName("cooldown")
      private int cooldown = 20;
      @SerializedName("delay")
      private int delay = 0;
      @SerializedName("hitbox")
      private ITargetFilter hitbox = CombatData.DEFAULT_HITBOX;
      @SerializedName("durability_damage")
      private int durabilityDamage = 1;
      @SerializedName("movement")
      private CombatData.MeleeMovement movement = null;

      public float getFactor() {
         return this.factor;
      }

      public float getKnockback() {
         return this.knockback;
      }

      public int getCooldown() {
         return this.cooldown;
      }

      public int getDelay() {
         return this.delay;
      }

      public ITargetFilter getHitbox() {
         return this.hitbox;
      }

      public int getDurabilityDamage() {
         return this.durabilityDamage;
      }

      @Nullable
      public CombatData.MeleeMovement getMovement() {
         return this.movement;
      }
   }

   public static class MeleeMovement {
      @SerializedName("delay")
      private int delay = 0;
      @SerializedName("speed")
      private float speed = 0.5F;

      public int getDelay() {
         return this.delay;
      }

      public float getSpeed() {
         return this.speed;
      }
   }
}
