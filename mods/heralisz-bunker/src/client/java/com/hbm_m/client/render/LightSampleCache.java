package com.hbm_m.client.render;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;

@OnlyIn(Dist.CLIENT)
public final class LightSampleCache {
   private static final Long2ObjectOpenHashMap<LightSampleCache.Entry> CACHE = new Long2ObjectOpenHashMap();
   private static final Long2ObjectOpenHashMap<LightSampleCache.Entry16> CACHE16 = new Long2ObjectOpenHashMap();
   private static long currentFrame = 0L;
   public static final ThreadLocal<Matrix4f> BASE_POSE = ThreadLocal.withInitial(Matrix4f::new);
   public static final ThreadLocal<Boolean> BASE_POSE_SET = ThreadLocal.withInitial(() -> false);
   private static BlockEntity lastQueriedBE = null;
   private static float lastBlockU = 0.0F;
   private static float lastSkyV = 0.0F;
   private static long lastFastFrame = -1L;
   private static final MutableBlockPos SAMPLE_POS = new MutableBlockPos();
   private static final int[][] FACE_OFFSETS = new int[6][3];
   private static final float[] PRECHECK_UV = new float[2];
   private static final int PRUNE_EVERY = 600;
   private static final int STALE_AFTER_FRAMES = 60;
   private static final Long2ObjectOpenHashMap<LightSampleCache.Entry8> CACHE8 = new Long2ObjectOpenHashMap();
   private static final MutableBlockPos SAMPLE_POS_8 = new MutableBlockPos();
   private static final Vector4f CORNER_TMP = new Vector4f();
   private static final float SAMPLE_INSET = 0.015625F;
   private static final MutableBlockPos SAMPLE_POS_16 = new MutableBlockPos();
   private static final Vector4f PROBE_TMP = new Vector4f();

   private LightSampleCache() {
   }

   private static boolean shouldSkipSpatialSampling(@Nullable BlockEntity be, int packedLightFallback) {
      int blockLight = packedLightFallback & 65535;
      if (blockLight != 0) {
         return false;
      }

      if (be == null) {
         return true;
      }

      getOrSample(be, packedLightFallback, PRECHECK_UV, 0);
      return Math.round(PRECHECK_UV[0]) <= 0;
   }

   public static void onFrameStart() {
      currentFrame++;
      lastQueriedBE = null;
      lastFastFrame = -1L;
      if (currentFrame % 600L == 0L) {
         CACHE.long2ObjectEntrySet().removeIf(e -> ((LightSampleCache.Entry)e.getValue()).lastFrame < currentFrame - 60L);
      }
   }

   public static void getOrSample(@Nullable BlockEntity be, int packedLightFallback, float[] outUV, int outBase) {
      if (be == null) {
         outUV[outBase] = packedLightFallback & 65535;
         outUV[outBase + 1] = packedLightFallback >>> 16 & 65535;
      } else if (be == lastQueriedBE && lastFastFrame == currentFrame) {
         outUV[outBase] = lastBlockU;
         outUV[outBase + 1] = lastSkyV;
      } else {
         long key = be.getBlockPos().asLong();
         LightSampleCache.Entry cached = (LightSampleCache.Entry)CACHE.get(key);
         if (cached != null && cached.lastFrame == currentFrame) {
            outUV[outBase] = cached.blockU;
            outUV[outBase + 1] = cached.skyV;
            lastQueriedBE = be;
            lastBlockU = cached.blockU;
            lastSkyV = cached.skyV;
            lastFastFrame = currentFrame;
         } else {
            sampleSmoothLightUV(be, packedLightFallback, outUV, outBase);
            if (cached == null) {
               cached = new LightSampleCache.Entry();
               CACHE.put(key, cached);
            }

            cached.blockU = outUV[outBase];
            cached.skyV = outUV[outBase + 1];
            cached.lastFrame = currentFrame;
            lastQueriedBE = be;
            lastBlockU = cached.blockU;
            lastSkyV = cached.skyV;
            lastFastFrame = currentFrame;
         }
      }
   }

   private static void sampleSmoothLightUV(BlockEntity be, int packedLightFallback, float[] outUV, int outBase) {
      Level level = be.getLevel();
      if (level == null) {
         outUV[outBase] = packedLightFallback & 65535;
         outUV[outBase + 1] = packedLightFallback >>> 16 & 65535;
      } else {
         AABB bounds;
         try {
            bounds = be.getRenderBoundingBox();
         } catch (Throwable t) {
            bounds = null;
         }

         if (bounds != null && Double.isFinite(bounds.minX) && Double.isFinite(bounds.maxX)) {
            int xLo = Mth.floor(bounds.minX) - 1;
            int xHi = Mth.floor(bounds.maxX - 1.0E-7) + 1;
            int yLo = Mth.floor(bounds.minY) - 1;
            int yHi = Mth.floor(bounds.maxY - 1.0E-7) + 1;
            int zLo = Mth.floor(bounds.minZ) - 1;
            int zHi = Mth.floor(bounds.maxZ - 1.0E-7) + 1;
            int xMid = xLo + xHi >> 1;
            int yMid = yLo + yHi >> 1;
            int zMid = zLo + zHi >> 1;
            FACE_OFFSETS[0][0] = xLo;
            FACE_OFFSETS[0][1] = yMid;
            FACE_OFFSETS[0][2] = zMid;
            FACE_OFFSETS[1][0] = xHi;
            FACE_OFFSETS[1][1] = yMid;
            FACE_OFFSETS[1][2] = zMid;
            FACE_OFFSETS[2][0] = xMid;
            FACE_OFFSETS[2][1] = yLo;
            FACE_OFFSETS[2][2] = zMid;
            FACE_OFFSETS[3][0] = xMid;
            FACE_OFFSETS[3][1] = yHi;
            FACE_OFFSETS[3][2] = zMid;
            FACE_OFFSETS[4][0] = xMid;
            FACE_OFFSETS[4][1] = yMid;
            FACE_OFFSETS[4][2] = zLo;
            FACE_OFFSETS[5][0] = xMid;
            FACE_OFFSETS[5][1] = yMid;
            FACE_OFFSETS[5][2] = zHi;
            long totalBlock = 0L;
            long totalSky = 0L;
            int n = 0;

            for (int i = 0; i < 6; i++) {
               int wx = FACE_OFFSETS[i][0];
               int wy = FACE_OFFSETS[i][1];
               int wz = FACE_OFFSETS[i][2];
               SAMPLE_POS.set(wx, wy, wz);

               BlockState state;
               try {
                  state = level.getBlockState(SAMPLE_POS);
               } catch (Throwable t) {
                  continue;
               }

               if (!state.isSolidRender(level, SAMPLE_POS)) {
                  int packed = LevelRenderer.getLightColor(level, SAMPLE_POS);
                  totalBlock += packed & 65535;
                  totalSky += packed >>> 16 & 65535;
                  n++;
               }
            }

            if (n == 0) {
               outUV[outBase] = packedLightFallback & 65535;
               outUV[outBase + 1] = packedLightFallback >>> 16 & 65535;
            } else {
               outUV[outBase] = (float)totalBlock / n;
               outUV[outBase + 1] = (float)totalSky / n;
            }
         } else {
            outUV[outBase] = packedLightFallback & 65535;
            outUV[outBase + 1] = packedLightFallback >>> 16 & 65535;
         }
      }
   }

   public static void invalidateAll() {
      CACHE.clear();
      CACHE8.clear();
      CACHE16.clear();
      lastQueriedBE = null;
      lastFastFrame = -1L;
   }

   public static void getOrSample8(
      @Nullable BlockEntity be, long partIdentityHash, float[] objBbox, BlockPos blockPos, Matrix4f localPose, int packedLightFallback, float[] out16
   ) {
      if (SingleMeshVboRenderer.isWorldMissileOverlayDraw()) {
         fillFallback8(packedLightFallback, out16);
      } else if (shouldSkipSpatialSampling(be, packedLightFallback)) {
         fillFallback8(packedLightFallback, out16);
      } else {
         Level level = be != null ? be.getLevel() : null;
         if (level != null && blockPos != null && localPose != null && objBbox != null) {
            long key = be.getBlockPos().asLong() ^ partIdentityHash;
            LightSampleCache.Entry8 cached = (LightSampleCache.Entry8)CACHE8.get(key);
            if (cached != null && cached.lastFrame == currentFrame) {
               System.arraycopy(cached.cornerUV, 0, out16, 0, 16);
            } else {
               sample8(level, objBbox, blockPos, localPose, packedLightFallback, out16);
               if (cached == null) {
                  cached = new LightSampleCache.Entry8();
                  CACHE8.put(key, cached);
               }

               System.arraycopy(out16, 0, cached.cornerUV, 0, 16);
               cached.lastFrame = currentFrame;
            }
         } else {
            fillFallback8(packedLightFallback, out16);
         }
      }
   }

   public static void getOrSample8Lod(
      @Nullable BlockEntity be,
      long partIdentityHash,
      float[] objBbox,
      BlockPos blockPos,
      Matrix4f localPose,
      int packedLightFallback,
      float[] out16,
      double distSqToCamera
   ) {
      if (distSqToCamera > RenderDistanceHelper.getLightCornerDetailDistanceSq()) {
         fillFallback8(packedLightFallback, out16);
      } else {
         getOrSample8(be, partIdentityHash, objBbox, blockPos, localPose, packedLightFallback, out16);
      }
   }

   public static void getOrSample16(
      @Nullable BlockEntity be, long partIdentityHash, float[] objBbox, BlockPos blockPos, Matrix4f localPose, int packedLightFallback, float[] out32
   ) {
      if (SingleMeshVboRenderer.isWorldMissileOverlayDraw()) {
         fillFallback16(packedLightFallback, out32);
      } else if (shouldSkipSpatialSampling(be, packedLightFallback)) {
         fillFallback16(packedLightFallback, out32);
      } else {
         Level level = be != null ? be.getLevel() : null;
         if (level != null && blockPos != null && localPose != null && objBbox != null) {
            long key = be.getBlockPos().asLong() ^ partIdentityHash ^ 370546198L;
            LightSampleCache.Entry16 cached = (LightSampleCache.Entry16)CACHE16.get(key);
            if (cached != null && cached.lastFrame == currentFrame) {
               System.arraycopy(cached.probeUV, 0, out32, 0, 32);
            } else {
               sample16(level, objBbox, blockPos, localPose, packedLightFallback, out32);
               if (cached == null) {
                  cached = new LightSampleCache.Entry16();
                  CACHE16.put(key, cached);
               }

               System.arraycopy(out32, 0, cached.probeUV, 0, 32);
               cached.lastFrame = currentFrame;
            }
         } else {
            fillFallback16(packedLightFallback, out32);
         }
      }
   }

   private static void sample16(Level level, float[] objBbox, BlockPos blockPos, Matrix4f localPose, int packedLightFallback, float[] out32) {
      float minX = objBbox[0];
      float minY = objBbox[1];
      float minZ = objBbox[2];
      float maxX = objBbox[3];
      float maxY = objBbox[4];
      float maxZ = objBbox[5];
      int bpx = blockPos.getX();
      int bpy = blockPos.getY();
      int bpz = blockPos.getZ();
      float insetX = Math.min(0.015625F, (maxX - minX) * 0.5F);
      float insetY = Math.min(0.015625F, (maxY - minY) * 0.5F);
      float insetZ = Math.min(0.015625F, (maxZ - minZ) * 0.5F);
      float minXs = minX + insetX;
      float maxXs = maxX - insetX;
      float minYs = minY + insetY;
      float maxYs = maxY - insetY;
      float minZs = minZ + insetZ;
      float maxZs = maxZ - insetZ;
      float shell = 0.55F;
      int outBase = 0;

      for (int slice = 0; slice < 4; slice++) {
         float t = slice / 3.0F;
         float oy = Mth.lerp(t, minYs, maxYs);

         for (int corner = 0; corner < 4; corner++) {
            boolean maxSideX = (corner & 1) != 0;
            boolean maxSideZ = (corner & 2) != 0;
            float ox = maxSideX ? maxXs : minXs;
            float oz = maxSideZ ? maxZs : minZs;
            ox += maxSideX ? 0.55F : -0.55F;
            oz += maxSideZ ? 0.55F : -0.55F;
            PROBE_TMP.set(ox, oy, oz, 1.0F);
            localPose.transform(PROBE_TMP);
            int wx = bpx + Mth.floor(PROBE_TMP.x);
            int wy = bpy + Mth.floor(PROBE_TMP.y);
            int wz = bpz + Mth.floor(PROBE_TMP.z);
            SAMPLE_POS_16.set(wx, wy, wz);

            int packed;
            try {
               BlockState state = level.getBlockState(SAMPLE_POS_16);
               if (state.isSolidRender(level, SAMPLE_POS_16)) {
                  packed = packedLightFallback;
               } else {
                  packed = LevelRenderer.getLightColor(level, SAMPLE_POS_16);
               }
            } catch (Throwable t0) {
               packed = packedLightFallback;
            }

            out32[outBase++] = packed & 65535;
            out32[outBase++] = packed >>> 16 & 65535;
         }
      }
   }

   private static void fillFallback16(int packedLightFallback, float[] out32) {
      float bu = packedLightFallback & 65535;
      float sv = packedLightFallback >>> 16 & 65535;

      for (int i = 0; i < 16; i++) {
         out32[i * 2] = bu;
         out32[i * 2 + 1] = sv;
      }
   }

   private static void sample8(Level level, float[] objBbox, BlockPos blockPos, Matrix4f localPose, int packedLightFallback, float[] out16) {
      float minX = objBbox[0];
      float minY = objBbox[1];
      float minZ = objBbox[2];
      float maxX = objBbox[3];
      float maxY = objBbox[4];
      float maxZ = objBbox[5];
      int bpx = blockPos.getX();
      int bpy = blockPos.getY();
      int bpz = blockPos.getZ();
      float insetX = Math.min(0.015625F, (maxX - minX) * 0.5F);
      float insetY = Math.min(0.015625F, (maxY - minY) * 0.5F);
      float insetZ = Math.min(0.015625F, (maxZ - minZ) * 0.5F);
      float minXs = minX + insetX;
      float maxXs = maxX - insetX;
      float minYs = minY + insetY;
      float maxYs = maxY - insetY;
      float minZs = minZ + insetZ;
      float maxZs = maxZ - insetZ;

      for (int i = 0; i < 8; i++) {
         float ox = (i & 1) == 0 ? minXs : maxXs;
         float oy = (i & 2) == 0 ? minYs : maxYs;
         float oz = (i & 4) == 0 ? minZs : maxZs;
         CORNER_TMP.set(ox, oy, oz, 1.0F);
         localPose.transform(CORNER_TMP);
         int wx = bpx + Mth.floor(CORNER_TMP.x);
         int wy = bpy + Mth.floor(CORNER_TMP.y);
         int wz = bpz + Mth.floor(CORNER_TMP.z);
         SAMPLE_POS_8.set(wx, wy, wz);

         int packed;
         try {
            BlockState state = level.getBlockState(SAMPLE_POS_8);
            if (state.isSolidRender(level, SAMPLE_POS_8)) {
               packed = packedLightFallback;
            } else {
               packed = LevelRenderer.getLightColor(level, SAMPLE_POS_8);
            }
         } catch (Throwable t) {
            packed = packedLightFallback;
         }

         out16[i * 2] = packed & 65535;
         out16[i * 2 + 1] = packed >>> 16 & 65535;
      }
   }

   private static void fillFallback8(int packedLightFallback, float[] out16) {
      float bu = packedLightFallback & 65535;
      float sv = packedLightFallback >>> 16 & 65535;

      for (int i = 0; i < 8; i++) {
         out16[i * 2] = bu;
         out16[i * 2 + 1] = sv;
      }
   }

   private static final class Entry {
      float blockU;
      float skyV;
      long lastFrame;
   }

   private static final class Entry16 {
      final float[] probeUV = new float[32];
      long lastFrame;
   }

   private static final class Entry8 {
      final float[] cornerUV = new float[16];
      long lastFrame;
   }
}
