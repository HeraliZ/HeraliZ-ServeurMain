package com.mrcrayfish.backpacked.common.challenge.impl;

import com.google.gson.JsonObject;
import com.mrcrayfish.backpacked.common.challenge.Challenge;
import com.mrcrayfish.backpacked.common.challenge.ChallengeSerializer;
import com.mrcrayfish.backpacked.common.tracker.IProgressTracker;
import com.mrcrayfish.backpacked.common.tracker.ProgressFormatter;
import com.mrcrayfish.backpacked.common.tracker.impl.CountProgressTracker;
import com.mrcrayfish.backpacked.data.unlock.UnlockManager;
import com.mrcrayfish.backpacked.event.BackpackedEvents;
import com.mrcrayfish.backpacked.event.entity.FeedAnimal;
import java.util.Optional;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Animal;

public class FeedAnimalChallenge extends Challenge {
   public static final ResourceLocation ID = new ResourceLocation("backpacked", "feed_animal");
   public static final FeedAnimalChallenge.Serializer SERIALIZER = new FeedAnimalChallenge.Serializer();
   private final ProgressFormatter formatter;
   private final Optional<EntityPredicate> entity;
   private final int count;

   public FeedAnimalChallenge(ProgressFormatter formatter, Optional<EntityPredicate> entity, int count) {
      super(ID);
      this.formatter = formatter;
      this.entity = entity;
      this.count = count;
   }

   @Override
   public ChallengeSerializer<?> getSerializer() {
      return SERIALIZER;
   }

   @Override
   public IProgressTracker createProgressTracker(ResourceLocation backpackId) {
      return new FeedAnimalChallenge.Tracker(this.count, this.formatter, this.entity);
   }

   public static class Serializer extends ChallengeSerializer<FeedAnimalChallenge> {
      public FeedAnimalChallenge deserialize(JsonObject object) {
         ProgressFormatter formatter = readFormatter(object, ProgressFormatter.FED_X_OF_X);
         Optional<EntityPredicate> predicate = object.has("animal") ? Optional.of(EntityPredicate.fromJson(object.get("animal"))) : Optional.empty();
         int count = readCount(object, 1);
         return new FeedAnimalChallenge(formatter, predicate, count);
      }
   }

   public static class Tracker extends CountProgressTracker {
      private final Optional<EntityPredicate> entityPredicate;

      private Tracker(int maxCount, ProgressFormatter formatter, Optional<EntityPredicate> entityPredicate) {
         super(maxCount, formatter);
         this.entityPredicate = entityPredicate;
      }

      private boolean test(ServerPlayer player, Animal animal) {
         return this.entityPredicate.<Boolean>map(p -> p.matches(player, animal)).orElse(true);
      }

      public static void registerEvent() {
         BackpackedEvents.FEED_ANIMAL.register((FeedAnimal)(animal, player) -> {
            if (!player.level().isClientSide()) {
               UnlockManager.getTrackers(player, FeedAnimalChallenge.Tracker.class).forEach(tracker -> {
                  ServerPlayer serverPlayer = (ServerPlayer)player;
                  if (!tracker.isComplete() && tracker.test(serverPlayer, animal)) {
                     tracker.increment(serverPlayer);
                  }
               });
            }
         });
      }
   }
}
