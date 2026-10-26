package com.hbm_m.client.render;

import com.hbm_m.client.render.shader.ShaderCompatibilityDetector;
import com.hbm_m.config.ModClothConfig;
import com.hbm_m.main.MainRegistry;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.lwjgl.opengl.ARBDrawIndirect;
import org.lwjgl.opengl.ARBMultiDrawIndirect;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;

@OnlyIn(Dist.CLIENT)
public final class MdiBatchCoordinator {
   static final int INDIRECT_CMD_PACKED_BYTES = 20;
   static final int INDIRECT_CMD_STRIDE_BYTES = 32;
   private static volatile boolean capsResolved = false;
   private static volatile boolean hasDrawIndirect = false;
   private static volatile boolean hasBaseInstance = false;
   private static volatile boolean loggedOnce = false;
   private static final ThreadLocal<MdiBatchCoordinator> ACTIVE = new ThreadLocal<>();
   private static volatile MdiBatchCoordinator.DeferredDraw scheduledDraw;
   private static volatile MdiBatchCoordinator.PreparedMdi cachedRedraw;
   private static volatile long cachedRedrawGameTime = -1L;
   private final Matrix4f projectionMatrix;
   private final List<MdiBatchCoordinator.Pending> pending = new ArrayList<>(16);
   private int totalInstances = 0;

   public void refreshProjection(Matrix4f projection) {
      if (projection != null) {
         this.projectionMatrix.set(projection);
      }
   }

   private MdiBatchCoordinator(Matrix4f projectionMatrix) {
      this.projectionMatrix = new Matrix4f(projectionMatrix);
   }

   public static void ensureCapsResolved() {
      if (!capsResolved) {
         synchronized (MdiBatchCoordinator.class) {
            if (!capsResolved) {
               try {
                  GLCapabilities caps = GL.getCapabilities();
                  if (caps != null) {
                     boolean multi = caps.glMultiDrawElementsIndirect != 0L || caps.GL_ARB_multi_draw_indirect;
                     boolean single = caps.glDrawElementsIndirect != 0L || caps.GL_ARB_draw_indirect;
                     hasDrawIndirect = multi || single;
                     hasBaseInstance = caps.glDrawElementsInstancedBaseVertexBaseInstance != 0L;
                  } else {
                     hasDrawIndirect = false;
                     hasBaseInstance = false;
                  }
               } catch (Throwable t) {
                  hasDrawIndirect = false;
                  hasBaseInstance = false;
               }

               capsResolved = true;
            }
         }
      }
   }

   public static boolean isMdiAvailable() {
      ensureCapsResolved();
      boolean ok = hasDrawIndirect && hasBaseInstance;
      if (!loggedOnce) {
         loggedOnce = true;

         try {
            String vendor = GL11.glGetString(7936);
            String renderer = GL11.glGetString(7937);
            String version = GL11.glGetString(7938);
            if (ok) {
               MainRegistry.LOGGER
                  .info(
                     "[HBM-M MDI] Draw indirect + base_instance available (atlas batch path). GL_VENDOR='{}', GL_RENDERER='{}', GL_VERSION='{}'",
                     new Object[]{vendor, renderer, version}
                  );
            } else {
               MainRegistry.LOGGER
                  .info(
                     "[HBM-M MDI] Draw indirect/base_instance NOT available (hasDrawIndirect={}, hasBaseInstance={}) — vanilla instanced path. GL_VENDOR='{}', GL_RENDERER='{}', GL_VERSION='{}'",
                     new Object[]{hasDrawIndirect, hasBaseInstance, vendor, renderer, version}
                  );
            }
         } catch (Throwable var4) {
         }
      }

      return ok;
   }

   public static MdiBatchCoordinator beginFrame(Matrix4f projectionMatrix) {
      if (ShaderCompatibilityDetector.isExternalShaderActive()) {
         return null;
      }

      ModClothConfig cfg;
      try {
         cfg = ModClothConfig.get();
      } catch (Throwable t) {
         return null;
      }

      if (cfg == null || !cfg.useMultiDrawIndirect) {
         return null;
      }

      if (!isMdiAvailable()) {
         return null;
      }

      MdiBatchCoordinator session = new MdiBatchCoordinator(projectionMatrix);
      ACTIVE.set(session);
      MdiRenderDiag.logBannerOnce();
      return session;
   }

   public static MdiBatchCoordinator active() {
      return ACTIVE.get();
   }

   public static void discardActiveSessionNoDispatch() {
      cancelScheduledDraw();
      MdiBatchCoordinator s = ACTIVE.get();
      if (s != null) {
         for (MdiBatchCoordinator.Pending p : s.pending) {
            if (p.instanceDataNativeOwned && p.instanceData != null) {
               MemoryUtil.memFree(p.instanceData);
               p.instanceData = null;
               p.instanceDataNativeOwned = false;
            }
         }

         s.pending.clear();
         s.totalInstances = 0;
         ACTIVE.remove();
      }
   }

   public void endFrame() {
      this.endFrame(false);
   }

   public void endFrame(boolean deferDraw) {
      try {
         if (deferDraw) {
            this.scheduleDeferredDraw();
         } else {
            this.dispatch();
         }
      } catch (Throwable t) {
         MainRegistry.LOGGER.error("[HBM-M MDI] dispatch failed; future flushes will use legacy path", t);
      } finally {
         for (MdiBatchCoordinator.Pending p : this.pending) {
            if (p.instanceDataNativeOwned && p.instanceData != null) {
               MemoryUtil.memFree(p.instanceData);
               p.instanceData = null;
               p.instanceDataNativeOwned = false;
            }
         }

         this.pending.clear();
         this.totalInstances = 0;
         if (ACTIVE.get() == this) {
            ACTIVE.remove();
         }
      }
   }

   public static void presentScheduledDraw(Matrix4f projection) {
      MdiBatchCoordinator.DeferredDraw draw = scheduledDraw;
      if (draw != null) {
         scheduledDraw = null;

         try {
            executeDeferredDraw(draw, projection);
         } catch (Throwable t) {
            MainRegistry.LOGGER.error("[HBM-M MDI] deferred present failed", t);
         } finally {
            draw.free();
         }
      }
   }

   public static void cancelScheduledDraw() {
      MdiBatchCoordinator.DeferredDraw draw = scheduledDraw;
      scheduledDraw = null;
      if (draw != null) {
         draw.free();
      }
   }

   public static void clearCachedRedraw() {
      cachedRedraw = null;
      cachedRedrawGameTime = -1L;
   }

   public static void redrawCachedIfAny(Matrix4f projection) {
      if (!ShaderCompatibilityDetector.isExternalShaderActive()) {
         MdiBatchCoordinator.PreparedMdi snap = cachedRedraw;
         if (snap != null && !snap.drawList.isEmpty() && projection != null) {
            try {
               MdiGeometryAtlas atlas = MdiGeometryAtlas.getOrCreate();
               if (atlas != null && atlas.isReady()) {
                  refreshDrawListAtlasSlots(snap.drawList, atlas);
               }

               if (snap.drawList.isEmpty()) {
                  return;
               }

               executeMdiGlDraw(snap, new Matrix4f(projection), cachedRedrawGameTime);
            } catch (Throwable t) {
               MainRegistry.LOGGER.error("[HBM-M MDI] cached redraw failed", t);
            }
         }
      }
   }

   private static void publishCachedRedraw(MdiBatchCoordinator.PreparedMdi prepared, long gameTime) {
      float minFade = 1.0F;
      MdiGeometryAtlas atlas = MdiGeometryAtlas.getOrCreate();
      if (atlas != null && atlas.isReady()) {
         int instanceFloatsPerInstance = atlas.getInstanceFloatsPerInstance();
         int fadeOffset = atlas.getInstanceFadeFloatOffset();
         if (instanceFloatsPerInstance > 0 && fadeOffset >= 0) {
            for (MdiBatchCoordinator.Pending p : prepared.drawList) {
               FloatBuffer buf = p.instanceData;
               if (buf != null) {
                  for (int i = 0; i < p.instanceCount; i++) {
                     float fa = buf.get(i * instanceFloatsPerInstance + fadeOffset);
                     if (fa < minFade) {
                        minFade = fa;
                     }
                  }
               }
            }
         }
      }

      List<MdiBatchCoordinator.Pending> snap = new ArrayList<>(prepared.drawList.size());

      for (MdiBatchCoordinator.Pending p : prepared.drawList) {
         MdiBatchCoordinator.Pending q = new MdiBatchCoordinator.Pending(p.renderer);
         q.baseVertex = p.baseVertex;
         q.firstIndexBytes = p.firstIndexBytes;
         q.indexCount = p.indexCount;
         q.baseInstance = p.baseInstance;
         q.instanceCount = p.instanceCount;
         snap.add(q);
      }

      cachedRedraw = new MdiBatchCoordinator.PreparedMdi(snap, prepared.drawTotalInstances, prepared.pendingSize, prepared.droppedNoSlot, minFade);
      cachedRedrawGameTime = gameTime;
   }

   private void scheduleDeferredDraw() {
      MdiBatchCoordinator.PreparedMdi prepared = this.prepareMdiDraw(false);
      if (prepared != null) {
         long gameTime = -1L;

         try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.level != null) {
               gameTime = mc.level.getGameTime();
            }
         } catch (Throwable var6) {
         }

         MdiBatchCoordinator.DeferredDraw next = new MdiBatchCoordinator.DeferredDraw(
            prepared.drawList, prepared.drawTotalInstances, prepared.pendingSize, prepared.droppedNoSlot, gameTime, this.projectionMatrix
         );
         MdiBatchCoordinator.DeferredDraw prev = scheduledDraw;
         scheduledDraw = next;
         if (prev != null) {
            prev.free();
         }
      }
   }

   private static void freeDrawListInstanceBuffers(List<MdiBatchCoordinator.Pending> drawList) {
      for (MdiBatchCoordinator.Pending p : drawList) {
         if (p.instanceDataNativeOwned && p.instanceData != null) {
            MemoryUtil.memFree(p.instanceData);
            p.instanceData = null;
            p.instanceDataNativeOwned = false;
         }
      }
   }

   public boolean submit(
      InstancedStaticPartRenderer renderer,
      int indexCount,
      int instanceCount,
      int instanceDataSize,
      FloatBuffer instanceDataFlipped,
      int[] sourceCullIndices,
      long[] sourceOcclusionKeys,
      ByteBuffer atlasVertexBytes,
      IntBuffer atlasIndices,
      int atlasIndexCount
   ) {
      if (renderer != null && instanceCount > 0 && indexCount > 0) {
         MdiGeometryAtlas atlas = MdiGeometryAtlas.getOrCreate();
         if (!atlas.acceptsInstanceDataSize(instanceDataSize)) {
            return false;
         }

         MdiGeometryAtlas.Slot slot = atlas.registerGeometryIfAbsent(renderer, atlasVertexBytes, atlasIndices, atlasIndexCount);
         if (slot == null) {
            return false;
         }

         int instanceFloats = instanceCount * instanceDataSize;
         FloatBuffer instanceSnapshot = MemoryUtil.memAllocFloat(instanceFloats);

         try {
            FloatBuffer srcView = instanceDataFlipped.duplicate();
            if (srcView.remaining() < instanceFloats) {
               MemoryUtil.memFree(instanceSnapshot);
               return false;
            }

            int srcStart = srcView.position();
            srcView.limit(srcStart + instanceFloats);
            instanceSnapshot.put(srcView);
            instanceSnapshot.flip();
         } catch (Throwable t) {
            MemoryUtil.memFree(instanceSnapshot);
            MainRegistry.LOGGER.warn("[HBM-M MDI] instance snapshot alloc/copy failed: {}", t.toString());
            return false;
         }

         MdiBatchCoordinator.Pending p = new MdiBatchCoordinator.Pending(renderer);
         p.baseVertex = slot.baseVertex;
         p.firstIndexBytes = slot.firstIndexBytes;
         p.indexCount = slot.indexCount;
         p.submitBaseVertex = slot.baseVertex;
         p.submitFirstIndexBytes = slot.firstIndexBytes;
         p.submitIndexCount = slot.indexCount;
         p.baseInstance = this.totalInstances;
         p.instanceCount = instanceCount;
         if (sourceCullIndices != null && sourceCullIndices.length >= instanceCount) {
            p.instanceCullIndices = new int[instanceCount];
            System.arraycopy(sourceCullIndices, 0, p.instanceCullIndices, 0, instanceCount);
         } else {
            p.instanceCullIndices = null;
         }

         if (sourceOcclusionKeys != null && sourceOcclusionKeys.length >= instanceCount) {
            p.instanceOcclusionKeys = new long[instanceCount];
            System.arraycopy(sourceOcclusionKeys, 0, p.instanceOcclusionKeys, 0, instanceCount);
         } else {
            p.instanceOcclusionKeys = null;
         }

         p.instanceData = instanceSnapshot;
         p.instanceDataNativeOwned = true;
         this.pending.add(p);
         this.totalInstances += instanceCount;
         return true;
      } else {
         return false;
      }
   }

   private void dispatch() {
      MdiBatchCoordinator.PreparedMdi prepared = this.prepareMdiDraw(true);
      if (prepared != null) {
         long gameTime = resolveLevelGameTime();

         try {
            executeMdiGlDraw(prepared, this.projectionMatrix, gameTime);
         } finally {
            freeDrawListInstanceBuffers(prepared.drawList);
         }
      }
   }

   private static long resolveLevelGameTime() {
      try {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.level != null) {
            return mc.level.getGameTime();
         }
      } catch (Throwable var1) {
      }

      return -1L;
   }

   private static void executeDeferredDraw(MdiBatchCoordinator.DeferredDraw draw, Matrix4f projectionOverride) {
      MdiGeometryAtlas atlas = MdiGeometryAtlas.getOrCreate();
      if (atlas != null && atlas.isReady()) {
         refreshDrawListAtlasSlots(draw.drawList, atlas);
         if (!draw.drawList.isEmpty()) {
            if (uploadInstancesToAtlas(draw.drawList, atlas)) {
               Matrix4f proj = projectionOverride != null ? new Matrix4f(projectionOverride) : new Matrix4f(draw.projectionMatrix);
               MdiBatchCoordinator.PreparedMdi prepared = new MdiBatchCoordinator.PreparedMdi(
                  draw.drawList, draw.drawTotalInstances, draw.pendingSize, draw.droppedNoSlot, 1.0F
               );
               executeMdiGlDraw(prepared, proj, draw.gameTime);
            }
         }
      }
   }

   private MdiBatchCoordinator.PreparedMdi prepareMdiDraw(boolean uploadInstances) {
      if (this.pending.isEmpty()) {
         return null;
      }

      this.coalescePendingByRenderer();
      MdiGeometryAtlas atlas = MdiGeometryAtlas.getOrCreate();
      if (atlas != null && atlas.isReady()) {
         int pendingSize = this.pending.size();
         List<MdiBatchCoordinator.Pending> drawList = new ArrayList<>(pendingSize);

         for (MdiBatchCoordinator.Pending p : this.pending) {
            MdiGeometryAtlas.Slot slot = atlas.getCurrentSlot(p.renderer);
            if (slot == null) {
               MainRegistry.LOGGER
                  .warn("[HBM-M MDI] Atlas slot missing for renderer {}; dropping this MDI sub-draw (others still draw)", System.identityHashCode(p.renderer));
               if (p.instanceDataNativeOwned && p.instanceData != null) {
                  MemoryUtil.memFree(p.instanceData);
                  p.instanceData = null;
                  p.instanceDataNativeOwned = false;
               }
            } else {
               boolean slotDrift = p.submitBaseVertex != slot.baseVertex
                  || p.submitFirstIndexBytes != slot.firstIndexBytes
                  || p.submitIndexCount != slot.indexCount;
               if (slotDrift && (MdiRenderDiag.isDebugEnabled() || MdiRenderDiag.isVerboseEnabled())) {
                  String tag = p.renderer.getMdiTraceTag();
                  MainRegistry.LOGGER
                     .warn(
                        "[HBM-M MDI] slot drift tag={} rid=0x{} submit(bv,fiB,ic)=({},{},{}) atlas=({},{},{})",
                        new Object[]{
                           tag != null ? tag : "?",
                           Integer.toHexString(System.identityHashCode(p.renderer)),
                           p.submitBaseVertex,
                           p.submitFirstIndexBytes,
                           p.submitIndexCount,
                           slot.baseVertex,
                           slot.firstIndexBytes,
                           slot.indexCount
                        }
                     );
               }

               p.baseVertex = slot.baseVertex;
               p.firstIndexBytes = slot.firstIndexBytes;
               p.indexCount = slot.indexCount;
               drawList.add(p);
            }
         }

         int droppedNoSlot = pendingSize - drawList.size();
         this.pending.clear();
         this.totalInstances = 0;
         if (drawList.isEmpty()) {
            return null;
         }

         int instanceFloatsPerInstance = atlas.getInstanceFloatsPerInstance();

         for (MdiBatchCoordinator.Pending p : drawList) {
            compactVisibleInstances(p, instanceFloatsPerInstance);
         }

         drawList.removeIf(px -> px.instanceCount <= 0);
         if (drawList.isEmpty()) {
            freeDrawListInstanceBuffers(drawList);
            return null;
         }

         int drawTotalInstances = 0;

         for (MdiBatchCoordinator.Pending p : drawList) {
            drawTotalInstances += p.instanceCount;
         }

         if (drawTotalInstances == 0) {
            freeDrawListInstanceBuffers(drawList);
            return null;
         }

         int baseInst = 0;

         for (MdiBatchCoordinator.Pending p : drawList) {
            p.baseInstance = baseInst;
            baseInst += p.instanceCount;
         }

         if (uploadInstances && !uploadInstancesToAtlas(drawList, atlas)) {
            freeDrawListInstanceBuffers(drawList);
            return null;
         } else {
            return new MdiBatchCoordinator.PreparedMdi(drawList, drawTotalInstances, pendingSize, droppedNoSlot, 1.0F);
         }
      } else {
         return null;
      }
   }

   private static void refreshDrawListAtlasSlots(List<MdiBatchCoordinator.Pending> drawList, MdiGeometryAtlas atlas) {
      for (int i = drawList.size() - 1; i >= 0; i--) {
         MdiBatchCoordinator.Pending p = drawList.get(i);
         MdiGeometryAtlas.Slot slot = atlas.getCurrentSlot(p.renderer);
         if (slot == null) {
            if (p.instanceDataNativeOwned && p.instanceData != null) {
               MemoryUtil.memFree(p.instanceData);
               p.instanceData = null;
               p.instanceDataNativeOwned = false;
            }

            drawList.remove(i);
         } else {
            p.baseVertex = slot.baseVertex;
            p.firstIndexBytes = slot.firstIndexBytes;
            p.indexCount = slot.indexCount;
         }
      }
   }

   private static boolean uploadInstancesToAtlas(List<MdiBatchCoordinator.Pending> drawList, MdiGeometryAtlas atlas) {
      int drawTotalInstances = 0;

      for (MdiBatchCoordinator.Pending p : drawList) {
         drawTotalInstances += p.instanceCount;
      }

      if (drawTotalInstances == 0) {
         return false;
      }

      if (!atlas.ensureInstanceCapacity(drawTotalInstances)) {
         return false;
      }

      int instanceFloatsPerInstance = atlas.getInstanceFloatsPerInstance();
      atlas.orphanInstanceBuffer(drawTotalInstances);
      int offsetFloats = 0;

      for (MdiBatchCoordinator.Pending p : drawList) {
         if (p.instanceCount > 0) {
            int floats = p.instanceCount * instanceFloatsPerInstance;
            if (p.instanceData == null || p.instanceData.remaining() < floats) {
               return false;
            }

            atlas.uploadInstanceWindow(offsetFloats, p.instanceData, floats);
            offsetFloats += floats;
         }
      }

      return true;
   }

   private static void executeMdiGlDraw(MdiBatchCoordinator.PreparedMdi prepared, Matrix4f projection, long gameTime) {
      List<MdiBatchCoordinator.Pending> drawList = prepared.drawList;
      if (!drawList.isEmpty()) {
         MdiGeometryAtlas atlas = MdiGeometryAtlas.getOrCreate();
         if (atlas != null && atlas.isReady()) {
            ShaderInstance shader = ModShaders.getBlockLitInstancedShader();
            if (shader != null) {
               int nCmd = drawList.size();
               ByteBuffer cmdBuf = MemoryUtil.memAlloc(nCmd * 32);
               cmdBuf.order(ByteOrder.nativeOrder());

               for (MdiBatchCoordinator.Pending p : drawList) {
                  int rowStart = cmdBuf.position();
                  cmdBuf.putInt(p.indexCount);
                  cmdBuf.putInt(p.instanceCount);
                  cmdBuf.putInt(p.firstIndexBytes >>> 2);
                  cmdBuf.putInt(p.baseVertex);
                  cmdBuf.putInt(p.baseInstance);

                  while (cmdBuf.position() < rowStart + 32) {
                     cmdBuf.putInt(0);
                  }
               }

               cmdBuf.flip();
               int instanceFloatsPerInstance = atlas.getInstanceFloatsPerInstance();

               try {
                  int prevVao = GL11.glGetInteger(34229);
                  int prevArrayBuf = GL11.glGetInteger(34964);
                  int prevIndirectBuf = 0;

                  try {
                     prevIndirectBuf = GL11.glGetInteger(36675);
                  } catch (Throwable var49) {
                  }

                  boolean cullWas = GL11.glIsEnabled(2884);
                  boolean depthTestWas = GL11.glIsEnabled(2929);
                  boolean depthMaskWas = GL11.glGetBoolean(2930);
                  int prevDepthFunc = GL11.glGetInteger(2932);
                  boolean blendWas = GL11.glIsEnabled(3042);
                  int prevSrcRgb = GL11.glGetInteger(32969);
                  int prevDstRgb = GL11.glGetInteger(32968);
                  int prevSrcA = GL11.glGetInteger(32971);
                  int prevDstA = GL11.glGetInteger(32970);
                  boolean primitiveRestartWas = false;

                  try {
                     primitiveRestartWas = GL11.glIsEnabled(36765);
                  } catch (Throwable var48) {
                  }

                  String dispatchDrawMode = "MULTI";

                  try {
                     GL30.glBindVertexArray(atlas.getVaoId());
                     atlas.enableVertexAttribArraysOnBoundVao();
                     Minecraft mc = Minecraft.getInstance();
                     if (mc.gameRenderer != null) {
                        mc.gameRenderer.lightTexture().updateLightTexture(mc.getFrameTime());
                     }

                     RenderSystem.setShader(() -> shader);
                     applyCommonUniforms(shader, projection);
                     SingleMeshVboRenderer.prepareBlockLitSamplers(shader);
                     shader.apply();
                     InstancedStaticPartRenderer.bindBlockLitTexturesBeforeDraw(shader);
                     float minFade = prepared.minFade();
                     int fadeOffset = atlas.getInstanceFadeFloatOffset();

                     for (MdiBatchCoordinator.Pending p : drawList) {
                        FloatBuffer buf = p.instanceData;
                        if (buf != null) {
                           for (int i = 0; i < p.instanceCount; i++) {
                              float fa = buf.get(i * instanceFloatsPerInstance + fadeOffset);
                              if (fa < minFade) {
                                 minFade = fa;
                              }
                           }
                        }
                     }

                     RenderSystem.enableDepthTest();
                     RenderSystem.depthFunc(515);
                     RenderSystem.depthMask(true);
                     RenderSystem.disableCull();
                     if (minFade < 0.99F) {
                        RenderSystem.enableBlend();
                        RenderSystem.defaultBlendFunc();
                     }

                     if (primitiveRestartWas) {
                        GL11.glDisable(36765);
                     }

                     int cmdByteLen = drawList.size() * 32;
                     GL15.glBindBuffer(36671, atlas.getIndirectBufferId());
                     atlas.ensureIndirectCommandByteCapacity(cmdByteLen);
                     GL15.glBufferSubData(36671, 0L, cmdBuf);

                     try {
                        GLCapabilities capsBarrier = GL.getCapabilities();
                        if (capsBarrier != null && capsBarrier.glMemoryBarrier != 0L) {
                           GL42.glMemoryBarrier(64);
                        }
                     } catch (Throwable var47) {
                     }

                     GLCapabilities caps2 = GL.getCapabilities();
                     boolean canMulti = caps2 != null && (caps2.glMultiDrawElementsIndirect != 0L || caps2.GL_ARB_multi_draw_indirect);
                     boolean canSingle = caps2 != null && (caps2.glDrawElementsIndirect != 0L || caps2.GL_ARB_draw_indirect);
                     if (canMulti) {
                        if (caps2.glMultiDrawElementsIndirect != 0L) {
                           GL43.glMultiDrawElementsIndirect(4, 5125, 0L, drawList.size(), 32);
                        } else {
                           ARBMultiDrawIndirect.glMultiDrawElementsIndirect(4, 5125, 0L, drawList.size(), 32);
                        }

                        dispatchDrawMode = "MULTI";
                     } else if (!canSingle) {
                        MainRegistry.LOGGER.error("[HBM-M MDI] Нет ни glDrawElementsIndirect, ни glMultiDrawElementsIndirect — пропуск отрисовки атласа");
                        dispatchDrawMode = "NONE";
                     } else {
                        int n = drawList.size();

                        for (int i = 0; i < n; i++) {
                           long cmdOff = i * 32L;
                           if (caps2.glDrawElementsIndirect != 0L) {
                              GL40.glDrawElementsIndirect(4, 5125, cmdOff);
                           } else {
                              ARBDrawIndirect.glDrawElementsIndirect(4, 5125, cmdOff);
                           }
                        }

                        dispatchDrawMode = "IND_LOOP";
                     }

                     if (minFade < 0.99F) {
                        RenderSystem.disableBlend();
                     }

                     logDispatchDiagStatic(
                        prepared.pendingSize, drawList, prepared.drawTotalInstances, atlas, prepared.droppedNoSlot, dispatchDrawMode, gameTime
                     );
                  } finally {
                     if (primitiveRestartWas) {
                        GL11.glEnable(36765);
                     }

                     GL15.glBindBuffer(36671, prevIndirectBuf);
                     GL15.glBindBuffer(34962, prevArrayBuf);
                     GL30.glBindVertexArray(prevVao);
                     RenderSystem.depthMask(depthMaskWas);
                     RenderSystem.depthFunc(prevDepthFunc);
                     if (depthTestWas) {
                        RenderSystem.enableDepthTest();
                     } else {
                        RenderSystem.disableDepthTest();
                     }

                     if (cullWas) {
                        RenderSystem.enableCull();
                     } else {
                        RenderSystem.disableCull();
                     }

                     RenderSystem.blendFuncSeparate(prevSrcRgb, prevDstRgb, prevSrcA, prevDstA);
                     if (blendWas) {
                        RenderSystem.enableBlend();
                     } else {
                        RenderSystem.disableBlend();
                     }

                     restoreVanillaSolidShader();
                  }
               } finally {
                  MemoryUtil.memFree(cmdBuf);
               }
            }
         }
      }
   }

   private static void restoreVanillaSolidShader() {
      RenderSystem.setShader(GameRenderer::getRendertypeSolidShader);
      RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
   }

   private void coalescePendingByRenderer() {
      if (this.pending.size() > 1) {
         IdentityHashMap<InstancedStaticPartRenderer, MdiBatchCoordinator.Pending> best = new IdentityHashMap<>();
         List<MdiBatchCoordinator.Pending> dropped = new ArrayList<>();

         for (MdiBatchCoordinator.Pending p : this.pending) {
            MdiBatchCoordinator.Pending prev = best.get(p.renderer);
            if (prev == null) {
               best.put(p.renderer, p);
            } else if (p.instanceCount > prev.instanceCount) {
               dropped.add(prev);
               best.put(p.renderer, p);
            } else {
               dropped.add(p);
            }
         }

         if (!dropped.isEmpty()) {
            for (MdiBatchCoordinator.Pending p : dropped) {
               if (p.instanceDataNativeOwned && p.instanceData != null) {
                  MemoryUtil.memFree(p.instanceData);
                  p.instanceData = null;
                  p.instanceDataNativeOwned = false;
               }
            }

            this.pending.clear();
            this.pending.addAll(best.values());
            this.totalInstances = 0;

            for (MdiBatchCoordinator.Pending p : this.pending) {
               this.totalInstances = this.totalInstances + p.instanceCount;
            }
         }
      }
   }

   private static void compactVisibleInstances(MdiBatchCoordinator.Pending p, int floatsPerInstance) {
   }

   private static void applyCommonUniforms(ShaderInstance shader, Matrix4f projection) {
      if (shader.PROJECTION_MATRIX != null) {
         shader.PROJECTION_MATRIX.set(projection);
      }

      if (shader.MODEL_VIEW_MATRIX != null) {
         shader.MODEL_VIEW_MATRIX.set(new Matrix4f());
      }

      Uniform fogStart = shader.getUniform("FogStart");
      if (fogStart != null) {
         fogStart.set(RenderSystem.getShaderFogStart());
      }

      Uniform fogEnd = shader.getUniform("FogEnd");
      if (fogEnd != null) {
         fogEnd.set(RenderSystem.getShaderFogEnd());
      }

      Uniform fogColor = shader.getUniform("FogColor");
      if (fogColor != null) {
         float[] c = RenderSystem.getShaderFogColor();
         fogColor.set(c[0], c[1], c[2], c[3]);
      }

      Uniform fade = shader.getUniform("FadeAlpha");
      if (fade != null) {
         fade.set(1.0F);
      }
   }

   private static void logDispatchDiagStatic(
      int pendingSize,
      List<MdiBatchCoordinator.Pending> drawList,
      int drawTotalInstances,
      MdiGeometryAtlas atlas,
      int droppedNoSlot,
      String drawMode,
      long gameTime
   ) {
      if (MdiRenderDiag.isDebugEnabled() || MdiRenderDiag.isVerboseEnabled()) {
         String summary = String.format(
            "[HBM-M MDI] dispatch gameTime=%s draws=%d/%d droppedNoSlot=%d instances=%d atlasParts=%d mode=%s",
            gameTime == -1L ? "?" : Long.toString(gameTime),
            drawList.size(),
            pendingSize,
            droppedNoSlot,
            drawTotalInstances,
            atlas.getRegisteredGeometryCount(),
            drawMode
         );
         if (!MdiRenderDiag.isDebugEnabled() && !MdiRenderDiag.isVerboseEnabled()) {
            MainRegistry.LOGGER.debug(summary);
         } else {
            MainRegistry.LOGGER.info(summary);
         }

         if (MdiRenderDiag.isVerboseEnabled()) {
            for (MdiBatchCoordinator.Pending p : drawList) {
               String tag = p.renderer.getMdiTraceTag();
               if (tag == null) {
                  tag = "?";
               }

               MainRegistry.LOGGER
                  .info(
                     "[HBM-M MDI]   sub tag={} rid=0x{} idxCount={} instanceCount={} baseInstance={} baseVertex={} firstIndexBytes={}",
                     new Object[]{
                        tag,
                        Integer.toHexString(System.identityHashCode(p.renderer)),
                        p.indexCount,
                        p.instanceCount,
                        p.baseInstance,
                        p.baseVertex,
                        p.firstIndexBytes
                     }
                  );
            }
         }
      }
   }

   private static final class DeferredDraw {
      final List<MdiBatchCoordinator.Pending> drawList;
      final int drawTotalInstances;
      final int pendingSize;
      final int droppedNoSlot;
      final long gameTime;
      final Matrix4f projectionMatrix;

      DeferredDraw(
         List<MdiBatchCoordinator.Pending> drawList, int drawTotalInstances, int pendingSize, int droppedNoSlot, long gameTime, Matrix4f projectionMatrix
      ) {
         this.drawList = drawList;
         this.drawTotalInstances = drawTotalInstances;
         this.pendingSize = pendingSize;
         this.droppedNoSlot = droppedNoSlot;
         this.gameTime = gameTime;
         this.projectionMatrix = new Matrix4f(projectionMatrix);
      }

      void free() {
         for (MdiBatchCoordinator.Pending p : this.drawList) {
            if (p.instanceDataNativeOwned && p.instanceData != null) {
               MemoryUtil.memFree(p.instanceData);
               p.instanceData = null;
               p.instanceDataNativeOwned = false;
            }
         }

         this.drawList.clear();
      }
   }

   static final class Pending {
      final InstancedStaticPartRenderer renderer;
      int baseVertex;
      int firstIndexBytes;
      int indexCount;
      int submitBaseVertex = -1;
      int submitFirstIndexBytes = -1;
      int submitIndexCount = -1;
      int baseInstance;
      int instanceCount;
      int[] instanceCullIndices;
      long[] instanceOcclusionKeys;
      FloatBuffer instanceData;
      boolean instanceDataNativeOwned;

      Pending(InstancedStaticPartRenderer renderer) {
         this.renderer = renderer;
      }
   }

   private record PreparedMdi(List<MdiBatchCoordinator.Pending> drawList, int drawTotalInstances, int pendingSize, int droppedNoSlot, float minFade) {
   }
}
