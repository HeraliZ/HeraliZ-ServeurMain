package com.mrcrayfish.backpacked.common.challenge.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import com.mrcrayfish.backpacked.common.MovementType;
import com.mrcrayfish.backpacked.common.challenge.Challenge;
import com.mrcrayfish.backpacked.common.challenge.ChallengeSerializer;
import com.mrcrayfish.backpacked.common.tracker.IProgressTracker;
import com.mrcrayfish.backpacked.common.tracker.ProgressFormatter;
import com.mrcrayfish.backpacked.common.tracker.impl.CountProgressTracker;
import com.mrcrayfish.backpacked.data.unlock.UnlockManager;
import com.mrcrayfish.backpacked.event.BackpackedEvents;
import com.mrcrayfish.backpacked.event.entity.PlayerTravel;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

public class TravelDistanceChallenge extends Challenge {
   public static final ResourceLocation ID = new ResourceLocation("backpacked", "travel_distance");
   public static final TravelDistanceChallenge.Serializer SERIALIZER = new TravelDistanceChallenge.Serializer();
   private final ProgressFormatter formatter;
   private final Optional<EnumSet<MovementType>> movementTypes;
   private final int totalDistanceInCm;

   protected TravelDistanceChallenge(ProgressFormatter formatter, Optional<EnumSet<MovementType>> movementTypes, int totalDistanceInCm) {
      super(ID);
      this.formatter = formatter;
      this.movementTypes = movementTypes;
      this.totalDistanceInCm = totalDistanceInCm;
   }

   @Override
   public ChallengeSerializer<?> getSerializer() {
      return SERIALIZER;
   }

   @Override
   public IProgressTracker createProgressTracker(ResourceLocation backpackId) {
      return new TravelDistanceChallenge.Tracker(this.formatter, this.movementTypes, this.totalDistanceInCm);
   }

   public static class Serializer extends ChallengeSerializer<TravelDistanceChallenge> {
      public TravelDistanceChallenge deserialize(JsonObject object) {
         ProgressFormatter formatter = readFormatter(object, ProgressFormatter.INT_PERCENT);
         Optional<EnumSet<MovementType>> movements = object.has("movement")
            ? MovementType.LIST_CODEC.xmap(EnumSet::copyOf, List::copyOf).parse(JsonOps.INSTANCE, object.get("movement")).result()
            : Optional.empty();
         int totalDistance = GsonHelper.getAsInt(object, "total_distance");
         if (totalDistance <= 0) {
            throw new JsonParseException("Total distance must be greater than zero. Found " + totalDistance);
         } else {
            return new TravelDistanceChallenge(formatter, movements, totalDistance);
         }
      }
   }

   public static class Tracker extends CountProgressTracker {
      private final Optional<EnumSet<MovementType>> movementTypes;

      public Tracker(ProgressFormatter formatter, Optional<EnumSet<MovementType>> movementTypes, int maxCount) {
         super(maxCount, formatter);
         this.movementTypes = movementTypes;
      }

      public static void registerEvent() {
         BackpackedEvents.PLAYER_TRAVEL.register((PlayerTravel)(player, distance, type) -> {
            int distanceInCm = Math.round((float)Math.sqrt(distance) * 100.0F);
            UnlockManager.getTrackers(player, TravelDistanceChallenge.Tracker.class).forEach(tracker -> {
               if (!tracker.isComplete()) {
                  if (tracker.movementTypes.map(types -> types.contains(type)).orElse(true)) {
                     tracker.increment(distanceInCm, player);
                  }
               }
            });
         });
      }
   }
}
