package com.mrcrayfish.backpacked.enchantment;

import com.mrcrayfish.backpacked.core.ModEnchantments;
import com.mrcrayfish.backpacked.inventory.BackpackInventory;
import com.mrcrayfish.backpacked.inventory.BackpackedInventoryAccess;
import com.mrcrayfish.backpacked.platform.Services;
import java.util.Collection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Enchantement Pillard, cote serveur (repris tel quel de LootedEnchantment.onDropLoot) : le butin des creatures tuees
 * va dans le sac. L'enchantement lui-meme (registre) reste commun.
 */
public final class LootedEnchantmentServeur {
    private LootedEnchantmentServeur() {}


   public static boolean onDropLoot(Collection<ItemEntity> drops, DamageSource source) {
      if (source.getEntity() instanceof ServerPlayer player) {
         ItemStack backpack = Services.BACKPACK.getBackpackStack(player);
         if (backpack.isEmpty()) {
            return false;
         }

         if (EnchantmentHelper.getItemEnchantmentLevel((Enchantment)ModEnchantments.LOOTED.get(), backpack) <= 0) {
            return false;
         }

         BackpackInventory inventory = ((BackpackedInventoryAccess)player).backpacked$GetBackpackInventory();
         if (inventory == null) {
            return false;
         }

         drops.forEach(itemEntity -> {
            ItemStack stack = itemEntity.getItem();
            ItemStack remaining = inventory.addItem(stack);
            if (!remaining.isEmpty()) {
               itemEntity.setItem(remaining);
               player.level().addFreshEntity(itemEntity);
            }
         });
         return true;
      } else {
         return false;
      }
   }
}
