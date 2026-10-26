package com.mrcrayfish.backpacked.client.renderer.backpack.function;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mrcrayfish.backpacked.client.renderer.backpack.BackpackRenderContext;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public record StackFunction(List<BaseFunction> functions) implements BaseFunction {
   public static final BaseFunction.Type TYPE = new BaseFunction.Type(
      new ResourceLocation("stack"),
      RecordCodecBuilder.<StackFunction>create(
         builder -> builder.group(BaseFunction.CODEC.listOf().fieldOf("functions").forGetter((StackFunction o) -> o.functions)).apply(builder, StackFunction::new)
      )
   );

   @Override
   public BaseFunction.Type type() {
      return TYPE;
   }

   @Override
   public void apply(BackpackRenderContext context) {
      PoseStack pose = context.pose();
      pose.pushPose();
      this.functions.forEach(f -> f.apply(context));
      pose.popPose();
   }
}
