package fr.heralisz.tactical.item.throwable.flash;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import fr.heralisz.tactical.item.throwable.ThrowableData;
import fr.heralisz.tactical.util.TooltipLine;
import fr.heralisz.tactical.util.TooltipUtil;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class StunThrowableData extends ThrowableData {
   @SerializedName("stun")
   private StunThrowableData.StunData stunData = new StunThrowableData.StunData();

   @NotNull
   public StunThrowableData.StunData getStunData() {
      return this.stunData;
   }

   @Override
   public List<TooltipLine> getTooltipLines() {
      List<TooltipLine> lines = super.getTooltipLines();
      StunThrowableData.StunData stunData = this.getStunData();
      lines.add(TooltipLine.normal(Component.translatable("tooltip.heralisz_tactical.throwable.stun.radius", new Object[]{TooltipUtil.format(stunData.getRadius())})));
      lines.add(
         TooltipLine.normal(
            Component.translatable(
               "tooltip.heralisz_tactical.throwable.stun.blind_deafened",
               new Object[]{TooltipUtil.formatTicks(stunData.getBlind().getMinDuration()), TooltipUtil.formatTicks(stunData.getBlind().getMaxDuration())}
            )
         )
      );
      return lines;
   }

   public static class BlindData {
      @SerializedName("max_duration")
      private int maxDuration = 200;
      @SerializedName("min_duration")
      private int minDuration = 10;
      @SerializedName("max_angle")
      private double maxAngle = 85.0;
      @SerializedName("view_angle_factor")
      private double viewAngleFactor = 0.5;

      public BlindData() {
      }

      public BlindData(int maxDuration, int minDuration, double maxAngle, double viewAngleFactor) {
         this.maxDuration = maxDuration;
         this.minDuration = minDuration;
         this.maxAngle = maxAngle;
         this.viewAngleFactor = viewAngleFactor;
      }

      public int getMaxDuration() {
         return this.maxDuration;
      }

      public int getMinDuration() {
         return this.minDuration;
      }

      public double getMaxAngle() {
         return this.maxAngle;
      }

      public double getViewAngleFactor() {
         return this.viewAngleFactor;
      }
   }

   public static class DeafenedData {
      @SerializedName("max_duration")
      private int maxDuration = 200;
      @SerializedName("min_duration")
      private int minDuration = 10;

      public DeafenedData() {
      }

      public DeafenedData(int maxDuration, int minDuration) {
         this.maxDuration = maxDuration;
         this.minDuration = minDuration;
      }

      public int getMaxDuration() {
         return this.maxDuration;
      }

      public int getMinDuration() {
         return this.minDuration;
      }
   }

   public static class StunData {
      @SerializedName("radius")
      private float radius = 24.0F;
      @SerializedName("blind")
      private StunThrowableData.BlindData blind = new StunThrowableData.BlindData();
      @SerializedName("deafened")
      private StunThrowableData.DeafenedData deafened = new StunThrowableData.DeafenedData();

      public StunData() {
      }

      public StunData(float radius, StunThrowableData.BlindData blind, StunThrowableData.DeafenedData deafened) {
         this.radius = radius;
         this.blind = blind;
         this.deafened = deafened;
      }

      public float getRadius() {
         return this.radius;
      }

      public StunThrowableData.BlindData getBlind() {
         return this.blind;
      }

      public StunThrowableData.DeafenedData getDeafened() {
         return this.deafened;
      }

      public int calcBlindDuration(double distance, double angle) {
         int mx = this.blind.getMaxDuration();
         int mn = this.blind.getMinDuration();
         int durationBlinded = (int)Math.round(mx - (mx - mn) * (distance / this.radius));
         double maxAngle = this.blind.getMaxAngle();
         double factor = this.blind.getViewAngleFactor();
         return (int)(durationBlinded * (1.0 - angle * (1.0 - factor) / maxAngle));
      }

      public int calcDeafenedDuration(double distance) {
         int mx = this.deafened.getMaxDuration();
         int mn = this.deafened.getMinDuration();
         return (int)Math.round(mx - (mx - mn) * (distance / this.radius));
      }
   }
}
