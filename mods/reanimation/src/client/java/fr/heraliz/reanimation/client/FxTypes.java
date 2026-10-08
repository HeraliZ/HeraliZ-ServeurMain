package fr.heraliz.reanimation.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard.TextureStateShard;
import net.minecraft.client.renderer.RenderType.CompositeState;
import net.minecraft.resources.ResourceLocation;

final class FxTypes extends RenderType {
   private static final Function<ResourceLocation, RenderType> GLOW = Util.memoize(
      tex -> create(
         "reanimation_glow",
         DefaultVertexFormat.NEW_ENTITY,
         Mode.QUADS,
         256,
         false,
         true,
         CompositeState.builder()
            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
            .setTextureState(new TextureStateShard(tex, false, false))
            .setTransparencyState(LIGHTNING_TRANSPARENCY)
            .setCullState(NO_CULL)
            .setWriteMaskState(COLOR_WRITE)
            .setOverlayState(OVERLAY)
            .createCompositeState(false)
      )
   );
   private static final Function<ResourceLocation, RenderType> SOFT = Util.memoize(
      tex -> create(
         "reanimation_soft",
         DefaultVertexFormat.NEW_ENTITY,
         Mode.QUADS,
         256,
         false,
         true,
         CompositeState.builder()
            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
            .setTextureState(new TextureStateShard(tex, false, false))
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setCullState(NO_CULL)
            .setWriteMaskState(COLOR_WRITE)
            .setOverlayState(OVERLAY)
            .createCompositeState(false)
      )
   );

   private FxTypes(String name, VertexFormat format, Mode mode, int size, boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
      super(name, format, mode, size, crumbling, sort, setup, clear);
   }

   static RenderType glow(ResourceLocation tex) {
      return GLOW.apply(tex);
   }

   static RenderType soft(ResourceLocation tex) {
      return SOFT.apply(tex);
   }
}
