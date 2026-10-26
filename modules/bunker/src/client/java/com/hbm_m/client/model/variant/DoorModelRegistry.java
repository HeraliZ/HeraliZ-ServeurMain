package com.hbm_m.client.model.variant;

import com.hbm_m.block.entity.doors.variant.DoorModelType;
import com.hbm_m.block.entity.doors.variant.DoorSkin;
import com.hbm_m.block.entity.doors.variant.DoorModelSelection;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hbm_m.main.MainRegistry;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DoorModelRegistry implements ResourceManagerReloadListener {
   private static final DoorModelRegistry INSTANCE = new DoorModelRegistry();
   private static final String CONFIG_PATH = "models/block/doors/";
   private static final String CONFIG_SUFFIX = "_config.json";
   private final Map<String, DoorModelRegistry.DoorConfig> doorConfigs = new LinkedHashMap<>();
   private final Map<String, DoorModelSelection> defaultSelections = new LinkedHashMap<>();
   private boolean initialized = false;

   private DoorModelRegistry() {
   }

   public static DoorModelRegistry getInstance() {
      return INSTANCE;
   }

   public void register(String doorId, DoorModelRegistry.DoorConfig config) {
      this.doorConfigs.put(doorId, config);
      MainRegistry.LOGGER.info("Registered door config: {} with {} skins", doorId, config.getSkins().size());
   }

   public void setDefaultSelection(String doorId, DoorModelSelection selection) {
      this.defaultSelections.put(doorId, selection);
   }

   public void registerSimple(String doorId, ResourceLocation legacyModel, ResourceLocation modernModel, List<DoorSkin> modernSkins) {
      DoorModelRegistry.DoorConfig config = new DoorModelRegistry.DoorConfig(doorId, legacyModel, modernModel, modernSkins);
      this.register(doorId, config);
   }

   public boolean isRegistered(String doorId) {
      return this.doorConfigs.containsKey(doorId);
   }

   public DoorModelRegistry.DoorConfig getConfig(String doorId) {
      return this.doorConfigs.get(doorId);
   }

   public ResourceLocation getModelPath(String doorId, DoorModelType type) {
      DoorModelRegistry.DoorConfig config = this.doorConfigs.get(doorId);
      if (config == null) {
         return null;
      } else {
         return type.isLegacy() ? config.getLegacyModel() : config.getModernModel();
      }
   }

   public ResourceLocation getModelPath(String doorId, DoorModelSelection selection) {
      DoorModelRegistry.DoorConfig config = this.doorConfigs.get(doorId);
      if (config == null) {
         return null;
      }

      if (selection.getModelType().isLegacy()) {
         return config.getLegacyModel();
      }

      ResourceLocation skinModel = selection.getSkin().getModelPath();
      if (skinModel != null) {
         return skinModel;
      }

      if (!selection.getSkin().isDefault()) {
         DoorSkin resolvedSkin = config.getSkinById(selection.getSkin().getId());
         if (resolvedSkin != null && resolvedSkin.getModelPath() != null) {
            return resolvedSkin.getModelPath();
         }
      }

      return config.getModernModel();
   }

   public List<DoorSkin> getSkins(String doorId) {
      DoorModelRegistry.DoorConfig config = this.doorConfigs.get(doorId);
      return config == null ? Collections.singletonList(DoorSkin.DEFAULT) : config.getSkins();
   }

   public DoorSkin getSkin(String doorId, String skinId) {
      if (skinId != null && !skinId.isEmpty() && !"default".equals(skinId)) {
         DoorModelRegistry.DoorConfig config = this.doorConfigs.get(doorId);
         return config == null ? DoorSkin.DEFAULT : config.getSkinById(skinId);
      } else {
         return DoorSkin.DEFAULT;
      }
   }

   public DoorModelSelection getDefaultSelection(String doorId) {
      DoorModelSelection custom = this.defaultSelections.get(doorId);
      if (custom != null) {
         return custom;
      }

      DoorModelRegistry.DoorConfig config = this.doorConfigs.get(doorId);
      return config != null && config.getDefaultSelection() != null ? config.getDefaultSelection() : DoorModelSelection.DEFAULT;
   }

   public Set<String> getRegisteredDoorIds() {
      return Collections.unmodifiableSet(this.doorConfigs.keySet());
   }

   public void onResourceManagerReload(ResourceManager resourceManager) {
      MainRegistry.LOGGER.info("Reloading door model configurations...");
      Map<String, DoorModelSelection> savedDefaults = new LinkedHashMap<>(this.defaultSelections);
      this.doorConfigs.clear();
      this.loadAllConfigs(resourceManager);
      this.defaultSelections.putAll(savedDefaults);
      this.initialized = true;
      MainRegistry.LOGGER.info("Door model registry reloaded. {} door types registered", this.doorConfigs.size());
   }

   private void loadAllConfigs(ResourceManager resourceManager) {
      String[] knownDoorTypes = new String[]{
         "large_vehicle_door",
         "round_airlock_door",
         "fire_door",
         "sliding_blast_door",
         "sliding_seal_door",
         "secure_access_door",
         "qe_sliding_door",
         "qe_containment_door",
         "water_door",
         "silo_hatch",
         "silo_hatch_large",
         "vault_door",
         "cargo_door"
      };

      for (String namespace : resourceManager.getNamespaces()) {
         for (String doorType : knownDoorTypes) {
            ResourceLocation configPath = new ResourceLocation(namespace, "models/block/doors/" + doorType + "_config.json");

            try {
               Optional<Resource> resource = resourceManager.getResource(configPath);
               if (resource.isPresent()) {
                  this.loadConfigFromResource(resource.get(), configPath);
               }
            } catch (Exception var11) {
            }
         }
      }
   }

   private void loadConfigFromResource(Resource resource, ResourceLocation configPath) {
      try (BufferedReader reader = resource.openAsReader()) {
         JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
         DoorModelRegistry.DoorConfig config = DoorModelRegistry.DoorConfig.fromJson(json);
         this.doorConfigs.put(config.getDoorId(), config);
         MainRegistry.LOGGER.debug("Loaded door config: {} from {}", config.getDoorId(), configPath);
      } catch (IOException e) {
         MainRegistry.LOGGER.error("Failed to load door config: {}", configPath, e);
      }
   }

   public boolean isInitialized() {
      return this.initialized;
   }

   public void clearCache() {
   }

   public static class DoorConfig {
      private final String doorId;
      private final ResourceLocation legacyModel;
      private final ResourceLocation modernModel;
      private final List<DoorSkin> skins;
      private final Map<String, DoorSkin> skinMap;
      private DoorModelSelection defaultSelection;

      public DoorConfig(String doorId, ResourceLocation legacyModel, ResourceLocation modernModel, List<DoorSkin> skins) {
         this.doorId = doorId;
         this.legacyModel = legacyModel;
         this.modernModel = modernModel;
         this.skins = new ArrayList<>(skins);
         this.skinMap = new LinkedHashMap<>();

         for (DoorSkin skin : skins) {
            this.skinMap.put(skin.getId(), skin);
         }

         this.defaultSelection = DoorModelSelection.DEFAULT;
      }

      public String getDoorId() {
         return this.doorId;
      }

      public ResourceLocation getLegacyModel() {
         return this.legacyModel;
      }

      public ResourceLocation getModernModel() {
         return this.modernModel;
      }

      public List<DoorSkin> getSkins() {
         return Collections.unmodifiableList(this.skins);
      }

      public DoorModelSelection getDefaultSelection() {
         return this.defaultSelection;
      }

      public void setDefaultSelection(DoorModelSelection selection) {
         this.defaultSelection = selection;
      }

      public DoorSkin getSkinById(String id) {
         return this.skinMap.getOrDefault(id, DoorSkin.DEFAULT);
      }

      public static DoorModelRegistry.DoorConfig fromJson(JsonObject json) {
         String doorId = json.get("door_id").getAsString();
         ResourceLocation legacyModel = null;
         if (json.has("legacy_model")) {
            legacyModel = ResourceLocation.tryParse(json.get("legacy_model").getAsString());
         }

         ResourceLocation modernModel = null;
         if (json.has("modern_model")) {
            JsonObject modern = json.getAsJsonObject("modern_model");
            if (modern.has("model")) {
               modernModel = ResourceLocation.tryParse(modern.get("model").getAsString());
            }
         }

         List<DoorSkin> skins = new ArrayList<>();
         skins.add(DoorSkin.DEFAULT);
         if (json.has("modern_model")) {
            JsonObject modern = json.getAsJsonObject("modern_model");
            if (modern.has("skins")) {
               for (JsonElement elem : modern.getAsJsonArray("skins")) {
                  DoorSkin skin = DoorSkin.fromJson(elem.getAsJsonObject());
                  if (!skin.isDefault()) {
                     skins.add(skin);
                  }
               }
            }
         }

         DoorModelRegistry.DoorConfig config = new DoorModelRegistry.DoorConfig(doorId, legacyModel, modernModel, skins);
         if (json.has("default")) {
            JsonObject def = json.getAsJsonObject("default");
            DoorModelType type = DoorModelType.fromId(def.get("type").getAsString());
            String skinId = def.has("skin") ? def.get("skin").getAsString() : "default";
            DoorSkin skin = config.getSkinById(skinId);
            config.setDefaultSelection(new DoorModelSelection(type, skin));
         }

         return config;
      }
   }
}
