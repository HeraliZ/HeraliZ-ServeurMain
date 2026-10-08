package com.mrcrayfish.backpacked.common.tracker.impl;

import com.mrcrayfish.backpacked.common.tracker.IProgressTracker;
import com.mrcrayfish.backpacked.common.tracker.ProgressFormatter;
import com.mrcrayfish.backpacked.data.unlock.UnlockManager;
import com.mrcrayfish.framework.api.event.PlayerEvents;
import com.mrcrayfish.framework.event.IPlayerEvent.CraftItem;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class CraftingProgressTracker implements IProgressTracker {
   protected int count;
   protected final int totalCount;
   private final ProgressFormatter formatter;
   protected final Predicate<ItemStack> predicate;

   public CraftingProgressTracker(int totalCount, ProgressFormatter formatter, Predicate<ItemStack> predicate) {
      this.totalCount = totalCount;
      this.formatter = formatter;
      this.predicate = predicate;
   }

   protected void processCrafted(ItemStack stack, ServerPlayer player) {
      if (this.predicate.test(stack)) {
         this.count = this.count + stack.getCount();
         this.markForCompletionTest(player);
      }
   }

   @Override
   public boolean isComplete() {
      return this.count >= this.totalCount;
   }

   @Override
   public void read(CompoundTag tag) {
      this.count = tag.getInt("Count");
   }

   @Override
   public void write(CompoundTag tag) {
      tag.putInt("Count", this.count);
   }

   @Override
   public Component getDisplayComponent() {
      return this.formatter.formatter().apply(this.count, this.totalCount);
   }

   public static void registerEvent() {
      PlayerEvents.CRAFT_ITEM.register((CraftItem)(player, stack, inventory) -> {
         if (!player.level().isClientSide()) {
            UnlockManager.getTrackers(player, CraftingProgressTracker.class).forEach(tracker -> {
               if (!tracker.isComplete()) {
                  tracker.processCrafted(stack, (ServerPlayer)player);
               }
            });
         }
      });
   }
}
