package com.mrcrayfish.backpacked.enchantment;

import com.mrcrayfish.backpacked.platform.Services;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantment.Rarity;

public class MarksmanEnchantment extends Enchantment {
   public MarksmanEnchantment() {
      super(Rarity.UNCOMMON, Services.BACKPACK.getEnchantmentCategory(), new EquipmentSlot[0]);
   }

   public boolean isTreasureOnly() {
      return true;
   }
}
