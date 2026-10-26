package com.mrcrayfish.backpacked.common.challenge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mrcrayfish.backpacked.common.tracker.IProgressTracker;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

public abstract class Challenge {
   public static final Challenge DUMMY = new Challenge.Dummy();
   private final ResourceLocation id;

   protected Challenge(ResourceLocation id) {
      this.id = id;
   }

   public static Optional<Challenge> deserialize(@Nullable JsonElement element) {
      if (element != null && element.isJsonObject()) {
         JsonObject object = element.getAsJsonObject();
         ResourceLocation type = new ResourceLocation(GsonHelper.getAsString(object, "type"));
         ChallengeSerializer serializer = ChallengeManager.instance().getSerializer(type);
         if (serializer == null) {
            throw new JsonParseException("Invalid challenge: " + type);
         } else {
            return Optional.ofNullable(serializer.deserialize(object));
         }
      } else {
         return Optional.empty();
      }
   }

   public abstract ChallengeSerializer<?> getSerializer();

   public abstract IProgressTracker createProgressTracker(ResourceLocation var1);

   private static class Dummy extends Challenge {
      protected Dummy() {
         super(new ResourceLocation("backpacked", "empty"));
      }

      @Override
      public ChallengeSerializer<?> getSerializer() {
         return new ChallengeSerializer<Challenge>() {
            @Override
            public Challenge deserialize(JsonObject object) {
               return Challenge.DUMMY;
            }
         };
      }

      @Override
      public IProgressTracker createProgressTracker(ResourceLocation backpackId) {
         return null;
      }
   }
}
