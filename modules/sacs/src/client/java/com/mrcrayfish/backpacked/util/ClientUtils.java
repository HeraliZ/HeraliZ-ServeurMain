package com.mrcrayfish.backpacked.util;

import com.mrcrayfish.backpacked.Config;
import com.mrcrayfish.backpacked.item.BackpackItem;
import com.mrcrayfish.backpacked.platform.Services;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

public class ClientUtils {
   public static void createBackpackTooltip(ItemStack stack, List<Component> list) {
      if ((Boolean)Config.SERVER.backpack.lockIntoSlot.get()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null && Services.BACKPACK.getBackpackStack(mc.player).equals(stack)) {
            CompoundTag tag = stack.getTag();
            if (tag != null && !tag.getList("Items", 10).isEmpty()) {
               mc.font
                  .getSplitter()
                  .splitLines(BackpackItem.REMOVE_ITEMS_TOOLTIP, 150, Style.EMPTY)
                  .forEach(formattedText -> list.add(Component.literal(formattedText.getString()).withStyle(ChatFormatting.RED)));
            }
         }
      }
   }
}
