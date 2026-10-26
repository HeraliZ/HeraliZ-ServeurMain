package com.mrcrayfish.backpacked.client.renderer.backpack.function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mrcrayfish.backpacked.client.renderer.backpack.BackpackRenderContext;
import net.minecraft.resources.ResourceLocation;

public interface BaseFunction {
   Codec<BaseFunction> CODEC = BaseFunction.Type.CODEC.dispatch(BaseFunction::type, BaseFunction.Type::codec);

   BaseFunction.Type type();

   void apply(BackpackRenderContext var1);

   record Type(ResourceLocation id, Codec<? extends BaseFunction> codec) {
      public static final Codec<BaseFunction.Type> CODEC = ResourceLocation.CODEC
         .flatXmap(
            id -> {
               BaseFunction.Type serializer = (BaseFunction.Type)FunctionTypes.getAll().get(id);
               return serializer != null ? DataResult.success(serializer) : DataResult.error(() -> "Unregistered function: " + id);
            },
            function -> FunctionTypes.getAll().containsKey(function.id)
               ? DataResult.success(function.id)
               : DataResult.error(() -> "Unregistered function: " + function.id)
         );
   }
}
