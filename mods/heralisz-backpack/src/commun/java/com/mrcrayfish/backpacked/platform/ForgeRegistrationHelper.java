package com.mrcrayfish.backpacked.platform;

import com.mrcrayfish.backpacked.platform.services.IRegistrationHelper;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.CreativeModeTab.Output;
import net.minecraft.world.item.CreativeModeTab.TabVisibility;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.fml.ModList;

public class ForgeRegistrationHelper implements IRegistrationHelper {
   @Override
   public void addEnchantedBookToCreativeTab(Output output, Enchantment enchantment) {
      output.accept(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, enchantment.getMaxLevel())), TabVisibility.PARENT_TAB_ONLY);
   }

   @Override
   public boolean isModLoaded(String modId) {
      return ModList.get().isLoaded(modId);
   }
}
