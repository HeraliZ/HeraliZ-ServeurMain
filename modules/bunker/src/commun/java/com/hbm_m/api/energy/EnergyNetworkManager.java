package com.hbm_m.api.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** HeralisZ : pas de réseau électrique ; appels ignorés. */
public final class EnergyNetworkManager {
   private static final EnergyNetworkManager NONE = new EnergyNetworkManager();

   public static EnergyNetworkManager get(ServerLevel level) {
      return NONE;
   }

   public void addNode(BlockPos pos) {}

   public void removeNode(BlockPos pos) {}
}
