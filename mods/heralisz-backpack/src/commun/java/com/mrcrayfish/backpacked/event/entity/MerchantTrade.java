package com.mrcrayfish.backpacked.event.entity;

import com.mrcrayfish.framework.api.event.IFrameworkEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;

public interface MerchantTrade extends IFrameworkEvent {
   void handle(Merchant var1, Player var2, ItemStack var3);
}
