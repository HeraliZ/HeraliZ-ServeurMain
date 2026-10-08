package com.hbm_m.client.render;

import com.hbm_m.main.MainRegistry;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;
import org.lwjgl.system.MemoryUtil;

@OnlyIn(Dist.CLIENT)
public record PartGeometry(List<BakedQuad> solidQuads) {
   public static final long BAKE_SEED = 42L;
   public static final PartGeometry EMPTY = new PartGeometry(List.of());

   public PartGeometry {
      solidQuads = List.copyOf(solidQuads);
   }

   public boolean isEmpty() {
      return this.solidQuads.isEmpty();
   }

   public static PartGeometry compile(BakedModel modelPart, String partName) {
      if (modelPart == null) {
         return EMPTY;
      } else {
         modelPart = AbstractPartBasedRenderer.unwrapFabricForwardingModels(modelPart);
         List<BakedQuad> quads = collectSolidQuads(modelPart, partName);
         if (quads.isEmpty()) {
            MainRegistry.LOGGER.debug("PartGeometry: Part '{}' has NO QUADS, skipping", partName);
            return EMPTY;
         } else {
            MainRegistry.LOGGER.debug("PartGeometry: Part '{}' - {} quads", partName, quads.size());
            return new PartGeometry(quads);
         }
      }
   }

   public static List<BakedQuad> collectSolidQuads(BakedModel modelPart, String partName) {
      if (modelPart == null) {
         return List.of();
      }

      List<BakedQuad> quads = new ArrayList<>();
      RandomSource random = RandomSource.create(42L);
      random.setSeed(42L);
      quads.addAll(modelPart.getQuads(null, null, random, ModelData.EMPTY, RenderType.solid()));

      for (Direction direction : Direction.values()) {
         random.setSeed(42L);
         quads.addAll(modelPart.getQuads(null, direction, random, ModelData.EMPTY, RenderType.solid()));
      }

      return quads.isEmpty() ? List.of() : Collections.unmodifiableList(quads);
   }

   public SingleMeshVboRenderer.VboData toVboData(String partName) {
      return buildVboDataFromQuads(this.solidQuads, partName, 0);
   }

   public SingleMeshVboRenderer.VboData toVboData(String partName, int perVertexBoneId) {
      return buildVboDataFromQuads(this.solidQuads, partName, perVertexBoneId);
   }

   public static SingleMeshVboRenderer.VboData buildVboDataFromQuads(List<BakedQuad> quads, String partName) {
      return buildVboDataFromQuads(quads, partName, 0);
   }

   public static SingleMeshVboRenderer.VboData buildVboDataFromQuads(List<BakedQuad> quads, String partName, int perVertexBoneId) {
      if (quads != null && !quads.isEmpty()) {
         int quadCount = quads.size();
         int vertexCount = quadCount * 4;
         int indexCapacity = quadCount * 6;
         int vertexStrideBytes = 36;
         ByteBuffer vb = null;
         IntBuffer ib = null;
         int indexOffset = 0;
         float minX = Float.POSITIVE_INFINITY;
         float minY = Float.POSITIVE_INFINITY;
         float minZ = Float.POSITIVE_INFINITY;
         float maxX = Float.NEGATIVE_INFINITY;
         float maxY = Float.NEGATIVE_INFINITY;
         float maxZ = Float.NEGATIVE_INFINITY;

         try {
            vb = MemoryUtil.memAlloc(vertexCount * 36);
            ib = MemoryUtil.memAllocInt(indexCapacity);

            for (BakedQuad quad : quads) {
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

                     float u = Float.intBitsToFloat(raw[base + 4]);
                     float v = Float.intBitsToFloat(raw[base + 5]);
                     int normalPacked = raw[base + 7];
                     float nx = (byte)(normalPacked & 0xFF) / 127.0F;
                     float ny = (byte)(normalPacked >> 8 & 0xFF) / 127.0F;
                     float nz = (byte)(normalPacked >> 16 & 0xFF) / 127.0F;
                     float len = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
                     if (len > 0.001F) {
                        nx /= len;
                        ny /= len;
                        nz /= len;
                     }

                     if (indexOffset == 0 && i == 0) {
                        MainRegistry.LOGGER.debug("PartGeometry VBO: pos({},{},{}), norm({},{},{}), uv({},{})", new Object[]{x, y, z, nx, ny, nz, u, v});
                     }

                     vb.putFloat(x).putFloat(y).putFloat(z);
                     vb.putFloat(nx).putFloat(ny).putFloat(nz);
                     vb.putFloat(u).putFloat(v);
                     vb.putInt(perVertexBoneId);
                  }

                  ib.put(indexOffset + 0);
                  ib.put(indexOffset + 1);
                  ib.put(indexOffset + 2);
                  ib.put(indexOffset + 2);
                  ib.put(indexOffset + 3);
                  ib.put(indexOffset + 0);
                  indexOffset += 4;
               } else {
                  MainRegistry.LOGGER
                     .warn("PartGeometry: Skipping malformed quad in part '{}' (vertex array length = {})", partName, raw == null ? -1 : raw.length);
               }
            }

            if (indexOffset == 0) {
               MainRegistry.LOGGER.debug("PartGeometry: Part '{}' produced no valid quads for VBO", partName);
               MemoryUtil.memFree(vb);
               MemoryUtil.memFree(ib);
               vb = null;
               ib = null;
               return null;
            }

            vb.flip();
            ib.flip();
            if (!Float.isFinite(minX)) {
               minZ = 0.0F;
               minY = 0.0F;
               minX = 0.0F;
               maxZ = 0.0F;
               maxY = 0.0F;
               maxX = 0.0F;
            }

            MainRegistry.LOGGER
               .debug(
                  "PartGeometry VBO: {} vertices, {} indices, bbox min({},{},{}) max({},{},{})",
                  new Object[]{indexOffset, ib.remaining(), minX, minY, minZ, maxX, maxY, maxZ}
               );
            return new SingleMeshVboRenderer.VboData(vb, ib, minX, minY, minZ, maxX, maxY, maxZ, 36);
         } catch (Exception e) {
            if (vb != null) {
               MemoryUtil.memFree(vb);
            }

            if (ib != null) {
               MemoryUtil.memFree(ib);
            }

            throw e;
         } catch (OutOfMemoryError e) {
            MainRegistry.LOGGER.error("PartGeometry: OOM building VBO, vertices ~ {}", vertexCount, e);
            if (vb != null) {
               MemoryUtil.memFree(vb);
            }

            if (ib != null) {
               MemoryUtil.memFree(ib);
            }

            throw e;
         }
      } else {
         return null;
      }
   }
}
