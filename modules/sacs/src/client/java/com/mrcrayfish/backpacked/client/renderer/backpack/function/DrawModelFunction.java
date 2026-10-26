package com.mrcrayfish.backpacked.client.renderer.backpack.function;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mrcrayfish.backpacked.client.renderer.backpack.BackpackRenderContext;
import com.mrcrayfish.backpacked.platform.ClientServices;
import java.util.Optional;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import org.joml.Vector3f;

public record DrawModelFunction(ResourceLocation id, Optional<Vector3f> origin) implements BaseFunction {
   public static final BaseFunction.Type TYPE = new BaseFunction.Type(
      new ResourceLocation("draw_model"),
      RecordCodecBuilder.<DrawModelFunction>create(
         builder -> builder.group(
               ResourceLocation.CODEC.fieldOf("model").forGetter((DrawModelFunction o) -> o.id), ExtraCodecs.VECTOR3F.optionalFieldOf("origin").forGetter((DrawModelFunction o) -> o.origin)
            )
            .apply(builder, DrawModelFunction::new)
      )
   );

   @Override
   public BaseFunction.Type type() {
      return TYPE;
   }

   @Override
   public void apply(BackpackRenderContext context) {
      BakedModel model = ClientServices.MODEL.getBakedModel(this.id);
      context.renderer().draw(model);
   }
}
