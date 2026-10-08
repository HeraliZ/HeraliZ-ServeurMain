package fr.heralisz.tactical.inventory.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

public class ThrowableTooltip implements TooltipComponent {
   private final ItemStack stack;

   public ThrowableTooltip(ItemStack stack) {
      this.stack = stack;
   }

   public ItemStack getStack() {
      return this.stack;
   }
}
