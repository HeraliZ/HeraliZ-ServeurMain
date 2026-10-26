package fr.heralisz.tactical.inventory.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

public class ConsumableTooltip implements TooltipComponent {
   private final ItemStack stack;

   public ConsumableTooltip(ItemStack stack) {
      this.stack = stack;
   }

   public ItemStack getStack() {
      return this.stack;
   }
}
