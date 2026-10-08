package fr.heraliz.reanimation.entity;

import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class BotInventory implements Container {
   private static final EquipmentSlot[] ARMOR = new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
   private final BotEntityServeur bot;

   BotInventory(BotEntityServeur bot) {
      this.bot = bot;
   }

   private static EquipmentSlot equipment(int index) {
      if (index == 0) {
         return EquipmentSlot.MAINHAND;
      } else if (index >= 36 && index < 40) {
         return ARMOR[index - 36];
      } else {
         return index == 40 ? EquipmentSlot.OFFHAND : null;
      }
   }

   public int getContainerSize() {
      return 41;
   }

   public boolean isEmpty() {
      for (int i = 0; i < this.getContainerSize(); i++) {
         if (!this.getItem(i).isEmpty()) {
            return false;
         }
      }

      return true;
   }

   public ItemStack getItem(int index) {
      if (index >= 0 && index < this.getContainerSize()) {
         EquipmentSlot slot = equipment(index);
         return slot != null ? this.bot.getItemBySlot(slot) : (ItemStack)this.bot.backpack.get(index);
      } else {
         return ItemStack.EMPTY;
      }
   }

   public ItemStack removeItem(int index, int count) {
      ItemStack current = this.getItem(index);
      if (!current.isEmpty() && count > 0) {
         ItemStack taken = current.split(count);
         this.setItem(index, current.isEmpty() ? ItemStack.EMPTY : current);
         return taken;
      } else {
         return ItemStack.EMPTY;
      }
   }

   public ItemStack removeItemNoUpdate(int index) {
      ItemStack current = this.getItem(index);
      this.setItem(index, ItemStack.EMPTY);
      return current;
   }

   public void setItem(int index, ItemStack stack) {
      if (index >= 0 && index < this.getContainerSize()) {
         EquipmentSlot slot = equipment(index);
         if (slot != null) {
            this.bot.setItemSlot(slot, stack);
         } else {
            this.bot.backpack.set(index, stack);
         }
      }
   }

   public void setChanged() {
   }

   public boolean stillValid(Player player) {
      return this.bot.isAlive();
   }

   public void clearContent() {
      for (int i = 0; i < this.getContainerSize(); i++) {
         this.setItem(i, ItemStack.EMPTY);
      }
   }
}
