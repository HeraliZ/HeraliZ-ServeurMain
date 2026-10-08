package com.hbm_m.client.render;

import com.google.common.collect.ImmutableList;
import com.hbm_m.client.render.shader.IrisExtendedShaderAccess;
import com.hbm_m.main.MainRegistry;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.vertex.BufferBuilder.DrawState;
import com.mojang.blaze3d.vertex.BufferBuilder.RenderedBuffer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.blaze3d.vertex.VertexFormatElement.Usage;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL44;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;

@OnlyIn(Dist.CLIENT)
public final class IrisCompanionMesh implements IrisCompanionMeshResource {
   private static final int IRIS_ATTRIB_ENTITY = 11;
   private static final int IRIS_ATTRIB_MID_TEX = 12;
   private static final int IRIS_ATTRIB_TANGENT = 13;
   private final List<BakedQuad> quads;
   private int vaoId = -1;
   private int vboId = -1;
   private int eboId = -1;
   private int indexCount = 0;
   private int vertexCount = 0;
   private int uv2Location = -1;
   private boolean built = false;
   private boolean failed = false;
   private final float[] objBbox = new float[6];
   private short[] lightmapTempArray = new short[0];
   private float[] perVertexCornerWeights;
   private float[] perVertexSlicedWeights;
   private int lightmapVboId = -1;
   private ByteBuffer lightmapCpuScratch;
   private ByteBuffer lightmapMapped;
   private ShortBuffer lightmapShortView;
   private boolean lightmapPersistentMapped = false;
   private boolean lightmapPersistentDirty = false;
   private int lightmapStagingVboId = -1;
   private ByteBuffer lightmapStagingMapped;
   private ShortBuffer lightmapStagingShortView;
   private long lightmapStagingFence = 0L;
   private boolean lightmapStagingAvailable = false;
   private int lightmapInstanceCapacity = 0;
   private boolean perVertexLightmapActive = false;
   private int lightmapCurrentSlot = -1;
   private final Long2IntOpenHashMap lightmapKeyToSlot = new Long2IntOpenHashMap();
   private long[] lightmapSlotKey = new long[0];
   private long[] lightmapSlotLastUsed = new long[0];
   private long lightmapSlotAllocCounter = 0L;
   private int lightmapEvictCursor = 0;
   private int lightmapDirtyMinSlot = Integer.MAX_VALUE;
   private int lightmapDirtyMaxSlot = -1;
   private VertexFormat actualFormat;
   private final Map<String, Integer> elementOffsets = new HashMap<>();
   private final Map<String, VertexFormatElement> elementByName = new HashMap<>();
   private static final int CACHED_PROGRAM_SLOTS = 4;
   private final int[] cachedProgramIds = new int[4];
   private int nextCacheSlot = 0;
   private long cachedProgramPipelineGeneration = -1L;
   private boolean irisExtendedAttrsPrimedInVao = false;

   public IrisCompanionMesh(List<BakedQuad> quads) {
      for (int i = 0; i < 4; i++) {
         this.cachedProgramIds[i] = -1;
      }

      this.quads = quads;
   }

   @Override
   public boolean ensureBuilt() {
      if (this.built) {
         return true;
      }

      if (this.failed) {
         return false;
      }

      if (this.quads == null || this.quads.isEmpty()) {
         this.failed = true;
         return false;
      }

      if (!RenderSystem.isOnRenderThread()) {
         return false;
      }

      int previousVao = GL11.glGetInteger(34229);
      int previousArrayBuffer = GL11.glGetInteger(34964);

      try {
         this.computeObjBboxAndWeights();
         BufferBuilder builder = new BufferBuilder(this.quads.size() * 256);
         builder.begin(Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
         Pose neutralPose = new PoseStack().last();
         int fullBrightLight = LightTexture.pack(15, 15);

         for (BakedQuad quad : this.quads) {
            builder.putBulkData(neutralPose, quad, 1.0F, 1.0F, 1.0F, 1.0F, fullBrightLight, OverlayTexture.NO_OVERLAY, false);
         }

         RenderedBuffer rendered = builder.end();
         DrawState drawState = rendered.drawState();
         VertexFormat actualFormat = drawState.format();
         this.actualFormat = actualFormat;
         ByteBuffer vertexBytes = rendered.vertexBuffer();
         this.vertexCount = drawState.vertexCount();
         this.elementOffsets.clear();
         this.elementByName.clear();
         ImmutableList<VertexFormatElement> elements = actualFormat.getElements();
         ImmutableList<String> names = actualFormat.getElementAttributeNames();
         int runningOffset = 0;

         for (int i = 0; i < elements.size(); i++) {
            String name = (String)names.get(i);
            VertexFormatElement el = (VertexFormatElement)elements.get(i);
            this.elementOffsets.put(name, runningOffset);
            this.elementByName.put(name, el);
            runningOffset += el.getByteSize();
         }

         this.vaoId = GL30.glGenVertexArrays();
         this.vboId = GL15.glGenBuffers();
         if (this.vaoId != 0 && this.vboId != 0) {
            GlStateManager._glBindVertexArray(this.vaoId);
            GL15.glBindBuffer(34962, this.vboId);
            GL15.glBufferData(34962, vertexBytes, 35044);
            int stride = actualFormat.getVertexSize();
            int offset = 0;
            int detectedUv2Location = -1;
            int elementCount = actualFormat.getElements().size();

            for (int location = 0; location < elementCount; location++) {
               VertexFormatElement element = (VertexFormatElement)actualFormat.getElements().get(location);
               if (element.getUsage() == Usage.PADDING) {
                  offset += element.getByteSize();
               } else if (location > 5) {
                  offset += element.getByteSize();
               } else {
                  GL20.glEnableVertexAttribArray(location);
                  int glType = element.getType().getGlType();
                  int count = element.getCount();
                  if (isIntegerAttribute(element)) {
                     GL30.glVertexAttribIPointer(location, count, glType, stride, offset);
                  } else {
                     boolean normalize = shouldNormalize(element);
                     GL20.glVertexAttribPointer(location, count, glType, normalize, stride, offset);
                  }

                  if (element.getUsage() == Usage.UV && element.getIndex() == 2) {
                     detectedUv2Location = location;
                  }

                  offset += element.getByteSize();
               }
            }

            if (detectedUv2Location != -1) {
               GL20.glDisableVertexAttribArray(detectedUv2Location);
            }

            this.uv2Location = detectedUv2Location;
            int quadCount = this.vertexCount / 4;
            int[] indices = new int[quadCount * 6];
            int q = 0;
            int idx = 0;

            for (int base = 0; q < quadCount; base += 4) {
               indices[idx++] = base;
               indices[idx++] = base + 1;
               indices[idx++] = base + 2;
               indices[idx++] = base + 2;
               indices[idx++] = base + 3;
               indices[idx++] = base;
               q++;
            }

            this.eboId = GL15.glGenBuffers();
            GL15.glBindBuffer(34963, this.eboId);
            GL15.glBufferData(34963, indices, 35044);
            this.indexCount = indices.length;
            this.primeIrisExtendedVertexAttributes(stride);
            rendered.release();
            this.built = true;
            MainRegistry.LOGGER
               .debug("IrisCompanionMesh: built {} quads, format stride {} bytes, {} attribute(s)", new Object[]{quadCount, stride, elementCount});
            return true;
         } else {
            throw new IllegalStateException("Failed to generate companion VAO/VBO");
         }
      } catch (Throwable t) {
         MainRegistry.LOGGER.warn("IrisCompanionMesh: build failed ({}); will fall back to vanilla path", t.getMessage());
         this.failed = true;
         this.destroy();
         return false;
      } finally {
         GlStateManager._glBindVertexArray(previousVao);
         GL15.glBindBuffer(34962, previousArrayBuffer);
      }
   }

   private void primeIrisExtendedVertexAttributes(int stride) {
      if (!this.elementOffsets.containsKey("iris_Entity")) {
         this.irisExtendedAttrsPrimedInVao = false;
      } else {
         int previousArrayBuffer = GL11.glGetInteger(34964);

         try {
            GL15.glBindBuffer(34962, this.vboId);
            this.bindIrisVertexAttributeAtLocation(11, "iris_Entity", stride);
            this.bindIrisVertexAttributeAtLocation(12, "mc_midTexCoord", stride);
            this.bindIrisVertexAttributeAtLocation(13, "at_tangent", stride);
            this.irisExtendedAttrsPrimedInVao = true;
         } finally {
            GL15.glBindBuffer(34962, previousArrayBuffer);
         }
      }
   }

   private void computeObjBboxAndWeights() {
      float minX = Float.POSITIVE_INFINITY;
      float minY = Float.POSITIVE_INFINITY;
      float minZ = Float.POSITIVE_INFINITY;
      float maxX = Float.NEGATIVE_INFINITY;
      float maxY = Float.NEGATIVE_INFINITY;
      float maxZ = Float.NEGATIVE_INFINITY;
      int provisionalVertexCount = 0;

      for (BakedQuad quad : this.quads) {
         int[] raw = quad.getVertices();
         if (raw != null && raw.length >= 32) {
            for (int i = 0; i < 4; i++) {
               int base = i * 8;
               float x = Float.intBitsToFloat(raw[base + 0]);
               float y = Float.intBitsToFloat(raw[base + 1]);
               float z = Float.intBitsToFloat(raw[base + 2]);
               if (x < minX) {
                  minX = x;
               }

               if (x > maxX) {
                  maxX = x;
               }

               if (y < minY) {
                  minY = y;
               }

               if (y > maxY) {
                  maxY = y;
               }

               if (z < minZ) {
                  minZ = z;
               }

               if (z > maxZ) {
                  maxZ = z;
               }

               provisionalVertexCount++;
            }
         }
      }

      if (Float.isFinite(minX) && provisionalVertexCount != 0) {
         this.objBbox[0] = minX;
         this.objBbox[1] = minY;
         this.objBbox[2] = minZ;
         this.objBbox[3] = maxX;
         this.objBbox[4] = maxY;
         this.objBbox[5] = maxZ;
         float sx = Math.max(maxX - minX, 1.0E-4F);
         float sy = Math.max(maxY - minY, 1.0E-4F);
         float sz = Math.max(maxZ - minZ, 1.0E-4F);
         float invSx = 1.0F / sx;
         float invSy = 1.0F / sy;
         float invSz = 1.0F / sz;
         this.perVertexCornerWeights = new float[provisionalVertexCount * 8];
         this.perVertexSlicedWeights = new float[provisionalVertexCount * 16];
         int wBase = 0;
         int wsBase = 0;

         for (BakedQuad quad : this.quads) {
            int[] raw = quad.getVertices();
            if (raw != null && raw.length >= 32) {
               for (int i = 0; i < 4; i++) {
                  int base = i * 8;
                  float x = Float.intBitsToFloat(raw[base + 0]);
                  float y = Float.intBitsToFloat(raw[base + 1]);
                  float z = Float.intBitsToFloat(raw[base + 2]);
                  float wx = Math.max(0.0F, Math.min(1.0F, (x - minX) * invSx));
                  float wy = Math.max(0.0F, Math.min(1.0F, (y - minY) * invSy));
                  float wz = Math.max(0.0F, Math.min(1.0F, (z - minZ) * invSz));
                  float oneMinusWx = 1.0F - wx;
                  float oneMinusWy = 1.0F - wy;
                  float oneMinusWz = 1.0F - wz;
                  float axy0 = oneMinusWx * oneMinusWy;
                  float axy1 = wx * oneMinusWy;
                  float axy2 = oneMinusWx * wy;
                  float axy3 = wx * wy;
                  this.perVertexCornerWeights[wBase + 0] = axy0 * oneMinusWz;
                  this.perVertexCornerWeights[wBase + 1] = axy1 * oneMinusWz;
                  this.perVertexCornerWeights[wBase + 2] = axy2 * oneMinusWz;
                  this.perVertexCornerWeights[wBase + 3] = axy3 * oneMinusWz;
                  this.perVertexCornerWeights[wBase + 4] = axy0 * wz;
                  this.perVertexCornerWeights[wBase + 5] = axy1 * wz;
                  this.perVertexCornerWeights[wBase + 6] = axy2 * wz;
                  this.perVertexCornerWeights[wBase + 7] = axy3 * wz;
                  wBase += 8;
                  float ty = wy * 3.0F;
                  int s0 = (int)Math.floor(ty);
                  if (s0 < 0) {
                     s0 = 0;
                  }

                  if (s0 > 3) {
                     s0 = 3;
                  }

                  int s1 = Math.min(s0 + 1, 3);
                  float fy = ty - s0;
                  if (fy < 0.0F) {
                     fy = 0.0F;
                  }

                  if (fy > 1.0F) {
                     fy = 1.0F;
                  }

                  float w0 = 1.0F - fy;
                  float w1 = fy;
                  float b00 = oneMinusWx * oneMinusWz;
                  float b10 = wx * oneMinusWz;
                  float b01 = oneMinusWx * wz;
                  float b11 = wx * wz;

                  for (int p = 0; p < 16; p++) {
                     this.perVertexSlicedWeights[wsBase + p] = 0.0F;
                  }

                  int o0 = wsBase + s0 * 4;
                  int o1 = wsBase + s1 * 4;
                  this.perVertexSlicedWeights[o0 + 0] = b00 * w0;
                  this.perVertexSlicedWeights[o0 + 1] = b10 * w0;
                  this.perVertexSlicedWeights[o0 + 2] = b01 * w0;
                  this.perVertexSlicedWeights[o0 + 3] = b11 * w0;
                  this.perVertexSlicedWeights[o1 + 0] = this.perVertexSlicedWeights[o1 + 0] + b00 * w1;
                  this.perVertexSlicedWeights[o1 + 1] = this.perVertexSlicedWeights[o1 + 1] + b10 * w1;
                  this.perVertexSlicedWeights[o1 + 2] = this.perVertexSlicedWeights[o1 + 2] + b01 * w1;
                  this.perVertexSlicedWeights[o1 + 3] = this.perVertexSlicedWeights[o1 + 3] + b11 * w1;
                  wsBase += 16;
               }
            }
         }
      } else {
         this.objBbox[0] = this.objBbox[1] = this.objBbox[2] = 0.0F;
         this.objBbox[3] = this.objBbox[4] = this.objBbox[5] = 1.0F;
         this.perVertexCornerWeights = null;
         this.perVertexSlicedWeights = null;
      }
   }

   @Override
   public boolean supportsPerVertexLightmap() {
      return this.built && this.uv2Location != -1 && this.perVertexCornerWeights != null && this.vertexCount > 0;
   }

   public boolean supportsSlicedPerVertexLightmap() {
      return this.built && this.uv2Location != -1 && this.perVertexSlicedWeights != null && this.vertexCount > 0;
   }

   @Override
   public void ensureLightmapCapacity(int requiredInstances) {
      if (this.supportsPerVertexLightmap()) {
         if (requiredInstances > this.lightmapInstanceCapacity) {
            int newCapacity = Math.max(8, this.lightmapInstanceCapacity);

            while (newCapacity < requiredInstances) {
               newCapacity <<= 1;
            }

            int perSlotBytes = this.vertexCount * 4;
            int totalBytes = newCapacity * perSlotBytes;
            if (this.lightmapMapped != null && this.lightmapVboId != -1) {
               try {
                  GL15.glBindBuffer(34962, this.lightmapVboId);
                  GL15.glUnmapBuffer(34962);
               } catch (Throwable var39) {
               }
            }

            if (this.lightmapStagingMapped != null && this.lightmapStagingVboId != -1) {
               try {
                  GL15.glBindBuffer(34962, this.lightmapStagingVboId);
                  GL15.glUnmapBuffer(34962);
               } catch (Throwable var38) {
               }
            }

            if (this.lightmapVboId != -1) {
               GL15.glDeleteBuffers(this.lightmapVboId);
               this.lightmapVboId = -1;
            }

            if (this.lightmapStagingVboId != -1) {
               GL15.glDeleteBuffers(this.lightmapStagingVboId);
               this.lightmapStagingVboId = -1;
            }

            if (this.lightmapStagingFence != 0L) {
               try {
                  GL32.glDeleteSync(this.lightmapStagingFence);
               } catch (Throwable var37) {
               }

               this.lightmapStagingFence = 0L;
            }

            this.lightmapMapped = null;
            this.lightmapPersistentMapped = false;
            this.lightmapPersistentDirty = false;
            this.lightmapShortView = null;
            this.lightmapStagingMapped = null;
            this.lightmapStagingShortView = null;
            this.lightmapStagingAvailable = false;
            if (this.lightmapCpuScratch != null) {
               MemoryUtil.memFree(this.lightmapCpuScratch);
               this.lightmapCpuScratch = null;
            }

            this.lightmapVboId = GL15.glGenBuffers();
            if (this.lightmapVboId == 0) {
               this.lightmapVboId = -1;
               MainRegistry.LOGGER.warn("IrisCompanionMesh: failed to allocate lightmap VBO; falling back to constant UV2");
            } else {
               boolean canPersistentMap = false;

               try {
                  GLCapabilities caps = GL.getCapabilities();
                  canPersistentMap = caps != null && (caps.OpenGL44 || caps.GL_ARB_buffer_storage);
               } catch (Throwable ignored) {
                  canPersistentMap = false;
               }

               int previousArrayBuffer = GL11.glGetInteger(34964);

               try {
                  GL15.glBindBuffer(34962, this.lightmapVboId);
                  if (canPersistentMap) {
                     try {
                        int storageFlags = 450;
                        if (GL.getCapabilities().OpenGL44) {
                           GL44.glBufferStorage(34962, totalBytes, storageFlags);
                        } else {
                           GL44.glBufferStorage(34962, totalBytes, storageFlags);
                        }

                        int mapFlags = 194;
                        this.lightmapMapped = GL30.glMapBufferRange(34962, 0L, totalBytes, mapFlags, this.lightmapMapped);
                        if (this.lightmapMapped != null) {
                           this.lightmapMapped.order(ByteOrder.nativeOrder());
                           this.lightmapShortView = this.lightmapMapped.asShortBuffer();
                           this.lightmapPersistentMapped = true;
                        }
                     } catch (Throwable t) {
                        this.lightmapMapped = null;
                        this.lightmapShortView = null;
                        this.lightmapPersistentMapped = false;
                        MainRegistry.LOGGER.debug("IrisCompanionMesh: persistent mapping unavailable, falling back ({})", t.toString());
                     }
                  }

                  if (!this.lightmapPersistentMapped) {
                     this.lightmapCpuScratch = MemoryUtil.memAlloc(totalBytes).order(ByteOrder.nativeOrder());
                     this.lightmapShortView = this.lightmapCpuScratch.asShortBuffer();
                     GL15.glBufferData(34962, totalBytes, 35048);
                  }
               } finally {
                  GL15.glBindBuffer(34962, previousArrayBuffer);
               }

               if (!this.lightmapPersistentMapped && canPersistentMap) {
                  try {
                     this.lightmapStagingVboId = GL15.glGenBuffers();
                     if (this.lightmapStagingVboId != 0) {
                        GL15.glBindBuffer(34962, this.lightmapStagingVboId);
                        int storageFlags = 322;
                        GL44.glBufferStorage(34962, totalBytes, storageFlags);
                        int mapFlags = 82;
                        this.lightmapStagingMapped = GL30.glMapBufferRange(34962, 0L, totalBytes, mapFlags, this.lightmapStagingMapped);
                        if (this.lightmapStagingMapped != null) {
                           this.lightmapStagingMapped.order(ByteOrder.nativeOrder());
                           this.lightmapStagingShortView = this.lightmapStagingMapped.asShortBuffer();
                           this.lightmapStagingAvailable = true;
                        }
                     }
                  } catch (Throwable t) {
                     this.lightmapStagingMapped = null;
                     this.lightmapStagingShortView = null;
                     this.lightmapStagingAvailable = false;
                     if (this.lightmapStagingVboId != -1) {
                        try {
                           GL15.glDeleteBuffers(this.lightmapStagingVboId);
                        } catch (Throwable var35) {
                        }
                     }

                     this.lightmapStagingVboId = -1;
                  } finally {
                     try {
                        GL15.glBindBuffer(34962, previousArrayBuffer);
                        GL15.glBindBuffer(36662, 0);
                        GL15.glBindBuffer(36663, 0);
                     } catch (Throwable var34) {
                     }
                  }
               }

               this.lightmapInstanceCapacity = newCapacity;
               this.perVertexLightmapActive = false;
               this.lightmapCurrentSlot = -1;
               this.lightmapKeyToSlot.clear();
               this.lightmapSlotKey = new long[newCapacity];
               this.lightmapSlotLastUsed = new long[newCapacity];
               this.lightmapSlotAllocCounter = 0L;
               this.lightmapEvictCursor = 0;
               this.lightmapDirtyMinSlot = Integer.MAX_VALUE;
               this.lightmapDirtyMaxSlot = -1;
            }
         }
      }
   }

   public long allocLightmapSlot(long key) {
      if (!this.supportsPerVertexLightmap()) {
         return 0L;
      }

      if (key == 0L) {
         key = 1L;
      }

      int slot = this.lightmapKeyToSlot.getOrDefault(key, -1);
      if (slot >= 0 && slot < this.lightmapInstanceCapacity) {
         this.touchLightmapSlot(slot);
         return 4294967296L | slot & 4294967295L;
      }

      if (this.lightmapInstanceCapacity <= 0) {
         return 0L;
      }

      slot = this.pickLruLightmapSlot();
      long oldKey = slot < this.lightmapSlotKey.length ? this.lightmapSlotKey[slot] : 0L;
      if (oldKey != 0L) {
         this.lightmapKeyToSlot.remove(oldKey);
      }

      if (slot >= this.lightmapSlotKey.length) {
         int newLen = Math.max(this.lightmapInstanceCapacity, slot + 1);
         long[] grown = new long[newLen];
         System.arraycopy(this.lightmapSlotKey, 0, grown, 0, this.lightmapSlotKey.length);
         this.lightmapSlotKey = grown;
         long[] grownUsed = new long[newLen];
         System.arraycopy(this.lightmapSlotLastUsed, 0, grownUsed, 0, this.lightmapSlotLastUsed.length);
         this.lightmapSlotLastUsed = grownUsed;
      }

      this.lightmapSlotKey[slot] = key;
      this.lightmapKeyToSlot.put(key, slot);
      this.touchLightmapSlot(slot);
      return slot & 4294967295L;
   }

   private void touchLightmapSlot(int slot) {
      if (slot >= 0) {
         this.lightmapSlotAllocCounter++;
         if (slot < this.lightmapSlotLastUsed.length) {
            this.lightmapSlotLastUsed[slot] = this.lightmapSlotAllocCounter;
         }
      }
   }

   private int pickLruLightmapSlot() {
      int cap = this.lightmapInstanceCapacity;
      if (cap <= 0) {
         return 0;
      }

      int len = Math.min(cap, this.lightmapSlotLastUsed.length);
      int best = -1;
      long bestStamp = Long.MAX_VALUE;

      for (int i = 0; i < len; i++) {
         long s = this.lightmapSlotLastUsed[i];
         if (s == 0L) {
            return i;
         }

         if (s < bestStamp) {
            bestStamp = s;
            best = i;
         }
      }

      if (best >= 0) {
         return best;
      }

      int slot = this.lightmapEvictCursor++;
      if (this.lightmapEvictCursor >= cap) {
         this.lightmapEvictCursor = 0;
      }

      return slot;
   }

   public int getOrAllocateLightmapSlot(long key) {
      return (int)(this.allocLightmapSlot(key) & 4294967295L);
   }

   private void markSlotDirty(int slot) {
      if (this.lightmapPersistentMapped) {
         this.lightmapPersistentDirty = true;
      } else {
         if (slot < this.lightmapDirtyMinSlot) {
            this.lightmapDirtyMinSlot = slot;
         }

         if (slot > this.lightmapDirtyMaxSlot) {
            this.lightmapDirtyMaxSlot = slot;
         }
      }
   }

   public void uploadDirtySlotsIfNeeded() {
      if (this.supportsPerVertexLightmap()) {
         if (this.lightmapPersistentMapped) {
            if (this.lightmapPersistentDirty) {
               try {
                  GLCapabilities caps = GL.getCapabilities();
                  if (caps != null && caps.OpenGL42) {
                     GL42.glMemoryBarrier(16384);
                  }
               } catch (Throwable var4) {
               }

               this.lightmapPersistentDirty = false;
            }
         } else if (this.lightmapVboId != -1 && this.lightmapCpuScratch != null) {
            if (this.lightmapDirtyMaxSlot >= this.lightmapDirtyMinSlot) {
               int firstSlot = this.lightmapDirtyMinSlot;
               int lastSlot = this.lightmapDirtyMaxSlot;
               int slotCount = lastSlot - firstSlot + 1;
               this.uploadLightmapRange(firstSlot, slotCount);
               this.lightmapDirtyMinSlot = Integer.MAX_VALUE;
               this.lightmapDirtyMaxSlot = -1;
            }
         }
      }
   }

   @Override
   public void finishLightmapWrites() {
      this.uploadDirtySlotsIfNeeded();
   }

   @Override
   public void writeInstanceLightmap(int slotIndex, float[] corner16) {
      if (this.supportsPerVertexLightmap()) {
         if (slotIndex >= 0 && slotIndex < this.lightmapInstanceCapacity) {
            if (corner16 != null && corner16.length >= 16) {
               if (this.lightmapShortView != null) {
                  boolean sliced = corner16.length >= 32 && this.perVertexSlicedWeights != null;
                  float[] w = sliced ? this.perVertexSlicedWeights : this.perVertexCornerWeights;
                  ShortBuffer dst = this.lightmapShortView;
                  int requiredLength = this.vertexCount * 2;
                  if (this.lightmapTempArray.length < requiredLength) {
                     this.lightmapTempArray = new short[requiredLength];
                  }

                  int shortOffset = slotIndex * this.vertexCount * 2;
                  if (shortOffset >= 0 && shortOffset + requiredLength <= dst.capacity()) {
                     if (!sliced) {
                        for (int v = 0; v < this.vertexCount; v++) {
                           int wBase = v * 8;
                           float blockU = w[wBase] * corner16[0]
                              + w[wBase + 1] * corner16[2]
                              + w[wBase + 2] * corner16[4]
                              + w[wBase + 3] * corner16[6]
                              + w[wBase + 4] * corner16[8]
                              + w[wBase + 5] * corner16[10]
                              + w[wBase + 6] * corner16[12]
                              + w[wBase + 7] * corner16[14];
                           float skyV = w[wBase] * corner16[1]
                              + w[wBase + 1] * corner16[3]
                              + w[wBase + 2] * corner16[5]
                              + w[wBase + 3] * corner16[7]
                              + w[wBase + 4] * corner16[9]
                              + w[wBase + 5] * corner16[11]
                              + w[wBase + 6] * corner16[13]
                              + w[wBase + 7] * corner16[15];
                           int bu = (int)(blockU + 0.5F);
                           int sv = (int)(skyV + 0.5F);
                           bu = bu < 0 ? 0 : (bu > 240 ? 240 : bu);
                           sv = sv < 0 ? 0 : (sv > 240 ? 240 : sv);
                           this.lightmapTempArray[v * 2] = (short)bu;
                           this.lightmapTempArray[v * 2 + 1] = (short)sv;
                        }
                     } else {
                        for (int v = 0; v < this.vertexCount; v++) {
                           float blockU = 0.0F;
                           float skyV = 0.0F;
                           int wBase = v * 16;

                           for (int p = 0; p < 16; p++) {
                              float wp = w[wBase + p];
                              blockU += wp * corner16[p * 2];
                              skyV += wp * corner16[p * 2 + 1];
                           }

                           int bu = (int)(blockU + 0.5F);
                           int sv = (int)(skyV + 0.5F);
                           this.lightmapTempArray[v * 2] = (short)(bu < 0 ? 0 : (bu > 240 ? 240 : bu));
                           this.lightmapTempArray[v * 2 + 1] = (short)(sv < 0 ? 0 : (sv > 240 ? 240 : sv));
                        }
                     }

                     int prevPos = dst.position();
                     dst.position(shortOffset);
                     dst.put(this.lightmapTempArray, 0, requiredLength);
                     dst.position(prevPos);
                     this.markSlotDirty(slotIndex);
                  }
               }
            }
         }
      }
   }

   public void uploadLightmapRange(int firstSlot, int slotCount) {
      if (this.supportsPerVertexLightmap()) {
         if (!this.lightmapPersistentMapped) {
            if (this.lightmapVboId != -1 && this.lightmapCpuScratch != null) {
               if (slotCount > 0) {
                  int perSlotBytes = this.vertexCount * 4;
                  int byteOffset = firstSlot * perSlotBytes;
                  int byteLen = slotCount * perSlotBytes;
                  if (byteOffset >= 0 && byteOffset + byteLen <= this.lightmapCpuScratch.capacity()) {
                     int previousArrayBuffer = GL11.glGetInteger(34964);

                     try {
                        this.lightmapCpuScratch.position(byteOffset);
                        this.lightmapCpuScratch.limit(byteOffset + byteLen);
                        if (this.lightmapStagingAvailable && this.lightmapStagingVboId != -1 && this.lightmapStagingMapped != null) {
                           boolean stagingSafe = true;
                           if (this.lightmapStagingFence != 0L) {
                              int wait = GL32.glClientWaitSync(this.lightmapStagingFence, 0, 0L);
                              if (wait == 37147) {
                                 stagingSafe = false;
                              } else {
                                 try {
                                    GL32.glDeleteSync(this.lightmapStagingFence);
                                 } catch (Throwable var21) {
                                 }

                                 this.lightmapStagingFence = 0L;
                              }
                           }

                           if (stagingSafe) {
                              long dstOffset = byteOffset;
                              ByteBuffer src = this.lightmapCpuScratch.duplicate();
                              src.position(byteOffset).limit(byteOffset + byteLen);
                              ByteBuffer dst = this.lightmapStagingMapped.duplicate();
                              dst.position(byteOffset).limit(byteOffset + byteLen);
                              dst.put(src);

                              try {
                                 GL15.glBindBuffer(34962, this.lightmapStagingVboId);
                                 GL30.glFlushMappedBufferRange(34962, dstOffset, byteLen);
                                 GL15.glBindBuffer(36662, this.lightmapStagingVboId);
                                 GL15.glBindBuffer(36663, this.lightmapVboId);
                                 GL31.glCopyBufferSubData(36662, 36663, dstOffset, dstOffset, byteLen);
                                 this.lightmapStagingFence = GL32.glFenceSync(37143, 0);
                              } finally {
                                 GL15.glBindBuffer(36662, 0);
                                 GL15.glBindBuffer(36663, 0);
                              }
                           } else {
                              GL15.glBindBuffer(34962, this.lightmapVboId);
                              GL15.glBufferSubData(34962, byteOffset, this.lightmapCpuScratch);
                           }
                        } else {
                           GL15.glBindBuffer(34962, this.lightmapVboId);
                           GL15.glBufferSubData(34962, byteOffset, this.lightmapCpuScratch);
                        }
                     } finally {
                        this.lightmapCpuScratch.clear();
                        GL15.glBindBuffer(34962, previousArrayBuffer);
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public void activatePerVertexLightmap() {
      if (this.supportsPerVertexLightmap()) {
         if (this.lightmapVboId != -1) {
            if (!this.perVertexLightmapActive) {
               if (this.ensureCompanionVaoBound()) {
                  int previousArrayBuffer = GL11.glGetInteger(34964);

                  try {
                     GL15.glBindBuffer(34962, this.lightmapVboId);
                     GL30.glVertexAttribIPointer(this.uv2Location, 2, 5123, 4, 0L);
                     GL20.glEnableVertexAttribArray(this.uv2Location);
                  } finally {
                     GL15.glBindBuffer(34962, previousArrayBuffer);
                  }

                  this.perVertexLightmapActive = true;
                  this.lightmapCurrentSlot = 0;
               }
            }
         }
      }
   }

   @Override
   public void bindLightmapForInstance(int slotIndex) {
      if (this.perVertexLightmapActive) {
         if (slotIndex != this.lightmapCurrentSlot) {
            if (slotIndex >= 0 && slotIndex < this.lightmapInstanceCapacity) {
               if (this.ensureCompanionVaoBound()) {
                  long byteOffset = (long)slotIndex * this.vertexCount * 4L;
                  int previousArrayBuffer = GL11.glGetInteger(34964);

                  try {
                     GL15.glBindBuffer(34962, this.lightmapVboId);
                     GL30.glVertexAttribIPointer(this.uv2Location, 2, 5123, 4, byteOffset);
                  } finally {
                     GL15.glBindBuffer(34962, previousArrayBuffer);
                  }

                  this.lightmapCurrentSlot = slotIndex;
               }
            }
         }
      }
   }

   @Override
   public void restoreConstantLightmap() {
      if (this.perVertexLightmapActive) {
         if (this.ensureCompanionVaoBound() && this.uv2Location != -1) {
            GL20.glDisableVertexAttribArray(this.uv2Location);
         }

         this.perVertexLightmapActive = false;
         this.lightmapCurrentSlot = -1;
      }
   }

   private static boolean shouldNormalize(VertexFormatElement element) {
      return switch (element.getUsage()) {
         case COLOR, NORMAL -> true;
         default -> false;
      };
   }

   private static boolean isIntegerAttribute(VertexFormatElement element) {
      if (element.getUsage() != Usage.UV) {
         return false;
      }

      int idx = element.getIndex();
      return idx == 1 || idx == 2;
   }

   @Override
   public void prepareForShader(int programId) {
      if (programId > 0) {
         if (this.actualFormat != null) {
            if (this.built && this.vaoId > 0) {
               long currentGen = IrisExtendedShaderAccess.getPipelineGeneration();
               if (this.cachedProgramPipelineGeneration != currentGen) {
                  for (int i = 0; i < 4; i++) {
                     this.cachedProgramIds[i] = -1;
                  }

                  this.nextCacheSlot = 0;
                  this.cachedProgramPipelineGeneration = currentGen;
               }

               for (int i = 0; i < 4; i++) {
                  if (this.cachedProgramIds[i] == programId) {
                     return;
                  }
               }

               if (this.irisExtendedAttrsPrimedInVao) {
                  this.cachedProgramIds[this.nextCacheSlot] = programId;
                  this.nextCacheSlot = (this.nextCacheSlot + 1) % 4;
               } else if (this.ensureCompanionVaoBound()) {
                  int previousArrayBuffer = GL11.glGetInteger(34964);

                  label68:
                  try {
                     GL15.glBindBuffer(34962, this.vboId);
                     int stride = this.actualFormat.getVertexSize();
                     this.bindIrisAttribute(programId, "iris_Entity", stride);
                     this.bindIrisAttribute(programId, "mc_midTexCoord", stride);
                     this.bindIrisAttribute(programId, "at_tangent", stride);
                     break label68;
                  } finally {
                     GL15.glBindBuffer(34962, previousArrayBuffer);
                  }
               }
            }
         }
      }
   }

   private boolean ensureCompanionVaoBound() {
      if (this.built && this.vaoId > 0) {
         GlStateManager._glBindVertexArray(this.vaoId);
         return GL11.glGetInteger(34229) == this.vaoId;
      } else {
         return false;
      }
   }

   private void bindIrisAttribute(int programId, String attributeName, int stride) {
      if (this.built && this.vaoId > 0) {
         if (this.ensureCompanionVaoBound()) {
            int location = GL20.glGetAttribLocation(programId, attributeName);
            if (location >= 0) {
               this.bindIrisVertexAttributeAtLocation(location, attributeName, stride);
            }
         }
      }
   }

   private void bindIrisVertexAttributeAtLocation(int location, String attributeName, int stride) {
      Integer offsetBoxed = this.elementOffsets.get(attributeName);
      VertexFormatElement element = this.elementByName.get(attributeName);
      if (offsetBoxed != null && element != null) {
         int offset = offsetBoxed;
         int glType = element.getType().getGlType();
         int count = element.getCount();
         if (isIntegerGlType(glType)) {
            GL30.glVertexAttribIPointer(location, count, glType, stride, offset);
         } else {
            boolean normalize = shouldNormalizeForLinkerBind(attributeName, element);
            GL20.glVertexAttribPointer(location, count, glType, normalize, stride, offset);
         }

         GL20.glEnableVertexAttribArray(location);
      }
   }

   private static boolean isIntegerGlType(int glType) {
      return glType == 5120 || glType == 5121 || glType == 5122 || glType == 5123 || glType == 5124 || glType == 5125;
   }

   private static boolean shouldNormalizeForLinkerBind(String attributeName, VertexFormatElement element) {
      return "at_tangent".equals(attributeName) ? true : element.getUsage() == Usage.NORMAL;
   }

   public boolean isBuilt() {
      return this.built;
   }

   public boolean isFailed() {
      return this.failed;
   }

   @Override
   public int getVaoId() {
      return this.vaoId;
   }

   public int getVboId() {
      return this.vboId;
   }

   @Override
   public int getIndexCount() {
      return this.indexCount;
   }

   @Override
   public int getUv2Location() {
      return this.uv2Location;
   }

   public void bindVaoIfNeeded() {
      if (!this.ensureCompanionVaoBound()) {
         MainRegistry.LOGGER.warn("IrisCompanionMesh: failed to bind VAO {}", this.vaoId);
      }
   }

   public int getVertexCount() {
      return this.vertexCount;
   }

   @Override
   public void destroy() {
      int vboToDelete = this.vboId;
      int eboToDelete = this.eboId;
      int vaoToDelete = this.vaoId;
      int lightmapVboToDelete = this.lightmapVboId;
      int lightmapStagingVboToDelete = this.lightmapStagingVboId;
      long lightmapStagingFenceToDelete = this.lightmapStagingFence;
      ByteBuffer scratchToFree = this.lightmapCpuScratch;
      ByteBuffer mappedToUnmap = this.lightmapMapped;
      ByteBuffer stagingMappedToUnmap = this.lightmapStagingMapped;
      this.vboId = -1;
      this.eboId = -1;
      this.vaoId = -1;
      this.lightmapVboId = -1;
      this.lightmapStagingVboId = -1;
      this.lightmapStagingFence = 0L;
      this.lightmapCpuScratch = null;
      this.lightmapMapped = null;
      this.lightmapShortView = null;
      this.lightmapPersistentMapped = false;
      this.lightmapPersistentDirty = false;
      this.lightmapInstanceCapacity = 0;
      this.perVertexLightmapActive = false;
      this.lightmapCurrentSlot = -1;
      this.perVertexCornerWeights = null;
      this.perVertexSlicedWeights = null;
      this.lightmapStagingMapped = null;
      this.lightmapStagingShortView = null;
      this.lightmapStagingAvailable = false;
      this.indexCount = 0;
      this.vertexCount = 0;
      this.built = false;
      this.irisExtendedAttrsPrimedInVao = false;
      Runnable deleter = () -> {
         try {
            if (vboToDelete != -1) {
               GL15.glDeleteBuffers(vboToDelete);
            }

            if (eboToDelete != -1) {
               GL15.glDeleteBuffers(eboToDelete);
            }

            if (mappedToUnmap != null && lightmapVboToDelete != -1) {
               try {
                  GL15.glBindBuffer(34962, lightmapVboToDelete);
                  GL15.glUnmapBuffer(34962);
               } catch (Throwable var12) {
               }
            }

            if (lightmapVboToDelete != -1) {
               GL15.glDeleteBuffers(lightmapVboToDelete);
            }

            if (stagingMappedToUnmap != null && lightmapStagingVboToDelete != -1) {
               try {
                  GL15.glBindBuffer(34962, lightmapStagingVboToDelete);
                  GL15.glUnmapBuffer(34962);
               } catch (Throwable var11x) {
               }
            }

            if (lightmapStagingVboToDelete != -1) {
               GL15.glDeleteBuffers(lightmapStagingVboToDelete);
            }

            if (lightmapStagingFenceToDelete != 0L) {
               try {
                  GL32.glDeleteSync(lightmapStagingFenceToDelete);
               } catch (Throwable var10x) {
               }
            }

            if (vaoToDelete != -1) {
               GL30.glDeleteVertexArrays(vaoToDelete);
            }
         } catch (Throwable t) {
            MainRegistry.LOGGER.error("IrisCompanionMesh: cleanup failed", t);
         }
      };
      if (RenderSystem.isOnRenderThread()) {
         deleter.run();
      } else {
         RenderSystem.recordRenderCall(deleter::run);
      }

      if (scratchToFree != null) {
         MemoryUtil.memFree(scratchToFree);
      }
   }
}
