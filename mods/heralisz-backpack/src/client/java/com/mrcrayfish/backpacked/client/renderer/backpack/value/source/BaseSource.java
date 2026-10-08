package com.mrcrayfish.backpacked.client.renderer.backpack.value.source;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mrcrayfish.backpacked.client.renderer.backpack.BackpackRenderContext;
import net.minecraft.resources.ResourceLocation;

public interface BaseSource {
   Codec<BaseSource> CODEC = BaseSource.Type.CODEC.dispatch(BaseSource.Type::get, BaseSource.Type::codec);

   BaseSource.Type type();

   double apply(BackpackRenderContext var1);

   record Type(ResourceLocation id, Codec<? extends BaseSource> codec) {
      private static final Codec<BaseSource.Type> CODEC = ResourceLocation.CODEC
         .flatXmap(
            id -> {
               BaseSource.Type codec = SourceTypes.getAll().get(id);
               return codec != null ? DataResult.success(codec) : DataResult.error(() -> "Unregistered dynamic value source: " + id);
            },
            codec -> SourceTypes.getAll().containsKey(codec.id)
               ? DataResult.success(codec.id)
               : DataResult.error(() -> "Unregistered dynamic value source: " + codec.id)
         );

      private static BaseSource.Type get(BaseSource source) {
         ResourceLocation id = source.type().id();
         if (!SourceTypes.getAll().containsKey(id)) {
            throw new IllegalArgumentException("Unregistered dynamic value source: " + id);
         } else {
            return SourceTypes.getAll().get(id);
         }
      }
   }
}
