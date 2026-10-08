package com.hbm_m.client.render.implementations;

import com.hbm_m.block.entity.doors.DoorDecl;
import com.hbm_m.client.model.DoorBakedModel;
import com.hbm_m.block.entity.doors.variant.DoorModelSelection;
import com.hbm_m.client.render.MeshRenderCache;
import com.hbm_m.client.render.ObjModelVboBuilder;
import com.hbm_m.client.render.SingleMeshVboRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class DoorVboRenderer extends SingleMeshVboRenderer {
   private final DoorBakedModel model;
   private final String partName;
   private final String quadCacheKey;
   private static final ConcurrentHashMap<String, DoorVboRenderer> partRenderers = new ConcurrentHashMap<>();
   private static final ThreadLocal<float[]> translationBuffer = ThreadLocal.withInitial(() -> new float[3]);
   private static final ThreadLocal<float[]> originBuffer = ThreadLocal.withInitial(() -> new float[3]);
   private static final ThreadLocal<float[]> rotationBuffer = ThreadLocal.withInitial(() -> new float[3]);

   private DoorVboRenderer(DoorBakedModel model, String partName, String quadCacheKey) {
      this.model = model;
      this.partName = partName;
      this.quadCacheKey = quadCacheKey;
   }

   public static DoorVboRenderer getOrCreate(DoorBakedModel model, String partName, String doorType) {
      if (isStaticPart(partName)) {
         throw new IllegalArgumentException("Static part '" + partName + "' should use InstancedStaticPartRenderer, not DoorVboRenderer!");
      }

      String key = "door_" + doorType + "_" + partName;
      return partRenderers.computeIfAbsent(key, k -> new DoorVboRenderer(model, partName, key));
   }

   public static DoorVboRenderer getOrCreate(DoorBakedModel model, String partName, String doorType, DoorModelSelection selection) {
      if (isStaticPart(partName)) {
         throw new IllegalArgumentException("Static part '" + partName + "' should use InstancedStaticPartRenderer!");
      }

      String key = "door_" + doorType + "_" + selection.getModelType().getId() + "_" + selection.getSkin().getId() + "_" + partName;
      return partRenderers.computeIfAbsent(key, k -> new DoorVboRenderer(model, partName, key));
   }

   public static boolean isStaticPart(String partName) {
      return "frame".equalsIgnoreCase(partName) || "DoorFrame".equals(partName) || "Base".equals(partName);
   }

   public static DoorVboRenderer getOrCreate(DoorBakedModel model, String partName) {
      return getOrCreate(model, partName, "unknown");
   }

   @Override
   protected SingleMeshVboRenderer.VboData buildVboData() {
      BakedModel partModel = this.model.getPart(this.partName);
      if (partModel == null) {
         throw new IllegalStateException("Part model not found: " + this.partName);
      } else {
         SingleMeshVboRenderer.VboData vboData = ObjModelVboBuilder.buildSinglePart(partModel);
         if (vboData == null) {
            throw new IllegalStateException("No geometry found in part: " + this.partName);
         } else {
            return vboData;
         }
      }
   }

   @Override
   protected List<BakedQuad> getQuadsForIrisPath() {
      BakedModel partModel = this.model.getPart(this.partName);
      return partModel == null ? null : MeshRenderCache.getOrCompile(this.quadCacheKey, partModel);
   }

   public void renderPart(
      PoseStack poseStack,
      int packedLight,
      BlockPos blockPos,
      @Nullable BlockEntity blockEntity,
      DoorDecl doorDecl,
      float openTicks,
      boolean child,
      @Nullable MultiBufferSource bufferSource
   ) {
      if (doorDecl.doesRender(this.partName, child)) {
         super.render(poseStack, packedLight, blockPos, blockEntity, bufferSource);
      }
   }

   public static void clearCache() {
      partRenderers.values().forEach(DoorVboRenderer::cleanup);
      partRenderers.clear();
   }

   @Override
   public void cleanup() {
      super.cleanup();
      translationBuffer.remove();
      originBuffer.remove();
      rotationBuffer.remove();
   }
}
