package com.mrcrayfish.backpacked.common.challenge.impl;

import com.google.gson.JsonObject;
import com.mrcrayfish.backpacked.common.challenge.Challenge;
import com.mrcrayfish.backpacked.common.challenge.ChallengeSerializer;
import com.mrcrayfish.backpacked.common.tracker.IProgressTracker;
import com.mrcrayfish.backpacked.common.tracker.ProgressFormatter;
import com.mrcrayfish.backpacked.common.tracker.impl.CountProgressTracker;
import com.mrcrayfish.backpacked.data.unlock.UnlockManager;
import com.mrcrayfish.backpacked.event.BackpackedEvents;
import com.mrcrayfish.backpacked.event.entity.MerchantTrade;
import java.util.Optional;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public class MerchantTradeChallenge extends Challenge {
   public static final ResourceLocation ID = new ResourceLocation("backpacked", "merchant_trade");
   public static final MerchantTradeChallenge.Serializer SERIALIZER = new MerchantTradeChallenge.Serializer();
   private final ProgressFormatter formatter;
   private final Optional<EntityPredicate> entity;
   private final Optional<ItemPredicate> item;
   private final int count;

   protected MerchantTradeChallenge(ProgressFormatter formatter, Optional<EntityPredicate> entity, Optional<ItemPredicate> item, int count) {
      super(ID);
      this.formatter = formatter;
      this.entity = entity;
      this.item = item;
      this.count = count;
   }

   @Override
   public ChallengeSerializer<?> getSerializer() {
      return SERIALIZER;
   }

   @Override
   public IProgressTracker createProgressTracker(ResourceLocation backpackId) {
      return new MerchantTradeChallenge.Tracker(this.count, this.formatter, this.entity, this.item);
   }

   public static class Serializer extends ChallengeSerializer<MerchantTradeChallenge> {
      public MerchantTradeChallenge deserialize(JsonObject object) {
         ProgressFormatter formatter = readFormatter(object, ProgressFormatter.TRADED_X_OF_X);
         Optional<EntityPredicate> entity = object.has("merchant") ? Optional.of(EntityPredicate.fromJson(object.get("merchant"))) : Optional.empty();
         Optional<ItemPredicate> item = object.has("item") ? Optional.of(ItemPredicate.fromJson(object.get("item"))) : Optional.empty();
         int count = readCount(object, 1);
         return new MerchantTradeChallenge(formatter, entity, item, count);
      }
   }

   public static class Tracker extends CountProgressTracker {
      private final Optional<EntityPredicate> entity;
      private final Optional<ItemPredicate> item;

      public Tracker(int maxCount, ProgressFormatter formatter, Optional<EntityPredicate> entity, Optional<ItemPredicate> item) {
         super(maxCount, formatter);
         this.entity = entity;
         this.item = item;
      }

      private boolean test(ServerPlayer player, Entity merchant, ItemStack stack) {
         return this.entity.<Boolean>map(p -> p.matches(player, merchant)).orElse(true) && this.item.<Boolean>map(p -> p.matches(stack)).orElse(true);
      }

      public static void registerEvent() {
         BackpackedEvents.MERCHANT_TRADE.register((MerchantTrade)(merchant, player, stack) -> {
            if (!player.level().isClientSide() && merchant instanceof Entity entity) {
               UnlockManager.getTrackers(player, MerchantTradeChallenge.Tracker.class).forEach(tracker -> {
                  if (!tracker.isComplete()) {
                     ServerPlayer serverPlayer = (ServerPlayer)player;
                     if (tracker.test(serverPlayer, entity, stack)) {
                        tracker.increment(serverPlayer);
                     }
                  }
               });
            }
         });
      }
   }
}
