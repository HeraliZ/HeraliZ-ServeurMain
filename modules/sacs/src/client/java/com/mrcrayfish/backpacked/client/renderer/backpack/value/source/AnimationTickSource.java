package com.mrcrayfish.backpacked.client.renderer.backpack.value.source;

import com.mojang.serialization.Codec;
import com.mrcrayfish.backpacked.client.renderer.backpack.BackpackRenderContext;
import net.minecraft.resources.ResourceLocation;

public class AnimationTickSource implements BaseSource {
   public static final BaseSource.Type TYPE = new BaseSource.Type(new ResourceLocation("animation_tick"), Codec.unit(new AnimationTickSource()));

   @Override
   public BaseSource.Type type() {
      return TYPE;
   }

   @Override
   public double apply(BackpackRenderContext context) {
      return context.animationTick() + context.partialTick();
   }
}
