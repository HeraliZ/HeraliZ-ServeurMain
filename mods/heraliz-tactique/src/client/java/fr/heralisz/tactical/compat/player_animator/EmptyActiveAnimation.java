package fr.heralisz.tactical.compat.player_animator;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import org.jetbrains.annotations.NotNull;

public class EmptyActiveAnimation implements IAnimation {
   public void tick() {
   }

   public boolean isActive() {
      return true;
   }

   @NotNull
   public Vec3f get3DTransform(@NotNull String modelName, @NotNull TransformType type, float tickDelta, @NotNull Vec3f value0) {
      return value0;
   }

   public void setupAnim(float tickDelta) {
   }

   @NotNull
   public FirstPersonMode getFirstPersonMode(float tickDelta) {
      return FirstPersonMode.DISABLED;
   }
}
