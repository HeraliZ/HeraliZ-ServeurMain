package com.hbm_m.client.render.shader;

import com.hbm_m.main.MainRegistry;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ShaderReloadListener extends SimplePreparableReloadListener<Void> {
   protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
      profiler.push("hbm_shader_detection");
      MainRegistry.LOGGER.debug("ShaderReloadListener: prepare phase");
      profiler.pop();
      return null;
   }

   protected void apply(Void prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
      profiler.push("hbm_shader_detection_apply");

      try {
         IrisExtendedShaderAccess.invalidateShaderCache();
         IrisRenderBatch.invalidateCaches();
         MainRegistry.LOGGER.debug("ShaderReloadListener: Resource reload complete, Iris caches invalidated");
      } catch (Exception e) {
         MainRegistry.LOGGER.error("Error during shader reload", e);
      } finally {
         profiler.pop();
      }
   }
}
