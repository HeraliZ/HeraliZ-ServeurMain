package fr.heralisz.tactical.client.resource.manager;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.tacz.guns.GunMod;
import com.tacz.guns.resource.manager.JsonDataManager;
import java.util.Map;
import java.util.Map.Entry;
import fr.heralisz.tactical.client.resource.display.MeleeDisplayInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

public class MeleeDisplayManager extends JsonDataManager<MeleeDisplayInstance> {
   public MeleeDisplayManager(Gson pGson) {
      super(null, pGson, "display/melee", "MeleeDisplay");
   }

   protected void apply(Map<ResourceLocation, JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
      this.dataMap.clear();

      for (Entry<ResourceLocation, JsonElement> entry : pObject.entrySet()) {
         ResourceLocation id = entry.getKey();
         JsonElement element = entry.getValue();

         try {
            MeleeDisplayInstance.MeleeDisplay pojo = (MeleeDisplayInstance.MeleeDisplay)this.getGson()
               .fromJson(element, MeleeDisplayInstance.MeleeDisplay.class);
            MeleeDisplayInstance data = MeleeDisplayInstance.create(pojo, id);
            this.dataMap.put(id, data);
         } catch (JsonParseException | IllegalArgumentException e) {
            GunMod.LOGGER.error(this.getMarker(), "Failed to load display file {}", id, e);
         }
      }
   }
}
