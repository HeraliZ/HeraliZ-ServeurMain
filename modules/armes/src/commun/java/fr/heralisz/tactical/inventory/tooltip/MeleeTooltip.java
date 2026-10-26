package fr.heralisz.tactical.inventory.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

public class MeleeTooltip implements TooltipComponent {
   private final ItemStack stack;

   public MeleeTooltip(ItemStack stack) {
      this.stack = stack;
   }

   public ItemStack getStack() {
      return this.stack;
   }
}
