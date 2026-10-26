package com.mrcrayfish.backpacked.integration.item;

import com.mrcrayfish.backpacked.integration.Curios;
import com.mrcrayfish.backpacked.item.BackpackItem;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

public class ForgeBackpackItem extends BackpackItem {
   public ForgeBackpackItem(Properties properties, int columns, int rows) {
      super(properties, columns, rows);
   }

   @Nullable
   public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
      return Curios.createBackpackProvider(stack);
   }
}
