package com.mrcrayfish.backpacked.datagen;

import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

public class CommonRecipeGen {
   public static void generate(Consumer<FinishedRecipe> consumer, Function<ItemLike, CriterionTriggerInstance> has) {
   }

   private static void backpackShelf(
      Consumer<FinishedRecipe> consumer, Function<ItemLike, CriterionTriggerInstance> has, ItemLike log, ItemLike slab, ItemLike craftedItem
   ) {
      ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, craftedItem, 4)
         .pattern("LHL")
         .pattern("S S")
         .define('L', log)
         .define('H', slab)
         .define('S', Items.STICK)
         .unlockedBy("has_slab", has.apply(slab))
         .unlockedBy("has_stick", has.apply(Items.STICK))
         .save(consumer);
   }
}
