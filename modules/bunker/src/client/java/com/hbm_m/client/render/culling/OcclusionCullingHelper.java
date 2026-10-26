package com.hbm_m.client.render.culling;

import com.hbm_m.client.render.shader.ShaderCompatibilityDetector;
import com.hbm_m.config.ModClothConfig;
import com.hbm_m.main.MainRegistry;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public final class OcclusionCullingHelper {
   private static final Long2ObjectOpenHashMap<OcclusionCullingHelper.CachedResult> occlusionCache = new Long2ObjectOpenHashMap();
   private static long currentFrame = 0L;
   private static final double CAMERA_REUSE_MAX_DIST_SQ = 0.25;
   private static final int CROSS_FRAME_TTL_TICKS = 20;
   private static final int MAX_KEEP_MANHATTAN_BLOCKS = 192;
   private static final int MAX_CACHE_ENTRIES = 16384;
   private static long clientGeometryStamp = 0L;
   @Nullable
   private static volatile Frustum blockEntityPassFrustum;
   @Nullable
   private static TagKey<Block> transparentBlocksTag = null;
   private static final MutableBlockPos RAY_MARCH_SCRATCH = new MutableBlockPos();

   private OcclusionCullingHelper() {
   }

   private static long occlusionCacheKey(BlockPos pos) {
      return pos.asLong();
   }

   private static long stripShadowKeyBit(long key) {
      return key;
   }

   public static void setTransparentBlocksTag(TagKey<Block> tag) {
      transparentBlocksTag = tag;
   }

   public static void onClientWorldGeometryMayHaveChanged() {
      clientGeometryStamp++;
   }

   public static void captureBlockEntityPassFrustum(@Nullable Frustum frustum) {
      blockEntityPassFrustum = frustum;
   }

   public static void captureCpuFrustumFallback(Matrix4f projection, Vec3 cameraPos) {
      if (projection == null) {
         CpuFrustumCuller.invalidate();
      } else {
         try {
            Matrix4f rot = new Matrix4f(RenderSystem.getModelViewMatrix());
            Matrix4f trans = new Matrix4f().setTranslation((float)(-cameraPos.x), (float)(-cameraPos.y), (float)(-cameraPos.z));
            Matrix4f view = rot.mul(trans);
            Matrix4f viewProj = new Matrix4f(projection).mul(view);
            CpuFrustumCuller.updateFrustum(viewProj);
         } catch (Throwable err) {
            CpuFrustumCuller.invalidate();
         }
      }
   }

   private static boolean aabbPassesBerPassFrustum(AABB renderBounds) {
      Frustum f = blockEntityPassFrustum;
      if (f == null) {
         Minecraft mc = Minecraft.getInstance();
         LevelRenderer lr = mc.levelRenderer;
         if (lr != null) {
            f = lr.getFrustum();
         }
      }

      return f != null ? f.isVisible(renderBounds) : CpuFrustumCuller.isVisible(renderBounds);
   }

   private static boolean legacyRaycastVisibility(Vec3 cameraPos, Level level, AABB renderBounds) {
      double centerX = (renderBounds.minX + renderBounds.maxX) * 0.5;
      double centerY = (renderBounds.minY + renderBounds.maxY) * 0.5;
      double centerZ = (renderBounds.minZ + renderBounds.maxZ) * 0.5;
      double dx = centerX - cameraPos.x;
      double dy = centerY - cameraPos.y;
      double dz = centerZ - cameraPos.z;
      double distSq = dx * dx + dy * dy + dz * dz;
      if (distSq < 16.0) {
         return true;
      }

      if (!isRayOccluded(cameraPos, centerX, centerY, centerZ, level, renderBounds)) {
         return true;
      }

      boolean visible = !isRayOccluded(cameraPos, renderBounds.minX, renderBounds.minY, renderBounds.minZ, level, renderBounds)
         || !isRayOccluded(cameraPos, renderBounds.maxX, renderBounds.minY, renderBounds.minZ, level, renderBounds)
         || !isRayOccluded(cameraPos, renderBounds.minX, renderBounds.maxY, renderBounds.minZ, level, renderBounds)
         || !isRayOccluded(cameraPos, renderBounds.maxX, renderBounds.maxY, renderBounds.minZ, level, renderBounds)
         || !isRayOccluded(cameraPos, renderBounds.minX, renderBounds.minY, renderBounds.maxZ, level, renderBounds)
         || !isRayOccluded(cameraPos, renderBounds.maxX, renderBounds.minY, renderBounds.maxZ, level, renderBounds)
         || !isRayOccluded(cameraPos, renderBounds.minX, renderBounds.maxY, renderBounds.maxZ, level, renderBounds)
         || !isRayOccluded(cameraPos, renderBounds.maxX, renderBounds.maxY, renderBounds.maxZ, level, renderBounds);
      if (!visible) {
         visible = !isRayOccluded(cameraPos, centerX, renderBounds.minY, centerZ, level, renderBounds)
            || !isRayOccluded(cameraPos, centerX, renderBounds.maxY, centerZ, level, renderBounds)
            || !isRayOccluded(cameraPos, renderBounds.minX, centerY, centerZ, level, renderBounds)
            || !isRayOccluded(cameraPos, renderBounds.maxX, centerY, centerZ, level, renderBounds)
            || !isRayOccluded(cameraPos, centerX, centerY, renderBounds.minZ, level, renderBounds)
            || !isRayOccluded(cameraPos, centerX, centerY, renderBounds.maxZ, level, renderBounds);
      }

      return visible;
   }

   public static long occlusionKeyForBlock(BlockPos pos) {
      return occlusionCacheKey(pos);
   }

   public static boolean shouldRender(BlockPos pos, Level level, AABB renderBounds) {
      if (!ModClothConfig.get().enableOcclusionCulling) {
         return true;
      } else if (ShaderCompatibilityDetector.isRenderingShadowPass()) {
         return true;
      } else {
         long posLong = occlusionCacheKey(pos);
         OcclusionCullingHelper.CachedResult cached = (OcclusionCullingHelper.CachedResult)occlusionCache.get(posLong);
         Minecraft mc = Minecraft.getInstance();
         Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
         if (cached != null && cached.frame == currentFrame) {
            return cached.visible;
         } else if (cached != null && canReuseCrossFrame(cached, level, cameraPos)) {
            cached.frame = currentFrame;
            logDecision(pos, cached.visible, "CROSS_FRAME_REUSE", cached);
            return cached.visible;
         } else {
            boolean frustum = aabbPassesBerPassFrustum(renderBounds);
            if (!frustum) {
               putCache(posLong, cached, false, cameraPos, level, "FRUSTUM_CULLED");
               return false;
            } else {
               boolean visible = legacyRaycastVisibility(cameraPos, level, renderBounds);
               putCache(posLong, cached, visible, cameraPos, level, "RAYCAST_OCCLUSION");
               return visible;
            }
         }
      }
   }

   private static boolean canReuseCrossFrame(OcclusionCullingHelper.CachedResult c, Level level, Vec3 cameraPos) {
      if (!c.visible) {
         return false;
      } else if (ModClothConfig.useInstancedBatching()) {
         return false;
      } else if (level == null) {
         return false;
      } else if (c.geometryStampAtCheck != clientGeometryStamp) {
         return false;
      } else {
         long nowTick = level.getGameTime();
         long age = nowTick - c.checkGameTime;
         if (age >= 0L && age <= 20L) {
            double ddx = cameraPos.x - c.lastCamX;
            double ddy = cameraPos.y - c.lastCamY;
            double ddz = cameraPos.z - c.lastCamZ;
            return ddx * ddx + ddy * ddy + ddz * ddz < 0.25;
         } else {
            return false;
         }
      }
   }

   private static void putCache(long key, @Nullable OcclusionCullingHelper.CachedResult existing, boolean visible, Vec3 cameraPos, Level level, String reason) {
      long tick = level == null ? 0L : level.getGameTime();
      if (existing == null) {
         occlusionCache.put(
            key,
            new OcclusionCullingHelper.CachedResult(
               visible, currentFrame, tick, cameraPos.x, cameraPos.y, cameraPos.z, clientGeometryStamp, reason
            )
         );
         logDecision(BlockPos.of(stripShadowKeyBit(key)), visible, reason, null);
      } else {
         logDecision(BlockPos.of(stripShadowKeyBit(key)), visible, reason, existing);
         existing.setAll(visible, currentFrame, tick, cameraPos.x, cameraPos.y, cameraPos.z, clientGeometryStamp, reason);
      }

      trimCacheIfNeeded();
   }

   private static void logDecision(BlockPos pos, boolean visible, String reason, OcclusionCullingHelper.CachedResult cached) {
      try {
         if (ModClothConfig.get().mdiDebugLogDispatch && (cached == null || cached.visible != visible || !reason.equals(cached.lastReason))) {
            MainRegistry.LOGGER.info("[HBM-Cull] BE at {} visibility changed to {}. Reason: {}", new Object[]{pos.toShortString(), visible, reason});
         }
      } catch (Throwable var5) {
      }
   }

   private static void trimCacheIfNeeded() {
      int size = occlusionCache.size();
      if (size > 16384) {
         int toRemove = size - 16384 + 2048;
         ObjectIterator<Entry<OcclusionCullingHelper.CachedResult>> it = occlusionCache.long2ObjectEntrySet().iterator();

         while (toRemove-- > 0 && it.hasNext()) {
            it.next();
            it.remove();
         }
      }
   }

   private static boolean isRayOccluded(Vec3 start, double endX, double endY, double endZ, Level level, AABB renderBounds) {
      double startX = start.x;
      double startY = start.y;
      double startZ = start.z;
      MutableBlockPos mutablePos = RAY_MARCH_SCRATCH;
      int currentX = Mth.floor(startX);
      int currentY = Mth.floor(startY);
      int currentZ = Mth.floor(startZ);
      int targetX = Mth.floor(endX);
      int targetY = Mth.floor(endY);
      int targetZ = Mth.floor(endZ);
      int stepX = Integer.signum(targetX - currentX);
      int stepY = Integer.signum(targetY - currentY);
      int stepZ = Integer.signum(targetZ - currentZ);
      if (stepX == 0 && stepY == 0 && stepZ == 0) {
         return false;
      }

      double dx = endX - startX;
      double dy = endY - startY;
      double dz = endZ - startZ;
      double deltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
      double deltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
      double deltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
      double maxX = stepX == 0 ? Double.MAX_VALUE : (stepX > 0 ? (currentX + 1 - startX) * deltaX : (startX - currentX) * deltaX);
      double maxY = stepY == 0 ? Double.MAX_VALUE : (stepY > 0 ? (currentY + 1 - startY) * deltaY : (startY - currentY) * deltaY);
      double maxZ = stepZ == 0 ? Double.MAX_VALUE : (stepZ > 0 ? (currentZ + 1 - startZ) * deltaZ : (startZ - currentZ) * deltaZ);
      int maxSteps = 100;

      while (maxSteps-- > 0) {
         if (currentX == targetX && currentY == targetY && currentZ == targetZ) {
            return false;
         }

         if (currentX != Mth.floor(startX) || currentY != Mth.floor(startY) || currentZ != Mth.floor(startZ)) {
            mutablePos.set(currentX, currentY, currentZ);
            if ((
                  renderBounds == null
                     || !(currentX + 1 > renderBounds.minX)
                     || !(currentX < renderBounds.maxX)
                     || !(currentY + 1 > renderBounds.minY)
                     || !(currentY < renderBounds.maxY)
                     || !(currentZ + 1 > renderBounds.minZ)
                     || !(currentZ < renderBounds.maxZ)
               )
               && isOccluder(level, mutablePos)) {
               return true;
            }
         }

         if (maxX < maxY) {
            if (maxX < maxZ) {
               currentX += stepX;
               maxX += deltaX;
            } else {
               currentZ += stepZ;
               maxZ += deltaZ;
            }
         } else if (maxY < maxZ) {
            currentY += stepY;
            maxY += deltaY;
         } else {
            currentZ += stepZ;
            maxZ += deltaZ;
         }
      }

      return false;
   }

   private static boolean isOccluder(Level level, BlockPos pos) {
      if (!level.hasChunkAt(pos)) {
         return false;
      } else {
         BlockState state = level.getBlockState(pos);
         if (state.isAir()) {
            return false;
         } else {
            return transparentBlocksTag != null && state.is(transparentBlocksTag) ? false : state.isSolidRender(level, pos);
         }
      }
   }

   public static void onFrameStart() {
      currentFrame++;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && !occlusionCache.isEmpty()) {
         BlockPos pp = mc.player.blockPosition();
         int px = pp.getX();
         int py = pp.getY();
         int pz = pp.getZ();
         occlusionCache.long2ObjectEntrySet().removeIf(e -> {
            long raw = stripShadowKeyBit(e.getLongKey());
            BlockPos bp = BlockPos.of(raw);
            int dist = Math.abs(bp.getX() - px) + Math.abs(bp.getY() - py) + Math.abs(bp.getZ() - pz);
            return dist > 192;
         });
      }

      if (currentFrame % 600L == 0L) {
         occlusionCache.long2ObjectEntrySet().removeIf(e -> currentFrame - ((OcclusionCullingHelper.CachedResult)e.getValue()).frame > 600L);
      }
   }

   public static void clearCache() {
      occlusionCache.clear();
      InstancedRenderFrame.clear();
   }

   private static final class CachedResult {
      boolean visible;
      long frame;
      long checkGameTime;
      double lastCamX;
      double lastCamY;
      double lastCamZ;
      long geometryStampAtCheck;
      String lastReason;

      CachedResult(boolean visible, long frame, long checkGameTime, double lastCamX, double lastCamY, double lastCamZ, long geometryStampAtCheck, String reason) {
         this.visible = visible;
         this.frame = frame;
         this.checkGameTime = checkGameTime;
         this.lastCamX = lastCamX;
         this.lastCamY = lastCamY;
         this.lastCamZ = lastCamZ;
         this.geometryStampAtCheck = geometryStampAtCheck;
         this.lastReason = reason;
      }

      void setAll(boolean visible, long frame, long checkGameTime, double lastCamX, double lastCamY, double lastCamZ, long geometryStampAtCheck, String reason) {
         this.visible = visible;
         this.frame = frame;
         this.checkGameTime = checkGameTime;
         this.lastCamX = lastCamX;
         this.lastCamY = lastCamY;
         this.lastCamZ = lastCamZ;
         this.geometryStampAtCheck = geometryStampAtCheck;
         this.lastReason = reason;
      }
   }
}
