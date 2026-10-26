package com.hbm_m.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraftforge.client.model.pipeline.QuadBakingVertexConsumer;
import net.minecraftforge.client.model.pipeline.QuadBakingVertexConsumer.Buffered;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class ModelHelper {
   private static final int POSITION_OFFSET = 0;
   private static final int MIN_STRIDE = 8;
   private static final Vector4f TMP_POS = new Vector4f();
   private static final Vector4f TMP_NORM = new Vector4f();

   public static void createCuboid(
      List<BakedQuad> quads, Vector3f from, Vector3f to, TextureAtlasSprite sprite, Map<Direction, ModelHelper.UVSpec> uvMap, Set<Direction> facesToSkip
   ) {
      for (Direction direction : Direction.values()) {
         if (!facesToSkip.contains(direction)) {
            ModelHelper.UVSpec spec = uvMap.get(direction);
            if (spec != null) {
               quads.add(createQuad(from, to, direction, sprite, spec));
            }
         }
      }
   }

   private static BakedQuad createQuad(Vector3f from, Vector3f to, Direction direction, TextureAtlasSprite sprite, ModelHelper.UVSpec spec) {
      Buffered builder = new Buffered();
      builder.setSprite(sprite);
      builder.setDirection(direction);
      builder.setHasAmbientOcclusion(true);
      Vector3f normal = direction.step();
      float x0 = from.x() / 16.0F;
      float y0 = from.y() / 16.0F;
      float z0 = from.z() / 16.0F;
      float x1 = to.x() / 16.0F;
      float y1 = to.y() / 16.0F;
      float z1 = to.z() / 16.0F;
      ModelHelper.UVBox uv = spec.box();
      float u0 = sprite.getU(uv.u0);
      float v0 = sprite.getV(uv.v0);
      float u1 = sprite.getU(uv.u1);
      float v1 = sprite.getV(uv.v1);
      switch (direction) {
         case DOWN:
            putVertices(
               builder,
               normal,
               spec.rotate90(),
               new float[]{x0, y0, z1, u0, v1},
               new float[]{x0, y0, z0, u0, v0},
               new float[]{x1, y0, z0, u1, v0},
               new float[]{x1, y0, z1, u1, v1}
            );
            break;
         case UP:
            putVertices(
               builder,
               normal,
               spec.rotate90(),
               new float[]{x0, y1, z0, u0, v0},
               new float[]{x0, y1, z1, u0, v1},
               new float[]{x1, y1, z1, u1, v1},
               new float[]{x1, y1, z0, u1, v0}
            );
            break;
         case NORTH:
            putVertices(
               builder,
               normal,
               spec.rotate90(),
               new float[]{x0, y1, z0, u0, v0},
               new float[]{x1, y1, z0, u1, v0},
               new float[]{x1, y0, z0, u1, v1},
               new float[]{x0, y0, z0, u0, v1}
            );
            break;
         case SOUTH:
            putVertices(
               builder,
               normal,
               spec.rotate90(),
               new float[]{x0, y0, z1, u0, v1},
               new float[]{x1, y0, z1, u1, v1},
               new float[]{x1, y1, z1, u1, v0},
               new float[]{x0, y1, z1, u0, v0}
            );
            break;
         case WEST:
            putVertices(
               builder,
               normal,
               spec.rotate90(),
               new float[]{x0, y0, z1, u0, v1},
               new float[]{x0, y1, z1, u0, v0},
               new float[]{x0, y1, z0, u1, v0},
               new float[]{x0, y0, z0, u1, v1}
            );
            break;
         case EAST:
            putVertices(
               builder,
               normal,
               spec.rotate90(),
               new float[]{x1, y0, z0, u0, v1},
               new float[]{x1, y1, z0, u0, v0},
               new float[]{x1, y1, z1, u1, v0},
               new float[]{x1, y0, z1, u1, v1}
            );
      }

      return builder.getQuad();
   }

   private static void putVertices(QuadBakingVertexConsumer builder, Vector3f normal, boolean rotate, float[] v1, float[] v2, float[] v3, float[] v4) {
      if (!rotate) {
         putVertex(builder, normal, v1[0], v1[1], v1[2], v1[3], v1[4]);
         putVertex(builder, normal, v2[0], v2[1], v2[2], v2[3], v2[4]);
         putVertex(builder, normal, v3[0], v3[1], v3[2], v3[3], v3[4]);
         putVertex(builder, normal, v4[0], v4[1], v4[2], v4[3], v4[4]);
      } else {
         putVertex(builder, normal, v1[0], v1[1], v1[2], v2[3], v2[4]);
         putVertex(builder, normal, v2[0], v2[1], v2[2], v3[3], v3[4]);
         putVertex(builder, normal, v3[0], v3[1], v3[2], v4[3], v4[4]);
         putVertex(builder, normal, v4[0], v4[1], v4[2], v1[3], v1[4]);
      }
   }

   private static void putVertex(QuadBakingVertexConsumer builder, Vector3f normal, float x, float y, float z, float u, float v) {
      builder.vertex(x, y, z).uv(u, v).uv2(0, 0).normal(normal.x(), normal.y(), normal.z()).color(-1).endVertex();
   }

   public static List<BakedQuad> transformQuadsByFacing(List<BakedQuad> quads, int angleDeg) {
      if (quads != null && !quads.isEmpty() && angleDeg != 0) {
         float angleRad = (float)Math.toRadians(angleDeg);
         float cos = (float)Math.cos(angleRad);
         float sin = (float)Math.sin(angleRad);
         float cx = 0.5F;
         float cz = 0.5F;
         List<BakedQuad> result = new ArrayList<>(quads.size());

         for (BakedQuad quad : quads) {
            int[] verts = quad.getVertices();
            if (verts.length < 32) {
               result.add(quad);
            } else {
               int stride = verts.length / 4;
               int normalOffset = stride - 1;
               int[] newVerts = (int[])verts.clone();

               for (int v = 0; v < 4; v++) {
                  int i = v * stride;
                  float x = Float.intBitsToFloat(newVerts[i + 0]);
                  float y = Float.intBitsToFloat(newVerts[i + 0 + 1]);
                  float z = Float.intBitsToFloat(newVerts[i + 0 + 2]);
                  float dx = x - cx;
                  float dz = z - cz;
                  float nx = dx * cos - dz * sin + cx;
                  float nz = dx * sin + dz * cos + cz;
                  newVerts[i] = Float.floatToIntBits(nx);
                  newVerts[i + 1] = Float.floatToIntBits(y);
                  newVerts[i + 2] = Float.floatToIntBits(nz);
                  if (normalOffset < stride) {
                     int packedNormal = newVerts[i + normalOffset];
                     byte nxb = (byte)(packedNormal & 0xFF);
                     byte nyb = (byte)(packedNormal >> 8 & 0xFF);
                     byte nzb = (byte)(packedNormal >> 16 & 0xFF);
                     float nxf = nxb / 127.0F;
                     float nyf = nyb / 127.0F;
                     float nzf = nzb / 127.0F;
                     float rnx = nxf * cos - nzf * sin;
                     float rnz = nxf * sin + nzf * cos;
                     int newPacked = (byte)(rnx * 127.0F) & 255 | ((byte)(nyf * 127.0F) & 255) << 8 | ((byte)(rnz * 127.0F) & 255) << 16;
                     newVerts[i + normalOffset] = newPacked;
                  }
               }

               Direction rotatedDir = rotateDirection(quad.getDirection(), angleDeg);
               result.add(new BakedQuad(newVerts, quad.getTintIndex(), rotatedDir, quad.getSprite(), quad.isShade()));
            }
         }

         return result;
      } else {
         return quads;
      }
   }

   public static List<BakedQuad> offsetQuadUvs(List<BakedQuad> quads, float du, float dv) {
      if (quads != null && !quads.isEmpty() && (du != 0.0F || dv != 0.0F)) {
         List<BakedQuad> result = new ArrayList<>(quads.size());

         for (BakedQuad quad : quads) {
            int[] verts = quad.getVertices();
            if (verts.length < 32) {
               result.add(quad);
            } else {
               int stride = verts.length / 4;
               if (stride < 6) {
                  result.add(quad);
               } else {
                  int[] newVerts = (int[])verts.clone();

                  for (int v = 0; v < 4; v++) {
                     int i = v * stride;
                     float u = Float.intBitsToFloat(newVerts[i + 4]);
                     float vv = Float.intBitsToFloat(newVerts[i + 5]);
                     newVerts[i + 4] = Float.floatToIntBits(u + du);
                     newVerts[i + 5] = Float.floatToIntBits(vv + dv);
                  }

                  result.add(new BakedQuad(newVerts, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade()));
               }
            }
         }

         return result;
      } else {
         return quads;
      }
   }

   public static List<BakedQuad> offsetQuadUvsWrapped(List<BakedQuad> quads, float du, float dv, float uMin, float uMax, float vMin, float vMax) {
      if (quads != null && !quads.isEmpty() && (du != 0.0F || dv != 0.0F)) {
         float uRange = uMax - uMin;
         float vRange = vMax - vMin;
         if (!(uRange <= 0.0F) && !(vRange <= 0.0F)) {
            List<BakedQuad> result = new ArrayList<>(quads.size());

            for (BakedQuad quad : quads) {
               int[] verts = quad.getVertices();
               if (verts.length < 32) {
                  result.add(quad);
               } else {
                  int stride = verts.length / 4;
                  if (stride < 6) {
                     result.add(quad);
                  } else {
                     int[] newVerts = (int[])verts.clone();

                     for (int v = 0; v < 4; v++) {
                        int i = v * stride;
                        float uSprite = Float.intBitsToFloat(newVerts[i + 4]);
                        float vSprite = Float.intBitsToFloat(newVerts[i + 5]);
                        float uRaw = (uSprite - uMin) / uRange;
                        float vRaw = (vSprite - vMin) / vRange;
                        float newURaw = uRaw + du;
                        float newVRaw = vRaw + dv;
                        newURaw -= Mth.floor(newURaw);
                        newVRaw -= Mth.floor(newVRaw);
                        newVerts[i + 4] = Float.floatToIntBits(uMin + newURaw * uRange);
                        newVerts[i + 5] = Float.floatToIntBits(vMin + newVRaw * vRange);
                     }

                     result.add(new BakedQuad(newVerts, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade()));
                  }
               }
            }

            return result;
         } else {
            return quads;
         }
      } else {
         return quads;
      }
   }

   public static List<BakedQuad> translateQuads(List<BakedQuad> quads, float dx, float dy, float dz) {
      if (quads != null && !quads.isEmpty() && (dx != 0.0F || dy != 0.0F || dz != 0.0F)) {
         List<BakedQuad> result = new ArrayList<>(quads.size());

         for (BakedQuad quad : quads) {
            int[] verts = quad.getVertices();
            if (verts.length < 32) {
               result.add(quad);
            } else {
               int stride = verts.length / 4;
               int[] newVerts = (int[])verts.clone();

               for (int v = 0; v < 4; v++) {
                  int i = v * stride;
                  float x = Float.intBitsToFloat(verts[i + 0]);
                  float y = Float.intBitsToFloat(verts[i + 0 + 1]);
                  float z = Float.intBitsToFloat(verts[i + 0 + 2]);
                  newVerts[i] = Float.floatToIntBits(x + dx);
                  newVerts[i + 1] = Float.floatToIntBits(y + dy);
                  newVerts[i + 2] = Float.floatToIntBits(z + dz);
               }

               result.add(new BakedQuad(newVerts, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade()));
            }
         }

         return result;
      } else {
         return quads;
      }
   }

   private static Direction rotateDirection(Direction dir, int angleDeg) {
      if (dir.getAxis() == Axis.Y) {
         return dir;
      }

      int steps = angleDeg / 90 % 4;
      if (steps < 0) {
         steps += 4;
      }

      Direction r = dir;

      for (int i = 0; i < steps; i++) {
         r = r.getClockWise(Axis.Y);
      }

      return r;
   }

   public static List<BakedQuad> transformQuadsByMatrix(List<BakedQuad> quads, Matrix4f matrix) {
      if (quads != null && !quads.isEmpty()) {
         Matrix4f normalMatrix = new Matrix4f(matrix).invert().transpose();
         List<BakedQuad> result = new ArrayList<>(quads.size());
         Vector4f pos = TMP_POS;
         Vector4f norm = TMP_NORM;

         for (BakedQuad quad : quads) {
            int[] verts = quad.getVertices();
            if (verts.length < 32) {
               result.add(quad);
            } else {
               int stride = verts.length / 4;
               int normalOffset = stride - 1;
               int[] newVerts = (int[])verts.clone();

               for (int v = 0; v < 4; v++) {
                  int i = v * stride;
                  float x = Float.intBitsToFloat(verts[i + 0]);
                  float y = Float.intBitsToFloat(verts[i + 0 + 1]);
                  float z = Float.intBitsToFloat(verts[i + 0 + 2]);
                  pos.set(x, y, z, 1.0F);
                  matrix.transform(pos);
                  newVerts[i] = Float.floatToIntBits(pos.x());
                  newVerts[i + 1] = Float.floatToIntBits(pos.y());
                  newVerts[i + 2] = Float.floatToIntBits(pos.z());
                  if (normalOffset < stride) {
                     int packedNormal = verts[i + normalOffset];
                     byte nxb = (byte)(packedNormal & 0xFF);
                     byte nyb = (byte)(packedNormal >> 8 & 0xFF);
                     byte nzb = (byte)(packedNormal >> 16 & 0xFF);
                     norm.set(nxb / 127.0F, nyb / 127.0F, nzb / 127.0F, 0.0F);
                     normalMatrix.transform(norm);
                     norm.normalize();
                     int newPacked = (byte)(norm.x * 127.0F) & 255 | ((byte)(norm.y * 127.0F) & 255) << 8 | ((byte)(norm.z * 127.0F) & 255) << 16;
                     newVerts[i + normalOffset] = newPacked;
                  }
               }

               int packedN = newVerts[normalOffset];
               byte nxb = (byte)(packedN & 0xFF);
               byte nyb = (byte)(packedN >> 8 & 0xFF);
               byte nzb = (byte)(packedN >> 16 & 0xFF);
               Direction transformedDir = directionFromNormal(nxb / 127.0F, nyb / 127.0F, nzb / 127.0F);
               if (transformedDir == null) {
                  transformedDir = quad.getDirection();
               }

               result.add(new BakedQuad(newVerts, quad.getTintIndex(), transformedDir, quad.getSprite(), quad.isShade()));
            }
         }

         return result;
      } else {
         return quads;
      }
   }

   private static Direction directionFromNormal(float dx, float dy, float dz) {
      float ax = Math.abs(dx);
      float ay = Math.abs(dy);
      float az = Math.abs(dz);
      if (ax >= ay && ax >= az) {
         return dx > 0.0F ? Direction.EAST : Direction.WEST;
      } else if (ay >= ax && ay >= az) {
         return dy > 0.0F ? Direction.UP : Direction.DOWN;
      } else if (az >= ax && az >= ay) {
         return dz > 0.0F ? Direction.SOUTH : Direction.NORTH;
      } else {
         return null;
      }
   }

   public record UVBox(float u0, float v0, float u1, float v1) {
   }

   public record UVSpec(ModelHelper.UVBox box, boolean rotate90) {
      public UVSpec(ModelHelper.UVBox box) {
         this(box, false);
      }
   }
}
