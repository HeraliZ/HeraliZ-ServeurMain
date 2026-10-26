package com.mrcrayfish.backpacked.event.block;

import com.mrcrayfish.framework.api.event.IFrameworkEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface InteractedWithBlock extends IFrameworkEvent {
   void handle(BlockState var1, ItemStack var2, @Nullable CompoundTag var3, ServerPlayer var4);

   @FunctionalInterface
   interface CaptureTag extends IFrameworkEvent {
      boolean handle(BlockState var1, ItemStack var2, ServerPlayer var3);
   }
}
