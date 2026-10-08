package com.mrcrayfish.backpacked.platform.services;

import com.mrcrayfish.backpacked.blockentity.ShelfBlockEntity;
import com.mrcrayfish.backpacked.item.BackpackItem;
import com.mrcrayfish.backpacked.item.BackpackItem_expand1;
import com.mrcrayfish.backpacked.item.BackpackItem_expand2;
import com.mrcrayfish.backpacked.item.Backpack_Mountain;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.level.block.state.BlockState;

public interface IBackpackHelper {
   ItemStack getBackpackStack(Player var1);

   boolean setBackpackStack(Player var1, ItemStack var2);

   EnchantmentCategory getEnchantmentCategory();

   boolean isBackpackVisible(Player var1);

   ShelfBlockEntity createShelfBlockEntityType(BlockPos var1, BlockState var2);

   void openBackpackScreen(ServerPlayer var1, Container var2, int var3, int var4, boolean var5, Component var6);

   BackpackItem createBackpackItem(Properties var1, int var2, int var3);

   Backpack_Mountain createBackpackMountainItem(Properties var1);

   BackpackItem_expand1 createBackpackExpand1Item(Properties var1);

   BackpackItem_expand2 createBackpackExpand2Item(Properties var1);
}
