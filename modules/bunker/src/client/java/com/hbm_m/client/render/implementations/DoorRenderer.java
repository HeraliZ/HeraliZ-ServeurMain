package com.hbm_m.client.render.implementations;

import com.hbm_m.block.entity.doors.DoorBlockEntity;
import com.hbm_m.block.entity.doors.DoorDecl;
import com.hbm_m.client.compat.create.ContraptionDoorAnimCache;
import com.hbm_m.client.loader.dae.DaeAnimation;
import com.hbm_m.client.loader.dae.DaeModel;
import com.hbm_m.client.loader.dae.DaeNode;
import com.hbm_m.client.loader.dae.DaeQuadBaker;
import com.hbm_m.client.model.DoorBakedModel;
import com.hbm_m.client.model.variant.DoorModelRegistry;
import com.hbm_m.block.entity.doors.variant.DoorModelSelection;
import com.hbm_m.block.entity.doors.variant.DoorSkin;
import com.hbm_m.client.render.AbstractPartBasedRenderer;
import com.hbm_m.client.render.InstancedStaticPartRenderer;
import com.hbm_m.client.render.LegacyAnimator;
import com.hbm_m.client.render.MeshRenderCache;
import com.hbm_m.client.render.ObjModelVboBuilder;
import com.hbm_m.client.render.RenderDistanceHelper;
import com.hbm_m.client.render.SingleMeshVboRenderer;
import com.hbm_m.client.render.culling.OcclusionCullingHelper;
import com.hbm_m.client.render.shader.IrisRenderBatch;
import com.hbm_m.client.render.shader.ShaderCompatibilityDetector;
import com.hbm_m.compat.ContraptionRenderCompat;
import com.hbm_m.config.ModClothConfig;
import com.hbm_m.main.MainRegistry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class DoorRenderer extends AbstractPartBasedRenderer<DoorBlockEntity, BakedModel> {
   private static final ConcurrentHashMap<String, InstancedStaticPartRenderer> instancedFrameCache = new ConcurrentHashMap<>();
   private static final ConcurrentHashMap<String, Boolean> frameInitializationFlags = new ConcurrentHashMap<>();
   private static final ConcurrentHashMap<String, InstancedStaticPartRenderer> instancedPartCache = new ConcurrentHashMap<>();
   private static final ConcurrentHashMap<String, Boolean> partInitializationFlags = new ConcurrentHashMap<>();
   private static final ConcurrentHashMap<String, SingleMeshVboRenderer> DAE_RENDERER_CACHE = new ConcurrentHashMap<>();
   private static final ConcurrentHashMap<ResourceLocation, DaeModel> DAE_MODELS_CACHE = new ConcurrentHashMap<>();
   private final float[] translation = new float[3];
   private final float[] origin = new float[3];
   private final float[] rotation = new float[3];
   private static final Set<String> PARTS_WITHOUT_GEOMETRY = ConcurrentHashMap.newKeySet();

   public DoorRenderer(Context context) {
   }

   private synchronized void initializeFrameInstancerForType(DoorBakedModel model, String doorType, String framePartName, String frameKey) {
      if (!frameInitializationFlags.getOrDefault(frameKey, false)) {
         try {
            BakedModel frameModel = model.getPart(framePartName);
            if (frameModel != null) {
               SingleMeshVboRenderer.VboData frameData = ObjModelVboBuilder.buildSinglePart(frameModel, framePartName);
               if (frameData != null) {
                  List<BakedQuad> frameQuads = MeshRenderCache.getOrCompile(frameKey, frameModel);
                  InstancedStaticPartRenderer frameRenderer = new InstancedStaticPartRenderer(frameData, frameQuads);
                  frameRenderer.setMdiTraceTag("Door/frame:" + frameKey);
                  instancedFrameCache.put(frameKey, frameRenderer);
               }
            }
         } catch (Exception e) {
            MainRegistry.LOGGER.debug("DoorRenderer: Failed to init frame for {}: {}", doorType, e.getMessage());
         } finally {
            frameInitializationFlags.put(frameKey, true);
         }
      }
   }

   private synchronized void initializePartInstancerFor(DoorBakedModel model, String doorType, String partName, DoorModelSelection selection, String cacheKey) {
      if (!partInitializationFlags.getOrDefault(cacheKey, false)) {
         try {
            BakedModel partModel = model.getPart(partName);
            if (partModel != null) {
               SingleMeshVboRenderer.VboData data = ObjModelVboBuilder.buildSinglePart(partModel, partName);
               if (data != null) {
                  List<BakedQuad> partQuads = MeshRenderCache.getOrCompile(cacheKey, partModel);
                  InstancedStaticPartRenderer renderer = new InstancedStaticPartRenderer(data, partQuads);
                  renderer.setMdiTraceTag("Door/part:" + partName + ":" + cacheKey);
                  instancedPartCache.put(cacheKey, renderer);
               }
            }
         } catch (Exception e) {
            MainRegistry.LOGGER.debug("DoorRenderer: Failed to init part instancer for '{}': {}", partName, e.getMessage());
         } finally {
            partInitializationFlags.put(cacheKey, true);
         }
      }
   }

   private static String getPartCacheKey(String doorType, String partName, DoorModelSelection selection) {
      return "anim_" + doorType + "_" + selection.getModelType().getId() + "_" + selection.getSkin().getId() + "_" + partName;
   }

   private static String getSelectionCacheKey(DoorModelSelection selection) {
      return selection.getModelType().getId() + "_" + selection.getSkin().getId();
   }

   private static boolean partHasGeometry(BakedModel partModel, String partName, String cacheKey) {
      if (partModel == null) {
         return false;
      }

      if (PARTS_WITHOUT_GEOMETRY.contains(cacheKey)) {
         return false;
      }

      int count = 0;
      RandomSource rand = RandomSource.create(42L);

      for (Direction d : Direction.values()) {
         count += partModel.getQuads(null, d, rand, ModelData.EMPTY, RenderType.solid()).size();
      }

      count += partModel.getQuads(null, null, rand, ModelData.EMPTY, RenderType.solid()).size();
      if (count == 0) {
         PARTS_WITHOUT_GEOMETRY.add(cacheKey);
         return false;
      } else {
         return true;
      }
   }

   protected BakedModel getModel(DoorBlockEntity blockEntity) {
      DoorDecl doorDecl = blockEntity.getDoorDecl();
      if (doorDecl == null) {
         return super.getModel(blockEntity);
      }

      String doorType = this.getDoorTypeKey(doorDecl);
      DoorModelRegistry registry = DoorModelRegistry.getInstance();
      if (!registry.isRegistered(doorType)) {
         return super.getModel(blockEntity);
      }

      DoorModelSelection selection = blockEntity.getModelSelection();
      ResourceLocation modelPath = registry.getModelPath(doorType, selection);
      if (modelPath == null) {
         return super.getModel(blockEntity);
      }

      ModelManager modelManager = Minecraft.getInstance().getModelManager();
      BakedModel selectionModel = modelManager.getModel(modelPath);
      return selectionModel != null && selectionModel != modelManager.getMissingModel() ? selectionModel : super.getModel(blockEntity);
   }

   @Override
   protected BakedModel getModelType(BakedModel rawModel) {
      return rawModel;
   }

   protected Direction getFacing(DoorBlockEntity blockEntity) {
      return blockEntity.getFacing();
   }

   protected void renderParts(
      DoorBlockEntity be,
      BakedModel model,
      LegacyAnimator animator,
      float partialTick,
      int packedLight,
      int packedOverlay,
      PoseStack poseStack,
      MultiBufferSource bufferSource
   ) {
      if (be.isController() || ContraptionRenderCompat.isContraptionRender(be)) {
         DoorDecl doorDecl = be.getDoorDecl();
         if (doorDecl != null) {
            BlockPos blockPos = be.getBlockPos();
            Minecraft minecraft = Minecraft.getInstance();
            if (!ContraptionRenderCompat.isContraptionRender(be)) {
               AABB renderBounds = be.getRenderBoundingBox();
               if (!OcclusionCullingHelper.shouldRender(blockPos, minecraft.level, renderBounds)) {
                  return;
               }
            }

            boolean onContraption = ContraptionRenderCompat.isContraptionRender(be);
            float openTicks;
            boolean isOpen;
            if (onContraption) {
               float progress = ContraptionDoorAnimCache.chase(be, doorDecl.getOpenTime());
               openTicks = progress * doorDecl.getOpenTime();
               isOpen = progress > 0.5F;
            } else {
               openTicks = be.getOpenProgress(partialTick) * doorDecl.getOpenTime();
               isOpen = be.isOpen();
            }

            float doorFade = RenderDistanceHelper.computeStaticFade(be);
            if (!(doorFade < 0.0F)) {
               SingleMeshVboRenderer.setFadeAlpha(doorFade);
               doorDecl.doOffsetTransform(animator);
               if (model instanceof DoorBakedModel doorModel) {
                  this.renderWithVBO(be, doorModel, doorDecl, openTicks, isOpen, poseStack, packedLight, blockPos, bufferSource);
               } else {
                  ResourceLocation daePath = doorDecl.getColladaAnimationSource();
                  if (daePath != null) {
                     this.renderDaeModel(be, daePath, doorDecl, openTicks, poseStack, packedLight, bufferSource);
                  }
               }
            }
         }
      }
   }

   private void renderDaeModel(
      DoorBlockEntity be, ResourceLocation daePath, DoorDecl doorDecl, float openTicks, PoseStack poseStack, int packedLight, MultiBufferSource bufferSource
   ) {
      DaeModel model = DAE_MODELS_CACHE.computeIfAbsent(daePath, path -> {
         try {
            return DaeModel.load(path);
         } catch (Exception e) {
            MainRegistry.LOGGER.error("DoorRenderer: failed to load DAE model {}", path, e);
            return null;
         }
      });
      if (model != null) {
         DaeAnimation clip = model.animations.get("animation");
         if (clip == null && !model.animations.isEmpty()) {
            clip = model.animations.values().iterator().next();
         }

         float time = openTicks / 20.0F;
         DoorModelSelection selection = be.getModelSelection();
         DoorSkin fullSkin = selection.getSkin();
         if (fullSkin.getTexturePath() == null && !fullSkin.isDefault()) {
            String doorId = doorDecl.getBlockId().getPath();
            DoorModelRegistry registry = DoorModelRegistry.getInstance();
            DoorSkin resolved = registry.getSkin(doorId, fullSkin.getId());
            if (resolved != null && resolved.getTexturePath() != null) {
               selection = new DoorModelSelection(selection.getModelType(), resolved);
            }
         }

         poseStack.pushPose();
         int offset = doorDecl.getBakedModelRotationOffsetY();
         if (offset != 0) {
            poseStack.translate(0.5F, 0.0F, 0.5F);
            poseStack.mulPose(Axis.YP.rotationDegrees(offset));
            poseStack.translate(-0.5F, 0.0F, -0.5F);
         }

         if (offset != 0) {
            poseStack.translate(0.0F, 0.0F, 1.0F);
         }

         boolean iris = ShaderCompatibilityDetector.isExternalShaderActive();
         if (iris) {
            boolean shadowPass = ShaderCompatibilityDetector.isRenderingShadowPass();

            try (IrisRenderBatch batch = IrisRenderBatch.begin(shadowPass, RenderSystem.getProjectionMatrix())) {
               this.renderDaeNodes(model.sceneRoots, clip, time, poseStack, packedLight, be, bufferSource, selection, doorDecl);
            }
         } else {
            this.renderDaeNodes(model.sceneRoots, clip, time, poseStack, packedLight, be, bufferSource, selection, doorDecl);
         }

         poseStack.popPose();
      }
   }

   private void renderDaeNodes(
      List<DaeNode> nodes,
      DaeAnimation clip,
      float time,
      PoseStack poseStack,
      int packedLight,
      DoorBlockEntity be,
      MultiBufferSource bufferSource,
      DoorModelSelection selection,
      DoorDecl doorDecl
   ) {
      for (DaeNode node : nodes) {
         poseStack.pushPose();
         poseStack.mulPoseMatrix(node.localMatrix(time, clip));
         if (node.mesh != null) {
            SingleMeshVboRenderer renderer = this.getDaeRendererForNode(node, selection, doorDecl);
            if (renderer != null) {
               renderer.render(poseStack, packedLight, be.getBlockPos(), be, bufferSource);
            }
         }

         this.renderDaeNodes(node.children, clip, time, poseStack, packedLight, be, bufferSource, selection, doorDecl);
         poseStack.popPose();
      }
   }

   private SingleMeshVboRenderer getDaeRendererForNode(DaeNode node, DoorModelSelection selection, DoorDecl doorDecl) {
      String skinId = selection.getSkin().getId();
      String key = "dae_door:" + doorDecl.getBlockId().getPath() + ":" + node.name + ":" + skinId;
      return DAE_RENDERER_CACHE.computeIfAbsent(key, k -> {
         try {
            ResourceLocation texture = this.resolveDaeTexture(node, selection, doorDecl);
            TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(texture);
            List<BakedQuad> quads = DaeQuadBaker.bakeNodeQuads(node.mesh, new Matrix4f(), sprite);
            return quads.isEmpty() ? null : MeshRenderCache.getOrCreateRendererFromQuadList(key, quads);
         } catch (Exception e) {
            MainRegistry.LOGGER.error("DoorRenderer: failed to bake DAE node '{}'", node.name, e);
            return null;
         }
      });
   }

   private ResourceLocation resolveDaeTexture(DaeNode node, DoorModelSelection selection, DoorDecl doorDecl) {
      DoorSkin skin = selection.getSkin();
      ResourceLocation tex = skin.getTextureForPart(node.name);
      if (tex != null && !tex.equals(skin.getTexturePath())) {
         return tex;
      }

      if (skin.getTexturePath() != null) {
         return skin.getTexturePath();
      }

      String basePath = doorDecl.getBlockId().getPath();
      return new ResourceLocation("heralisz_bunker", "block/doors/" + basePath + (selection.isLegacy() ? "_old" : ""));
   }

   private void renderWithVBO(
      DoorBlockEntity be,
      DoorBakedModel model,
      DoorDecl doorDecl,
      float openTicks,
      boolean isOpen,
      PoseStack poseStack,
      int packedLight,
      BlockPos blockPos,
      MultiBufferSource bufferSource
   ) {
      try {
         String doorType = this.getDoorTypeKey(doorDecl);
         DoorModelSelection selection = be.getModelSelection();
         String frameKey = "frame_" + doorType + "_" + getSelectionCacheKey(selection);
         String[] partNames = model.getPartNames();
         String staticFramePart = this.detectFramePart(partNames);
         boolean useBatchingNow = ModClothConfig.useInstancedBatching();
         boolean shadowPass = ShaderCompatibilityDetector.isRenderingShadowPass();
         boolean useIrisBatch = ShaderCompatibilityDetector.isExternalShaderActive() && (!useBatchingNow || shadowPass);
         if (useIrisBatch) {
            try (IrisRenderBatch batch = IrisRenderBatch.begin(shadowPass, RenderSystem.getProjectionMatrix())) {
               this.renderDoorVboParts(
                  be, model, doorDecl, partNames, staticFramePart, doorType, selection, frameKey, openTicks, poseStack, packedLight, blockPos, bufferSource
               );
            }
         } else {
            this.renderDoorVboParts(
               be, model, doorDecl, partNames, staticFramePart, doorType, selection, frameKey, openTicks, poseStack, packedLight, blockPos, bufferSource
            );
         }
      } catch (Exception e) {
         MainRegistry.LOGGER.error("Error in VBO door render", e);
      }
   }

   private void renderDoorVboParts(
      DoorBlockEntity be,
      DoorBakedModel model,
      DoorDecl doorDecl,
      String[] partNames,
      String staticFramePart,
      String doorType,
      DoorModelSelection selection,
      String frameKey,
      float openTicks,
      PoseStack poseStack,
      int packedLight,
      BlockPos blockPos,
      MultiBufferSource bufferSource
   ) {
      if (staticFramePart != null) {
         if (!frameInitializationFlags.getOrDefault(frameKey, false)) {
            this.initializeFrameInstancerForType(model, doorType, staticFramePart, frameKey);
         }

         InstancedStaticPartRenderer frameRenderer = instancedFrameCache.get(frameKey);
         boolean useInstancedFrame = frameRenderer != null && frameRenderer.isInitialized();
         if (useInstancedFrame) {
            poseStack.pushPose();
            boolean useBatching = ModClothConfig.useInstancedBatching();
            boolean inShadowPass = ShaderCompatibilityDetector.isRenderingShadowPass();
            if (useBatching && !inShadowPass) {
               frameRenderer.addInstance(poseStack, packedLight, blockPos, be, bufferSource);
            } else {
               frameRenderer.renderSingle(poseStack, packedLight, blockPos, be, bufferSource);
            }

            poseStack.popPose();
         } else {
            BakedModel frameModel = model.getPart(staticFramePart);
            if (frameModel != null) {
               poseStack.pushPose();

               try {
                  String fallbackKey = "door_" + doorType + "_" + getSelectionCacheKey(selection) + "_" + staticFramePart;
                  SingleMeshVboRenderer fallbackRenderer = MeshRenderCache.getOrCreateRenderer(fallbackKey, frameModel);
                  if (fallbackRenderer != null) {
                     fallbackRenderer.render(poseStack, packedLight, blockPos, be, bufferSource);
                  }
               } catch (Exception e) {
                  MainRegistry.LOGGER.debug("DoorRenderer: Fallback frame render failed: {}", e.getMessage());
               }

               poseStack.popPose();
            }
         }
      }

      for (String partName : partNames) {
         if (staticFramePart == null || !staticFramePart.equals(partName)) {
            this.renderHierarchyVbo(partName, false, be, model, doorDecl, openTicks, poseStack, packedLight, blockPos, bufferSource);
         }
      }
   }

   private void renderHierarchyVbo(
      String partName,
      boolean child,
      DoorBlockEntity be,
      DoorBakedModel model,
      DoorDecl doorDecl,
      float openTicks,
      PoseStack poseStack,
      int packedLight,
      BlockPos blockPos,
      MultiBufferSource bufferSource
   ) {
      if (doorDecl.doesRender(partName, child)) {
         DoorModelSelection selection = be.getModelSelection();
         poseStack.pushPose();
         this.doPartTransform(poseStack, doorDecl, partName, openTicks, child, selection);
         boolean isStaticPart = "frame".equalsIgnoreCase(partName)
            || "Frame".equals(partName)
            || "DoorFrame".equals(partName)
            || "base".equalsIgnoreCase(partName);
         if (!isStaticPart) {
            BakedModel partModel = model.getPart(partName);
            String doorType = this.getDoorTypeKey(doorDecl);
            String geomCacheKey = "geom_" + doorType + "_" + getSelectionCacheKey(selection) + "_" + partName;
            if (partModel != null && partHasGeometry(partModel, partName, geomCacheKey)) {
               String partCacheKey = getPartCacheKey(doorType, partName, selection);
               boolean useBatching = ModClothConfig.useInstancedBatching();
               boolean inShadowPass = ShaderCompatibilityDetector.isRenderingShadowPass();
               if (useBatching && !inShadowPass) {
                  if (!partInitializationFlags.getOrDefault(partCacheKey, false)) {
                     this.initializePartInstancerFor(model, doorType, partName, selection, partCacheKey);
                  }

                  InstancedStaticPartRenderer partRenderer = instancedPartCache.get(partCacheKey);
                  if (partRenderer != null && partRenderer.isInitialized()) {
                     partRenderer.addInstance(poseStack, packedLight, blockPos, be, bufferSource);
                  } else {
                     this.renderPartViaDoorVbo(
                        model, partName, doorType, selection, poseStack, packedLight, blockPos, be, doorDecl, openTicks, child, bufferSource
                     );
                  }
               } else {
                  this.renderPartViaDoorVbo(
                     model, partName, doorType, selection, poseStack, packedLight, blockPos, be, doorDecl, openTicks, child, bufferSource
                  );
               }
            }
         }

         for (String c : doorDecl.getChildren(partName, selection)) {
            this.renderHierarchyVbo(c, true, be, model, doorDecl, openTicks, poseStack, packedLight, blockPos, bufferSource);
         }

         poseStack.popPose();
      }
   }

   private String getDoorTypeKey(DoorDecl doorDecl) {
      if (doorDecl == DoorDecl.LARGE_VEHICLE_DOOR) {
         return "large_vehicle_door";
      } else if (doorDecl == DoorDecl.ROUND_AIRLOCK_DOOR) {
         return "round_airlock_door";
      } else if (doorDecl == DoorDecl.FIRE_DOOR) {
         return "fire_door";
      } else if (doorDecl == DoorDecl.SLIDING_SEAL_DOOR) {
         return "sliding_seal_door";
      } else if (doorDecl == DoorDecl.SECURE_ACCESS_DOOR) {
         return "secure_access_door";
      } else if (doorDecl == DoorDecl.QE_SLIDING) {
         return "qe_sliding_door";
      } else if (doorDecl == DoorDecl.QE_CONTAINMENT) {
         return "qe_containment_door";
      } else if (doorDecl == DoorDecl.WATER_DOOR) {
         return "water_door";
      } else if (doorDecl == DoorDecl.SILO_HATCH) {
         return "silo_hatch";
      } else if (doorDecl == DoorDecl.SILO_HATCH_LARGE) {
         return "silo_hatch_large";
      } else if (doorDecl == DoorDecl.VAULT_DOOR) {
         return "vault_door";
      } else if (doorDecl == DoorDecl.CARGO_DOOR) {
         return "cargo_door";
      } else if (doorDecl.getBlockId().getPath().equals("sliding_blast_door")) {
         return "sliding_blast_door";
      } else {
         throw new IllegalStateException("Unknown door type: " + doorDecl.getClass().getName());
      }
   }

   private void renderPartViaDoorVbo(
      DoorBakedModel model,
      String partName,
      String doorType,
      DoorModelSelection selection,
      PoseStack poseStack,
      int packedLight,
      BlockPos blockPos,
      DoorBlockEntity be,
      DoorDecl doorDecl,
      float openTicks,
      boolean child,
      MultiBufferSource bufferSource
   ) {
      try {
         DoorVboRenderer partRenderer = DoorVboRenderer.getOrCreate(model, partName, doorType, selection);
         partRenderer.renderPart(poseStack, packedLight, blockPos, be, doorDecl, openTicks, child, bufferSource);
      } catch (IllegalStateException e) {
         MainRegistry.LOGGER.debug("No mesh for part {}, skipping render: {}", partName, e.getMessage());
      }
   }

   private String detectFramePart(String[] partNames) {
      for (String p : partNames) {
         if ("frame".equals(p)) {
            return "frame";
         }
      }

      for (String p : partNames) {
         if ("Frame".equals(p)) {
            return "Frame";
         }
      }

      for (String p : partNames) {
         if ("DoorFrame".equals(p)) {
            return "DoorFrame";
         }
      }

      for (String p : partNames) {
         if ("base".equals(p)) {
            return "base";
         }
      }

      for (String p : partNames) {
         if ("Base".equals(p)) {
            return "Base";
         }
      }

      return null;
   }

   private void doPartTransform(PoseStack poseStack, DoorDecl doorDecl, String partName, float openTicks, boolean child, DoorModelSelection selection) {
      doorDecl.getOrigin(partName, this.origin, selection);
      doorDecl.getRotation(partName, openTicks, this.rotation, selection);
      poseStack.translate(this.origin[0], this.origin[1], this.origin[2]);
      if (this.rotation[0] != 0.0F) {
         poseStack.mulPose(Axis.XP.rotationDegrees(this.rotation[0]));
      }

      if (this.rotation[1] != 0.0F) {
         poseStack.mulPose(Axis.YP.rotationDegrees(this.rotation[1]));
      }

      if (this.rotation[2] != 0.0F) {
         poseStack.mulPose(Axis.ZP.rotationDegrees(this.rotation[2]));
      }

      doorDecl.getTranslation(partName, openTicks, child, this.translation, selection);
      poseStack.translate(-this.origin[0] + this.translation[0], -this.origin[1] + this.translation[1], -this.origin[2] + this.translation[2]);
   }

   public boolean shouldRenderOffScreen(DoorBlockEntity be) {
      return !ShaderCompatibilityDetector.isRenderingShadowPass();
   }

   public static void flushInstancedBatches(Matrix4f projectionMatrix) {
      ArrayList<String> frameKeys = new ArrayList<>(instancedFrameCache.keySet());
      Collections.sort(frameKeys);

      for (String key : frameKeys) {
         InstancedStaticPartRenderer renderer = instancedFrameCache.get(key);
         if (renderer != null) {
            renderer.flush(projectionMatrix);
         }
      }

      ArrayList<String> partKeys = new ArrayList<>(instancedPartCache.keySet());
      Collections.sort(partKeys);

      for (String key : partKeys) {
         InstancedStaticPartRenderer renderer = instancedPartCache.get(key);
         if (renderer != null) {
            renderer.flush(projectionMatrix);
         }
      }
   }

   public int getViewDistance() {
      return RenderDistanceHelper.getStaticViewDistanceBlocks();
   }

   public void onResourceManagerReload() {
      clearAllCaches();
   }

   public static void clearAllCaches() {
      for (InstancedStaticPartRenderer renderer : instancedFrameCache.values()) {
         if (renderer != null) {
            renderer.cleanup();
         }
      }

      instancedFrameCache.clear();
      frameInitializationFlags.clear();

      for (InstancedStaticPartRenderer renderer : instancedPartCache.values()) {
         if (renderer != null) {
            renderer.cleanup();
         }
      }

      instancedPartCache.clear();
      partInitializationFlags.clear();
      DoorVboRenderer.clearCache();
      DAE_RENDERER_CACHE.values().forEach(SingleMeshVboRenderer::cleanup);
      DAE_RENDERER_CACHE.clear();
      DAE_MODELS_CACHE.clear();
      PARTS_WITHOUT_GEOMETRY.clear();
      MainRegistry.LOGGER.debug("Door renderer caches cleared");
   }
}
