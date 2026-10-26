package com.mrcrayfish.backpacked.event.entity;

import com.mrcrayfish.framework.api.event.IFrameworkEvent;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

@FunctionalInterface
public interface InteractedWithEntity extends IFrameworkEvent {
   void handle(ServerPlayer var1, ItemStack var2, Entity var3, List<ResourceLocation> var4);

   interface Capture extends IFrameworkEvent {
      void handle(ServerPlayer var1, ItemStack var2, Entity var3, Consumer<ResourceLocation> var4);
   }
}
