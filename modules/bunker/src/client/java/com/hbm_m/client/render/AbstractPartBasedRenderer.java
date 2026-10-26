package com.hbm_m.client.render;

import com.hbm_m.client.render.shader.ShaderCompatibilityDetector;
import com.hbm_m.compat.ContraptionRenderCompat;
import com.hbm_m.main.MainRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public abstract class AbstractPartBasedRenderer<T extends BlockEntity, M extends BakedModel> implements BlockEntityRenderer<T> {
   private final Matrix4f currentModelViewMatrix = new Matrix4f();
   private static Field fabricWrappedField;
   private static boolean fabricWrappedFieldChecked = false;

   protected BakedModel getModel(T blockEntity) {
      return Minecraft.getInstance().getBlockRenderer().getBlockModel(blockEntity.getBlockState());
   }

   protected abstract M getModelType(BakedModel var1);

   protected abstract Direction getFacing(T var1);

   protected abstract void renderParts(T var1, M var2, LegacyAnimator var3, float var4, int var5, int var6, PoseStack var7, MultiBufferSource var8);

   protected void setupBlockTransform(LegacyAnimator animator, T blockEntity) {
      animator.setupBlockTransform(this.getFacing(blockEntity));
   }

   public Matrix4f getCurrentModelViewMatrix() {
      return new Matrix4f(this.currentModelViewMatrix);
   }

   public boolean shouldRenderOffScreen(T blockEntity) {
      return ShaderCompatibilityDetector.shouldRenderBlockEntityOffScreen();
   }

   public void render(T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
      if (ShaderCompatibilityDetector.isRenderingShadowPass() || this.isInViewFrustum(blockEntity)) {
         this.currentModelViewMatrix.set(poseStack.last().pose());
         BakedModel rawModel = this.getModel(blockEntity);
         rawModel = unwrapFabricForwardingModels(rawModel);
         M model = this.getModelType(rawModel);
         if (model != null) {
            LegacyAnimator animator = LegacyAnimator.create(poseStack, bufferSource, packedLight, packedOverlay);
            LightSampleCache.BASE_POSE.get().set(poseStack.last().pose());
            LightSampleCache.BASE_POSE_SET.set(true);
            poseStack.pushPose();

            try {
               this.setupBlockTransform(animator, blockEntity);
               this.renderParts(blockEntity, model, animator, partialTick, packedLight, packedOverlay, poseStack, bufferSource);
            } finally {
               poseStack.popPose();
               LightSampleCache.BASE_POSE_SET.set(false);
            }
         }
      }
   }

   protected final Minecraft getMinecraft() {
      return Minecraft.getInstance();
   }

   protected boolean isInViewFrustum(T blockEntity) {
      if (ContraptionRenderCompat.isContraptionRender(blockEntity)) {
         return true;
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.levelRenderer != null) {
         Frustum frustum = mc.levelRenderer.getFrustum();
         if (frustum == null) {
            return true;
         }

         AABB box = frustumCullBounds(blockEntity);
         return frustum.isVisible(box);
      } else {
         return true;
      }
   }

   private static AABB frustumCullBounds(BlockEntity blockEntity) {
      return blockEntity.getRenderBoundingBox();
   }

   public static BakedModel unwrapFabricForwardingModels(BakedModel model) {
      if (model == null) {
         return null;
      }

      if (!fabricWrappedFieldChecked) {
         fabricWrappedFieldChecked = true;

         try {
            Class<?> cls = Class.forName("net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel");
            Field f = cls.getDeclaredField("wrapped");
            f.setAccessible(true);
            fabricWrappedField = f;
         } catch (ClassNotFoundException var5) {
         } catch (Exception e) {
            MainRegistry.LOGGER.warn("[HBM] Не удалось получить поле ForwardingBakedModel.wrapped: {}", e.toString());
         }
      }

      if (fabricWrappedField == null) {
         return model;
      }

      int depth = 0;

      while (depth++ < 8) {
         Class<?> cls = model.getClass();
         boolean isFrapi = false;

         while (cls != null && cls != Object.class) {
            if (cls.getName().equals("net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel")) {
               isFrapi = true;
               break;
            }

            cls = cls.getSuperclass();
         }

         if (!isFrapi) {
            break;
         }

         try {
            BakedModel inner = (BakedModel)fabricWrappedField.get(model);
            if (inner == null || inner == model) {
               break;
            }

            if (depth == 1) {
               MainRegistry.LOGGER.debug("[HBM] Разворачиваем {} → {}", model.getClass().getSimpleName(), inner.getClass().getSimpleName());
            }

            model = inner;
         } catch (Exception e) {
            break;
         }
      }

      return model;
   }
}
