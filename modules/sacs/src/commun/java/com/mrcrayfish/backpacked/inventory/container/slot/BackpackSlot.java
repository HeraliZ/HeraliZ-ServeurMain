package com.mrcrayfish.backpacked.inventory.container.slot;

import com.mrcrayfish.backpacked.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class BackpackSlot extends Slot {
   public BackpackSlot(Container inventoryIn, int index, int xPosition, int yPosition) {
      super(inventoryIn, index, xPosition, yPosition);
   }

   public boolean mayPlace(ItemStack stack) {
      return !isBannedItem(stack);
   }

   public static boolean isBannedItem(ItemStack stack) {
      if (stack.getItem() == Items.BUNDLE) {
         return true;
      }

      ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
      return Config.getBannedItemsList().contains(id) ? true : !stack.getItem().canFitInsideContainerItems();
   }
}
