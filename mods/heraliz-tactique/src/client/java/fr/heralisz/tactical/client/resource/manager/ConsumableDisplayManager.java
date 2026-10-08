package fr.heralisz.tactical.client.resource.manager;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.tacz.guns.GunMod;
import com.tacz.guns.resource.manager.JsonDataManager;
import java.util.Map;
import java.util.Map.Entry;
import fr.heralisz.tactical.client.resource.display.ConsumableDisplayInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

public class ConsumableDisplayManager extends JsonDataManager<ConsumableDisplayInstance> {
   public ConsumableDisplayManager(Gson pGson) {
      super(null, pGson, "display/consumable", "ConsumableDisplay");
   }

   protected void apply(Map<ResourceLocation, JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
      this.dataMap.clear();

      for (Entry<ResourceLocation, JsonElement> entry : pObject.entrySet()) {
         ResourceLocation id = entry.getKey();
         JsonElement element = entry.getValue();

         try {
            ConsumableDisplayInstance.ConsumableDisplay pojo = (ConsumableDisplayInstance.ConsumableDisplay)this.getGson()
               .fromJson(element, ConsumableDisplayInstance.ConsumableDisplay.class);
            ConsumableDisplayInstance data = ConsumableDisplayInstance.create(pojo, id);
            this.dataMap.put(id, data);
         } catch (JsonParseException | IllegalArgumentException e) {
            GunMod.LOGGER.error(this.getMarker(), "Failed to load display file {}", id, e);
         }
      }
   }
}
