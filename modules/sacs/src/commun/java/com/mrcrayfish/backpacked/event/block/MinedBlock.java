package com.mrcrayfish.backpacked.event.block;

import com.mrcrayfish.framework.api.event.IFrameworkEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface MinedBlock extends IFrameworkEvent {
   void handle(BlockState var1, ItemStack var2, @Nullable CompoundTag var3, Player var4);

   @FunctionalInterface
   interface CaptureTag extends IFrameworkEvent {
      boolean handle(BlockState var1, ItemStack var2, Player var3);
   }
}
