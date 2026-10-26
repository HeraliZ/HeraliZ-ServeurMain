package com.mrcrayfish.backpacked.datagen;

import com.mrcrayfish.backpacked.common.Tags;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.Tags.Blocks;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

public class BlockTagGen extends BlockTagsProvider {
   public BlockTagGen(PackOutput output, CompletableFuture<Provider> lookupProvider, @Nullable ExistingFileHelper existingFileHelper) {
      super(output, lookupProvider, "backpacked", existingFileHelper);
   }

   protected void addTags(Provider provider) {
      this.tag(Tags.Blocks.FUNNELLING).addTag(Blocks.ORES);
   }

   public String getName() {
      return "Backpacked Block Tags";
   }
}
