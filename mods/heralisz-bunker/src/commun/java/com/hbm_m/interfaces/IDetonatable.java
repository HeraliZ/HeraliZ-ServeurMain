package com.hbm_m.interfaces;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public interface IDetonatable {
   boolean onDetonate(Level var1, BlockPos var2, BlockState var3, Player var4);
}
