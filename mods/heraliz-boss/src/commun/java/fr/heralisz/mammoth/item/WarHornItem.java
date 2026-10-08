package fr.heralisz.mammoth.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Le cor de guerre de la tribu : on le pose sur l'autel du Cor (clic droit) pour réveiller Ymirak. */
public class WarHornItem extends Item {
   public WarHornItem(Properties props) {
      super(props);
   }

   @Override
   public boolean isFoil(ItemStack stack) {
      return true;
   }

   @Override
   public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
      tip.add(Component.translatable(this.getDescriptionId() + ".lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
      tip.add(Component.translatable(this.getDescriptionId() + ".use").withStyle(ChatFormatting.AQUA));
      super.appendHoverText(stack, level, tip, flag);
   }
}
