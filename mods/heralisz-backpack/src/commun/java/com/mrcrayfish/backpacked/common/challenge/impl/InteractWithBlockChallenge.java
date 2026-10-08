package com.mrcrayfish.backpacked.common.challenge.impl;

import com.google.gson.JsonObject;
import com.mrcrayfish.backpacked.common.challenge.Challenge;
import com.mrcrayfish.backpacked.common.challenge.ChallengeSerializer;
import com.mrcrayfish.backpacked.common.challenge.ChallengeUtils;
import com.mrcrayfish.backpacked.common.tracker.IProgressTracker;
import com.mrcrayfish.backpacked.common.tracker.ProgressFormatter;
import com.mrcrayfish.backpacked.common.tracker.impl.CountProgressTracker;
import com.mrcrayfish.backpacked.data.unlock.UnlockManager;
import com.mrcrayfish.backpacked.event.BackpackedEvents;
import com.mrcrayfish.backpacked.event.block.InteractedWithBlock;
import java.util.Optional;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class InteractWithBlockChallenge extends Challenge {
   public static final ResourceLocation ID = new ResourceLocation("backpacked", "interact_with_block");
   public static final InteractWithBlockChallenge.Serializer SERIALIZER = new InteractWithBlockChallenge.Serializer();
   private final ProgressFormatter formatter;
   private final Optional<BlockPredicate> block;
   private final Optional<ItemPredicate> item;
   private final int count;

   public InteractWithBlockChallenge(ProgressFormatter formatter, Optional<BlockPredicate> block, Optional<ItemPredicate> item, int count) {
      super(ID);
      this.formatter = formatter;
      this.block = block;
      this.item = item;
      this.count = count;
   }

   @Override
   public ChallengeSerializer<?> getSerializer() {
      return SERIALIZER;
   }

   @Override
   public IProgressTracker createProgressTracker(ResourceLocation backpackId) {
      return new InteractWithBlockChallenge.Tracker(this.count, this.formatter, this.block, this.item);
   }

   public static class Serializer extends ChallengeSerializer<InteractWithBlockChallenge> {
      public InteractWithBlockChallenge deserialize(JsonObject object) {
         ProgressFormatter formatter = readFormatter(object, ProgressFormatter.COMPLETED_X_OF_X);
         Optional<BlockPredicate> block = object.has("block") ? Optional.of(BlockPredicate.fromJson(object.get("block"))) : Optional.empty();
         Optional<ItemPredicate> item = object.has("item") ? Optional.of(ItemPredicate.fromJson(object.get("item"))) : Optional.empty();
         int count = readCount(object, 1);
         return new InteractWithBlockChallenge(formatter, block, item, count);
      }
   }

   public static class Tracker extends CountProgressTracker {
      private final Optional<BlockPredicate> block;
      private final Optional<ItemPredicate> item;

      private Tracker(int maxCount, ProgressFormatter formatter, Optional<BlockPredicate> block, Optional<ItemPredicate> item) {
         super(maxCount, formatter);
         this.block = block;
         this.item = item;
      }

      private boolean test(BlockState state, ItemStack stack) {
         return ChallengeUtils.testPredicate(this.block, state, null) && ChallengeUtils.testPredicate(this.item, stack);
      }

      public static void registerEvent() {
         BackpackedEvents.INTERACTED_WITH_BLOCK_CAPTURE_TAG
            .register(
               (InteractedWithBlock.CaptureTag)(state, stack, player) -> UnlockManager.getTrackers(player, InteractWithBlockChallenge.Tracker.class)
                  .stream()
                  .anyMatch(tracker -> !tracker.isComplete() && tracker.test(state, stack))
            );
         BackpackedEvents.INTERACTED_WITH_BLOCK
            .register(
               (InteractedWithBlock)(state, stack, tag, player) -> UnlockManager.getTrackers(player, InteractWithBlockChallenge.Tracker.class)
                  .forEach(tracker -> {
                     if (!tracker.isComplete() && tracker.test(state, stack)) {
                        tracker.increment(player);
                     }
                  })
            );
      }
   }
}
