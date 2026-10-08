package com.mrcrayfish.backpacked.datagen;

import com.mrcrayfish.backpacked.core.ArkaniaZModItems;
import java.util.function.Consumer;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

public class RecipeGen extends RecipeProvider {
   public RecipeGen(PackOutput output) {
      super(output);
   }

   protected void buildRecipes(Consumer<FinishedRecipe> consumer) {
      ShapedRecipeBuilder.shaped(RecipeCategory.MISC, (ItemLike)ArkaniaZModItems.BACKPACK.get())
         .pattern("HHH")
         .pattern("SIS")
         .pattern("HHH")
         .define('H', Items.RABBIT_HIDE)
         .define('S', net.minecraftforge.common.Tags.Items.STRING)
         .define('I', net.minecraftforge.common.Tags.Items.INGOTS_IRON)
         .unlockedBy("has_hide", has(Items.RABBIT_HIDE))
         .save(consumer);
      CommonRecipeGen.generate(consumer, x$0 -> RecipeProvider.has(x$0));
   }
}
