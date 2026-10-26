package com.hbm_m.client.loader;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.hbm_m.main.MainRegistry;
import com.mojang.math.Transformation;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;
import net.minecraftforge.client.model.obj.ObjLoader;
import net.minecraftforge.client.model.obj.ObjModel;
import net.minecraftforge.client.model.obj.ObjModel.ModelSettings;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractObjPartModelLoader<T extends BakedModel> implements IGeometryLoader<AbstractObjPartModelLoader.ObjPartGeometry<T>> {
   protected abstract Set<String> getPartNames(JsonObject var1);

   protected abstract T createBakedModel(HashMap<String, BakedModel> var1, ItemTransforms var2, ResourceLocation var3);

   protected boolean flipV() {
      return true;
   }

   protected ResourceLocation mapAtlasForTexture(ResourceLocation texture) {
      return null;
   }

   public AbstractObjPartModelLoader.ObjPartGeometry<T> read(JsonObject jsonObject, JsonDeserializationContext deserializationContext) {
      String modelStr = GsonHelper.getAsString(jsonObject, "model");
      MainRegistry.LOGGER.debug("{}: model string='{}'", this.getClass().getSimpleName(), modelStr);
      ResourceLocation model = ResourceLocation.tryParse(modelStr);
      Set<String> partNames = this.getPartNames(jsonObject);
      boolean flipV = GsonHelper.getAsBoolean(jsonObject, "flip_v", true);
      return new AbstractObjPartModelLoader.ObjPartGeometry<>(model, partNames, flipV, this);
   }

   public static class ObjPartGeometry<T extends BakedModel> implements IUnbakedGeometry<AbstractObjPartModelLoader.ObjPartGeometry<T>> {
      private final ResourceLocation modelLocation;
      private final Set<String> partNames;
      private final boolean flipV;
      private final AbstractObjPartModelLoader<T> loader;

      public ObjPartGeometry(ResourceLocation modelLocation, Set<String> partNames, boolean flipV, AbstractObjPartModelLoader<T> loader) {
         this.modelLocation = modelLocation;
         this.partNames = partNames;
         this.flipV = flipV;
         this.loader = loader;
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
         ObjModel model = this.loadObjModel();
         HashMap<String, BakedModel> bakedParts = this.bakeParts(model, context, baker, spriteGetter, overrides, modelName);
         this.ensureBasePart(model, bakedParts, context, baker, spriteGetter, overrides, modelName);
         MainRegistry.LOGGER.info("{}: Total baked parts: {}", this.loader.getClass().getSimpleName(), bakedParts.size());
         return this.loader.createBakedModel(bakedParts, context.getTransforms(), modelName);
      }

      private ObjModel loadObjModel() {
         try {
            ObjModel model = ObjLoader.INSTANCE.loadModel(new ModelSettings(this.modelLocation, this.flipV, false, true, true, null));
            MainRegistry.LOGGER.info("{}: Successfully loaded OBJ model: {}", this.loader.getClass().getSimpleName(), this.modelLocation);
            return model;
         } catch (Exception e) {
            MainRegistry.LOGGER.error("Failed to load OBJ model: " + this.modelLocation, e);
            throw new RuntimeException("Не удалось загрузить OBJ модель: " + this.modelLocation, e);
         }
      }

      private HashMap<String, BakedModel> bakeParts(
         ObjModel model,
         IGeometryBakingContext context,
         ModelBaker baker,
         Function<Material, TextureAtlasSprite> spriteGetter,
         ItemOverrides overrides,
         ResourceLocation modelName
      ) {
         HashMap<String, BakedModel> bakedParts = new HashMap<>();
         ModelState identityState = this.createIdentityState();
         MainRegistry.LOGGER.info("{}: Baking {} parts: {}", new Object[]{this.loader.getClass().getSimpleName(), this.partNames.size(), this.partNames});

         for (String partName : this.partNames) {
            AbstractObjPartModelLoader.SinglePartBakingContext partContext = new AbstractObjPartModelLoader.SinglePartBakingContext(
               context, partName, this.loader
            );
            BakedModel bakedPart = model.bake(partContext, baker, spriteGetter, identityState, overrides, modelName);
            bakedParts.put(partName, bakedPart);
            if (bakedPart != null) {
               int quadCount = 0;

               for (Direction dir : Direction.values()) {
                  quadCount += bakedPart.getQuads(null, dir, RandomSource.create(42L)).size();
               }

               quadCount += bakedPart.getQuads(null, null, RandomSource.create(42L)).size();
               MainRegistry.LOGGER
                  .info(
                     "{}: Baked part '{}' -> {} quads, model: {}",
                     new Object[]{this.loader.getClass().getSimpleName(), partName, quadCount, bakedPart.getClass().getSimpleName()}
                  );
            } else {
               MainRegistry.LOGGER.warn("{}: Part '{}' baked to NULL!", this.loader.getClass().getSimpleName(), partName);
            }
         }

         return bakedParts;
      }

      private void ensureBasePart(
         ObjModel model,
         HashMap<String, BakedModel> bakedParts,
         IGeometryBakingContext context,
         ModelBaker baker,
         Function<Material, TextureAtlasSprite> spriteGetter,
         ItemOverrides overrides,
         ResourceLocation modelName
      ) {
         if (!bakedParts.containsKey("Base")) {
            MainRegistry.LOGGER.info("{}: Creating fallback 'Base' part", this.loader.getClass().getSimpleName());
            BakedModel baseModel = model.bake(
               new AbstractObjPartModelLoader.SinglePartBakingContext(context, "Base", this.loader),
               baker,
               spriteGetter,
               this.createIdentityState(),
               overrides,
               modelName
            );
            bakedParts.put("Base", baseModel);
            if (baseModel != null) {
               int quadCount = 0;

               for (Direction dir : Direction.values()) {
                  quadCount += baseModel.getQuads(null, dir, RandomSource.create(42L)).size();
               }

               quadCount += baseModel.getQuads(null, null, RandomSource.create(42L)).size();
               MainRegistry.LOGGER.info("{}: Fallback 'Base' part has {} quads", this.loader.getClass().getSimpleName(), quadCount);
            } else {
               MainRegistry.LOGGER.warn("{}: Fallback 'Base' part is NULL!", this.loader.getClass().getSimpleName());
            }
         }
      }

      private ModelState createIdentityState() {
         return new ModelState() {
            @NotNull
            public Transformation getRotation() {
               return Transformation.identity();
            }
         };
      }
   }

   protected static class SinglePartBakingContext implements IGeometryBakingContext {
      private final IGeometryBakingContext parent;
      private final String visiblePart;
      private final AbstractObjPartModelLoader<?> loader;

      public SinglePartBakingContext(IGeometryBakingContext parent, String visiblePart, AbstractObjPartModelLoader<?> loader) {
         this.parent = parent;
         this.visiblePart = visiblePart;
         this.loader = loader;
      }

      public String getModelName() {
         return this.parent.getModelName();
      }

      public boolean isGui3d() {
         return this.parent.isGui3d();
      }

      public boolean useBlockLight() {
         return this.parent.useBlockLight();
      }

      public boolean useAmbientOcclusion() {
         return this.parent.useAmbientOcclusion();
      }

      public ItemTransforms getTransforms() {
         return this.parent.getTransforms();
      }

      public Material getMaterial(String name) {
         Material mat = this.parent.getMaterial(name);
         if (this.visiblePart.equalsIgnoreCase("Label")) {
            if (this.parent.hasMaterial("label")) {
               mat = this.parent.getMaterial("label");
            }
         } else if (!this.visiblePart.equalsIgnoreCase("Frame") && !this.visiblePart.equalsIgnoreCase("Door")) {
            String lowerPart = this.visiblePart.toLowerCase(Locale.ROOT);
            if (this.parent.hasMaterial(lowerPart)) {
               mat = this.parent.getMaterial(lowerPart);
            } else if (this.parent.hasMaterial("default")) {
               mat = this.parent.getMaterial("default");
            }
         } else if (this.parent.hasMaterial("default")) {
            mat = this.parent.getMaterial("default");
         } else {
            String lowerPart = this.visiblePart.toLowerCase(Locale.ROOT);
            if (this.parent.hasMaterial(lowerPart)) {
               mat = this.parent.getMaterial(lowerPart);
            }
         }

         if (mat == null) {
            return null;
         }

         ResourceLocation overrideAtlas = this.loader.mapAtlasForTexture(mat.texture());
         return overrideAtlas != null && !overrideAtlas.equals(mat.atlasLocation()) ? new Material(overrideAtlas, mat.texture()) : mat;
      }

      public Transformation getRootTransform() {
         return this.parent.getRootTransform();
      }

      public boolean hasMaterial(String name) {
         return this.parent.hasMaterial(name);
      }

      public ResourceLocation getRenderTypeHint() {
         return this.parent.getRenderTypeHint();
      }

      public boolean isComponentVisible(String component, boolean fallback) {
         return component.equals(this.visiblePart) || component.startsWith(this.visiblePart + "/");
      }
   }
}
