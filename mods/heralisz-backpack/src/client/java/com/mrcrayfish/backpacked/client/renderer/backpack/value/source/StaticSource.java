package com.mrcrayfish.backpacked.client.renderer.backpack.value.source;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mrcrayfish.backpacked.client.renderer.backpack.BackpackRenderContext;
import net.minecraft.resources.ResourceLocation;

public record StaticSource(double value) implements BaseSource {
   public static final BaseSource.Type TYPE = new BaseSource.Type(
      new ResourceLocation("static"),
      RecordCodecBuilder.<StaticSource>create(builder -> builder.group(Codec.DOUBLE.fieldOf("value").forGetter((StaticSource o) -> o.value)).apply(builder, StaticSource::new))
   );

   @Override
   public BaseSource.Type type() {
      return TYPE;
   }

   @Override
   public double apply(BackpackRenderContext context) {
      return this.value;
   }
}
