package fr.heralisz.camo.item;

import fr.heralisz.camo.Camo;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class CamoItem extends Item {
   private final Camo camo;

   public CamoItem(Camo camo) {
      super(new Properties().stacksTo(16));
      this.camo = camo;
   }

   public Camo camo() {
      return this.camo;
   }

   @Override
   public Component getName(ItemStack stack) {
      return super.getName(stack).copy().withStyle(this.camo.tier.color);
   }

   @Override
   public boolean isFoil(ItemStack stack) {
      return this.camo.tier == Camo.Tier.LEGENDARY || this.camo.tier == Camo.Tier.MYTHIC;
   }

   @Override
   public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable(this.camo.tier.key()).withStyle(this.camo.tier.color, ChatFormatting.BOLD));
      tooltip.add(Component.translatable("item.heralisz_camo.camo.desc." + this.camo.id).withStyle(ChatFormatting.GRAY));
      if (this.camo.gun != null) {
         tooltip.add(Component.translatable("item.heralisz_camo.camo.gun." + this.camo.id).withStyle(ChatFormatting.GOLD));
      }
      tooltip.add(Component.translatable("item.heralisz_camo.camo.howto").withStyle(ChatFormatting.DARK_GRAY));
      if (this.camo.gun == null && fr.heralisz.camo.BagSupport.PRESENT) {
         tooltip.add(Component.translatable("item.heralisz_camo.camo.howto_bag").withStyle(ChatFormatting.DARK_GRAY));
      }
      tooltip.add(Component.translatable("item.heralisz_camo.camo.permanent").withStyle(ChatFormatting.DARK_RED));
   }
}
