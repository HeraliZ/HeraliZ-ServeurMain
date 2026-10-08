package com.mrcrayfish.backpacked.item;

import com.mrcrayfish.backpacked.inventory.BackpackInventory;
import com.mrcrayfish.backpacked.inventory.BackpackedInventoryAccess;
import com.mrcrayfish.backpacked.platform.Services;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;

public class BackpackItem extends Item {
   /**
    * Infobulle du sac (util.ClientUtils.createBackpackTooltip), branchee par la partie client (PartieClient) ; utilisee aussi
    * par BackpackItem_expand1 / _expand2 et Backpack_Mountain.
    */
   public static volatile java.util.function.BiConsumer<ItemStack, List<Component>> INFOBULLE_CLIENT = (stack, list) -> {
   };

   private final int columns;
   private final int rows;
   public static final Component BACKPACK_TRANSLATION = Component.translatable("container.backpack");
   public static final MutableComponent REMOVE_ITEMS_TOOLTIP = Component.translatable("backpacked.tooltip.remove_items").withStyle(ChatFormatting.RED);

   public BackpackItem(Properties properties, int columns, int rows) {
      super(properties);
      this.columns = columns;
      this.rows = rows;
   }

   public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> list, TooltipFlag flag) {
      if (level != null) {
         BackpackItem.INFOBULLE_CLIENT.accept(stack, list);
      }
   }

   public static boolean openBackpack(ServerPlayer ownerPlayer, ServerPlayer openingPlayer) {
      ItemStack backpack = Services.BACKPACK.getBackpackStack(ownerPlayer);
      if (!backpack.isEmpty()) {
         BackpackInventory backpackInventory = ((BackpackedInventoryAccess)ownerPlayer).backpacked$GetBackpackInventory();
         if (backpackInventory == null) {
            return false;
         }

         BackpackItem backpackItem = (BackpackItem)backpack.getItem();
         Component title = backpack.hasCustomHoverName() ? backpack.getHoverName() : BACKPACK_TRANSLATION;
         int cols = backpackItem.getColumnCount();
         int rows = backpackItem.getRowCount();
         boolean owner = ownerPlayer.equals(openingPlayer);
         Services.BACKPACK.openBackpackScreen(openingPlayer, backpackInventory, cols, rows, owner, title);
         return true;
      } else {
         return false;
      }
   }

   public int getColumnCount() {
      return this.columns;
   }

   public int getRowCount() {
      return this.rows;
   }

   public boolean canFitInsideContainerItems() {
      return false;
   }
}
