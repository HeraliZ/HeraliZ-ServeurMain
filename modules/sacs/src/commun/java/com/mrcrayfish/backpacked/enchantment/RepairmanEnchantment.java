package com.mrcrayfish.backpacked.enchantment;

import com.mrcrayfish.backpacked.platform.Services;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantment.Rarity;

public class RepairmanEnchantment extends Enchantment {
   public RepairmanEnchantment() {
      super(Rarity.UNCOMMON, Services.BACKPACK.getEnchantmentCategory(), new EquipmentSlot[0]);
   }

   public boolean isTreasureOnly() {
      return true;
   }

   // HeraliZ : enchantements des sacs retirés du jeu (ni livre, ni marchand, ni table, ni butin)
   public boolean isDiscoverable() {
      return false;
   }

   public boolean isTradeable() {
      return false;
   }

   public boolean isAllowedOnBooks() {
      return false;
   }

   public boolean canApplyAtEnchantingTable(net.minecraft.world.item.ItemStack stack) {
      return false;
   }
}
