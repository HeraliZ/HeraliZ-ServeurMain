package com.hbm_m.block.entity.doors.variant;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class DoorSkin {
   public static final DoorSkin DEFAULT = new DoorSkin("default", null, null, Collections.emptyMap());
   private final String id;
   private final ResourceLocation texturePath;
   private final ResourceLocation modelPath;
   private final Map<String, ResourceLocation> textureMap;

   public DoorSkin(String id, ResourceLocation texturePath, ResourceLocation modelPath, Map<String, ResourceLocation> textureMap) {
      this.id = id;
      this.texturePath = texturePath;
      this.modelPath = modelPath;
      this.textureMap = textureMap != null ? textureMap : Collections.emptyMap();
   }

   public String getId() {
      return this.id;
   }

   public Component getDisplayName(String doorId) {
      String key = "door.skin.hbm_m." + doorId + "." + this.id;
      return Component.translatable(key);
   }

   public ResourceLocation getTexturePath() {
      return this.texturePath;
   }

   public ResourceLocation getTextureForPart(String materialName) {
      return this.textureMap.containsKey(materialName) ? this.textureMap.get(materialName) : this.texturePath;
   }

   public boolean isDefault() {
      return "default".equals(this.id);
   }

   public ResourceLocation getModelPath() {
      return this.modelPath;
   }

   public static DoorSkin fromJson(JsonObject json) {
      String id = json.get("id").getAsString();
      ResourceLocation texturePath = null;
      if (json.has("texture")) {
         texturePath = ResourceLocation.tryParse(json.get("texture").getAsString());
      }

      ResourceLocation modelPath = null;
      if (json.has("model")) {
         modelPath = ResourceLocation.tryParse(json.get("model").getAsString());
      }

      Map<String, ResourceLocation> textureMap = new HashMap<>();
      if (json.has("textures")) {
         JsonObject texturesObj = json.getAsJsonObject("textures");

         for (Entry<String, JsonElement> entry : texturesObj.entrySet()) {
            textureMap.put(entry.getKey(), ResourceLocation.tryParse(entry.getValue().getAsString()));
         }
      }

      return new DoorSkin(id, texturePath, modelPath, textureMap);
   }

   public static DoorSkin of(String id) {
      return new DoorSkin(id, null, null, Collections.emptyMap());
   }

   public static DoorSkin of(String id, String texturePath) {
      return new DoorSkin(id, texturePath != null ? ResourceLocation.tryParse(texturePath) : null, null, Collections.emptyMap());
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else {
         return obj instanceof DoorSkin other ? this.id.equals(other.id) : false;
      }
   }

   @Override
   public int hashCode() {
      return this.id.hashCode();
   }

   @Override
   public String toString() {
      return "DoorSkin{" + this.id + "}";
   }
}
