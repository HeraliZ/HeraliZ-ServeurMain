package fr.heralisz.tactical.client.audio;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public interface ICustomSoundSupplier {
   String FEEDBACK_SUFFIX = "_feedback";

   Map<String, ResourceLocation> getSounds();

   default ResourceLocation getSound(String key) {
      ResourceLocation sound = this.getSounds().get(key);
      if (sound == null && key.endsWith("_feedback")) {
         sound = this.getSounds().get(key.substring(0, key.length() - "_feedback".length()));
      }

      return sound;
   }
}
