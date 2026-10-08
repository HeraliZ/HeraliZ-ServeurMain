package com.mrcrayfish.backpacked.common.backpack;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Metadonnees de modele des sacs (fichiers assets/<ns>/backpacked/*.backpack, lus par ModelMetaLoader) : cote client
 * seulement. Separation client / serveur : etaient dans BackpackManager (clientModelMeta, updateModelMeta, getModelMeta),
 * memes valeurs et meme repli (ModelMeta.DEFAULT).
 */
public final class ModelMetas {
   private static Map<ResourceLocation, ModelMeta> clientModelMeta = new HashMap<>();

   private ModelMetas() {
   }

   public static void updateModelMeta(Map<ResourceLocation, ModelMeta> map) {
      clientModelMeta = map;
   }

   public static ModelMeta getModelMeta(Backpack backpack) {
      return clientModelMeta.getOrDefault(backpack.getId(), ModelMeta.DEFAULT);
   }
}
