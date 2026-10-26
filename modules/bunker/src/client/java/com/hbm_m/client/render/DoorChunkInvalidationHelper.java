package com.hbm_m.client.render;

import com.hbm_m.client.render.culling.OcclusionCullingHelper;
import com.hbm_m.main.MainRegistry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DoorChunkInvalidationHelper {
   private static final ConcurrentLinkedQueue<BlockPos> PENDING_INVALIDATIONS = new ConcurrentLinkedQueue<>();
   private static final Set<BlockPos> PENDING_POSITIONS = ConcurrentHashMap.newKeySet();

   public static void scheduleChunkInvalidation(BlockPos pos) {
      if (pos != null) {
         BlockPos imm = pos.immutable();
         if (PENDING_POSITIONS.add(imm)) {
            PENDING_INVALIDATIONS.add(imm);
         }
      }
   }

   public static void processPendingInvalidations() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.levelRenderer != null) {
         BlockPos pos;
         while ((pos = PENDING_INVALIDATIONS.poll()) != null) {
            PENDING_POSITIONS.remove(pos);

            try {
               BlockState state = mc.level.getBlockState(pos);
               mc.levelRenderer.blockChanged(mc.level, pos, state, state, 2);
               OcclusionCullingHelper.onClientWorldGeometryMayHaveChanged();
            } catch (Exception e) {
               MainRegistry.LOGGER.debug("Door chunk invalidation at {}: {}", pos, e.getMessage());
            }
         }
      }
   }
}
