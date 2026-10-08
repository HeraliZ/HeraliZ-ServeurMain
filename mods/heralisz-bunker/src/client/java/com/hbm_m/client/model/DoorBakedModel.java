package com.hbm_m.client.model;

import com.hbm_m.block.decorations.DoorBlock;
import com.hbm_m.block.entity.doors.DoorDecl;
import com.hbm_m.block.entity.doors.DoorDeclRegistry;
import com.hbm_m.client.model.variant.DoorModelProperties;
import com.hbm_m.client.model.variant.DoorModelRegistry;
import com.hbm_m.block.entity.doors.variant.DoorModelSelection;
import com.hbm_m.util.MultipartFacingTransforms;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

public class DoorBakedModel extends AbstractMultipartBakedModel implements AbstractMultipartBakedModel.PartNamesProvider {
   private final String[] cachedPartNames;
   private final ResourceLocation doorId;
   private static final String[] STATIC_PART_NAMES = new String[]{"frame", "Frame", "DoorFrame", "Base", "base"};
   private List<BakedQuad> cachedItemQuads;
   private boolean itemQuadsCached = false;

   public DoorBakedModel(Map<String, BakedModel> parts, ItemTransforms transforms, ResourceLocation doorId) {
      super(parts, transforms);
      this.doorId = doorId;
      this.cachedPartNames = parts.keySet().toArray(new String[0]);
   }

   @Override
   public String[] getPartNames() {
      return this.cachedPartNames;
   }

   public Map<String, BakedModel> getParts() {
      return this.parts;
   }

   @Override
   protected boolean shouldSkipWorldRendering(@Nullable BlockState state) {
      return state != null;
   }

   @Override
   public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
      return this.getQuads(state, side, rand, ModelData.EMPTY, null);
   }

   @Override
   public List<BakedQuad> getQuads(
      @Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData modelData, @Nullable RenderType renderType
   ) {
      return state == null ? this.getItemQuads(side, rand, modelData, renderType) : Collections.emptyList();
   }

   private Map<String, BakedModel> getPartsForModelData(ModelData modelData) {
      DoorModelSelection selection = (DoorModelSelection)modelData.get(DoorModelProperties.MODEL_SELECTION_PROPERTY);
      if (selection == null) {
         return this.parts;
      } else {
         String doorType = extractDoorTypeFromPath(this.doorId.getPath());
         DoorModelRegistry registry = DoorModelRegistry.getInstance();
         if (!registry.isRegistered(doorType)) {
            return this.parts;
         } else {
            ResourceLocation modelPath = registry.getModelPath(doorType, selection);
            if (modelPath == null) {
               return this.parts;
            } else {
               BakedModel selectionModel = Minecraft.getInstance().getModelManager().getModel(modelPath);
               if (selectionModel == null || selectionModel == Minecraft.getInstance().getModelManager().getMissingModel()) {
                  return this.parts;
               } else {
                  return selectionModel instanceof DoorBakedModel doorModel ? doorModel.getParts() : this.parts;
               }
            }
         }
      }
   }

   private List<BakedQuad> getStaticPartQuads(
      @Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData modelData, @Nullable RenderType renderType
   ) {
      List<BakedQuad> result = new ArrayList<>();
      int rotationY = this.getRotationYForFacing(state);
      Map<String, BakedModel> partsToUse = this.getPartsForModelData(modelData);

      for (String partName : STATIC_PART_NAMES) {
         BakedModel part = partsToUse.get(partName);
         if (part != null) {
            List<BakedQuad> partQuads = new ArrayList<>();

            for (Direction d : Direction.values()) {
               partQuads.addAll(part.getQuads(state, d, rand, modelData, renderType));
            }

            partQuads.addAll(part.getQuads(state, null, rand, modelData, renderType));
            if (!partQuads.isEmpty()) {
               List<BakedQuad> translated = ModelHelper.translateQuads(partQuads, 0.5F, 0.0F, 0.5F);
               List<BakedQuad> rotated = ModelHelper.transformQuadsByFacing(translated, rotationY);
               if (side != null) {
                  for (BakedQuad q : rotated) {
                     if (q.getDirection() == side) {
                        result.add(q);
                     }
                  }
               } else {
                  result.addAll(rotated);
               }
            }
         }
      }

      return result;
   }

   private List<BakedQuad> getAllPartQuads(
      @Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData modelData, @Nullable RenderType renderType
   ) {
      if (state == null) {
         return Collections.emptyList();
      }

      DoorDecl doorDecl = DoorDeclRegistry.getById(extractDoorTypeFromPath(this.doorId.getPath()));
      if (doorDecl == null) {
         return Collections.emptyList();
      }

      boolean isOpen = state.hasProperty(DoorBlock.OPEN) && (Boolean)state.getValue(DoorBlock.OPEN);
      Boolean openFromData = (Boolean)modelData.get(DoorModelProperties.OPEN_PROPERTY);
      if (openFromData != null) {
         isOpen = openFromData;
      }

      float openTicks = isOpen ? doorDecl.getOpenTime() : 0.0F;
      int rotationY = this.getRotationYForFacing(state);
      Map<String, BakedModel> partsToUse = this.getPartsForModelData(modelData);
      String[] partNamesToUse = partsToUse.keySet().toArray(new String[0]);
      List<BakedQuad> allQuads = new ArrayList<>();
      Map<String, Matrix4f> transformCache = new HashMap<>();

      for (String partName : partNamesToUse) {
         BakedModel part = partsToUse.get(partName);
         if (part != null) {
            List<BakedQuad> partQuads = new ArrayList<>();

            for (Direction d : Direction.values()) {
               partQuads.addAll(part.getQuads(state, d, rand, modelData, renderType));
            }

            partQuads.addAll(part.getQuads(state, null, rand, modelData, renderType));
            if (!partQuads.isEmpty()) {
               if (isStaticPart(partName)) {
                  List<BakedQuad> translated = ModelHelper.translateQuads(partQuads, 0.5F, 0.0F, 0.5F);
                  allQuads.addAll(ModelHelper.transformQuadsByFacing(translated, rotationY));
               } else {
                  DoorModelSelection selection = (DoorModelSelection)modelData.get(DoorModelProperties.MODEL_SELECTION_PROPERTY);
                  Matrix4f transform = buildPartTransformWithParent(doorDecl, partName, openTicks, partNamesToUse, transformCache, selection);
                  if (transform != null) {
                     partQuads = ModelHelper.transformQuadsByMatrix(partQuads, transform);
                  }

                  List<BakedQuad> translated = ModelHelper.translateQuads(partQuads, 0.5F, 0.0F, 0.5F);
                  allQuads.addAll(ModelHelper.transformQuadsByFacing(translated, rotationY));
               }
            }
         }
      }

      return allQuads;
   }

   private static boolean isStaticPart(String partName) {
      for (String s : STATIC_PART_NAMES) {
         if (s.equals(partName)) {
            return true;
         }
      }

      return false;
   }

   private static String extractDoorTypeFromPath(String path) {
      String base;
      if (path.contains("block/doors/")) {
         base = path.substring(path.indexOf("block/doors/") + "block/doors/".length());
      } else {
         base = path.substring(Math.max(0, path.lastIndexOf(47) + 1));
      }

      int dot = base.lastIndexOf(46);
      return dot > 0 ? base.substring(0, dot) : base;
   }

   private int getRotationYForFacing(BlockState state) {
      if (!state.hasProperty(DoorBlock.FACING)) {
         return 90;
      }

      DoorDecl doorDecl = DoorDeclRegistry.getById(extractDoorTypeFromPath(this.doorId.getPath()));
      int offset = doorDecl != null ? doorDecl.getBakedModelRotationOffsetY() : 0;
      return MultipartFacingTransforms.doorChunkMeshRotationY((Direction)state.getValue(DoorBlock.FACING), offset);
   }

   @Nullable
   private static Matrix4f buildPartTransformWithParent(
      DoorDecl doorDecl, String partName, float openTicks, String[] allPartNames, Map<String, Matrix4f> transformCache, DoorModelSelection selection
   ) {
      String parentName = findParent(doorDecl, partName, allPartNames, selection);
      Matrix4f parentMat = null;
      if (parentName != null) {
         parentMat = transformCache.get(parentName);
         if (parentMat == null) {
            parentMat = buildPartTransformWithParent(doorDecl, parentName, openTicks, allPartNames, transformCache, selection);
            if (parentMat != null) {
               transformCache.put(parentName, parentMat);
            }
         }
      }

      Matrix4f mat = buildPartTransformMatrix(doorDecl, partName, openTicks, parentName != null, selection);
      if (mat == null) {
         return null;
      }

      if (parentMat != null) {
         mat = new Matrix4f(parentMat).mul(mat);
      }

      transformCache.put(partName, mat);
      return mat;
   }

   @Nullable
   private static String findParent(DoorDecl doorDecl, String partName, String[] allPartNames, DoorModelSelection selection) {
      for (String p : allPartNames) {
         for (String c : doorDecl.getChildren(p, selection)) {
            if (c.equals(partName)) {
               return p;
            }
         }
      }

      return null;
   }

   @Nullable
   private static Matrix4f buildPartTransformMatrix(DoorDecl doorDecl, String partName, float openTicks, boolean child, DoorModelSelection selection) {
      float[] origin = new float[3];
      float[] rotation = new float[3];
      float[] translation = new float[3];
      doorDecl.getOrigin(partName, origin, selection);
      doorDecl.getRotation(partName, openTicks, rotation, selection);
      doorDecl.getTranslation(partName, openTicks, child, translation, selection);
      Matrix4f mat = new Matrix4f();
      mat.translate(origin[0], origin[1], origin[2]);
      if (rotation[0] != 0.0F) {
         mat.rotateX((float)Math.toRadians(rotation[0]));
      }

      if (rotation[1] != 0.0F) {
         mat.rotateY((float)Math.toRadians(rotation[1]));
      }

      if (rotation[2] != 0.0F) {
         mat.rotateZ((float)Math.toRadians(rotation[2]));
      }

      mat.translate(-origin[0] + translation[0], -origin[1] + translation[1], -origin[2] + translation[2]);
      return mat;
   }

   private List<BakedQuad> getItemQuads(@Nullable Direction side, RandomSource rand, ModelData modelData, @Nullable RenderType renderType) {
      if (!this.itemQuadsCached) {
         this.buildItemQuads(rand, modelData, renderType);
         this.itemQuadsCached = true;
      }

      return side != null ? this.cachedItemQuads.stream().filter(quad -> quad.getDirection() == side).toList() : this.cachedItemQuads;
   }

   private void buildItemQuads(RandomSource rand, ModelData modelData, @Nullable RenderType renderType) {
      List<BakedQuad> allQuads = new ArrayList<>();

      for (String partName : this.getItemRenderPartNames()) {
         BakedModel part = this.parts.get(partName);
         if (part != null) {
            for (Direction dir : Direction.values()) {
               List<BakedQuad> partQuads = part.getQuads(null, dir, rand, modelData, renderType);
               allQuads.addAll(partQuads);
            }

            List<BakedQuad> generalQuads = part.getQuads(null, null, rand, modelData, renderType);
            allQuads.addAll(generalQuads);
         }
      }

      this.cachedItemQuads = allQuads;
   }

   @Override
   public ItemOverrides getOverrides() {
      return ItemOverrides.EMPTY;
   }

   public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
      return ChunkRenderTypeSet.of(new RenderType[]{RenderType.cutoutMipped()});
   }

   @Override
   public TextureAtlasSprite getParticleIcon() {
      return this.getParticleIcon(ModelData.EMPTY);
   }

   @Override
   public void clearCaches() {
      super.clearCaches();
      this.clearItemQuadCache();
   }

   public void clearItemQuadCache() {
      this.itemQuadsCached = false;
      this.cachedItemQuads = null;
   }
}
