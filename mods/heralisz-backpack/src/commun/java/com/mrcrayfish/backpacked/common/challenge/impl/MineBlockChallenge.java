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
import com.mrcrayfish.backpacked.event.block.MinedBlock;
import java.util.Optional;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class MineBlockChallenge extends Challenge {
   public static final ResourceLocation ID = new ResourceLocation("backpacked", "mine_block");
   public static final MineBlockChallenge.Serializer SERIALIZER = new MineBlockChallenge.Serializer();
   private final ProgressFormatter formatter;
   private final Optional<BlockPredicate> block;
   private final Optional<ItemPredicate> item;
   private final int count;

   public MineBlockChallenge(ProgressFormatter formatter, Optional<BlockPredicate> block, Optional<ItemPredicate> item, int count) {
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
      return new MineBlockChallenge.Tracker(this.count, this.formatter, this.block, this.item);
   }

   public static class Serializer extends ChallengeSerializer<MineBlockChallenge> {
      public MineBlockChallenge deserialize(JsonObject object) {
         ProgressFormatter formatter = readFormatter(object, ProgressFormatter.MINED_X_OF_X);
         Optional<BlockPredicate> block = object.has("block") ? Optional.of(BlockPredicate.fromJson(object.get("block"))) : Optional.empty();
         Optional<ItemPredicate> item = object.has("item") ? Optional.of(ItemPredicate.fromJson(object.get("item"))) : Optional.empty();
         int count = readCount(object, 1);
         return new MineBlockChallenge(formatter, block, item, count);
      }
   }

   public static class Tracker extends CountProgressTracker {
      private final Optional<BlockPredicate> block;
      private final Optional<ItemPredicate> item;

      protected Tracker(int maxCount, ProgressFormatter formatter, Optional<BlockPredicate> block, Optional<ItemPredicate> item) {
         super(maxCount, formatter);
         this.block = block;
         this.item = item;
      }

      private boolean test(BlockState state, ItemStack stack, @Nullable CompoundTag tag) {
         return ChallengeUtils.testPredicate(this.block, state, tag) && ChallengeUtils.testPredicate(this.item, stack);
      }

      public static void registerEvent() {
         BackpackedEvents.MINED_BLOCK_CAPTURE_TAG
            .register(
               (MinedBlock.CaptureTag)(state, stack, player) -> player.level().isClientSide()
                  ? false
                  : UnlockManager.getTrackers(player, MineBlockChallenge.Tracker.class)
                     .stream()
                     .anyMatch(tracker -> !tracker.isComplete() && tracker.test(state, stack, null))
            );
         BackpackedEvents.MINED_BLOCK.register((MinedBlock)(state, stack, tag, player) -> {
            if (!player.level().isClientSide()) {
               UnlockManager.getTrackers(player, MineBlockChallenge.Tracker.class).forEach(tracker -> {
                  if (!tracker.isComplete() && tracker.test(state, stack, tag)) {
                     tracker.increment((ServerPlayer)player);
                  }
               });
            }
         });
      }
   }
}
