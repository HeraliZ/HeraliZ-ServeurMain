package com.hbm_m.multiblock;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

public class DoorBlockItem extends MultiblockBlockItem {
   public DoorBlockItem(Block pBlock, Properties pProperties) {
      super(pBlock, pProperties);
   }

   public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
      super.appendHoverText(stack, level, tooltip, flag);
      tooltip.add(Component.translatable("tooltip.hbm_m.door_skin").withStyle(ChatFormatting.GRAY));
   }
}
