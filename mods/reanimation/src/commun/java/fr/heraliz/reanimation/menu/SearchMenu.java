package fr.heraliz.reanimation.menu;

import fr.heraliz.reanimation.core.DownedManager;
import fr.heraliz.reanimation.registry.ModMenus;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class SearchMenu extends AbstractContainerMenu {
   public static final int TARGET_SIZE = 41;
   public static final int FIRST_BACKPACK = 5;
   public static final int WIDTH = 176;
   public static final int EQUIP_Y = 20;
   public static final int OFFHAND_X = 98;
   public static final int MAIN_Y = 50;
   public static final int HOTBAR_Y = 108;
   public static final int INV_LABEL_Y = 132;
   public static final int INV_Y = 144;
   public static final int INV_HOTBAR_Y = 202;
   public static final int HEIGHT = 226;
   private static final EquipmentSlot[] ARMOR = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   private static final ResourceLocation[] ARMOR_ICONS = new ResourceLocation[]{
      InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE, InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS
   };
   private final Container target;
   private final int targetId;
   @Nullable
   private final LivingEntity owner;

   public SearchMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
      this(id, playerInventory, new SimpleContainer(41), buf.readVarInt(), (LivingEntity)null);
   }

   public SearchMenu(int id, Inventory playerInventory, Container target, LivingEntity owner) {
      this(id, playerInventory, target, owner.getId(), owner);
   }

   private SearchMenu(int id, Inventory playerInventory, Container target, int targetId, @Nullable final LivingEntity owner) {
      super((MenuType)ModMenus.SEARCH.get(), id);
      checkContainerSize(target, 41);
      this.target = target;
      this.targetId = targetId;
      this.owner = owner;
      final Player viewer = playerInventory.player;

      for (int i = 0; i < 4; i++) {
         final EquipmentSlot type = ARMOR[i];
         this.addSlot((new Slot(target, 39 - i, 8 + i * 18, 20) {
            public boolean mayPlace(ItemStack stack) {
               return stack.canEquip(type, (Entity)(owner != null ? owner : viewer));
            }

            public int getMaxStackSize() {
               return 1;
            }
         }).setBackground(InventoryMenu.BLOCK_ATLAS, ARMOR_ICONS[i]));
      }

      this.addSlot(new Slot(target, 40, 98, 20).setBackground(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD));

      for (int row = 0; row < 3; row++) {
         for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(target, 9 + row * 9 + col, 8 + col * 18, 50 + row * 18));
         }
      }

      for (int col = 0; col < 9; col++) {
         this.addSlot(new Slot(target, col, 8 + col * 18, 108));
      }

      for (int row = 0; row < 3; row++) {
         for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, 144 + row * 18));
         }
      }

      for (int col = 0; col < 9; col++) {
         this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 202));
      }
   }

   public int targetId() {
      return this.targetId;
   }

   @Nullable
   public LivingEntity owner() {
      return this.owner;
   }

   public ItemStack quickMoveStack(Player player, int index) {
      Slot slot = (Slot)this.slots.get(index);
      if (!slot.hasItem()) {
         return ItemStack.EMPTY;
      }

      ItemStack stack = slot.getItem();
      ItemStack copy = stack.copy();
      if (index < 41) {
         if (!this.moveItemStackTo(stack, 41, this.slots.size(), true)) {
            return ItemStack.EMPTY;
         }
      } else {
         boolean moved = false;

         for (int i = 0; i < 4 && !moved; i++) {
            if (!((Slot)this.slots.get(i)).hasItem() && ((Slot)this.slots.get(i)).mayPlace(stack)) {
               moved = this.moveItemStackTo(stack, i, i + 1, false);
            }
         }

         if (!moved && !this.moveItemStackTo(stack, 5, 41, false)) {
            return ItemStack.EMPTY;
         }
      }

      if (stack.isEmpty()) {
         slot.set(ItemStack.EMPTY);
      } else {
         slot.setChanged();
      }

      if (stack.getCount() == copy.getCount()) {
         return ItemStack.EMPTY;
      }

      slot.onTake(player, stack);
      return copy;
   }

   public boolean stillValid(Player player) {
      return this.owner == null || DownedManager.isSearching(player, this.owner);
   }

   public void removed(Player player) {
      super.removed(player);
      if (this.owner != null && !player.level().isClientSide) {
         DownedManager.searchClosed(this.owner, player);
      }
   }
}
