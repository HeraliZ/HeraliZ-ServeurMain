package com.mrcrayfish.backpacked.mixin.common;

import com.mrcrayfish.backpacked.core.ArkaniaZModItems;
import com.mrcrayfish.backpacked.core.ModEnchantments;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntity.class)
public class ItemEntityMixin {
   @Inject(method = "fireImmune", at = @At("HEAD"), cancellable = true)
   public void backpacked$FireImmuneHead(CallbackInfoReturnable<Boolean> cir) {
      ItemEntity entity = (ItemEntity)(Object)this;
      ItemStack stack = entity.getItem();
      if (stack.getItem() == ArkaniaZModItems.BACKPACK.get() && EnchantmentHelper.getItemEnchantmentLevel((Enchantment)ModEnchantments.IMBUED_HIDE.get(), stack) > 0) {
         cir.setReturnValue(true);
      }

      if (stack.getItem() == ArkaniaZModItems.BACKPACK_EXPAND1.get() && EnchantmentHelper.getItemEnchantmentLevel((Enchantment)ModEnchantments.IMBUED_HIDE.get(), stack) > 0) {
         cir.setReturnValue(true);
      }

      if (stack.getItem() == ArkaniaZModItems.BACKPACK_MOUNTAIN_BLACK.get()
         && EnchantmentHelper.getItemEnchantmentLevel((Enchantment)ModEnchantments.IMBUED_HIDE.get(), stack) > 0) {
         cir.setReturnValue(true);
      }

      if (stack.getItem() == ArkaniaZModItems.BACKPACK_EXPAND2.get() && EnchantmentHelper.getItemEnchantmentLevel((Enchantment)ModEnchantments.IMBUED_HIDE.get(), stack) > 0) {
         cir.setReturnValue(true);
      }
   }
}
