package fr.heralisz.n07.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Butin de boss : objet de collection (ou matériau) avec sa légende en infobulle et le nom du boss qui l'a lâché.
 * Clés de langue : &lt;item&gt;.lore et &lt;item&gt;.source.
 */
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
      tip.add(Component.translatable(this.getDescriptionId() + ".source").withStyle(ChatFormatting.DARK_GREEN));
      super.appendHoverText(stack, level, tip, flag);
   }
}
