package com.mrcrayfish.backpacked.blockentity;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public interface IOptionalStorage extends Container {
   @Nullable
   SimpleContainer getInventory();

   default int getContainerSize() {
      return Optional.ofNullable(this.getInventory()).<Integer>map(SimpleContainer::getContainerSize).orElse(0);
   }

   default boolean isEmpty() {
      return Optional.ofNullable(this.getInventory()).<Boolean>map(SimpleContainer::isEmpty).orElse(true);
   }

   default ItemStack getItem(int index) {
      return Optional.ofNullable(this.getInventory()).map(inv -> inv.getItem(index)).orElse(ItemStack.EMPTY);
   }

   default ItemStack removeItem(int index, int count) {
      return Optional.ofNullable(this.getInventory()).map(inv -> inv.removeItem(index, count)).orElse(ItemStack.EMPTY);
   }

   default ItemStack removeItemNoUpdate(int index) {
      return Optional.ofNullable(this.getInventory()).map(inv -> inv.removeItemNoUpdate(index)).orElse(ItemStack.EMPTY);
   }

   default void setItem(int index, ItemStack stack) {
      Optional.ofNullable(this.getInventory()).ifPresent(inv -> inv.setItem(index, stack));
   }

   default boolean stillValid(Player player) {
      return Optional.ofNullable(this.getInventory()).map(inv -> inv.stillValid(player)).orElse(false);
   }

   default void clearContent() {
      Optional.ofNullable(this.getInventory()).ifPresent(SimpleContainer::clearContent);
   }

   default boolean canPlaceItem(int index, ItemStack stack) {
      return this.getInventory() != null;
   }
}
