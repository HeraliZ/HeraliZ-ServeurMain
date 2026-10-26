package fr.heralisz.tactical.compat.player_animator;

import com.google.gson.annotations.SerializedName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

public class ThirdPersonAnimationConfig {
   @SerializedName("animation")
   private String animationPath;
   @SerializedName("upper")
   private Map<String, Object> upper = new HashMap<>();
   @SerializedName("lower")
   private Map<String, Object> lower = new HashMap<>();

   @Nullable
   public ResourceLocation getAnimationPath() {
      return this.animationPath == null ? null : new ResourceLocation(this.animationPath);
   }

   @Nullable
   public String getAnimation(PlayerAnimatorIntegration.AnimationLayer layer, String action, int index) {
      Map<String, Object> actions = layer == PlayerAnimatorIntegration.AnimationLayer.UPPER ? this.upper : this.lower;
      Object value = actions.get(action);
      if (value == null) {
         return null;
      } else if (value instanceof String) {
         return (String)value;
      } else if (value instanceof List<?> list && !list.isEmpty()) {
         Object item = list.get(index % list.size());
         return item instanceof String ? (String)item : null;
      } else {
         return null;
      }
   }

   public boolean hasMultiple(PlayerAnimatorIntegration.AnimationLayer layer, String action) {
      Map<String, Object> actions = layer == PlayerAnimatorIntegration.AnimationLayer.UPPER ? this.upper : this.lower;
      return actions.get(action) instanceof List<?> list && list.size() > 1;
   }

   public int getCount(PlayerAnimatorIntegration.AnimationLayer layer, String action) {
      Map<String, Object> actions = layer == PlayerAnimatorIntegration.AnimationLayer.UPPER ? this.upper : this.lower;
      Object value = actions.get(action);
      if (value instanceof List<?> list) {
         return list.size();
      } else {
         return value != null ? 1 : 0;
      }
   }

   @Deprecated
   public boolean hasMultipleAnimations(String actionName) {
      return this.hasMultiple(PlayerAnimatorIntegration.AnimationLayer.UPPER, actionName);
   }

   @Deprecated
   public int getAnimationCount(String actionName) {
      return this.getCount(PlayerAnimatorIntegration.AnimationLayer.UPPER, actionName);
   }
}
