package com.hbm_m.client.render;

import com.hbm_m.main.MainRegistry;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryUtil;

@OnlyIn(Dist.CLIENT)
public final class MdiGeometryAtlas {
   private static final int VERTEX_STRIDE_BYTES = 36;
   private static final int INSTANCE_FLOATS = 30;
   private static final int INSTANCE_FADE_FLOAT_OFFSET = 13;
   private static volatile MdiGeometryAtlas INSTANCE;
   private boolean ready = false;
   private int vaoId = 0;
   private int vertexVboId = 0;
   private int indexEboId = 0;
   private int instanceVboId = 0;
   private int indirectBufId = 0;
   private long vertexCapBytes = 0L;
   private long vertexUsedBytes = 0L;
   private long indexCapBytes = 0L;
   private long indexUsedBytes = 0L;
   private long instanceCapInstances = 0L;
   private long indirectCmdCapBytes = 0L;
   private final Map<InstancedStaticPartRenderer, MdiGeometryAtlas.GeoRecord> geometryByRenderer = new LinkedHashMap<>();

   private MdiGeometryAtlas() {
   }

   public static MdiGeometryAtlas getOrCreate() {
      MdiGeometryAtlas inst = INSTANCE;
      if (inst != null) {
         return inst;
      }

      synchronized (MdiGeometryAtlas.class) {
         inst = INSTANCE;
         if (inst != null) {
            return inst;
         }

         inst = new MdiGeometryAtlas();

         try {
            inst.initialise();
         } catch (Throwable t) {
            MainRegistry.LOGGER.error("[HBM-M MDI] MdiGeometryAtlas init failed; MDI path disabled", t);
            inst.ready = false;
         }

         INSTANCE = inst;
         return inst;
      }
   }

   public static void resetForResourceLifecycle() {
      if (!RenderSystem.isOnRenderThread()) {
         RenderSystem.recordRenderCall(MdiGeometryAtlas::resetForResourceLifecycle);
      } else {
         synchronized (MdiGeometryAtlas.class) {
            MdiGeometryAtlas inst = INSTANCE;
            if (inst != null) {
               try {
                  inst.destroyInternal();
               } catch (Throwable t) {
                  MainRegistry.LOGGER.error("[HBM-M MDI] MdiGeometryAtlas reset failed", t);
               } finally {
                  INSTANCE = null;
               }
            }
         }
      }
   }

   private void destroyInternal() {
      for (MdiGeometryAtlas.GeoRecord rec : this.geometryByRenderer.values()) {
         try {
            if (rec.vertexBytesCopy != null) {
               MemoryUtil.memFree(rec.vertexBytesCopy);
            }

            if (rec.indicesCopy != null) {
               MemoryUtil.memFree(rec.indicesCopy);
            }
         } catch (Throwable t) {
            MainRegistry.LOGGER.warn("[HBM-M MDI] GeoRecord native free: {}", t.toString());
         }
      }

      this.geometryByRenderer.clear();
      this.vertexUsedBytes = 0L;
      this.indexUsedBytes = 0L;
      if (this.ready) {
         MdiGeometryAtlas.GLCapabilitiesGuard guard = MdiGeometryAtlas.GLCapabilitiesGuard.snapshot();

         try {
            if (this.vaoId != 0) {
               GL30.glDeleteVertexArrays(this.vaoId);
            }

            if (this.vertexVboId != 0) {
               GL15.glDeleteBuffers(this.vertexVboId);
            }

            if (this.indexEboId != 0) {
               GL15.glDeleteBuffers(this.indexEboId);
            }

            if (this.instanceVboId != 0) {
               GL15.glDeleteBuffers(this.instanceVboId);
            }

            if (this.indirectBufId != 0) {
               GL15.glDeleteBuffers(this.indirectBufId);
            }
         } finally {
            guard.restore();
         }

         this.vaoId = 0;
         this.vertexVboId = 0;
         this.indexEboId = 0;
         this.instanceVboId = 0;
         this.indirectBufId = 0;
         this.vertexCapBytes = 0L;
         this.indexCapBytes = 0L;
         this.instanceCapInstances = 0L;
         this.indirectCmdCapBytes = 0L;
         this.ready = false;
         MainRegistry.LOGGER.info("[HBM-M MDI] MdiGeometryAtlas reset (resource lifecycle)");
      }
   }

   private void initialise() {
      if (!RenderSystem.isOnRenderThread()) {
         MainRegistry.LOGGER.warn("[HBM-M MDI] Atlas init invoked off render thread; deferring");
      } else {
         MdiGeometryAtlas.GLCapabilitiesGuard guard = MdiGeometryAtlas.GLCapabilitiesGuard.snapshot();

         try {
            this.vaoId = GL30.glGenVertexArrays();
            this.vertexVboId = GL15.glGenBuffers();
            this.indexEboId = GL15.glGenBuffers();
            this.instanceVboId = GL15.glGenBuffers();
            this.indirectBufId = GL15.glGenBuffers();
            if (this.vaoId == 0 || this.vertexVboId == 0 || this.indexEboId == 0 || this.instanceVboId == 0 || this.indirectBufId == 0) {
               throw new IllegalStateException("Failed to generate one or more atlas GL objects");
            }

            this.vertexCapBytes = 262144L;
            this.indexCapBytes = 65536L;
            this.instanceCapInstances = 4096L;
            GL30.glBindVertexArray(this.vaoId);
            GL15.glBindBuffer(34962, this.vertexVboId);
            GL15.glBufferData(34962, this.vertexCapBytes, 35044);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 3, 5126, false, 36, 0L);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 3, 5126, false, 36, 12L);
            GL20.glEnableVertexAttribArray(2);
            GL20.glVertexAttribPointer(2, 2, 5126, false, 36, 24L);
            GL30.glEnableVertexAttribArray(3);
            GL30.glVertexAttribIPointer(3, 1, 5124, 36, 32L);
            GL15.glBindBuffer(34963, this.indexEboId);
            GL15.glBufferData(34963, this.indexCapBytes, 35044);
            GL15.glBindBuffer(34962, this.instanceVboId);
            GL15.glBufferData(34962, this.instanceCapInstances * 30L * 4L, 35040);
            int stride = 120;
            GL20.glEnableVertexAttribArray(4);
            GL20.glVertexAttribPointer(4, 3, 5126, false, stride, 0L);
            GL33.glVertexAttribDivisor(4, 1);
            GL20.glEnableVertexAttribArray(5);
            GL20.glVertexAttribPointer(5, 4, 5126, false, stride, 12L);
            GL33.glVertexAttribDivisor(5, 1);
            GL20.glEnableVertexAttribArray(6);
            GL20.glVertexAttribPointer(6, 3, 5126, false, stride, 28L);
            GL33.glVertexAttribDivisor(6, 1);
            GL20.glEnableVertexAttribArray(7);
            GL20.glVertexAttribPointer(7, 4, 5126, false, stride, 40L);
            GL33.glVertexAttribDivisor(7, 1);
            GL20.glEnableVertexAttribArray(8);
            GL20.glVertexAttribPointer(8, 4, 5126, false, stride, 56L);
            GL33.glVertexAttribDivisor(8, 1);
            GL20.glEnableVertexAttribArray(9);
            GL20.glVertexAttribPointer(9, 4, 5126, false, stride, 72L);
            GL33.glVertexAttribDivisor(9, 1);
            GL20.glEnableVertexAttribArray(10);
            GL20.glVertexAttribPointer(10, 4, 5126, false, stride, 88L);
            GL33.glVertexAttribDivisor(10, 1);
            GL20.glEnableVertexAttribArray(11);
            GL20.glVertexAttribPointer(11, 4, 5126, false, stride, 104L);
            GL33.glVertexAttribDivisor(11, 1);
            long initialIndirectBytes = 131072L;
            GL15.glBindBuffer(36671, this.indirectBufId);
            GL15.glBufferData(36671, initialIndirectBytes, 35040);
            this.indirectCmdCapBytes = initialIndirectBytes;
            GL15.glBindBuffer(36671, 0);
            GL30.glBindVertexArray(0);
            this.ready = true;
            MainRegistry.LOGGER
               .info(
                  "[HBM-M MDI] MdiGeometryAtlas initialised (vao={}, vbo={}, ebo={}, inst={}, indirect={})",
                  new Object[]{this.vaoId, this.vertexVboId, this.indexEboId, this.instanceVboId, this.indirectBufId}
               );
         } finally {
            guard.restore();
         }
      }
   }

   public boolean isReady() {
      return this.ready;
   }

   public int getVaoId() {
      return this.vaoId;
   }

   public int getIndirectBufferId() {
      return this.indirectBufId;
   }

   public long getIndirectCommandBufferCapBytes() {
      return this.indirectCmdCapBytes;
   }

   public int getInstanceFloatsPerInstance() {
      return 30;
   }

   public int getInstanceFadeFloatOffset() {
      return 13;
   }

   public synchronized int getRegisteredGeometryCount() {
      return this.geometryByRenderer.size();
   }

   public void enableVertexAttribArraysOnBoundVao() {
      if (this.ready && this.vaoId > 0) {
         if (GL11.glGetInteger(34229) == this.vaoId) {
            for (int i = 0; i <= 12; i++) {
               GL20.glEnableVertexAttribArray(i);
            }
         }
      }
   }

   public boolean acceptsInstanceDataSize(int floatsPerInstance) {
      return this.ready && floatsPerInstance == 30;
   }

   public synchronized MdiGeometryAtlas.Slot getCurrentSlot(InstancedStaticPartRenderer renderer) {
      if (this.ready && renderer != null) {
         MdiGeometryAtlas.GeoRecord rec = this.geometryByRenderer.get(renderer);
         return rec != null ? rec.slot : null;
      } else {
         return null;
      }
   }

   public static void evictRendererIfRegistered(InstancedStaticPartRenderer renderer) {
      if (renderer != null) {
         MdiGeometryAtlas inst = INSTANCE;
         if (inst != null) {
            synchronized (inst) {
               if (INSTANCE == inst && inst.ready) {
                  inst.evictRendererLocked(renderer);
               }
            }
         }
      }
   }

   private void evictRendererLocked(InstancedStaticPartRenderer renderer) {
      MdiGeometryAtlas.GeoRecord rec = this.geometryByRenderer.remove(renderer);
      if (rec != null) {
         try {
            if (rec.vertexBytesCopy != null) {
               MemoryUtil.memFree(rec.vertexBytesCopy);
            }

            if (rec.indicesCopy != null) {
               MemoryUtil.memFree(rec.indicesCopy);
            }
         } catch (Throwable t) {
            MainRegistry.LOGGER.warn("[HBM-M MDI] evict GeoRecord native free: {}", t.toString());
         }

         if (this.geometryByRenderer.isEmpty()) {
            this.vertexUsedBytes = 0L;
            this.indexUsedBytes = 0L;
         } else {
            this.repackGeometryAndRefreshSlots();
         }
      }
   }

   public synchronized MdiGeometryAtlas.Slot registerGeometryIfAbsent(
      InstancedStaticPartRenderer renderer, ByteBuffer vertexBytes, IntBuffer indices, int indexCount
   ) {
      if (!this.ready) {
         return null;
      }

      MdiGeometryAtlas.GeoRecord existing = this.geometryByRenderer.get(renderer);
      if (existing != null) {
         if (existing.slot != null && vertexBytes != null && indices != null && indexCount > 0) {
            int inVertexLen = vertexBytes.remaining();
            if (inVertexLen == existing.registeredVertexBytesLen && indexCount == existing.registeredIndexCount) {
               return existing.slot;
            }
         }

         this.evictRendererLocked(renderer);
      }

      if (vertexBytes != null && indices != null && indexCount > 0) {
         ByteBuffer vertexBytesView = vertexBytes.duplicate();
         IntBuffer indicesView = indices.duplicate();
         long vertexBytesLen = vertexBytesView.remaining();
         long indexBytesLen = indexCount * 4L;
         long vertexCount = vertexBytesLen / 36L;
         ByteBuffer vbCopy = MemoryUtil.memAlloc((int)vertexBytesLen);
         vbCopy.put(vertexBytesView);
         vbCopy.flip();
         IntBuffer ibCopy = MemoryUtil.memAllocInt(indexCount);
         ibCopy.put(indicesView);
         ibCopy.flip();
         MdiGeometryAtlas.GeoRecord rec = new MdiGeometryAtlas.GeoRecord(vbCopy, ibCopy, (int)vertexBytesLen, indexCount);

         try {
            this.ensureVertexCapacity(this.vertexUsedBytes + vertexBytesLen);
            this.ensureIndexCapacity(this.indexUsedBytes + indexBytesLen);
            int baseVertex = (int)(this.vertexUsedBytes / 36L);
            int firstIndexBytes = (int)this.indexUsedBytes;
            MdiGeometryAtlas.GLCapabilitiesGuard guard = MdiGeometryAtlas.GLCapabilitiesGuard.snapshot();

            try {
               GL15.glBindBuffer(34962, this.vertexVboId);
               GL15.glBufferSubData(34962, this.vertexUsedBytes, vbCopy.duplicate());
               GL15.glBindBuffer(34963, this.indexEboId);
               GL15.glBufferSubData(34963, this.indexUsedBytes, ibCopy.duplicate());
            } finally {
               guard.restore();
            }

            this.vertexUsedBytes += vertexBytesLen;
            this.indexUsedBytes += indexBytesLen;
            rec.slot = new MdiGeometryAtlas.Slot(baseVertex, firstIndexBytes, indexCount);
            this.geometryByRenderer.put(renderer, rec);
            MainRegistry.LOGGER
               .debug(
                  "[HBM-M MDI] Atlas registered renderer {}: verts={}, idx={}, baseVertex={}, firstIndexBytes={}",
                  new Object[]{System.identityHashCode(renderer), vertexCount, indexCount, baseVertex, firstIndexBytes}
               );
            return rec.slot;
         } catch (Throwable t) {
            MainRegistry.LOGGER.error("[HBM-M MDI] Geometry registration failed", t);
            MemoryUtil.memFree(vbCopy);
            MemoryUtil.memFree(ibCopy);
            return null;
         }
      } else {
         return null;
      }
   }

   private void ensureVertexCapacity(long requiredBytes) {
      if (requiredBytes > this.vertexCapBytes) {
         long newCap = this.vertexCapBytes;

         while (newCap < requiredBytes) {
            newCap *= 2L;
         }

         MdiGeometryAtlas.GLCapabilitiesGuard guard = MdiGeometryAtlas.GLCapabilitiesGuard.snapshot();

         try {
            GL30.glBindVertexArray(this.vaoId);
            GL15.glBindBuffer(34962, this.vertexVboId);
            GL15.glBufferData(34962, newCap, 35044);
            this.vertexCapBytes = newCap;
         } finally {
            guard.restore();
         }

         this.repackGeometryAndRefreshSlots();
      }
   }

   private void ensureIndexCapacity(long requiredBytes) {
      if (requiredBytes > this.indexCapBytes) {
         long newCap = this.indexCapBytes;

         while (newCap < requiredBytes) {
            newCap *= 2L;
         }

         MdiGeometryAtlas.GLCapabilitiesGuard guard = MdiGeometryAtlas.GLCapabilitiesGuard.snapshot();

         try {
            GL30.glBindVertexArray(this.vaoId);
            GL15.glBindBuffer(34963, this.indexEboId);
            GL15.glBufferData(34963, newCap, 35044);
            this.indexCapBytes = newCap;
         } finally {
            guard.restore();
         }

         this.repackGeometryAndRefreshSlots();
      }
   }

   private void repackGeometryAndRefreshSlots() {
      MdiGeometryAtlas.GLCapabilitiesGuard guard = MdiGeometryAtlas.GLCapabilitiesGuard.snapshot();

      try {
         GL30.glBindVertexArray(this.vaoId);
         long vOff = 0L;
         long iOff = 0L;

         for (Entry<InstancedStaticPartRenderer, MdiGeometryAtlas.GeoRecord> e : this.geometryByRenderer.entrySet()) {
            MdiGeometryAtlas.GeoRecord rec = e.getValue();
            String rid = Integer.toHexString(System.identityHashCode(e.getKey()));
            if (rec.vertexBytesCopy == null || rec.indicesCopy == null) {
               rec.slot = null;
               MainRegistry.LOGGER.warn("[HBM-M MDI] repack skip: null native copy rid=0x{} — slot invalidated (stale offsets after buffer resize)", rid);
            } else if (rec.registeredVertexBytesLen > 0 && rec.registeredIndexCount > 0) {
               ByteBuffer vbView = rec.vertexBytesCopy.duplicate();
               vbView.clear();
               vbView.limit(rec.registeredVertexBytesLen);
               IntBuffer ibView = rec.indicesCopy.duplicate();
               ibView.clear();
               ibView.limit(rec.registeredIndexCount);
               int vLen = vbView.remaining();
               int idxCount = ibView.remaining();
               if (vLen == rec.registeredVertexBytesLen && idxCount == rec.registeredIndexCount) {
                  long idxBytes = idxCount * 4L;
                  int baseVertex = (int)(vOff / 36L);
                  int firstIndexBytes = (int)iOff;
                  GL15.glBindBuffer(34962, this.vertexVboId);
                  GL15.glBufferSubData(34962, vOff, vbView);
                  GL15.glBindBuffer(34963, this.indexEboId);
                  GL15.glBufferSubData(34963, iOff, ibView);
                  rec.slot = new MdiGeometryAtlas.Slot(baseVertex, firstIndexBytes, idxCount);
                  vOff += vLen;
                  iOff += idxBytes;
               } else {
                  rec.slot = null;
                  MainRegistry.LOGGER
                     .warn(
                        "[HBM-M MDI] repack skip: buffer view len mismatch rid=0x{} vLen={} expV={} idxCount={} expI={} — slot invalidated",
                        new Object[]{rid, vLen, rec.registeredVertexBytesLen, idxCount, rec.registeredIndexCount}
                     );
               }
            } else {
               rec.slot = null;
               MainRegistry.LOGGER.warn("[HBM-M MDI] repack skip: non-positive registered sizes rid=0x{} — slot invalidated", rid);
            }
         }

         this.vertexUsedBytes = vOff;
         this.indexUsedBytes = iOff;
      } finally {
         guard.restore();
      }
   }

   public synchronized boolean ensureInstanceCapacity(int instances) {
      if (!this.ready) {
         return false;
      }

      if (instances <= this.instanceCapInstances) {
         return true;
      }

      long newCap = this.instanceCapInstances;

      while (newCap < instances) {
         newCap *= 2L;
      }

      MdiGeometryAtlas.GLCapabilitiesGuard guard = MdiGeometryAtlas.GLCapabilitiesGuard.snapshot();

      try {
         GL15.glBindBuffer(34962, this.instanceVboId);
         GL15.glBufferData(34962, newCap * 30L * 4L, 35040);
         this.instanceCapInstances = newCap;
         return true;
      } catch (Throwable t) {
         MainRegistry.LOGGER.error("[HBM-M MDI] Instance VBO grow failed", t);
         return false;
      } finally {
         guard.restore();
      }
   }

   public void orphanInstanceBuffer(int instances) {
      MdiGeometryAtlas.GLCapabilitiesGuard guard = MdiGeometryAtlas.GLCapabilitiesGuard.snapshot();

      try {
         GL15.glBindBuffer(34962, this.instanceVboId);
         GL15.glBufferData(34962, this.instanceCapInstances * 30L * 4L, 35040);
      } finally {
         guard.restore();
      }
   }

   public void ensureIndirectCommandByteCapacity(int needBytes) {
      if (this.ready && needBytes > 0) {
         if (needBytes > this.indirectCmdCapBytes) {
            long newCap = this.indirectCmdCapBytes <= 0L ? 65536L : this.indirectCmdCapBytes;

            while (newCap < needBytes) {
               newCap *= 2L;
            }

            MdiGeometryAtlas.GLCapabilitiesGuard guard = MdiGeometryAtlas.GLCapabilitiesGuard.snapshot();

            try {
               GL15.glBindBuffer(36671, this.indirectBufId);
               GL15.glBufferData(36671, newCap, 35040);
               this.indirectCmdCapBytes = newCap;
            } finally {
               guard.restore();
            }
         }
      }
   }

   public void uploadInstanceWindow(int floatOffset, FloatBuffer src, int floatCount) {
      MdiGeometryAtlas.GLCapabilitiesGuard guard = MdiGeometryAtlas.GLCapabilitiesGuard.snapshot();

      try {
         GL15.glBindBuffer(34962, this.instanceVboId);
         FloatBuffer slice = src.duplicate();
         slice.limit(slice.position() + floatCount);
         GL15.glBufferSubData(34962, floatOffset * 4L, slice);
      } finally {
         guard.restore();
      }
   }

   private static final class GLCapabilitiesGuard {
      private final int prevVao;
      private final int prevArrayBuf;
      private final int prevElemBuf;

      private GLCapabilitiesGuard(int vao, int ab, int eb) {
         this.prevVao = vao;
         this.prevArrayBuf = ab;
         this.prevElemBuf = eb;
      }

      static MdiGeometryAtlas.GLCapabilitiesGuard snapshot() {
         int v = GL11.glGetInteger(34229);
         int a = GL11.glGetInteger(34964);
         int e = GL11.glGetInteger(34965);
         return new MdiGeometryAtlas.GLCapabilitiesGuard(v, a, e);
      }

      void restore() {
         try {
            GL30.glBindVertexArray(this.prevVao);
            GL15.glBindBuffer(34962, this.prevArrayBuf);
            GL15.glBindBuffer(34963, this.prevElemBuf);
         } catch (Throwable var2) {
         }
      }

      private static void touchGL() {
         GL.getCapabilities();
      }
   }

   private static final class GeoRecord {
      final ByteBuffer vertexBytesCopy;
      final IntBuffer indicesCopy;
      final int registeredVertexBytesLen;
      final int registeredIndexCount;
      MdiGeometryAtlas.Slot slot;

      GeoRecord(ByteBuffer vb, IntBuffer ib, int registeredVertexBytesLen, int registeredIndexCount) {
         this.vertexBytesCopy = vb;
         this.indicesCopy = ib;
         this.registeredVertexBytesLen = registeredVertexBytesLen;
         this.registeredIndexCount = registeredIndexCount;
      }
   }

   public static final class Slot {
      public final int baseVertex;
      public final int firstIndexBytes;
      public final int indexCount;

      Slot(int baseVertex, int firstIndexBytes, int indexCount) {
         this.baseVertex = baseVertex;
         this.firstIndexBytes = firstIndexBytes;
         this.indexCount = indexCount;
      }
   }
}
