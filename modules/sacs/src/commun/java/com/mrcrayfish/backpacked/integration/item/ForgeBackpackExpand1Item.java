package com.mrcrayfish.backpacked.integration.item;

import com.mrcrayfish.backpacked.integration.Curios;
import com.mrcrayfish.backpacked.item.BackpackItem_expand1;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

public class ForgeBackpackExpand1Item extends BackpackItem_expand1 {
   public ForgeBackpackExpand1Item(Properties properties) {
      super(properties);
   }

   @Nullable
   public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
      return Curios.createBackpackProvider(stack);
   }
}
