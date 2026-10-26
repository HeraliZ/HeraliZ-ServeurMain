package com.mrcrayfish.backpacked.event.entity;

import com.mrcrayfish.framework.api.event.IFrameworkEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Animal;

@FunctionalInterface
public interface BredAnimal extends IFrameworkEvent {
   void handle(Animal var1, Animal var2, ServerPlayer var3);
}
