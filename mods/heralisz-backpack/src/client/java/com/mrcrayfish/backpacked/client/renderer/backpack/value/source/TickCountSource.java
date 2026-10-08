package com.mrcrayfish.backpacked.client.renderer.backpack.value.source;

import com.mojang.serialization.Codec;
import com.mrcrayfish.backpacked.client.renderer.backpack.BackpackRenderContext;
import net.minecraft.resources.ResourceLocation;

public class TickCountSource implements BaseSource {
   public static final BaseSource.Type TYPE = new BaseSource.Type(new ResourceLocation("tick_count"), Codec.unit(new TickCountSource()));

   @Override
   public BaseSource.Type type() {
      return TYPE;
   }

   @Override
   public double apply(BackpackRenderContext context) {
      return context.entity() != null ? context.entity().tickCount + context.partialTick() : 0.0;
   }
}
