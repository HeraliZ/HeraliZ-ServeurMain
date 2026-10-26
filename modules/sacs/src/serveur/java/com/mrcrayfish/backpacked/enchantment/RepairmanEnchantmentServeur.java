package com.mrcrayfish.backpacked.enchantment;

import com.mrcrayfish.backpacked.core.ModEnchantments;
import com.mrcrayfish.backpacked.inventory.BackpackInventory;
import com.mrcrayfish.backpacked.inventory.BackpackedInventoryAccess;
import com.mrcrayfish.backpacked.platform.Services;
import com.mrcrayfish.backpacked.util.InventoryHelper;
import com.mrcrayfish.framework.api.event.PlayerEvents;
import com.mrcrayfish.framework.api.util.ItemStackHelper;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Enchantement Reparateur, cote serveur (repris tel quel de RepairmanEnchantment) : l'experience ramassee repare les
 * objets Raccommodage du sac. L'enchantement lui-meme (registre) reste commun.
 */
public final class RepairmanEnchantmentServeur {
    private RepairmanEnchantmentServeur() {}


   public static void init() {
      PlayerEvents.PICKUP_EXPERIENCE.register(RepairmanEnchantmentServeur::onPickupExperience);
   }

   private static boolean onPickupExperience(Player player, ExperienceOrb experienceOrb) {
      ItemStack backpack = Services.BACKPACK.getBackpackStack(player);
      if (backpack.isEmpty()) {
         return false;
      }

      if (EnchantmentHelper.getItemEnchantmentLevel((Enchantment)ModEnchantments.REPAIRMAN.get(), backpack) <= 0) {
         return false;
      }

      BackpackInventory inventory = ((BackpackedInventoryAccess)player).backpacked$GetBackpackInventory();
      if (inventory == null) {
         return false;
      }

      if (experienceOrb.isRemoved()) {
         return false;
      }

      InventoryHelper.streamFor(inventory).filter(stack -> EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MENDING, stack) > 0 && stack.isDamaged()).forEach(stack -> {
         int repaired = Math.min((int)(experienceOrb.getValue() / 2 * ItemStackHelper.getRepairRatio(stack)), stack.getDamageValue());
         stack.setDamageValue(stack.getDamageValue() - repaired);
      });
      return false;
   }
}
