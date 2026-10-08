package com.mrcrayfish.backpacked.event.entity;

import com.mrcrayfish.framework.api.event.IFrameworkEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;

@FunctionalInterface
public interface ExploreUpdate extends IFrameworkEvent {
   void handle(ResourceKey<Biome> var1, Player var2);
}
