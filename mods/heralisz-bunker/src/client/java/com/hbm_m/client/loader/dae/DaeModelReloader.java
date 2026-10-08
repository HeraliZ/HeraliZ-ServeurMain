package com.hbm_m.client.loader.dae;

import java.util.ArrayList;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public class DaeModelReloader extends SimplePreparableReloadListener<Void> {
   protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
      return null;
   }

   protected void apply(Void unused, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
      for (DaeModel model : new ArrayList<>(DaeModel.allModels)) {
         model.reload();
      }
   }
}
