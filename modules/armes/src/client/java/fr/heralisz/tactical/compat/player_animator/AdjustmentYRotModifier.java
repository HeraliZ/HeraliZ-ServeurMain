package fr.heralisz.tactical.compat.player_animator;

import dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier;
import dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier.PartModifier;
import dev.kosmx.playerAnim.core.util.Vec3f;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

public class AdjustmentYRotModifier implements Function<String, Optional<PartModifier>> {
   private final Player player;

   private AdjustmentYRotModifier(Player player) {
      this.player = player;
   }

   public Optional<PartModifier> apply(String partName) {
      Minecraft mc = Minecraft.getInstance();
      if (this.player.equals(mc.player) && mc.screen != null) {
         return Optional.empty();
      }

      if (this.player.getVehicle() != null && "body".equals(partName)) {
         return Optional.empty();
      }

      float partialTick = mc.getPartialTick();
      float yBodyRot = Mth.rotLerp(partialTick, this.player.yBodyRotO, this.player.yBodyRot);
      float yHeadRot = Mth.rotLerp(partialTick, this.player.yHeadRotO, this.player.yHeadRot);
      float xRot = Mth.lerp(partialTick, this.player.xRotO, this.player.getXRot());
      float yaw = Mth.clamp(Mth.wrapDegrees(yHeadRot - yBodyRot), -85.0F, 85.0F);
      float pitch = Mth.wrapDegrees(xRot);

      return switch (partName) {
         case "body" -> !this.player.isSwimming() && this.player.getPose() == Pose.SWIMMING
            ? Optional.of(new PartModifier(new Vec3f(0.0F, 0.0F, -yaw * (float) (Math.PI / 180.0)), Vec3f.ZERO))
            : Optional.of(new PartModifier(new Vec3f(0.0F, -yaw * (float) (Math.PI / 180.0), 0.0F), Vec3f.ZERO));
         case "head" -> Optional.of(new PartModifier(new Vec3f(pitch * (float) (Math.PI / 180.0), 0.0F, 0.0F), Vec3f.ZERO));
         case "leftArm", "rightArm" -> Optional.of(
            new PartModifier(new Vec3f(Mth.clamp(pitch, -15.0F, 15.0F) * (float) (Math.PI / 180.0), 0.0F, 0.0F), Vec3f.ZERO)
         );
         default -> Optional.empty();
      };
   }

   public static AdjustmentModifier getModifier(Player player) {
      return new AdjustmentModifier(new AdjustmentYRotModifier(player));
   }
}
