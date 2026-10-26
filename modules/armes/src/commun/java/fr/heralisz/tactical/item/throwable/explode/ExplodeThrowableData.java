package fr.heralisz.tactical.item.throwable.explode;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import fr.heralisz.tactical.item.throwable.ThrowableData;
import fr.heralisz.tactical.util.TooltipLine;
import fr.heralisz.tactical.util.TooltipUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;

public class ExplodeThrowableData extends ThrowableData {
   @SerializedName("explode")
   private ExplodeThrowableData.ExplodeData explode = new ExplodeThrowableData.ExplodeData();

   @NotNull
   public ExplodeThrowableData.ExplodeData getExplode() {
      return this.explode;
   }

   @Override
   public List<TooltipLine> getTooltipLines() {
      List<TooltipLine> lines = super.getTooltipLines();
      ExplodeThrowableData.ExplodeData explode = this.getExplode();
      lines.add(
         TooltipLine.normal(
            Component.translatable(
               "tooltip.heralisz_tactical.throwable.explode.line",
               new Object[]{TooltipUtil.format((float)explode.getDamage()), TooltipUtil.format(explode.getRadius())}
            )
         )
      );
      List<Component> traits = new ArrayList<>();
      if (explode.isDestroyBlocks()) {
         traits.add(Component.translatable("tooltip.heralisz_tactical.throwable.explode.destroy_blocks"));
      }

      if (explode.isRemoteDetonation()) {
         traits.add(Component.translatable("tooltip.heralisz_tactical.throwable.explode.remote_detonation"));
      }

      if (!traits.isEmpty()) {
         MutableComponent joined = traits.get(0).copy();

         for (int i = 1; i < traits.size(); i++) {
            joined.append(Component.literal(", ")).append(traits.get(i));
         }

         lines.add(TooltipLine.normal(Component.translatable("tooltip.heralisz_tactical.throwable.explode.traits", new Object[]{joined})));
      }

      return lines;
   }

   public static class ExplodeData {
      @SerializedName("radius")
      private float radius = 5.5F;
      @SerializedName("damage")
      private double damage = 22.0;
      @SerializedName("destroy_blocks")
      private boolean destroyBlocks = false;
      @SerializedName("destroy_multiplier")
      private float destroyMultiplier = 1.0F;
      @SerializedName("trigger_on_explode")
      private boolean triggerOnExplode = false;
      @SerializedName("remote_detonation")
      private boolean remoteDetonation = false;
      @SerializedName("screen_shake_time")
      private double screenShakeTime = 20.0;
      @SerializedName("screen_shake_amplitude")
      private double screenShakeAmplitude = 50.0;

      public float getRadius() {
         return this.radius;
      }

      public double getDamage() {
         return this.damage;
      }

      public boolean isDestroyBlocks() {
         return this.destroyBlocks;
      }

      public float getDestroyMultiplier() {
         return this.destroyMultiplier;
      }

      public boolean isTriggerOnExplode() {
         return this.triggerOnExplode;
      }

      public double getScreenShakeTime() {
         return this.screenShakeTime;
      }

      public double getScreenShakeAmplitude() {
         return this.screenShakeAmplitude;
      }

      public boolean isRemoteDetonation() {
         return this.remoteDetonation;
      }
   }
}
