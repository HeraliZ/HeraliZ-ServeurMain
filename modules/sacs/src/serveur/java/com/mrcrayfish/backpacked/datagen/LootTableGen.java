package com.mrcrayfish.backpacked.datagen;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.loot.LootTableProvider.SubProviderEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.registries.ForgeRegistries;

public class LootTableGen extends LootTableProvider {
   public LootTableGen(PackOutput output) {
      super(output, Collections.emptySet(), ImmutableList.of(new SubProviderEntry(LootTableGen.BlockProvider::new, LootContextParamSets.BLOCK)));
   }

   protected void validate(Map<ResourceLocation, LootTable> map, ValidationContext context) {
   }

   private static class BlockProvider extends BlockLootSubProvider {
      protected BlockProvider() {
         super(ImmutableSet.of(), FeatureFlags.REGISTRY.allFlags());
      }

      protected void generate() {
         CommonLootTableGen.generate(x$0 -> this.dropSelf(x$0));
      }

      protected Iterable<Block> getKnownBlocks() {
         return ForgeRegistries.BLOCKS
            .getValues()
            .stream()
            .filter(block -> "backpacked".equals(Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(block)).getNamespace()))
            .collect(Collectors.toSet());
      }
   }
}
