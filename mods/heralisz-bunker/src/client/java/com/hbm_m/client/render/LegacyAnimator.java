package com.hbm_m.client.render;

import com.hbm_m.interfaces.IDoorAnimator;
import com.hbm_m.util.MultipartFacingTransforms;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class LegacyAnimator implements IDoorAnimator {
   protected final PoseStack poseStack;
   final VertexConsumer buffer;
   private final int packedLight;
   private final int packedOverlay;

   public LegacyAnimator(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay) {
      this.poseStack = poseStack;
      this.buffer = buffer;
      this.packedLight = packedLight;
      this.packedOverlay = packedOverlay;
   }

   public static LegacyAnimator create(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
      VertexConsumer buffer = bufferSource.getBuffer(RenderType.cutout());
      return new LegacyAnimator(poseStack, buffer, packedLight, packedOverlay);
   }

   public void push() {
      this.poseStack.pushPose();
   }

   public void pop() {
      this.poseStack.popPose();
   }

   @Override
   public void translate(double x, double y, double z) {
      this.poseStack.translate(x, y, z);
   }

   @Override
   public void rotate(float degrees, float x, float y, float z) {
      if (degrees != 0.0F) {
         if (x != 0.0F) {
            this.poseStack.mulPose(Axis.XP.rotationDegrees(degrees));
         }

         if (y != 0.0F) {
            this.poseStack.mulPose(Axis.YP.rotationDegrees(degrees));
         }

         if (z != 0.0F) {
            this.poseStack.mulPose(Axis.ZP.rotationDegrees(degrees));
         }
      }
   }

   public void setupBlockTransform(Direction facing) {
      this.translate(0.5, 0.0, 0.5);
      this.rotate(90.0F, 0.0F, 1.0F, 0.0F);
      this.rotate(MultipartFacingTransforms.legacyFacingRotationYDegrees(facing), 0.0F, 1.0F, 0.0F);
   }

   public void setupChemicalPlantBlockTransform(Direction facing) {
      this.translate(0.5, 0.0, 0.5);
      this.rotate(MultipartFacingTransforms.chemicalPlantPoseRotationY(facing), 0.0F, 1.0F, 0.0F);
   }

   public void renderQuads(List<BakedQuad> quads) {
      if (quads != null && !quads.isEmpty()) {
         Pose pose = this.poseStack.last();

         for (BakedQuad quad : quads) {
            this.buffer.putBulkData(pose, quad, 1.0F, 1.0F, 1.0F, 1.0F, this.packedLight, this.packedOverlay, false);
         }
      }
   }

   @Deprecated
   public void renderPart(BakedModel modelPart) {
      if (modelPart != null) {
         String tempKey = "Temp:" + System.identityHashCode(modelPart);
         List<BakedQuad> quads = MeshRenderCache.getOrCompile(tempKey, modelPart);
         this.renderQuads(quads);
      }
   }

   public Matrix4f currentMatrix() {
      return new Matrix4f(this.poseStack.last().pose());
   }
}
