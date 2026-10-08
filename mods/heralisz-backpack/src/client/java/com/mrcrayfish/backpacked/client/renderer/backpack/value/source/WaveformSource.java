package com.mrcrayfish.backpacked.client.renderer.backpack.value.source;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mrcrayfish.backpacked.client.renderer.backpack.BackpackRenderContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.StringRepresentable.EnumCodec;

public record WaveformSource(WaveformSource.Waveform waveform, double wavelength, double amplitude, double phase) implements BaseSource {
   public static final BaseSource.Type TYPE = new BaseSource.Type(
      new ResourceLocation("backpacked", "waveform"),
      RecordCodecBuilder.<WaveformSource>create(
         builder -> builder.group(
               WaveformSource.Waveform.CODEC.fieldOf("waveform").forGetter((WaveformSource o) -> o.waveform),
               Codec.DOUBLE.fieldOf("wavelength").orElse(2.0).forGetter((WaveformSource o) -> o.wavelength),
               Codec.DOUBLE.fieldOf("amplitude").orElse(1.0).forGetter((WaveformSource o) -> o.amplitude),
               Codec.DOUBLE.fieldOf("phase").orElse(0.0).forGetter((WaveformSource o) -> o.phase)
            )
            .apply(builder, WaveformSource::new)
      )
   );

   @Override
   public BaseSource.Type type() {
      return TYPE;
   }

   @Override
   public double apply(BackpackRenderContext context) {
      double time = context.animationTick() + context.partialTick();
      return this.waveform.function.apply(time, this.wavelength, this.amplitude, this.phase);
   }

   public enum Waveform implements StringRepresentable {
      SINE("sine", (time, wavelength, amplitude, phase) -> amplitude * Mth.sin((float)(((float) (Math.PI * 2) * time - phase) / wavelength))),
      SQUARE("square", (time, wavelength, amplitude, phase) -> Mth.positiveModulo(time - phase, wavelength) < wavelength / 2.0 ? amplitude : -amplitude),
      TRIANGLE(
         "triangle",
         (time, wavelength, amplitude, phase) -> 2.0
            * amplitude
            / (float) Math.PI
            * Math.asin(Mth.sin((float)(((float) (Math.PI * 2) * time - phase) / wavelength)))
      ),
      SAWTOOTH(
         "sawtooth",
         (time, wavelength, amplitude, phase) -> 2.0
            * amplitude
            / (float) Math.PI
            * Math.atan(Math.tan((float)(((float) (Math.PI * 2) * time - phase) / (2.0 * wavelength))))
      );

      public static final EnumCodec<WaveformSource.Waveform> CODEC = StringRepresentable.fromEnum(WaveformSource.Waveform::values);
      private final String name;
      private final WaveformSource.WaveformFunction function;

      Waveform(String name, WaveformSource.WaveformFunction function) {
         this.name = name;
         this.function = function;
      }

      public String getSerializedName() {
         return this.name;
      }
   }

   @FunctionalInterface
   public interface WaveformFunction {
      double apply(double var1, double var3, double var5, double var7);
   }
}
