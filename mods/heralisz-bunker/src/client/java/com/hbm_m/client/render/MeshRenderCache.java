package com.hbm_m.client.render;

import com.hbm_m.client.render.shader.IrisBufferHelper;
import com.hbm_m.main.MainRegistry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.BufferBuilder.RenderedBuffer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexBuffer.Usage;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MeshRenderCache {
   private static final int MAX_CACHE_SIZE = 256;
   private static final ConcurrentHashMap<String, SingleMeshVboRenderer> PART_RENDERERS = new ConcurrentHashMap<>();
   private static final Set<String> FAILED_RENDERER_KEYS = ConcurrentHashMap.newKeySet();
   private static final Map<String, PartGeometry> COMPILED_GEOMETRY = Collections.synchronizedMap(new LinkedHashMap<String, PartGeometry>(257, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Entry<String, PartGeometry> eldest) {
         return this.size() > 256;
      }
   });
   private static final Map<String, VertexBuffer> GPU_BUFFERS = Collections.synchronizedMap(new LinkedHashMap<String, VertexBuffer>(257, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Entry<String, VertexBuffer> eldest) {
         if (this.size() > 256) {
            VertexBuffer vb = eldest.getValue();
            if (vb != null) {
               vb.close();
            }

            return true;
         } else {
            return false;
         }
      }
   });
   private static final Map<String, IrisCompanionMesh> IRIS_COMPANION_MESHES = Collections.synchronizedMap(
      new LinkedHashMap<String, IrisCompanionMesh>(257, 0.75F, true) {
         @Override
         protected boolean removeEldestEntry(Entry<String, IrisCompanionMesh> eldest) {
            if (this.size() > 256) {
               IrisCompanionMesh m = eldest.getValue();
               if (m != null) {
                  m.destroy();
               }

               return true;
            } else {
               return false;
            }
         }
      }
   );

   public static List<BakedQuad> getOrCompile(String cacheKey, BakedModel modelPart) {
      return getOrCompilePartGeometry(cacheKey, modelPart).solidQuads();
   }

   public static List<BakedQuad> getOrCompile(Class<?> modelClass, String partName, BakedModel modelPart) {
      String cacheKey = modelClass.getSimpleName() + ":" + partName;
      return getOrCompile(cacheKey, modelPart);
   }

   public static PartGeometry getOrCompilePartGeometry(String cacheKey, BakedModel modelPart) {
      return modelPart == null
         ? PartGeometry.EMPTY
         : COMPILED_GEOMETRY.computeIfAbsent(cacheKey, k -> PartGeometry.compile(modelPart, partNameFromKey(cacheKey)));
   }

   public static VertexBuffer getOrCreateGPUBuffer(String cacheKey, BakedModel modelPart) {
      RenderSystem.assertOnRenderThread();
      return GPU_BUFFERS.computeIfAbsent(cacheKey, k -> {
         List<BakedQuad> quads = getOrCompile(cacheKey, modelPart);
         return uploadToGPU(quads);
      });
   }

   public static SingleMeshVboRenderer getOrCreateRenderer(String partKey, BakedModel model) {
      return FAILED_RENDERER_KEYS.contains(partKey) ? null : PART_RENDERERS.computeIfAbsent(partKey, key -> {
         SingleMeshVboRenderer renderer = createRendererForPart(key, model);
         if (renderer == null) {
            FAILED_RENDERER_KEYS.add(key);
         }

         return renderer;
      });
   }

   public static List<BakedQuad> getOrCompile(String entityType, String partName, BakedModel modelPart) {
      String cacheKey = entityType + ":" + partName;
      return getOrCompile(cacheKey, modelPart);
   }

   public static PartGeometry getOrCompilePartGeometry(String entityType, String partName, BakedModel modelPart) {
      return getOrCompilePartGeometry(entityType + ":" + partName, modelPart);
   }

   public static VertexBuffer getOrCreateGPUBuffer(String entityType, String partName, BakedModel modelPart) {
      String cacheKey = entityType + ":" + partName;
      return getOrCreateGPUBuffer(cacheKey, modelPart);
   }

   public static SingleMeshVboRenderer getOrCreateRenderer(String entityType, String partName, BakedModel model) {
      String partKey = entityType + ":" + partName;
      return getOrCreateRenderer(partKey, model);
   }

   private static String partNameFromKey(String cacheKey) {
      return cacheKey.contains(":") ? cacheKey.substring(cacheKey.lastIndexOf(":") + 1) : cacheKey;
   }

   private static VertexBuffer uploadToGPU(List<BakedQuad> quads) {
      if (quads.isEmpty()) {
         return null;
      }

      BufferBuilder builder = new BufferBuilder(quads.size() * 32);
      IrisBufferHelper.begin(builder, Mode.QUADS, DefaultVertexFormat.BLOCK);
      Pose neutralPose = new PoseStack().last();
      float r = 1.0F;
      float g = 1.0F;
      float b = 1.0F;
      float a = 1.0F;
      int neutralLight = 0;

      for (BakedQuad quad : quads) {
         builder.putBulkData(neutralPose, quad, r, g, b, a, neutralLight, OverlayTexture.NO_OVERLAY, false);
      }

      RenderedBuffer renderedBuffer = builder.end();
      VertexBuffer vbo = new VertexBuffer(Usage.STATIC);
      vbo.bind();
      vbo.upload(renderedBuffer);
      VertexBuffer.unbind();
      return vbo;
   }

   public static SingleMeshVboRenderer getOrCreateRendererFromQuadList(String partKey, List<BakedQuad> quads) {
      if (FAILED_RENDERER_KEYS.contains(partKey)) {
         return null;
      } else {
         return quads != null && !quads.isEmpty() ? PART_RENDERERS.computeIfAbsent(partKey, key -> {
            final String partName = partNameFromKey(key);
            final SingleMeshVboRenderer.VboData prebuiltData = PartGeometry.buildVboDataFromQuads(quads, partName);
            if (prebuiltData == null) {
               FAILED_RENDERER_KEYS.add(key);
               return null;
            } else {
               final List<BakedQuad> quadsForIris = List.copyOf(quads);
               MainRegistry.LOGGER.debug("MeshRenderCache: renderer from quad list '{}', {} quads", partName, quadsForIris.size());
               return new SingleMeshVboRenderer() {
                  private SingleMeshVboRenderer.VboData pendingData = prebuiltData;

                  @Override
                  protected SingleMeshVboRenderer.VboData buildVboData() {
                     SingleMeshVboRenderer.VboData data = this.pendingData;
                     if (data == null) {
                        throw new IllegalStateException("buildVboData() called twice for merged part '" + partName + "'; VboData is one-shot");
                     }

                     this.pendingData = null;
                     return data;
                  }

                  @Override
                  protected List<BakedQuad> getQuadsForIrisPath() {
                     return quadsForIris;
                  }
               };
            }
         }) : null;
      }
   }

   private static SingleMeshVboRenderer createRendererForPart(String partKey, BakedModel model) {
      PartGeometry geo = getOrCompilePartGeometry(partKey, model);
      if (geo.isEmpty()) {
         return null;
      }

      final String partName = partNameFromKey(partKey);
      final SingleMeshVboRenderer.VboData prebuiltData = geo.toVboData(partName);
      if (prebuiltData == null) {
         return null;
      }

      MainRegistry.LOGGER
         .debug("MeshRenderCache: renderer for part '{}', {} vertices", partName, prebuiltData.byteBuffer.remaining() / prebuiltData.bytesPerVertex);
      final List<BakedQuad> quadsForIris = geo.solidQuads();
      return new SingleMeshVboRenderer() {
         private SingleMeshVboRenderer.VboData pendingData = prebuiltData;

         @Override
         protected SingleMeshVboRenderer.VboData buildVboData() {
            SingleMeshVboRenderer.VboData data = this.pendingData;
            if (data == null) {
               throw new IllegalStateException("buildVboData() called twice for part '" + partName + "'; VboData is one-shot");
            }

            this.pendingData = null;
            return data;
         }

         @Override
         protected List<BakedQuad> getQuadsForIrisPath() {
            return quadsForIris;
         }
      };
   }

   public static IrisCompanionMesh getOrCreateIrisCompanion(String cacheKey, BakedModel modelPart) {
      return modelPart == null ? null : IRIS_COMPANION_MESHES.computeIfAbsent(cacheKey, k -> {
         List<BakedQuad> quads = getOrCompile(k, modelPart);
         return quads != null && !quads.isEmpty() ? new IrisCompanionMesh(quads) : null;
      });
   }

   public static IrisCompanionMesh getOrCreateIrisCompanion(String cacheKey, List<BakedQuad> quads) {
      return quads != null && !quads.isEmpty() ? IRIS_COMPANION_MESHES.computeIfAbsent(cacheKey, k -> new IrisCompanionMesh(quads)) : null;
   }

   public static void clear() {
      COMPILED_GEOMETRY.clear();
      GPU_BUFFERS.values().forEach(vb -> {
         if (vb != null) {
            vb.close();
         }
      });
      GPU_BUFFERS.clear();
      IRIS_COMPANION_MESHES.values().forEach(m -> {
         if (m != null) {
            m.destroy();
         }
      });
      IRIS_COMPANION_MESHES.clear();
   }

   public static void clearAll() {
      for (SingleMeshVboRenderer renderer : PART_RENDERERS.values()) {
         if (renderer != null) {
            renderer.cleanup();
         }
      }

      PART_RENDERERS.clear();
      FAILED_RENDERER_KEYS.clear();
      clear();
   }

   public static int getCachedQuadsCount() {
      return COMPILED_GEOMETRY.size();
   }

   public static int getCachedBuffersCount() {
      return GPU_BUFFERS.size();
   }

   public static int getCachedRenderersCount() {
      return PART_RENDERERS.size();
   }

   public static void logCacheStats() {
      MainRegistry.LOGGER.debug("MeshRenderCache stats:");
      MainRegistry.LOGGER.debug("  Compiled part geometry entries: " + getCachedQuadsCount());
      MainRegistry.LOGGER.debug("  GPU buffers: " + getCachedBuffersCount());
      MainRegistry.LOGGER.debug("  Renderers: " + getCachedRenderersCount());
   }
}
