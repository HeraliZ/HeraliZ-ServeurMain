package com.mrcrayfish.backpacked.common.challenge.impl;

import com.google.gson.JsonObject;
import com.mrcrayfish.backpacked.common.challenge.Challenge;
import com.mrcrayfish.backpacked.common.challenge.ChallengeSerializer;
import com.mrcrayfish.backpacked.common.tracker.IProgressTracker;
import com.mrcrayfish.backpacked.common.tracker.ProgressFormatter;
import com.mrcrayfish.backpacked.common.tracker.impl.CountProgressTracker;
import com.mrcrayfish.backpacked.data.unlock.UnlockManager;
import com.mrcrayfish.framework.api.event.EntityEvents;
import com.mrcrayfish.framework.event.IEntityEvent.LivingEntityDeath;
import java.util.Optional;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class KillMobChallenge extends Challenge {
   public static final ResourceLocation ID = new ResourceLocation("backpacked", "kill_mob");
   public static final KillMobChallenge.Serializer SERIALIZER = new KillMobChallenge.Serializer();
   private final ProgressFormatter formatter;
   private final Optional<EntityPredicate> entity;
   private final Optional<ItemPredicate> item;
   private final int count;

   public KillMobChallenge(ProgressFormatter formatter, Optional<EntityPredicate> entity, Optional<ItemPredicate> item, int count) {
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
      return new KillMobChallenge.Tracker(this.count, this.formatter, this.entity, this.item);
   }

   public static final class Serializer extends ChallengeSerializer<KillMobChallenge> {
      public KillMobChallenge deserialize(JsonObject object) {
         ProgressFormatter formatter = readFormatter(object, ProgressFormatter.KILLED_X_OF_X);
         Optional<EntityPredicate> entity = object.has("mob") ? Optional.of(EntityPredicate.fromJson(object.get("mob"))) : Optional.empty();
         Optional<ItemPredicate> item = object.has("item") ? Optional.of(ItemPredicate.fromJson(object.get("item"))) : Optional.empty();
         int count = readCount(object, 1);
         return new KillMobChallenge(formatter, entity, item, count);
      }
   }

   public static class Tracker extends CountProgressTracker {
      private final Optional<EntityPredicate> entityPredicate;
      private final Optional<ItemPredicate> itemPredicate;

      private Tracker(int maxCount, ProgressFormatter formatter, Optional<EntityPredicate> entityPredicate, Optional<ItemPredicate> itemPredicate) {
         super(maxCount, formatter);
         this.entityPredicate = entityPredicate;
         this.itemPredicate = itemPredicate;
      }

      private boolean test(LivingEntity entity, ItemStack stack, ServerPlayer player) {
         return this.entityPredicate.<Boolean>map(p -> p.matches(player, entity)).orElse(true)
            && this.itemPredicate.<Boolean>map(p -> p.matches(stack)).orElse(true);
      }

      public static void registerEvent() {
         EntityEvents.LIVING_ENTITY_DEATH.register((LivingEntityDeath)(entity, source) -> {
            if (entity.level().isClientSide()) {
               return false;
            }

            Entity cause = source.getEntity();
            if (cause != null && cause.getType() == EntityType.PLAYER) {
               ServerPlayer player = (ServerPlayer)cause;
               UnlockManager.getTrackers(player, KillMobChallenge.Tracker.class).forEach(tracker -> {
                  if (!tracker.isComplete()) {
                     ItemStack heldItem = player.getMainHandItem();
                     if (tracker.test(entity, heldItem, player)) {
                        tracker.increment(player);
                     }
                  }
               });
            }

            return false;
         });
      }
   }
}
