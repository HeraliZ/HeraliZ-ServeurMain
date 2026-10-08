package com.mrcrayfish.backpacked.client.renderer.backpack.function;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mrcrayfish.backpacked.client.renderer.backpack.BackpackRenderContext;
import com.mrcrayfish.backpacked.client.renderer.backpack.value.Value;
import net.minecraft.resources.ResourceLocation;

public record ScaleFunction(Value x, Value y, Value z) implements BaseFunction {
   public static final BaseFunction.Type TYPE = new BaseFunction.Type(
      new ResourceLocation("scale"),
      RecordCodecBuilder.<ScaleFunction>create(
         builder -> builder.group(
               Value.CODEC.fieldOf("x").orElse(Value.ZERO).forGetter((ScaleFunction o) -> o.x),
               Value.CODEC.fieldOf("y").orElse(Value.ZERO).forGetter((ScaleFunction o) -> o.y),
               Value.CODEC.fieldOf("z").orElse(Value.ZERO).forGetter((ScaleFunction o) -> o.z)
            )
            .apply(builder, ScaleFunction::new)
      )
   );

   @Override
   public BaseFunction.Type type() {
      return TYPE;
   }

   @Override
   public void apply(BackpackRenderContext context) {
      double x = this.x.getValue(context);
      double y = this.y.getValue(context);
      double z = this.z.getValue(context);
      PoseStack pose = context.pose();
      pose.scale((float)x, (float)y, (float)z);
   }
}
