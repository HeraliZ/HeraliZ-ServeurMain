package com.hbm_m.client.render.culling;

import com.hbm_m.client.render.ClientRenderFlags;
import com.hbm_m.client.render.LightSampleCache;
import com.hbm_m.client.render.MdiBatchCoordinator;
import com.hbm_m.client.render.RenderFrameLight;
import com.hbm_m.client.render.implementations.DoorRenderer;
import com.hbm_m.client.render.shader.IrisExtendedShaderAccess;
import com.hbm_m.client.render.shader.IrisRenderBatch;
import com.hbm_m.config.ModClothConfig;
import com.hbm_m.main.MainRegistry;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public final class InstancedRenderFrame {
   private InstancedRenderFrame() {
   }

   public static void onBeforeBlockEntities(Matrix4f projection, Vec3 cameraPos, @Nullable Frustum blockEntityFrustum) {
      if (projection != null) {
         RenderFrameLight.onFrameStart();
         ClientRenderFlags.onFrameStart();
         IrisExtendedShaderAccess.tickPass();
         if (ClientRenderFlags.enableOcclusionCulling()) {
            OcclusionCullingHelper.onFrameStart();
            OcclusionCullingHelper.captureBlockEntityPassFrustum(blockEntityFrustum);
            OcclusionCullingHelper.captureCpuFrustumFallback(projection, cameraPos);
         } else {
            OcclusionCullingHelper.captureBlockEntityPassFrustum(null);
         }
      }
   }

   public static void presentAfterBlockEntities(Matrix4f projection, Vec3 cameraPos) {
      if (RenderSystem.isOnRenderThread() && projection != null) {
         IrisRenderBatch.closePersistentIfActive();

         try {
            if (ClientRenderFlags.useInstancedBatching()) {
               ModClothConfig cfg = ModClothConfig.get();
               boolean useMdi = cfg.useMultiDrawIndirect && MdiBatchCoordinator.isMdiAvailable();
               RenderFrameLight.ensureLightTextureUpdated();
               if (useMdi) {
                  MdiBatchCoordinator coord = MdiBatchCoordinator.beginFrame(projection);
                  flushAllInstanced(projection);
                  if (coord != null) {
                     coord.endFrame(false);
                  }
               } else {
                  flushAllInstanced(projection);
               }

               LightSampleCache.onFrameStart();
               MdiRenderFrameGate.advanceAfterPresent();
            }
         } catch (Throwable t) {
            MainRegistry.LOGGER.error("[HBM-M] instanced present failed", t);
            MdiBatchCoordinator.discardActiveSessionNoDispatch();
         }
      } else {
      }
   }

   public static void onRenderSliceEnd() {
      IrisRenderBatch.closePersistentIfActive();
   }

   @Deprecated
   public static void onBeforeBlockEntitySlices(Matrix4f projection, Vec3 cameraPos, @Nullable Frustum blockEntityFrustum) {
      onBeforeBlockEntities(projection, cameraPos, blockEntityFrustum);
   }

   @Deprecated
   public static void accumulateSliceInstances(Matrix4f projection, Vec3 cameraPos) {
      presentAfterBlockEntities(projection, cameraPos);
   }

   @Deprecated
   public static void onRenderFrameBegin(float partialTick, Matrix4f projection, Vec3 cameraPos) {
      onBeforeBlockEntities(projection, cameraPos, null);
   }

   @Deprecated
   public static void flushDeferredPresent(Matrix4f projection, Vec3 cameraPos) {
      onRenderSliceEnd();
   }

   public static void flushPendingNow(Matrix4f projection, Vec3 cameraPos) {
      presentAfterBlockEntities(projection, cameraPos);
   }

   private static void flushAllInstanced(Matrix4f projection) {
      DoorRenderer.flushInstancedBatches(projection);
   }

   public static void clear() {
      MdiRenderFrameGate.reset();
      InstancedRenderStats.clear();
      MdiBatchCoordinator.cancelScheduledDraw();
      MdiBatchCoordinator.discardActiveSessionNoDispatch();
      MdiBatchCoordinator.clearCachedRedraw();
   }
}
