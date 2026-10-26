package fr.heralisz.tactical.api.item;

import java.util.Optional;
import fr.heralisz.tactical.api.melee.MeleeAction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public interface ICustomItem {
   ResourceLocation getId(ItemStack var1);

   default ResourceLocation getDisplayId(ItemStack stack) {
      return this.getId(stack);
   }

   void setId(ItemStack var1, ResourceLocation var2);

   boolean isSame(ItemStack var1, ItemStack var2);

   default Optional<ResourceLocation> getCoolDownId(ItemStack stack) {
      return Optional.empty();
   }

   default int getMaxUsingTick(ItemStack stack) {
      return 0;
   }

   default int getDrawTime(ItemStack stack) {
      return 0;
   }

   default int getPutAwayTime(ItemStack stack) {
      return 0;
   }

   default int getAttackCoolDown(ItemStack stack, MeleeAction action) {
      return this.getAttackCoolDown(stack, action, 0);
   }

   default int getAttackCoolDown(ItemStack stack, MeleeAction action, int cnt) {
      return 20;
   }

   default boolean shouldBlockAttack() {
      return false;
   }

   default boolean shouldBlockUse() {
      return false;
   }

   default boolean shouldBlockPickBlock() {
      return false;
   }

   default boolean blockOffhandRendering(ItemStack stack) {
      return true;
   }
}
