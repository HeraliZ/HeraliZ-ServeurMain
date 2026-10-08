package fr.heralisz.plague.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Butin de la Mère des Ruines : objet de collection (ou matériau), sa légende en infobulle. */
public class TrophyItem extends Item {
   private final boolean foil;

   public TrophyItem(Properties props, boolean foil) {
      super(props);
      this.foil = foil;
   }

   @Override
   public boolean isFoil(ItemStack stack) {
      return foil || super.isFoil(stack);
   }

   @Override
   public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
      tip.add(Component.translatable(this.getDescriptionId() + ".lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
      tip.add(Component.translatable("item.heralisz_plague.trophy.source").withStyle(ChatFormatting.GOLD));
      super.appendHoverText(stack, level, tip, flag);
   }
}
