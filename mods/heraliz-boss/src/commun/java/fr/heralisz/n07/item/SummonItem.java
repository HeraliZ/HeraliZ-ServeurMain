package fr.heralisz.n07.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Objet d'invocation : se présente au sanctuaire de son donjon (clic droit) pour lancer le rituel qui réveille le boss.
 * Clés de langue : &lt;item&gt;.lore et &lt;item&gt;.use.
 */
public class SummonItem extends Item {
   public SummonItem(Properties props) {
      super(props);
   }

   @Override
   public boolean isFoil(ItemStack stack) {
      return true;
   }

   @Override
   public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
      tip.add(Component.translatable(this.getDescriptionId() + ".lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
      tip.add(Component.translatable(this.getDescriptionId() + ".use").withStyle(ChatFormatting.GOLD));
      super.appendHoverText(stack, level, tip, flag);
   }
}
