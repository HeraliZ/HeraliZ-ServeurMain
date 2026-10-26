package com.hbm_m.client.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hbm_m.client.model.DoorBakedModel;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;

public class DoorModelLoader extends AbstractObjPartModelLoader<DoorBakedModel> {
   private static final Set<String> DEFAULT_PART_NAMES = Set.of("frame", "doorLeft", "doorRight");

   @Override
   protected Set<String> getPartNames(JsonObject jsonObject) {
      return jsonObject.has("parts")
         ? jsonObject.getAsJsonArray("parts").asList().stream().<String>map(JsonElement::getAsString).collect(Collectors.toSet())
         : DEFAULT_PART_NAMES;
   }

   protected DoorBakedModel createBakedModel(HashMap<String, BakedModel> bakedParts, ItemTransforms transforms, ResourceLocation modelLocation) {
      return new DoorBakedModel(bakedParts, transforms, modelLocation);
   }
}
