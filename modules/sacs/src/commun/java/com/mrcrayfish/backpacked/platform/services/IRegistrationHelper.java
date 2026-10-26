package com.mrcrayfish.backpacked.platform.services;

import net.minecraft.world.item.CreativeModeTab.Output;
import net.minecraft.world.item.enchantment.Enchantment;

public interface IRegistrationHelper {
   void addEnchantedBookToCreativeTab(Output var1, Enchantment var2);

   boolean isModLoaded(String var1);
}
