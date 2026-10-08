package com.mrcrayfish.backpacked.blockentity;

import com.mrcrayfish.backpacked.block.ShelfBlock;
import com.mrcrayfish.backpacked.core.ModSounds;
import com.mrcrayfish.backpacked.inventory.container.slot.BackpackSlot;
import com.mrcrayfish.backpacked.item.BackpackItem;
import com.mrcrayfish.backpacked.item.BackpackItem_expand1;
import com.mrcrayfish.backpacked.item.BackpackItem_expand2;
import com.mrcrayfish.backpacked.platform.Services;
import com.mrcrayfish.backpacked.util.BlockEntityUtil;
import com.mrcrayfish.backpacked.util.InventoryHelper;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ShelfBlockEntity extends BlockEntity implements IOptionalStorage {
   private ItemStack backpack = ItemStack.EMPTY;
   private SimpleContainer inventory = null;

   public ShelfBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
      super(type, pos, state);
   }

   @Nullable
   @Override
   public SimpleContainer getInventory() {
      return this.inventory;
   }

   public ItemStack getBackpackWithContents() {
      ItemStack stack = this.backpack.copy();
      if (!stack.isEmpty()) {
         this.copyInventoryToStack(stack);
      }

      return stack;
   }

   public InteractionResult interact(Player player) {
      if (player.isCrouching() || this.backpack.isEmpty()) {
         ItemStack stack = Services.BACKPACK.getBackpackStack(player);
         if (!stack.isEmpty() && !(stack.getItem() instanceof BackpackItem)) {
            if (!this.backpack.isEmpty()) {
               player.displayClientMessage(Component.translatable("message.backpacked.occupied_back_slot"), true);
            }

            return InteractionResult.FAIL;
         }

         ItemStack result = this.shelveBackpack(stack);
         Services.BACKPACK.setBackpackStack(player, result);
      } else if (player instanceof ServerPlayer serverPlayer) {
         this.openBackpackInventory(serverPlayer);
      }

      return InteractionResult.SUCCESS;
   }

   public ItemStack shelveBackpack(ItemStack stack) {
      ItemStack shelvedBackpack = this.backpack.copy();
      this.copyInventoryToStack(shelvedBackpack);
      this.backpack = stack.copy();
      if (!this.backpack.isEmpty() || !shelvedBackpack.isEmpty()) {
         boolean removed = this.backpack.isEmpty();
         this.updateInventory(false);
         BlockEntityUtil.sendUpdatePacket(this);
         this.level.playSound(null, this.worldPosition, (SoundEvent)ModSounds.ITEM_BACKPACK_PLACE.get(), SoundSource.BLOCKS, 1.0F, removed ? 0.75F : 1.0F);
         this.setChanged();
      }

      return shelvedBackpack;
   }

   private void openBackpackInventory(ServerPlayer player) {
      this.getBackpackInventory().ifPresent(inventory -> this.getBackpackItem().ifPresent(backpackItem -> {
         Component title = this.backpack.hasCustomHoverName() ? this.backpack.getHoverName() : BackpackItem.BACKPACK_TRANSLATION;
         int cols = backpackItem.getColumnCount();
         int rows = backpackItem.getRowCount();
         Services.BACKPACK.openBackpackScreen(player, inventory, cols, rows, false, title);
      }));
   }

   private void copyInventoryToStack(ItemStack stack) {
      if (!stack.isEmpty()) {
         this.getBackpackInventory().ifPresent(inventory -> stack.getOrCreateTag().put("Items", InventoryHelper.saveAllItems(new ListTag(), inventory)));
      }
   }

   private Optional<SimpleContainer> getBackpackInventory() {
      if (this.backpack.isEmpty()) {
         return Optional.empty();
      }

      if (this.inventory != null && this.inventory.getContainerSize() != this.getBackpackSize()) {
         this.updateInventory(true);
      }

      return Optional.ofNullable(this.inventory);
   }

   private void updateInventory(boolean resized) {
      if (!this.backpack.isEmpty()) {
         SimpleContainer oldInventory = this.inventory;
         this.inventory = new ShelfBlockEntity.ShelfContainer(this.getBackpackSize());
         CompoundTag compound = this.backpack.getOrCreateTag();
         this.loadBackpackItems(compound);
         compound.remove("Items");
         if (resized && oldInventory != null) {
            InventoryHelper.mergeInventory(oldInventory, this.inventory, this.level, Vec3.atCenterOf(this.worldPosition));
         }
      } else {
         this.inventory = null;
      }
   }

   private void loadBackpackItems(CompoundTag compound) {
      if (compound.contains("Items", 9)) {
         InventoryHelper.loadAllItems(compound.getList("Items", 10), this.inventory, this.level, Vec3.atCenterOf(this.worldPosition));
      }
   }

   public void load(CompoundTag tag) {
      super.load(tag);
      this.backpack = ItemStack.of(tag.getCompound("Backpack"));
      this.inventory = this.backpack.isEmpty() ? null : new ShelfBlockEntity.ShelfContainer(this.getBackpackSize());
      this.loadBackpackItems(tag);
   }

   protected void saveAdditional(CompoundTag tag) {
      tag.put("Backpack", this.backpack.save(new CompoundTag()));
      if (this.inventory != null) {
         ListTag items = new ListTag();
         InventoryHelper.saveAllItems(items, this.inventory);
         tag.put("Items", items);
      }
   }

   public CompoundTag getUpdateTag() {
      return this.saveWithFullMetadata();
   }

   @Nullable
   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }

   public ItemStack getBackpack() {
      return this.backpack;
   }

   public Direction getDirection() {
      return (Direction)this.getBlockState().getValue(ShelfBlock.FACING);
   }

   private Optional<BackpackItem> getBackpackItem() {
      return !(this.backpack.getItem() instanceof BackpackItem)
            && !(this.backpack.getItem() instanceof BackpackItem_expand1)
            && !(this.backpack.getItem() instanceof BackpackItem_expand2)
         ? Optional.empty()
         : Optional.of((BackpackItem)this.backpack.getItem());
   }

   private int getBackpackSize() {
      return this.getBackpackItem().map(item -> item.getRowCount() * item.getColumnCount()).orElse(0);
   }

   @Override
   public boolean canPlaceItem(int index, ItemStack stack) {
      return !BackpackSlot.isBannedItem(stack);
   }

   public class ShelfContainer extends SimpleContainer {
      public ShelfContainer(int size) {
         super(size);
      }

      public void setChanged() {
         super.setChanged();
         ShelfBlockEntity.this.setChanged();
      }

      public boolean stillValid(Player player) {
         return ShelfBlockEntity.this.inventory == this && !ShelfBlockEntity.this.backpack.isEmpty() && !ShelfBlockEntity.this.remove;
      }
   }
}
