package com.mrcrayfish.backpacked.common.challenge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mrcrayfish.backpacked.common.tracker.ProgressFormatter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

public abstract class ChallengeSerializer<T extends Challenge> {
   public abstract T deserialize(JsonObject var1);

   public static ProgressFormatter readFormatter(JsonObject object, ProgressFormatter defaultValue) {
      if (object.has("formatter")) {
         ResourceLocation formatterId = new ResourceLocation(GsonHelper.getAsString(object, "formatter"));
         ProgressFormatter newFormatter = (ProgressFormatter)ProgressFormatter.REGISTERED_FORMATTERS.get(formatterId);
         if (newFormatter != null) {
            return newFormatter;
         } else {
            throw new JsonParseException("Invalid formatter: " + formatterId);
         }
      } else {
         return defaultValue;
      }
   }

   public static int readCount(JsonObject object, int defaultValue) {
      if (object.has("count")) {
         int value = GsonHelper.getAsInt(object, "count");
         if (value < 1) {
            throw new JsonParseException("Count must be positive and greater than zero");
         } else {
            return value;
         }
      } else {
         return defaultValue;
      }
   }
}
