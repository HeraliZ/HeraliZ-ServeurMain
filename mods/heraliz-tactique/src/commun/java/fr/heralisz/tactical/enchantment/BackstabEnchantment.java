package fr.heralisz.tactical.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantment.Rarity;

public class BackstabEnchantment extends Enchantment {
   public static final EquipmentSlot[] SUITABLE_SLOTS = new EquipmentSlot[]{EquipmentSlot.MAINHAND};

   public BackstabEnchantment() {
      super(Rarity.RARE, EnchantmentCategory.WEAPON, SUITABLE_SLOTS);
   }

   public int getMaxLevel() {
      return 2;
   }
}
