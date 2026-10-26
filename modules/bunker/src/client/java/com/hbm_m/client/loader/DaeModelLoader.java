package com.hbm_m.client.loader;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.hbm_m.client.loader.dae.DaeAnimation;
import com.hbm_m.client.loader.dae.DaeModel;
import com.hbm_m.client.loader.dae.DaeQuadBaker;
import com.hbm_m.client.model.DaeBakedModel;
import com.hbm_m.lib.RefStrings;
import com.hbm_m.main.MainRegistry;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;

public class DaeModelLoader implements IGeometryLoader<DaeModelLoader.DaeUnbakedGeometry> {
   private static final String CLIP_NAME = "animation";
   private static final String FALLBACK_TEXTURE = "hbm_m:block/doors/transition_seal";

   public DaeModelLoader.DaeUnbakedGeometry read(JsonObject jsonObject, JsonDeserializationContext deserializationContext) throws JsonParseException {
      ResourceLocation model = ResourceLocation.tryParse(GsonHelper.getAsString(jsonObject, "model"));
      float time = GsonHelper.getAsFloat(jsonObject, "time", 0.0F);
      return new DaeModelLoader.DaeUnbakedGeometry(model, time);
   }

   public static class DaeUnbakedGeometry implements IUnbakedGeometry<DaeModelLoader.DaeUnbakedGeometry> {
      private final ResourceLocation modelLocation;
      private final float time;

      public DaeUnbakedGeometry(ResourceLocation modelLocation, float time) {
         this.modelLocation = modelLocation;
         this.time = time;
      }

      public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
      }

      public BakedModel bake(
         IGeometryBakingContext context,
         ModelBaker baker,
         Function<Material, TextureAtlasSprite> spriteGetter,
         ModelState modelState,
         ItemOverrides overrides,
         ResourceLocation modelName
      ) {
         Material material = context.hasMaterial("default")
            ? context.getMaterial("default")
            : new Material(TextureAtlas.LOCATION_BLOCKS, RefStrings.resourceLocation("hbm_m:block/doors/transition_seal"));
         TextureAtlasSprite sprite = spriteGetter.apply(material);
         DaeModel model = DaeModel.load(this.modelLocation);
         DaeAnimation clip = model.animations.get("animation");
         if (clip == null && !model.animations.isEmpty()) {
            clip = model.animations.values().iterator().next();
         }

         List<BakedQuad> quads = DaeQuadBaker.bakeScene(model, clip, this.time, sprite);
         MainRegistry.LOGGER.info("DaeModelLoader: baked {} quads for {}", quads.size(), this.modelLocation);
         return new DaeBakedModel(quads, sprite, context.getTransforms());
      }
   }
}
